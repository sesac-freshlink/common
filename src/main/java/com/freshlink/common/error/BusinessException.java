package com.freshlink.common.error;

/**
 * 업무 규칙 위반. 서비스의 GlobalExceptionHandler가 {@link ErrorResponse}로 바꿔 응답한다.
 * 메시지를 따로 주면 ErrorCode의 기본 메시지 대신 그 메시지가 응답에 나간다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.message(), cause);
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String code() {
        return errorCode.code();
    }

    public int status() {
        return errorCode.status();
    }
}
