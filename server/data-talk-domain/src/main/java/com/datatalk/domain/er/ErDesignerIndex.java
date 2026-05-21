package com.datatalk.domain.er;

import java.util.List;

public record ErDesignerIndex(String name, List<String> columns) {
    public ErDesignerIndex {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
