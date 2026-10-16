package com.freshlink.common.message;

/** RELEASE 쪽지의 해제 사유 */
public enum ReleaseReason {
    /** 결제 실패 */
    PAYMENT_FAILED,
    /** 결제 대기 시간 초과 */
    EXPIRED
}
