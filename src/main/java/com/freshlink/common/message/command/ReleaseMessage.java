package com.freshlink.common.message.command;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageType;

import java.time.OffsetDateTime;

/** RELEASE: 결제 실패·시간 초과·결제 전 고객 취소로 예약 해제. order-service → inventory-commands.fifo */
public record ReleaseMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long orderId,
        Long storeId,
        ReleaseReason reason
) implements BaseMessage {

    @Override
    public MessageType type() {
        return MessageType.RELEASE;
    }

    public static ReleaseMessage of(Long orderId, Long storeId, ReleaseReason reason) {
        return new ReleaseMessage(BaseMessage.newMessageId(), BaseMessage.now(), orderId, storeId, reason);
    }
}
