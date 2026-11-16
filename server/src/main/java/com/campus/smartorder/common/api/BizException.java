package com.campus.smartorder.common.api;

public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public BizException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail == null ? errorCode.message() : detail);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String detail) {
        return new BizException(errorCode, detail);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String detail() {
        return detail;
    }

    public boolean isAuth() {
        return errorCode.code() >= 20000 && errorCode.code() < 20004;
    }
}
