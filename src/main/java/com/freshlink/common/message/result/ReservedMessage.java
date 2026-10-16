package com.freshlink.common.message.result;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageType;

import java.time.OffsetDateTime;
import java.util.List;

/** RESERVED: 예약 성공. inventory-service → inventory-results. unitPrice(원 단위)·productName은 예약 시점 값이고, order가 스냅샷으로 저장한다 */
public record ReservedMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long orderId,
        Long storeId,
        Long productId,
        String productName,
        int quantity,
        long unitPrice,
        List<ReservedLot> lots
) implements BaseMessage {

    public ReservedMessage {
        lots = lots == null ? List.of() : List.copyOf(lots);
    }

    @Override
    public MessageType type() {
        return MessageType.RESERVED;
    }

    public static ReservedMessage of(Long orderId, Long storeId, Long productId, String productName, int quantity, long unitPrice, List<ReservedLot> lots) {
        return new ReservedMessage(BaseMessage.newMessageId(), BaseMessage.now(), orderId, storeId, productId, productName, quantity, unitPrice, lots);
    }
}
