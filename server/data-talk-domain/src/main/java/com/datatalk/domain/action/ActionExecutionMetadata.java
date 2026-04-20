package com.datatalk.domain.action;

public record ActionExecutionMetadata(SqlExecutionRisk sqlRisk) {

    private static final ActionExecutionMetadata EMPTY = new ActionExecutionMetadata(null);

    public static ActionExecutionMetadata empty() {
        return EMPTY;
    }
}
