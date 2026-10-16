package com.freshlink.common.message;

import java.time.OffsetDateTime;

/** CONFIRM: 결제 완료 후 예약 확정. order-service → inventory-commands.fifo */
public record ConfirmMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long orderId,
        Long storeId
) implements BaseMessage {

    @Override
    public MessageType type() {
        return MessageType.CONFIRM;
    }

    public static ConfirmMessage of(Long orderId, Long storeId) {
        return new ConfirmMessage(BaseMessage.newMessageId(), BaseMessage.now(), orderId, storeId);
    }
}
