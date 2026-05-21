package com.datatalk.domain.er;

public record ErDesignerRelation(
    String id,
    String fromTableId,
    String fromColumnId,
    String toTableId,
    String toColumnId,
    String type,
    String constraintMethod
) {}
