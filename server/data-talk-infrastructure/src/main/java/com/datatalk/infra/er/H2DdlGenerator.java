package com.datatalk.infra.er;

import com.datatalk.application.er.DialectTypeRegistry;
import com.datatalk.application.er.ErDdlGenerator;
import com.datatalk.domain.er.Dialect;
import com.datatalk.domain.er.ErDdlKind;
import com.datatalk.domain.er.ErDdlStatement;
import com.datatalk.domain.er.ErDesignerColumn;
import com.datatalk.domain.er.ErDesignerIndex;
import com.datatalk.domain.er.ErDesignerRelation;
import com.datatalk.domain.er.ErDesignerTable;
import com.datatalk.domain.er.ErSchemaDiff;
import com.datatalk.domain.er.SkippedOp;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class H2DdlGenerator implements ErDdlGenerator {

    @Override
    public Dialect dialect() {
        return Dialect.H2;
    }

    @Override
    public GenerateResult generate(ErSchemaDiff diff) {
        if (diff instanceof ErSchemaDiff.TableAdded added) return generated(createTable(added.table()));
        if (diff instanceof ErSchemaDiff.ColumnAdded added) return generated(addColumn(added.tableName(), added.column()));
        if (diff instanceof ErSchemaDiff.IndexAdded added) return generated(createIndex(added.tableName(), added.index()));
        if (diff instanceof ErSchemaDiff.ConstraintAdded added && added.constraint() instanceof ErDesignerRelation relation) return generated(addFk(relation));
        if (diff instanceof ErSchemaDiff.TableDropped dropped) return skipped("drop_table", dropped.tableName(), null);
        if (diff instanceof ErSchemaDiff.ColumnDropped dropped) return skipped("drop_column", dropped.tableName(), dropped.columnName());
        if (diff instanceof ErSchemaDiff.ColumnTypeChanged changed) return skipped("alter_column_type", changed.tableName(), changed.columnName());
        if (diff instanceof ErSchemaDiff.ConstraintDropped) return skipped("drop_constraint", null, null);
        if (diff instanceof ErSchemaDiff.IndexDropped dropped) return skipped("drop_index", dropped.tableName(), dropped.indexName());
        if (diff instanceof ErSchemaDiff.ConstraintAdded added && added.constraint() instanceof ErSchemaDiff.UniqueConstraint unique) return skipped("add_unique", unique.tableName(), null);
        return skipped("constraint_unsupported", null, null);
    }

    private ErDdlStatement createTable(ErDesignerTable table) {
        List<String> lines = new ArrayList<>();
        table.columns().stream().map(this::renderColumn).forEach(lines::add);
        table.uniques().stream()
            .map(unique -> unique.columns().stream().map(col -> DialectTypeRegistry.quote(dialect(), col)).collect(Collectors.joining(", ", "UNIQUE (", ")")))
            .forEach(lines::add);
        String body = lines.stream().collect(Collectors.joining(",\n  ", "  ", ""));
        String sql = "CREATE TABLE " + DialectTypeRegistry.quote(dialect(), table.name()) + " (\n" + body + "\n)";
        return new ErDdlStatement(sql, ErDdlKind.CREATE_TABLE, table.name());
    }

    private String renderColumn(ErDesignerColumn column) {
        if (column.isAutoIncrement() && column.isPrimaryKey()) return DialectTypeRegistry.autoIncrementPk(dialect(), column.name());
        StringBuilder builder = new StringBuilder(DialectTypeRegistry.quote(dialect(), column.name()))
            .append(' ')
            .append(column.type());
        if (!column.nullable()) builder.append(" NOT NULL");
        if (column.defaultValue() != null) builder.append(" DEFAULT ").append(column.defaultValue());
        if (column.isPrimaryKey()) builder.append(" PRIMARY KEY");
        return builder.toString();
    }

    private ErDdlStatement addColumn(String tableName, ErDesignerColumn column) {
        String sql = "ALTER TABLE " + DialectTypeRegistry.quote(dialect(), tableName) + " ADD COLUMN " + renderColumn(column);
        return new ErDdlStatement(sql, ErDdlKind.ADD_COLUMN, tableName);
    }

    private ErDdlStatement addFk(ErDesignerRelation relation) {
        String sql = "ALTER TABLE " + DialectTypeRegistry.quote(dialect(), relation.fromTableId())
            + " ADD CONSTRAINT " + DialectTypeRegistry.quote(dialect(), fkName(relation))
            + " FOREIGN KEY (" + DialectTypeRegistry.quote(dialect(), relation.fromColumnId()) + ")"
            + " REFERENCES " + DialectTypeRegistry.quote(dialect(), relation.toTableId())
            + " (" + DialectTypeRegistry.quote(dialect(), relation.toColumnId()) + ")";
        return new ErDdlStatement(sql, ErDdlKind.ADD_FK, relation.fromTableId());
    }

    private ErDdlStatement createIndex(String tableName, ErDesignerIndex index) {
        String cols = index.columns().stream().map(col -> DialectTypeRegistry.quote(dialect(), col)).collect(Collectors.joining(", "));
        String sql = "CREATE INDEX " + DialectTypeRegistry.quote(dialect(), index.name())
            + " ON " + DialectTypeRegistry.quote(dialect(), tableName) + " (" + cols + ")";
        return new ErDdlStatement(sql, ErDdlKind.CREATE_INDEX, tableName);
    }

    private static String fkName(ErDesignerRelation relation) {
        return "fk_" + relation.fromTableId() + "_" + relation.fromColumnId();
    }

    private static GenerateResult generated(ErDdlStatement statement) {
        return new GenerateResult.Generated(statement);
    }

    private static GenerateResult skipped(String opType, String table, String column) {
        return new GenerateResult.Skipped(new SkippedOp(
            opType, table, column, "day1_unsupported",
            "This operation is not generated day-1. Add the SQL manually in the query_editor."));
    }
}
