package com.freshlink.common.error;

/**
 * 오류 코드. 서비스마다 enum으로 목록을 만들고 이 인터페이스를 구현한다.
 * <pre>{@code
 * public enum OrderErrorCode implements ErrorCode {
 *     ORDER_NOT_FOUND(404, "주문이 없습니다");
 *     ...
 * }
 * throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
 * }</pre>
 * enum이면 {@link #code()}는 enum 이름이다.
 */
public interface ErrorCode {

    /** HTTP 상태 코드 (404, 409 ...) */
    int status();

    /** 사용자에게 보여 줄 메시지 */
    String message();

    /** 프론트가 분기하는 코드 문자열. enum이면 enum 이름 */
    default String code() {
        return this instanceof Enum<?> e ? e.name() : getClass().getSimpleName();
    }

    /** enum이 아닌 즉석 오류 코드. 다른 서비스의 ErrorResponse를 그대로 되던질 때 쓴다 */
    static ErrorCode of(String code, int status, String message) {
        return new SimpleErrorCode(code, status, message);
    }
}
