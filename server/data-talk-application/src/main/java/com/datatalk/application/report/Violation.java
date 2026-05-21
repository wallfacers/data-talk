package com.datatalk.application.report;

/**
 * Single validation violation reported by {@link ReportSchemaValidator}.
 *
 * <p>{@code path} uses dot/index notation pointing at the offending JSON node
 * (e.g. {@code "meta.title"}, {@code "sections[3].blocks[1].rows[201]"}).
 * An empty path means the violation is at the document root.
 */
public record Violation(String code, String path, String message) {}
