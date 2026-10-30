package com.freshlink.common.message.notification;

/**
 * 알림 종류 (10/30 확정 세 가지 + 10/30 회의로 더한 주문 알림 두 가지). 받는 사람은 notification-service가 종류로 정한다.
 */
public enum NotificationKind {
    /** 점주: 유통기한이 24시간 안으로 들어온 lot (inventory 유통기한 스케줄러가 IMMINENT로 바꿀 때) */
    EXPIRY_IMMINENT,
    /** 점주: 유통기한이 지난 lot (inventory 유통기한 스케줄러가 EXPIRED로 바꿀 때) */
    EXPIRED,
    /** 고객: 품절이던 상품이 입고로 다시 팔 수 있게 됨. 재입고 알림을 신청한 고객에게 간다 */
    RESTOCKED,
    /** 점주: 결제가 끝난 새 픽업 주문 (order가 PAID로 바꿀 때, v0.7.0) */
    NEW_ORDER,
    /** 고객: 점주가 상품을 포장해 픽업 준비를 끝냄 (order가 픽업 준비 완료로 바꿀 때, v0.7.0). userId = 주문한 고객 */
    PICKUP_READY
}
