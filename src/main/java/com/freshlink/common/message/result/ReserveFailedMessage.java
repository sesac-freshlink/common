package com.freshlink.common.message.result;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageType;

import java.time.OffsetDateTime;

/**
 * RESERVE_FAILED: 예약 실패. inventory-service → inventory-results.
 *
 * <p>{@code availableQuantity}는 기본 재고의 남은 수량이다. {@code clearanceAvailableQuantity}(v0.8.0)는 마감세일 재고의 남은 수량이고,
 * 마감세일을 요청한 주문의 실패에만 넣는다(그 밖에는 null). 사유 {@code CLEARANCE_SOLD_OUT}와 같이 쓰면 화면이 "보는 사이 마감세일이 다 팔렸어요"를 구분한다.
 */
public record ReserveFailedMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long orderId,
        Long storeId,
        Long productId,
        ReserveFailReason reason,
        int availableQuantity,
        Integer clearanceAvailableQuantity
) implements BaseMessage {

    /** v0.7.x까지의 모양. 마감세일 남은 수량 없음(null) */
    public ReserveFailedMessage(String messageId, OffsetDateTime createdAt, Long orderId, Long storeId, Long productId,
                                ReserveFailReason reason, int availableQuantity) {
        this(messageId, createdAt, orderId, storeId, productId, reason, availableQuantity, null);
    }

    @Override
    public MessageType type() {
        return MessageType.RESERVE_FAILED;
    }

    public static ReserveFailedMessage of(Long orderId, Long storeId, Long productId, ReserveFailReason reason, int availableQuantity) {
        return of(orderId, storeId, productId, reason, availableQuantity, null);
    }

    /** 마감세일을 요청한 주문의 실패. clearanceAvailableQuantity = 마감세일 재고의 남은 수량 */
    public static ReserveFailedMessage of(Long orderId, Long storeId, Long productId, ReserveFailReason reason,
                                          int availableQuantity, Integer clearanceAvailableQuantity) {
        return new ReserveFailedMessage(BaseMessage.newMessageId(), BaseMessage.now(), orderId, storeId, productId,
                reason, availableQuantity, clearanceAvailableQuantity);
    }
}
