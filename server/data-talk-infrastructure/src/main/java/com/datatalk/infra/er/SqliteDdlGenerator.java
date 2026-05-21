package com.datatalk.infra.er;

import com.datatalk.application.er.DialectTypeRegistry;
import com.datatalk.application.er.ErDdlGenerator;
import com.datatalk.domain.er.Dialect;
import com.datatalk.domain.er.ErDdlKind;
import com.datatalk.domain.er.ErDdlStatement;
import com.datatalk.domain.er.ErDesignerColumn;
import com.datatalk.domain.er.ErDesignerIndex;
import com.datatalk.domain.er.ErDesignerTable;
import com.datatalk.domain.er.ErSchemaDiff;
import com.datatalk.domain.er.SkippedOp;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SqliteDdlGenerator implements ErDdlGenerator {

    @Override
    public Dialect dialect() {
        return Dialect.SQLITE;
    }

    @Override
    public GenerateResult generate(ErSchemaDiff diff) {
        if (diff instanceof ErSchemaDiff.TableAdded added) return generated(createTable(added.table()));
        if (diff instanceof ErSchemaDiff.ColumnAdded added) {
            return skipped("alter_add_column", added.tableName(), added.column().name(), "sqlite_alter_unsupported",
                "SQLite ALTER TABLE changes are not generated day-1. Add the SQL manually with a SQLite-specific migration pattern.");
        }
        if (diff instanceof ErSchemaDiff.ConstraintAdded added && added.constraint() instanceof ErSchemaDiff.UniqueConstraint unique) {
            return skipped("add_unique", unique.tableName(), null, "sqlite_create_only",
                "SQLite Designer DDL is create-table only day-1. Add the SQL manually in the query_editor.");
        }
        if (diff instanceof ErSchemaDiff.ConstraintAdded added) {
            return skipped("add_fk", relationTable(added.constraint()), relationColumn(added.constraint()), "sqlite_alter_unsupported",
                "SQLite cannot ADD CONSTRAINT after table creation. Recreate the table or add the SQL manually.");
        }
        if (diff instanceof ErSchemaDiff.IndexAdded added) {
            return skipped("create_index", added.tableName(), null, "sqlite_create_only",
                "SQLite Designer DDL is create-table only day-1. Add CREATE INDEX manually in the query_editor.");
        }
        if (diff instanceof ErSchemaDiff.TableDropped dropped) return skipped("drop_table", dropped.tableName(), null, "day1_unsupported", "DROP TABLE is intentionally not generated. Add it manually in the query_editor.");
        if (diff instanceof ErSchemaDiff.ColumnDropped dropped) return skipped("drop_column", dropped.tableName(), dropped.columnName(), "day1_unsupported", "SQLite DROP COLUMN requires table recreation. Add it manually in the query_editor.");
        if (diff instanceof ErSchemaDiff.ColumnTypeChanged changed) return skipped("alter_column_type", changed.tableName(), changed.columnName(), "day1_unsupported", "SQLite cannot change column types in place. Add the migration SQL manually.");
        if (diff instanceof ErSchemaDiff.ConstraintDropped) return skipped("drop_constraint", null, null, "sqlite_alter_unsupported", "SQLite cannot DROP CONSTRAINT in place. Add the SQL manually.");
        if (diff instanceof ErSchemaDiff.IndexDropped dropped) return skipped("drop_index", dropped.tableName(), dropped.indexName(), "sqlite_create_only", "SQLite Designer DDL is create-table only day-1. Add DROP INDEX manually.");
        return skipped("constraint_unsupported", null, null, "sqlite_create_only", "SQLite Designer DDL is create-table only day-1. Add the SQL manually.");
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

    private static String relationTable(Object constraint) {
        return constraint instanceof com.datatalk.domain.er.ErDesignerRelation relation ? relation.fromTableId() : null;
    }

    private static String relationColumn(Object constraint) {
        return constraint instanceof com.datatalk.domain.er.ErDesignerRelation relation ? relation.fromColumnId() : null;
    }

    private static GenerateResult generated(ErDdlStatement statement) {
        return new GenerateResult.Generated(statement);
    }

    private static GenerateResult skipped(String opType, String table, String column, String reason, String hint) {
        return new GenerateResult.Skipped(new SkippedOp(opType, table, column, reason, hint));
    }
}
