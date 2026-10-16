package com.freshlink.common.message.command;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageType;

import java.time.OffsetDateTime;

/** RESERVE: 주문 생성 시 재고 예약 요청. order-service → inventory-commands.fifo */
public record ReserveMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long orderId,
        Long storeId,
        Long productId,
        int quantity
) implements BaseMessage {

    @Override
    public MessageType type() {
        return MessageType.RESERVE;
    }

    public static ReserveMessage of(Long orderId, Long storeId, Long productId, int quantity) {
        return new ReserveMessage(BaseMessage.newMessageId(), BaseMessage.now(), orderId, storeId, productId, quantity);
    }
}
