package com.datatalk.domain.er;

import java.util.List;

public record ErDesignerPayload(
    String dialect,
    String targetConnectionId,
    String targetDatabase,
    String targetSchema,
    List<ErDesignerTable> tables,
    List<ErDesignerRelation> relations
) {
    public ErDesignerPayload {
        tables = tables == null ? List.of() : List.copyOf(tables);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    public Dialect resolveDialect() {
        return Dialect.fromConnectionKind(dialect)
            .orElseThrow(() -> new ErErrors.DialectUnsupportedException(dialect));
    }
}
