package com.freshlink.common.message;

import java.time.OffsetDateTime;

/** RELEASE: 결제 실패나 시간 초과로 예약 해제. order-service → inventory-commands.fifo */
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
