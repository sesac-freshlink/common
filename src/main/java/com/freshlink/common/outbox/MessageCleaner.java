package com.freshlink.common.outbox;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 오래된 보낸 줄({@code outbox}, {@code published = TRUE})과 처리 기록({@code processed_message})을 지운다 (v0.4.0).
 *
 * <ul>
 *   <li>common이 가진 스레드 1개로 {@code interval}마다 돈다. 첫 회차는 뜬 뒤 1분 뒤.</li>
 *   <li>ShedLock 잠금 {@code freshlink-message-cleanup}으로 감싸 한 파드만 지운다. 발행기 잠금과 이름이 달라서 지우는 동안에도 발행은 멈추지 않는다.</li>
 *   <li>안 보낸 행({@code published = FALSE})은 오래돼도 지우지 않는다.</li>
 *   <li>{@code batch-size}개씩 나눠 지워 잠금·undo 로그를 짧게 유지한다. 시각 비교는 DB 시계({@code NOW(6)})로 한다.</li>
 * </ul>
 */
public class MessageCleaner implements SmartLifecycle {

    public static final String LOCK_NAME = "freshlink-message-cleanup";
    static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(10);
    private static final Duration FIRST_DELAY = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(MessageCleaner.class);

    private static final String DELETE_OUTBOX_SQL = """
            DELETE FROM outbox
            WHERE published = TRUE AND published_at < NOW(6) - INTERVAL ? SECOND
            ORDER BY id
            LIMIT ?
            """;
    private static final String DELETE_PROCESSED_SQL = """
            DELETE FROM processed_message
            WHERE processed_at < NOW(6) - INTERVAL ? SECOND
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final CleanupProperties properties;
    private final Clock clock;

    private ScheduledExecutorService scheduler;

    public MessageCleaner(JdbcTemplate jdbcTemplate, LockingTaskExecutor lockingTaskExecutor,
                          CleanupProperties properties, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 한 회차. 잠금을 잡으면 두 표를 정리하고 지운 행 수를 돌려준다. 잠금을 못 잡거나 실패하면 null.
     */
    public Result cleanOnce() {
        LockConfiguration lock = new LockConfiguration(clock.instant(), LOCK_NAME, LOCK_AT_MOST_FOR, Duration.ZERO);
        try {
            LockingTaskExecutor.TaskResult<Result> result = lockingTaskExecutor.executeWithLock(this::clean, lock);
            return result.wasExecuted() ? result.getResult() : null;
        } catch (Throwable e) {
            log.warn("보낸 줄·처리 기록 정리 회차 실패: {}", e.toString(), e);
            return null;
        }
    }

    private Result clean() {
        int outbox = deleteAll(DELETE_OUTBOX_SQL, properties.outboxRetention());
        int processed = deleteAll(DELETE_PROCESSED_SQL, properties.processedRetention());
        if (outbox > 0 || processed > 0) {
            log.info("보낸 줄·처리 기록 정리: outbox {}행, processed_message {}행", outbox, processed);
        }
        return new Result(outbox, processed);
    }

    private int deleteAll(String sql, Duration retention) {
        int total = 0;
        int deleted;
        do {
            deleted = jdbcTemplate.update(sql, retention.toSeconds(), properties.batchSize());
            total += deleted;
        } while (deleted == properties.batchSize());
        return total;
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "freshlink-message-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::cleanOnce, FIRST_DELAY.toMillis(),
                properties.interval().toMillis(), TimeUnit.MILLISECONDS);
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

    /** 한 회차에 지운 행 수 */
    public record Result(int outbox, int processedMessage) {
    }
}
