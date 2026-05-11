package com.datatalk.domain.dashboard;

import java.util.List;

public record Dashboard(
    int schemaVersion,
    String id,
    String title,
    String description,
    String defaultConnectionId,
    String theme,
    String renderer,
    DashboardRefresh refresh,
    List<ParameterDef> parameters,
    List<Widget> widgets,
    GridLayout layout,
    long version,
    long createdAt,
    long updatedAt
) {
    public Dashboard {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
        if (schemaVersion != 2) {
            throw new IllegalArgumentException("schemaVersion must be 2, got " + schemaVersion);
        }
        if (!"bezel".equals(renderer)) {
            throw new IllegalArgumentException("renderer must be 'bezel', got '" + renderer + "'");
        }
        if (theme == null || !theme.startsWith("industry-")) {
            throw new IllegalArgumentException("theme must start with 'industry-', got '" + theme + "'");
        }
    }
}
