package com.freshlink.common.message.command;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageType;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import java.time.OffsetDateTime;

/**
 * RESERVE: 주문 생성 시 재고 예약 요청. order-service → inventory-commands.fifo.
 *
 * <p>{@code quantity}는 주문 전체 수량이고, {@code clearanceQuantity}는 그중 마감세일 재고에서 잡을 수량이다(v0.8.0, 마감세일 함께 사기).
 * 기본 재고에서 잡을 수량은 {@code quantity - clearanceQuantity}다. 칸이 없는 옛 쪽지는 0으로 읽는다(마감세일 없음).
 * Jackson 3은 기본으로 없는 int 칸을 실패로 보므로 {@code @JsonSetter(nulls = AS_EMPTY)}로 0을 넣는다.
 */
public record ReserveMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long orderId,
        Long storeId,
        Long productId,
        int quantity,
        @JsonSetter(nulls = Nulls.AS_EMPTY) int clearanceQuantity
) implements BaseMessage {

    /** v0.7.x까지의 모양. 마감세일 수량 0 */
    public ReserveMessage(String messageId, OffsetDateTime createdAt, Long orderId, Long storeId, Long productId, int quantity) {
        this(messageId, createdAt, orderId, storeId, productId, quantity, 0);
    }

    @Override
    public MessageType type() {
        return MessageType.RESERVE;
    }

    /** 기본 재고에서 잡을 수량 = 전체 − 마감세일 */
    public int regularQuantity() {
        return quantity - clearanceQuantity;
    }

    public static ReserveMessage of(Long orderId, Long storeId, Long productId, int quantity) {
        return of(orderId, storeId, productId, quantity, 0);
    }

    /** 마감세일 함께 사기. quantity = 전체 수량, clearanceQuantity = 그중 마감세일 수량 */
    public static ReserveMessage of(Long orderId, Long storeId, Long productId, int quantity, int clearanceQuantity) {
        return new ReserveMessage(BaseMessage.newMessageId(), BaseMessage.now(), orderId, storeId, productId, quantity, clearanceQuantity);
    }
}
