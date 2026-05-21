package com.datatalk.infra.er;

import com.datatalk.application.er.ErDdlGenerator;
import com.datatalk.domain.er.ErDesignerColumn;
import com.datatalk.domain.er.ErDesignerIndex;
import com.datatalk.domain.er.ErDesignerRelation;
import com.datatalk.domain.er.ErDesignerTable;
import com.datatalk.domain.er.ErSchemaDiff;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteDdlGeneratorTest {

    private final SqliteDdlGenerator generator = new SqliteDdlGenerator();

    @Test
    void createTableIsGenerated() {
        var table = new ErDesignerTable(
            "t1", "users", null,
            List.of(
                new ErDesignerColumn("c1", "id", "INTEGER", false, true, true, null, null),
                new ErDesignerColumn("c2", "email", "TEXT", false, false, false, null, null)
            ),
            List.of(), List.of()
        );

        var sql = generatedSql(generator.generate(new ErSchemaDiff.TableAdded(table)));

        assertThat(sql).startsWith("CREATE TABLE \"users\" (");
        assertThat(sql).contains("\"id\" INTEGER PRIMARY KEY AUTOINCREMENT");
        assertThat(sql).contains("\"email\" TEXT NOT NULL");
    }

    @Test
    void addColumnAddForeignKeyAndCreateIndexAreSkippedForCreateOnlySQLiteScope() {
        var column = new ErDesignerColumn("c", "discount", "NUMERIC(10,2)", true, false, false, null, null);
        assertSkipped(generator.generate(new ErSchemaDiff.ColumnAdded("orders", column)), "alter_add_column", "sqlite_alter_unsupported");

        var relation = new ErDesignerRelation("r1", "orders", "user_id", "users", "id", "many_to_one", "database_fk");
        assertSkipped(generator.generate(new ErSchemaDiff.ConstraintAdded(relation)), "add_fk", "sqlite_alter_unsupported");

        var index = new ErDesignerIndex("idx_orders_created_at", List.of("created_at"));
        assertSkipped(generator.generate(new ErSchemaDiff.IndexAdded("orders", index)), "create_index", "sqlite_create_only");
    }

    @Test
    void destructiveChangesAreSkipped() {
        assertSkipped(generator.generate(new ErSchemaDiff.TableDropped("orders")), "drop_table", "day1_unsupported");
    }

    private static String generatedSql(ErDdlGenerator.GenerateResult result) {
        assertThat(result).isInstanceOf(ErDdlGenerator.GenerateResult.Generated.class);
        return ((ErDdlGenerator.GenerateResult.Generated) result).statement().sql();
    }

    private static void assertSkipped(ErDdlGenerator.GenerateResult result, String opType, String reason) {
        assertThat(result).isInstanceOf(ErDdlGenerator.GenerateResult.Skipped.class);
        var skipped = ((ErDdlGenerator.GenerateResult.Skipped) result).skipped();
        assertThat(skipped.opType()).isEqualTo(opType);
        assertThat(skipped.reason()).isEqualTo(reason);
        assertThat(skipped.hint()).contains("manually");
    }
}
