package com.datatalk.application.report;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Promote 流程的 schema / 业务校验失败异常。携带 collect-all 的全部 {@link Violation}。
 *
 * <p>{@link #getErrorCode()} 返回第一个 violation 的 code，保留与单错误码 contract 的向后兼容。
 * 新调用方应使用 {@link #getViolations()} / {@link #getErrorCodes()} 拿到完整列表。
 */
public class ReportValidationException extends RuntimeException {

    private final List<Violation> violations;

    public ReportValidationException(List<Violation> violations) {
        super(buildMessage(violations));
        if (violations == null || violations.isEmpty()) {
            throw new IllegalArgumentException("violations must be non-empty");
        }
        this.violations = List.copyOf(violations);
    }

    /** Backward-compatible single-violation constructor. */
    public ReportValidationException(String errorCode, String message) {
        this(List.of(new Violation(errorCode, "", message)));
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public List<String> getErrorCodes() {
        return violations.stream().map(Violation::code).toList();
    }

    /** First violation code — kept for backward compatibility with single-error callers. */
    public String getErrorCode() {
        return violations.get(0).code();
    }

    private static String buildMessage(List<Violation> violations) {
        if (violations == null || violations.isEmpty()) return "validation failed";
        return "validation failed: "
                + violations.stream().map(Violation::message).collect(Collectors.joining("; "));
    }
}
