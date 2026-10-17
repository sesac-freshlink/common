package com.freshlink.common.outbox;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@code outbox}에서 아직 안 보낸 행을 저장 순서({@code id})대로 꺼내 SQS로 보낸다.
 *
 * <ul>
 *   <li>common이 가진 스레드 1개로 {@code publish-interval}마다 돈다. 서비스의 {@code @EnableScheduling}에 기대지 않는다.</li>
 *   <li>매 회차를 ShedLock {@code freshlink-outbox-publisher} 잠금으로 감싼다. 파드가 여러 개여도 한 번에 한 파드만 보내서 순서가 지켜진다.</li>
 *   <li>보내다 실패하면 그 회차를 거기서 멈춘다. 다음 행을 먼저 보내면 같은 점포 안에서 순서가 바뀐다. 실패한 행은 다음 회차에 다시 보낸다.</li>
 *   <li>보낸 뒤 {@code published = true}로 바꾸기 전에 파드가 죽으면 같은 쪽지가 한 번 더 나간다. 받는 쪽은 {@code ProcessedMessages}로 거른다.</li>
 * </ul>
 */
public class OutboxPublisher implements SmartLifecycle {

    public static final String LOCK_NAME = "freshlink-outbox-publisher";
    static final Duration LOCK_AT_MOST_FOR = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final String SELECT_SQL = """
            SELECT id, message_id, queue_name, group_id, payload
            FROM outbox
            WHERE published = FALSE
            ORDER BY id
            LIMIT ?
            """;
    private static final String MARK_PUBLISHED_SQL =
            "UPDATE outbox SET published = TRUE, published_at = NOW(6) WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final SqsTemplate sqsTemplate;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final OutboxProperties properties;
    private final Clock clock;

    private ScheduledExecutorService scheduler;

    public OutboxPublisher(JdbcTemplate jdbcTemplate, SqsTemplate sqsTemplate,
                           LockingTaskExecutor lockingTaskExecutor, OutboxProperties properties, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqsTemplate = sqsTemplate;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 한 회차. 잠금을 잡으면 최대 {@code batch-size}개를 보내고 보낸 개수를 돌려준다. 잠금을 못 잡으면 0.
     */
    public int publishOnce() {
        LockConfiguration lock = new LockConfiguration(clock.instant(), LOCK_NAME, LOCK_AT_MOST_FOR, Duration.ZERO);
        try {
            LockingTaskExecutor.TaskResult<Integer> result = lockingTaskExecutor.executeWithLock(this::publishBatch, lock);
            return result.wasExecuted() && result.getResult() != null ? result.getResult() : 0;
        } catch (Throwable e) {
            log.warn("Outbox 발행 회차 실패: {}", e.toString(), e);
            return 0;
        }
    }

    private int publishBatch() {
        List<OutboxRow> rows = jdbcTemplate.query(SELECT_SQL, (rs, i) -> new OutboxRow(
                rs.getLong("id"), rs.getString("message_id"), rs.getString("queue_name"),
                rs.getString("group_id"), rs.getString("payload")), properties.batchSize());
        int sent = 0;
        for (OutboxRow row : rows) {
            try {
                send(row);
            } catch (RuntimeException e) {
                log.warn("Outbox 쪽지 전송 실패, 이번 회차를 멈추고 다음 회차에 다시 보낸다. message_id={}, queue_name={}: {}",
                        row.messageId(), row.queueName(), e.toString());
                return sent;
            }
            jdbcTemplate.update(MARK_PUBLISHED_SQL, row.id());
            sent++;
        }
        return sent;
    }

    private void send(OutboxRow row) {
        sqsTemplate.send(to -> {
            to.queue(row.queueName()).payload(row.payload());
            if (OutboxWriter.isFifo(row.queueName())) {
                to.messageGroupId(row.groupId()).messageDeduplicationId(row.messageId());
            }
        });
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "freshlink-outbox-publisher");
            t.setDaemon(true);
            return t;
        });
        long interval = properties.publishInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::publishOnce, interval, interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        scheduler = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return scheduler != null;
    }

    private record OutboxRow(long id, String messageId, String queueName, String groupId, String payload) {
    }
}
