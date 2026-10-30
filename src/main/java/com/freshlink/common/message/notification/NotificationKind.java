package com.freshlink.common.message.notification;

/**
 * 알림 종류 (10/30 확정, 이 세 가지만 한다). 받는 사람은 notification-service가 종류로 정한다.
 */
public enum NotificationKind {
    /** 점주: 유통기한이 24시간 안으로 들어온 lot (inventory 유통기한 스케줄러가 IMMINENT로 바꿀 때) */
    EXPIRY_IMMINENT,
    /** 점주: 유통기한이 지난 lot (inventory 유통기한 스케줄러가 EXPIRED로 바꿀 때) */
    EXPIRED,
    /** 고객: 품절이던 상품이 입고로 다시 팔 수 있게 됨. 재입고 알림을 신청한 고객에게 간다 */
    RESTOCKED
}
