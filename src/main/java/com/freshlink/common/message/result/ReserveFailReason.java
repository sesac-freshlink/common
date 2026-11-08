package com.freshlink.common.message.result;

/** RESERVE_FAILED 쪽지의 실패 사유 */
public enum ReserveFailReason {
    OUT_OF_STOCK,
    PRODUCT_NOT_FOUND,
    INVALID_QUANTITY,
    /** v0.8.0: 요청한 마감세일 수량만큼 마감세일 재고가 없다(보는 사이 다 팔림). 기본가로 바꾸지 않고 실패로 돌린다 */
    CLEARANCE_SOLD_OUT
}
