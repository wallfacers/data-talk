package com.datatalk.application.er;

import com.datatalk.domain.er.Dialect;
import com.datatalk.domain.er.ErDdlKind;
import com.datatalk.domain.er.ErDdlStatement;
import com.datatalk.domain.er.ErDesignerPayload;
import com.datatalk.domain.er.ErDesignerTable;
import com.datatalk.domain.er.ErErrors;
import com.datatalk.domain.er.ErSchemaDiff;
import com.datatalk.domain.er.SkippedOp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErDdlGeneratorServiceTest {

    @Test
    void generateRoutesDiffsToMatchingDialectGenerator() {
        var table = new ErDesignerTable("t1", "users", null, List.of(), List.of(), List.of());
        var service = new ErDdlGeneratorService(new StubDiff(List.of(new ErSchemaDiff.TableAdded(table))), List.of(new StubGenerator(Dialect.MYSQL)));
        var draft = new ErDesignerPayload("mysql", "c1", null, null, List.of(table), List.of());

        var plan = service.generate(draft, "c1", false);

        assertThat(plan.statements()).containsExactly(new ErDdlStatement("-- MYSQL TableAdded", ErDdlKind.CREATE_TABLE, "users"));
        assertThat(plan.skipped()).isEmpty();
    }

    @Test
    void skippedResultsAreSeparatedFromStatements() {
        var service = new ErDdlGeneratorService(new StubDiff(List.of(new ErSchemaDiff.TableDropped("orders"))), List.of(new StubGenerator(Dialect.MYSQL)));
        var draft = new ErDesignerPayload("mysql", "c1", null, null, List.of(), List.of());

        var plan = service.generate(draft, "c1", false);

        assertThat(plan.statements()).isEmpty();
        assertThat(plan.skipped()).containsExactly(new SkippedOp("drop_table", "orders", null, "day1_unsupported", "Add the SQL manually in the query_editor."));
    }

    @Test
    void missingTargetThrowsBeforeDiffing() {
        var service = new ErDdlGeneratorService(new StubDiff(List.of()), List.of(new StubGenerator(Dialect.MYSQL)));
        var draft = new ErDesignerPayload("mysql", null, null, null, List.of(), List.of());

        assertThatThrownBy(() -> service.generate(draft, null, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bind_target");
    }

    @Test
    void unsupportedDialectThrowsStructuredErError() {
        var service = new ErDdlGeneratorService(new StubDiff(List.of()), List.of(new StubGenerator(Dialect.MYSQL)));
        var draft = new ErDesignerPayload("oracle", "c1", null, null, List.of(), List.of());

        assertThatThrownBy(() -> service.generate(draft, "c1", false))
            .isInstanceOf(ErErrors.DialectUnsupportedException.class)
            .matches(e -> ((ErErrors.DialectUnsupportedException) e).kind().equals("oracle"));
    }

    private record StubDiff(List<ErSchemaDiff> diffs) implements ErDdlGeneratorService.SchemaDiffPort {
        @Override
        public List<ErSchemaDiff> diff(ErDesignerPayload payload, String connectionId) {
            return diffs;
        }
    }

    private record StubGenerator(Dialect dialect) implements ErDdlGenerator {
        @Override
        public GenerateResult generate(ErSchemaDiff diff) {
            if (diff instanceof ErSchemaDiff.TableDropped dropped) {
                return new GenerateResult.Skipped(new SkippedOp("drop_table", dropped.tableName(), null, "day1_unsupported", "Add the SQL manually in the query_editor."));
            }
            return new GenerateResult.Generated(new ErDdlStatement("-- " + dialect.name() + " " + diff.getClass().getSimpleName(), ErDdlKind.CREATE_TABLE, "users"));
        }
    }
}
