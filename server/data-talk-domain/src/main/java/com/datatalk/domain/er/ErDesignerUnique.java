package com.datatalk.domain.er;

import java.util.List;

public record ErDesignerUnique(List<String> columns) {
    public ErDesignerUnique {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
