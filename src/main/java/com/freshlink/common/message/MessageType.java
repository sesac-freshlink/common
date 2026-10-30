package com.freshlink.common.message;

/**
 * 쪽지 종류. JSON의 {@code type} 값과 같다.
 * 명세는 .github 레포 docs/인수인계/백엔드-공통작업.md 3-3.
 */
public enum MessageType {
    /** 물류센터 입고 (logistics-simulator → inventory-commands.fifo) */
    INBOUND,
    /** 재고 예약 (order-service → inventory-commands.fifo) */
    RESERVE,
    /** 예약 확정 (order-service → inventory-commands.fifo) */
    CONFIRM,
    /** 예약 해제 (order-service → inventory-commands.fifo) */
    RELEASE,
    /** 환불 뒤 판매 재고 복원 (order-service → inventory-commands.fifo, A10, v0.5.0) */
    RETURN,
    /** 폐기 (inventory-service → inventory-commands.fifo) */
    DISPOSE,
    /** 예약 성공 결과 (inventory-service → inventory-results) */
    RESERVED,
    /** 예약 실패 결과 (inventory-service → inventory-results) */
    RESERVE_FAILED
}
