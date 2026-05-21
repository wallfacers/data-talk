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

class H2DdlGeneratorTest {

    private final H2DdlGenerator generator = new H2DdlGenerator();

    @Test
    void createTableUsesH2IdentitySyntax() {
        var table = new ErDesignerTable(
            "t1", "users", null,
            List.of(new ErDesignerColumn("c1", "id", "BIGINT", false, true, true, null, null)),
            List.of(), List.of()
        );

        assertThat(generatedSql(generator.generate(new ErSchemaDiff.TableAdded(table))))
            .contains("\"id\" BIGINT AUTO_INCREMENT PRIMARY KEY");
    }

    @Test
    void supportsAddColumnAddFkAndCreateIndex() {
        var column = new ErDesignerColumn("c", "discount", "DECIMAL(10,2)", false, false, false, "0", null);
        assertThat(generatedSql(generator.generate(new ErSchemaDiff.ColumnAdded("orders", column))))
            .isEqualTo("ALTER TABLE \"orders\" ADD COLUMN \"discount\" DECIMAL(10,2) NOT NULL DEFAULT 0");

        var relation = new ErDesignerRelation("r1", "orders", "user_id", "users", "id", "many_to_one", "database_fk");
        assertThat(generatedSql(generator.generate(new ErSchemaDiff.ConstraintAdded(relation))))
            .contains("FOREIGN KEY (\"user_id\") REFERENCES \"users\" (\"id\")");

        assertThat(generatedSql(generator.generate(new ErSchemaDiff.IndexAdded("orders", new ErDesignerIndex("idx_orders_created_at", List.of("created_at"))))))
            .isEqualTo("CREATE INDEX \"idx_orders_created_at\" ON \"orders\" (\"created_at\")");
    }

    @Test
    void destructiveChangesAreSkipped() {
        var result = generator.generate(new ErSchemaDiff.ColumnDropped("orders", "legacy"));

        assertThat(result).isInstanceOf(ErDdlGenerator.GenerateResult.Skipped.class);
        assertThat(((ErDdlGenerator.GenerateResult.Skipped) result).skipped().opType()).isEqualTo("drop_column");
    }

    private static String generatedSql(ErDdlGenerator.GenerateResult result) {
        assertThat(result).isInstanceOf(ErDdlGenerator.GenerateResult.Generated.class);
        return ((ErDdlGenerator.GenerateResult.Generated) result).statement().sql();
    }
}
