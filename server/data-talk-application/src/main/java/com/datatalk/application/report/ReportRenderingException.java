package com.datatalk.application.report;

/**
 * 渲染失败（HTML/PDF/Markdown）。
 */
public class ReportRenderingException extends RuntimeException {

    public ReportRenderingException(String message) {
        super(message);
    }

    public ReportRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
