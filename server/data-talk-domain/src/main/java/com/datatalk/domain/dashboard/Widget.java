package com.datatalk.domain.dashboard;

import java.util.List;
import java.util.Map;

public record Widget(
    String id,
    WidgetType type,
    GridPosition position,
    List<ParameterDef> parameters,
    WidgetQuery query,
    Map<String, Object> options
) {
    public Widget {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
