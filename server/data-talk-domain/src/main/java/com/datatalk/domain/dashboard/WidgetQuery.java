package com.datatalk.domain.dashboard;

import java.util.Map;

public record WidgetQuery(
    String connectionId,
    String database,
    String schema,
    String sql,
    Map<String, String> paramRefs
) {
    public WidgetQuery {
        paramRefs = paramRefs == null ? Map.of() : Map.copyOf(paramRefs);
    }
}
