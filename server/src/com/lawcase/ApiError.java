package com.lawcase;

/** 带 HTTP 状态码的业务异常。 */
public class ApiError extends RuntimeException {
    public final int status;

    public ApiError(int status, String message) {
        super(message);
        this.status = status;
    }
}
