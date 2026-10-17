package com.freshlink.common.outbox;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * 쪽지를 비즈니스 변경과 같은 트랜잭션으로 {@code outbox} 표에 적는다. 실제 SQS 전송은 {@link OutboxPublisher}가 한다.
 *
 * <pre>{@code
 * @Transactional
 * public Order create(...) {
 *     Order order = orderRepository.save(...);
 *     outboxWriter.save(commandsQueue, ReserveMessage.of(order.getId(), storeId, productId, quantity));
 *     return order;
 * }
 * }</pre>
 *
 * <p>트랜잭션 전파 MANDATORY와 같다. 트랜잭션 밖에서 부르면 {@link IllegalTransactionStateException}이 난다.
 * "같은 트랜잭션"을 실수로 빼먹지 못하게 하려는 것이라 프록시 설정과 상관없이 코드에서 직접 확인한다.
 */
public class OutboxWriter {

    private static final String INSERT_SQL = """
            INSERT INTO outbox (message_id, message_type, queue_name, group_id, payload, published, created_at)
            VALUES (?, ?, ?, ?, ?, FALSE, NOW(6))
            """;

    private final JdbcTemplate jdbcTemplate;

    public OutboxWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param queueName 실제 큐 이름(예: {@code dev-inventory-commands.fifo}). 서비스 설정 값으로 받는다
     * @param message   보낼 쪽지. {@code .fifo} 큐면 그룹 ID = storeId, 중복 제거 ID = messageId
     */
    public void save(String queueName, BaseMessage message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "OutboxWriter.save는 @Transactional 안에서만 부를 수 있다. 비즈니스 변경과 같은 트랜잭션이어야 한다");
        }
        Objects.requireNonNull(message, "message");
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("queueName이 비어 있다");
        }
        String groupId = isFifo(queueName) ? message.messageGroupId() : null;
        jdbcTemplate.update(INSERT_SQL,
                message.messageId(), message.type().name(), queueName, groupId, MessageJson.toJson(message));
    }

    static boolean isFifo(String queueName) {
        return queueName.endsWith(".fifo");
    }
}
