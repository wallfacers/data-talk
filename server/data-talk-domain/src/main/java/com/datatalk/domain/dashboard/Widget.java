package com.datatalk.domain.dashboard;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record Widget(
    String id,
    String type,
    String slot,
    String title,
    String patternId,
    ChartSemantics chartSemantics,
    WidgetRefresh refresh,
    List<ParameterDef> parameters,
    WidgetQuery query,
    Map<String, Object> options
) {
    private static final Pattern PATTERN_ID_RE = Pattern.compile("[a-z0-9-]+\\.[a-z0-9-]+");

    public Widget {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        options = options == null ? Map.of() : Map.copyOf(options);
        if (patternId != null && !PATTERN_ID_RE.matcher(patternId).matches()) {
            throw new IllegalArgumentException("patternId must match [a-z0-9-]+.[a-z0-9-]+, got '" + patternId + "'");
        }
    }

    public enum RefreshStrategy { DATA_ONLY, FULL_RERENDER }
}
