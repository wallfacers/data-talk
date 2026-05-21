package com.datatalk.domain.script;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ScriptLanguage {
    PYTHON, JAVASCRIPT;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ScriptLanguage fromDb(String dbValue) {
        return valueOf(dbValue.toUpperCase(Locale.ROOT));
    }
}
