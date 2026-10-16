package com.freshlink.common.error;

record SimpleErrorCode(String code, int status, String message) implements ErrorCode {
}
