package com.freshlink.common.message.notification;

import com.freshlink.common.message.BaseMessage;
import com.freshlink.common.message.MessageType;

import java.time.OffsetDateTime;

/**
 * NOTIFICATION: 화면 알림을 만들 일이 생겼다. inventory-service(·order-service) → notification-events (표준 큐, v0.6.0)
 *
 * <p>보내는 쪽은 "무슨 일이 있었는지"만 싣는다. 알림 문구와 받는 사람은 notification-service가 정한다.
 * <ul>
 *   <li>EXPIRY_IMMINENT·EXPIRED: 그 점포({@code storeId}) 점주에게. {@code lotId}·{@code expiresAt}·{@code quantity}를 채운다</li>
 *   <li>RESTOCKED: 그 점포·상품에 재입고 알림을 신청한 고객에게. {@code quantity}는 입고 뒤 팔 수 있는 수량</li>
 * </ul>
 * {@code userId}는 특정 고객 한 명에게 보낼 때만 채운다(Cognito sub). 지금 세 종류는 모두 null이다.
 *
 * <p>멱등 키는 {@code messageId}다. notification-service는 같은 messageId를 두 번 받아도 알림을 한 번만 만든다.
 */
public record NotificationMessage(
        String messageId,
        OffsetDateTime createdAt,
        NotificationKind kind,
        Long storeId,
        String userId,
        Long productId,
        String productName,
        Long lotId,
        OffsetDateTime expiresAt,
        Integer quantity
) implements BaseMessage {

    @Override
    public MessageType type() {
        return MessageType.NOTIFICATION;
    }

    /** 유통기한 임박(24시간 이내) lot 하나 */
    public static NotificationMessage expiryImminent(Long storeId, Long productId, String productName,
                                                     Long lotId, OffsetDateTime expiresAt, int quantity) {
        return new NotificationMessage(BaseMessage.newMessageId(), BaseMessage.now(), NotificationKind.EXPIRY_IMMINENT,
                storeId, null, productId, productName, lotId, expiresAt, quantity);
    }

    /** 유통기한 경과 lot 하나 */
    public static NotificationMessage expired(Long storeId, Long productId, String productName,
                                              Long lotId, OffsetDateTime expiresAt, int quantity) {
        return new NotificationMessage(BaseMessage.newMessageId(), BaseMessage.now(), NotificationKind.EXPIRED,
                storeId, null, productId, productName, lotId, expiresAt, quantity);
    }

    /** 품절(팔 수 있는 수량 0)이던 상품이 입고로 다시 팔 수 있게 됨 */
    public static NotificationMessage restocked(Long storeId, Long productId, String productName, int quantity) {
        return new NotificationMessage(BaseMessage.newMessageId(), BaseMessage.now(), NotificationKind.RESTOCKED,
                storeId, null, productId, productName, null, null, quantity);
    }
}
