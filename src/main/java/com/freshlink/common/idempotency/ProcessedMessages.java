package com.freshlink.common.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 받은 쪽지의 messageId를 {@code processed_message}에 적어 같은 쪽지를 두 번 반영하지 않게 한다.
 *
 * <pre>{@code
 * @Transactional
 * public void handle(BaseMessage message) {
 *     if (!processedMessages.markIfNew(message.messageId())) {
 *         return;                     // 이미 처리한 쪽지
 *     }
 *     ...                             // 비즈니스 처리 (같은 트랜잭션)
 * }
 * }</pre>
 *
 * <p>트랜잭션 전파 MANDATORY와 같다. 기록과 비즈니스 처리가 한 트랜잭션이어야 처리 중 실패하면 기록도 같이 롤백되고,
 * SQS가 다시 보낸 쪽지를 정상으로 처리한다.
 *
 * <p>{@code INSERT IGNORE} 뒤 영향받은 행 수로 판단한다(1 = 새것, 0 = 중복). 중복 키 예외를 잡는 방식은 바깥 트랜잭션을
 * 롤백 전용으로 만들 수 있어서 쓰지 않는다. 두 파드가 같은 쪽지를 동시에 받으면 뒤쪽 INSERT가 앞 트랜잭션이 끝날 때까지 기다린다.
 */
public class ProcessedMessages {

    static final int MAX_MESSAGE_ID_LENGTH = 36;

    private static final String INSERT_SQL =
            "INSERT IGNORE INTO processed_message (message_id, processed_at) VALUES (?, NOW(6))";

    private final JdbcTemplate jdbcTemplate;

    public ProcessedMessages(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return 처음 보는 messageId면 기록하고 {@code true}, 이미 있으면 {@code false}
     */
    public boolean markIfNew(String messageId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "ProcessedMessages.markIfNew는 @Transactional 안에서만 부를 수 있다. 쪽지 처리와 같은 트랜잭션이어야 한다");
        }
        // INSERT IGNORE는 너무 긴 값을 오류 대신 잘라서 넣는다. 서로 다른 ID가 같은 값으로 잘리지 않게 먼저 막는다
        if (messageId == null || messageId.isBlank() || messageId.length() > MAX_MESSAGE_ID_LENGTH) {
            throw new IllegalArgumentException("messageId는 1~36자여야 한다: " + messageId);
        }
        return jdbcTemplate.update(INSERT_SQL, messageId) == 1;
    }
}
