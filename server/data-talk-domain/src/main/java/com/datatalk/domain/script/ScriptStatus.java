package com.datatalk.domain.script;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ScriptStatus {
    RUNNING, COMPLETED, FAILED, CANCELLED;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ScriptStatus fromDb(String dbValue) {
        return valueOf(dbValue.toUpperCase(Locale.ROOT));
    }
}
