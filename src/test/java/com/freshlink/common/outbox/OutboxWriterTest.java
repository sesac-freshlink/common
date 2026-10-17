package com.freshlink.common.outbox;

import com.freshlink.common.message.MessageJson;
import com.freshlink.common.message.command.ReserveMessage;
import com.freshlink.common.message.result.ReserveFailReason;
import com.freshlink.common.message.result.ReserveFailedMessage;
import com.freshlink.common.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxWriterTest {

    private static final String FIFO = "dev-inventory-commands.fifo";
    private static final String STANDARD = "dev-inventory-results";

    private final JdbcTemplate jdbc = TestDatabase.jdbcTemplate();
    private final TransactionTemplate tx = TestDatabase.transactionTemplate();
    private final OutboxWriter writer = new OutboxWriter(jdbc);

    @BeforeEach
    void setUp() {
        TestDatabase.resetSchema();
    }

    @Test
    @DisplayName("트랜잭션 안에서 save 후 커밋하면 outbox 행 1개, published = false")
    void saveAndCommit() {
        ReserveMessage message = ReserveMessage.of(10L, 1L, 2L, 3);

        tx.executeWithoutResult(status -> writer.save(FIFO, message));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM outbox");
        assertThat(row.get("message_id")).isEqualTo(message.messageId());
        assertThat(row.get("message_type")).isEqualTo("RESERVE");
        assertThat(row.get("queue_name")).isEqualTo(FIFO);
        assertThat(row.get("payload")).isEqualTo(MessageJson.toJson(message));
        assertThat(row.get("published")).isEqualTo(false);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("trace_parent")).isNull();
        assertThat(row.get("published_at")).isNull();
    }

    @Test
    @DisplayName("같은 트랜잭션이 롤백되면 outbox 행이 없다")
    void rollback() {
        tx.executeWithoutResult(status -> {
            writer.save(FIFO, ReserveMessage.of(10L, 1L, 2L, 3));
            status.setRollbackOnly();
        });

        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 save를 부르면 예외")
    void outsideTransaction() {
        assertThatThrownBy(() -> writer.save(FIFO, ReserveMessage.of(10L, 1L, 2L, 3)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("FIFO 큐면 group_id = storeId 문자열, 표준 큐면 NULL")
    void groupId() {
        tx.executeWithoutResult(status -> {
            writer.save(FIFO, ReserveMessage.of(10L, 1L, 2L, 3));
            writer.save(STANDARD, ReserveFailedMessage.of(10L, 1L, 2L, ReserveFailReason.OUT_OF_STOCK, 0));
        });

        assertThat(jdbc.queryForObject("SELECT group_id FROM outbox WHERE queue_name = ?", String.class, FIFO))
                .isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT group_id FROM outbox WHERE queue_name = ?", String.class, STANDARD))
                .isNull();
    }

    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class);
    }
}
