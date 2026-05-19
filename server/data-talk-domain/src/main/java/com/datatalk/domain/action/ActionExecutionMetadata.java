package com.datatalk.domain.action;

public record ActionExecutionMetadata(SqlExecutionRisk sqlRisk, CallerKind callerKind) {

    private static final ActionExecutionMetadata AI_EMPTY = new ActionExecutionMetadata(null, CallerKind.AI);
    private static final ActionExecutionMetadata USER_EMPTY = new ActionExecutionMetadata(null, CallerKind.USER);

    public static ActionExecutionMetadata empty() {
        return AI_EMPTY;
    }

    public static ActionExecutionMetadata userInitiated() {
        return USER_EMPTY;
    }

    public static ActionExecutionMetadata aiInitiated() {
        return AI_EMPTY;
    }

    public static ActionExecutionMetadata userInitiated(SqlExecutionRisk risk) {
        return new ActionExecutionMetadata(risk, CallerKind.USER);
    }

    public static ActionExecutionMetadata aiInitiated(SqlExecutionRisk risk) {
        return new ActionExecutionMetadata(risk, CallerKind.AI);
    }
}
