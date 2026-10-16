package com.freshlink.common.error;

/**
 * 모든 서비스의 공통 오류 응답. 항상 세 키로 나간다.
 * <pre>{"code": "LOT_NOT_IN_STORE", "message": "다른 점포의 lot입니다", "traceId": "4bf92f3577b34da6"}</pre>
 * traceId는 로그와 Zipkin에 찍히는 값과 같다. 추적이 꺼져 있으면 빈 문자열.
 */
public record ErrorResponse(String code, String message, String traceId) {

    public ErrorResponse {
        if (traceId == null) {
            traceId = "";
        }
    }

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), traceId);
    }

    public static ErrorResponse of(BusinessException e, String traceId) {
        return new ErrorResponse(e.code(), e.getMessage(), traceId);
    }

    /** 다른 서비스가 보낸 오류 응답을 내 서비스의 BusinessException으로 되던질 때 쓴다 */
    public ErrorCode toErrorCode(int status) {
        return ErrorCode.of(code, status, message);
    }
}
