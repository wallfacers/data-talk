package com.datatalk.domain.report;

/**
 * PDF / Markdown 派生产物的状态机。
 * HTML 一律同步派生成功后才落 report，因此不需要 status。
 */
public enum ReportDerivativeStatus {
    PROCESSING,
    READY,
    FAILED;

    public boolean isTerminal() {
        return this != PROCESSING;
    }

    public static ReportDerivativeStatus fromDb(String value) {
        return switch (value) {
            case "processing" -> PROCESSING;
            case "ready" -> READY;
            case "failed" -> FAILED;
            default -> throw new IllegalArgumentException("Unknown ReportDerivativeStatus: " + value);
        };
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
