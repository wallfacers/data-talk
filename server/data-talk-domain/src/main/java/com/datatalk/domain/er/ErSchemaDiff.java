package com.datatalk.domain.er;

import java.util.List;

public sealed interface ErSchemaDiff permits
    ErSchemaDiff.TableAdded,
    ErSchemaDiff.TableDropped,
    ErSchemaDiff.ColumnAdded,
    ErSchemaDiff.ColumnDropped,
    ErSchemaDiff.ColumnTypeChanged,
    ErSchemaDiff.ConstraintAdded,
    ErSchemaDiff.ConstraintDropped,
    ErSchemaDiff.IndexAdded,
    ErSchemaDiff.IndexDropped {

    record TableAdded(ErDesignerTable table) implements ErSchemaDiff {}
    record TableDropped(String tableName) implements ErSchemaDiff {}
    record ColumnAdded(String tableName, ErDesignerColumn column) implements ErSchemaDiff {}
    record ColumnDropped(String tableName, String columnName) implements ErSchemaDiff {}
    record ColumnTypeChanged(String tableName, String columnName, String oldType, String newType) implements ErSchemaDiff {}
    record ConstraintAdded(Object constraint) implements ErSchemaDiff {}
    record ConstraintDropped(String relationName) implements ErSchemaDiff {}
    record IndexAdded(String tableName, ErDesignerIndex index) implements ErSchemaDiff {}
    record IndexDropped(String tableName, String indexName) implements ErSchemaDiff {}

    record UniqueConstraint(String tableName, List<String> columns) {
        public UniqueConstraint {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }
}
