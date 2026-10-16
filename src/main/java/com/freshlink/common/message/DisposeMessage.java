package com.freshlink.common.message;

import java.time.OffsetDateTime;

/** DISPOSE: 점주의 폐기 요청. inventory-service → inventory-commands.fifo (같은 점포 큐를 거쳐 순서를 지킨다) */
public record DisposeMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long disposalId,
        Long storeId,
        Long lotId,
        int quantity,
        String requestedBy
) implements BaseMessage {

    @Override
    public MessageType type() {
        return MessageType.DISPOSE;
    }

    /** @param requestedBy 점주의 Cognito sub (UUID 문자열) */
    public static DisposeMessage of(Long disposalId, Long storeId, Long lotId, int quantity, String requestedBy) {
        return new DisposeMessage(BaseMessage.newMessageId(), BaseMessage.now(), disposalId, storeId, lotId, quantity, requestedBy);
    }
}
