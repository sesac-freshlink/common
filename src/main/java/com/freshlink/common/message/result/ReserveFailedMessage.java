package com.freshlink.common.message.result;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageType;

import java.time.OffsetDateTime;

/** RESERVE_FAILED: 예약 실패. inventory-service → inventory-results */
public record ReserveFailedMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long orderId,
        Long storeId,
        Long productId,
        ReserveFailReason reason,
        int availableQuantity
) implements BaseMessage {

    @Override
    public MessageType type() {
        return MessageType.RESERVE_FAILED;
    }

    public static ReserveFailedMessage of(Long orderId, Long storeId, Long productId, ReserveFailReason reason, int availableQuantity) {
        return new ReserveFailedMessage(BaseMessage.newMessageId(), BaseMessage.now(), orderId, storeId, productId, reason, availableQuantity);
    }
}
