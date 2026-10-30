package com.freshlink.common.message.command;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageType;

import java.time.OffsetDateTime;

/**
 * RETURN: 환불(A9) 뒤 확정된 판매 재고를 되돌린다. order-service → inventory-commands.fifo (A10, v0.5.0)
 *
 * <p>order는 orderId·storeId만 보낸다. inventory가 그 주문의 판매 기록(SALE, refId = orderId)으로 원래 lot과 수량을 찾고,
 * 주문당 한 번만 반영한다(같은 주문의 RETURN이 messageId가 달라도 두 번 복원하지 않는다).
 */
public record ReturnMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long orderId,
        Long storeId
) implements BaseMessage {

    @Override
    public MessageType type() {
        return MessageType.RETURN;
    }

    public static ReturnMessage of(Long orderId, Long storeId) {
        return new ReturnMessage(BaseMessage.newMessageId(), BaseMessage.now(), orderId, storeId);
    }
}
