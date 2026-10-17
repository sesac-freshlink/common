package com.freshlink.common.idempotency;

import com.freshlink.common.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessedMessagesTest {

    private final JdbcTemplate jdbc = TestDatabase.jdbcTemplate();
    private final TransactionTemplate tx = TestDatabase.transactionTemplate();
    private final ProcessedMessages processed = new ProcessedMessages(jdbc);

    @BeforeEach
    void setUp() {
        TestDatabase.resetSchema();
    }

    @Test
    @DisplayName("같은 messageId로 두 번 부르면 true, false")
    void markTwice() {
        String id = UUID.randomUUID().toString();

        assertThat(mark(id)).isTrue();
        assertThat(mark(id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_message", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("markIfNew 후 트랜잭션이 롤백되면 다시 불렀을 때 true")
    void rollbackForgets() {
        String id = UUID.randomUUID().toString();

        tx.executeWithoutResult(status -> {
            assertThat(processed.markIfNew(id)).isTrue();
            status.setRollbackOnly();
        });

        assertThat(mark(id)).isTrue();
    }

    @Test
    @DisplayName("중복이어서 false가 나와도 바깥 트랜잭션은 정상 커밋된다")
    void duplicateDoesNotMarkRollbackOnly() {
        String id = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> processed.markIfNew(id));
        AtomicBoolean rollbackOnly = new AtomicBoolean(true);

        tx.executeWithoutResult(status -> {
            assertThat(processed.markIfNew(id)).isFalse();
            jdbc.update("INSERT INTO processed_message (message_id, processed_at) VALUES ('after-duplicate', NOW(6))");
            rollbackOnly.set(status.isRollbackOnly());
        });

        assertThat(rollbackOnly).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_message WHERE message_id = 'after-duplicate'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 예외")
    void outsideTransaction() {
        assertThatThrownBy(() -> processed.markIfNew(UUID.randomUUID().toString()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("비었거나 36자를 넘는 messageId는 거절한다 (INSERT IGNORE가 잘라 넣지 않게)")
    void invalidMessageId() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> processed.markIfNew("x".repeat(37))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> processed.markIfNew(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private boolean mark(String messageId) {
        Boolean result = tx.execute(status -> processed.markIfNew(messageId));
        return Boolean.TRUE.equals(result);
    }
}
