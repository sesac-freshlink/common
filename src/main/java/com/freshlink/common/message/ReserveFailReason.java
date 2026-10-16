package com.freshlink.common.message;

/** RESERVE_FAILED 쪽지의 실패 사유 */
public enum ReserveFailReason {
    OUT_OF_STOCK,
    PRODUCT_NOT_FOUND,
    INVALID_QUANTITY
}
