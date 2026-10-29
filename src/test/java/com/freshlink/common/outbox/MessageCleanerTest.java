package com.freshlink.common.outbox;

import com.freshlink.common.support.TestDatabase;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCleanerTest {

    private final JdbcTemplate jdbc = TestDatabase.jdbcTemplate();
    private final JdbcTemplateLockProvider lockProvider = new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder().withJdbcTemplate(jdbc).usingDbTime().build());

    @BeforeEach
    void setUp() {
        TestDatabase.resetSchema();
    }

    @Test
    @DisplayName("보낸 지 7일 넘은 줄만 지우고, 안 보낸 줄은 오래돼도 남긴다")
    void deletesOnlyOldPublishedRows() {
        long oldPublished = outbox(true, 8);
        long recentPublished = outbox(true, 6);
        long oldUnpublished = outbox(false, 30);

        MessageCleaner.Result result = cleaner(properties(1000)).cleanOnce();

        assertThat(result.outbox()).isEqualTo(1);
        assertThat(outboxIds()).containsExactly(recentPublished, oldUnpublished).doesNotContain(oldPublished);
    }

    @Test
    @DisplayName("처리 기록은 30일 넘은 것만 지운다")
    void deletesOldProcessedMessages() {
        processed(31);
        processed(29);

        MessageCleaner.Result result = cleaner(properties(1000)).cleanOnce();

        assertThat(result.processedMessage()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_message", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("batch-size보다 많으면 여러 번 나눠 모두 지운다")
    void deletesInBatches() {
        for (int i = 0; i < 5; i++) {
            outbox(true, 10);
            processed(40);
        }

        MessageCleaner.Result result = cleaner(properties(2)).cleanOnce();

        assertThat(result).isEqualTo(new MessageCleaner.Result(5, 5));
        assertThat(outboxIds()).isEmpty();
    }

    @Test
    @DisplayName("다른 파드가 잠금을 잡고 있으면 지우지 않는다. 발행기 잠금과는 이름이 달라 서로 막지 않는다")
    void skipsWhenLocked() {
        outbox(true, 10);
        Optional<SimpleLock> held = lockProvider.lock(new LockConfiguration(Instant.now(), MessageCleaner.LOCK_NAME,
                Duration.ofMinutes(1), Duration.ZERO));
        assertThat(held).isPresent();

        assertThat(cleaner(properties(1000)).cleanOnce()).isNull();
        assertThat(outboxIds()).hasSize(1);
        assertThat(MessageCleaner.LOCK_NAME).isNotEqualTo(OutboxPublisher.LOCK_NAME);
        held.get().unlock();
    }

    @Test
    @DisplayName("처리 기록 보관이 18일(SQS 본 큐 + DLQ) 이하면 설정 오류")
    void processedRetentionMustOutliveSqs() {
        assertThatThrownBy(() -> new CleanupProperties(true, Duration.ofHours(1), Duration.ofDays(7), Duration.ofDays(14), 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processed-retention");
    }

    private MessageCleaner cleaner(CleanupProperties properties) {
        return new MessageCleaner(jdbc, new DefaultLockingTaskExecutor(lockProvider), properties, Clock.systemUTC());
    }

    private static CleanupProperties properties(int batchSize) {
        return new CleanupProperties(true, Duration.ofHours(1), Duration.ofDays(7), Duration.ofDays(30), batchSize);
    }

    private long outbox(boolean published, int daysAgo) {
        jdbc.update("""
                INSERT INTO outbox (message_id, message_type, queue_name, payload, published, created_at, published_at)
                VALUES (?, 'RESERVE', 'dev-inventory-commands.fifo', '{}', ?,
                        NOW(6) - INTERVAL ? DAY, IF(?, NOW(6) - INTERVAL ? DAY, NULL))
                """, UUID.randomUUID().toString(), published, daysAgo, published, daysAgo);
        return jdbc.queryForObject("SELECT MAX(id) FROM outbox", Long.class);
    }

    private void processed(int daysAgo) {
        jdbc.update("INSERT INTO processed_message (message_id, processed_at) VALUES (?, NOW(6) - INTERVAL ? DAY)",
                UUID.randomUUID().toString(), daysAgo);
    }

    private java.util.List<Long> outboxIds() {
        return jdbc.queryForList("SELECT id FROM outbox ORDER BY id", Long.class);
    }
}
