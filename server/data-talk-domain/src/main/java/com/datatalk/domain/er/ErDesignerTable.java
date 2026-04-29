package com.datatalk.domain.er;

import java.util.List;

public record ErDesignerTable(
    String id,
    String name,
    String comment,
    List<ErDesignerColumn> columns,
    List<ErDesignerIndex> indexes,
    List<ErDesignerUnique> uniques
) {
    public ErDesignerTable {
        columns = columns == null ? List.of() : List.copyOf(columns);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
        uniques = uniques == null ? List.of() : List.copyOf(uniques);
    }
}
