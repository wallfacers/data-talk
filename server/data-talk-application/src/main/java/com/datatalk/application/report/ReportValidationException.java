package com.datatalk.application.report;

/**
 * Promote 流程的 schema / 业务校验失败异常。errorCode 直接对应 spec 中定义的错误码字符串。
 */
public class ReportValidationException extends RuntimeException {

    private final String errorCode;

    public ReportValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
