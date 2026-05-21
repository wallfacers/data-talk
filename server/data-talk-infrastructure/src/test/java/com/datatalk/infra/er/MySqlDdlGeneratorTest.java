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

class MySqlDdlGeneratorTest {

    private final MySqlDdlGenerator generator = new MySqlDdlGenerator();

    @Test
    void createTableWithAutoIncrementPrimaryKeyAndUniqueConstraint() {
        var table = new ErDesignerTable(
            "t1", "users", null,
            List.of(
                new ErDesignerColumn("c1", "id", "BIGINT", false, true, true, null, null),
                new ErDesignerColumn("c2", "email", "VARCHAR(255)", false, false, false, null, "login email")
            ),
            List.of(),
            List.of(new com.datatalk.domain.er.ErDesignerUnique(List.of("email")))
        );

        var sql = generatedSql(generator.generate(new ErSchemaDiff.TableAdded(table)));

        assertThat(sql).startsWith("CREATE TABLE `users` (");
        assertThat(sql).contains("`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        assertThat(sql).contains("`email` VARCHAR(255) NOT NULL COMMENT 'login email'");
        assertThat(sql).contains("UNIQUE (`email`)");
    }

    @Test
    void addColumnEmitsAlterTableAddColumn() {
        var column = new ErDesignerColumn("c", "discount", "DECIMAL(10,2)", true, false, false, null, null);

        assertThat(generatedSql(generator.generate(new ErSchemaDiff.ColumnAdded("orders", column))))
            .isEqualTo("ALTER TABLE `orders` ADD COLUMN `discount` DECIMAL(10,2) NULL");
    }

    @Test
    void addForeignKeyEmitsAlterTableAddConstraint() {
        var relation = new ErDesignerRelation("r1", "orders", "user_id", "users", "id", "many_to_one", "database_fk");

        var sql = generatedSql(generator.generate(new ErSchemaDiff.ConstraintAdded(relation)));

        assertThat(sql).contains("ALTER TABLE `orders` ADD CONSTRAINT `fk_orders_user_id`");
        assertThat(sql).contains("FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)");
    }

    @Test
    void createIndexEmitsCreateIndex() {
        var index = new ErDesignerIndex("idx_orders_created_at", List.of("created_at"));

        assertThat(generatedSql(generator.generate(new ErSchemaDiff.IndexAdded("orders", index))))
            .isEqualTo("CREATE INDEX `idx_orders_created_at` ON `orders` (`created_at`)");
    }

    @Test
    void destructiveAndUnsupportedChangesAreSkippedWithEnglishHint() {
        var result = generator.generate(new ErSchemaDiff.ColumnTypeChanged("orders", "amount", "DECIMAL(10,2)", "DECIMAL(12,2)"));

        assertThat(result).isInstanceOf(ErDdlGenerator.GenerateResult.Skipped.class);
        var skipped = ((ErDdlGenerator.GenerateResult.Skipped) result).skipped();
        assertThat(skipped.opType()).isEqualTo("alter_column_type");
        assertThat(skipped.reason()).isEqualTo("day1_unsupported");
        assertThat(skipped.hint()).contains("manually");
    }

    private static String generatedSql(ErDdlGenerator.GenerateResult result) {
        assertThat(result).isInstanceOf(ErDdlGenerator.GenerateResult.Generated.class);
        return ((ErDdlGenerator.GenerateResult.Generated) result).statement().sql();
    }
}
