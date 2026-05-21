package com.datatalk.application.sql;

import java.util.Map;

public record BulkSqlVerdict(
    boolean shouldReject,
    String reason,
    String message,
    Map<String, Object> nextActionParams
) {

    private static final BulkSqlVerdict PASS = new BulkSqlVerdict(false, null, null, null);

    public static BulkSqlVerdict pass() {
        return PASS;
    }

    public static BulkSqlVerdict reject(String reason, String message, Map<String, Object> nextActionParams) {
        return new BulkSqlVerdict(true, reason, message, nextActionParams);
    }
}
