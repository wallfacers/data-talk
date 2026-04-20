package com.datatalk.application.sql;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class SqlBearingActionInspector {

    public Optional<String> extractSql(Object input) {
        if (!(input instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object sql = map.get("sql");
        if (!(sql instanceof String text)) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }
}
