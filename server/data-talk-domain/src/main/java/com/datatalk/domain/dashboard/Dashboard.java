package com.datatalk.domain.dashboard;

import java.util.List;

public record Dashboard(
    int schemaVersion,
    String id,
    String title,
    String description,
    String defaultConnectionId,
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
    }
}
