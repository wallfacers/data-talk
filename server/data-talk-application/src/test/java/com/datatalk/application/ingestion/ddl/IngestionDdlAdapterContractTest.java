package com.datatalk.application.ingestion.ddl;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.MappingColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

abstract class IngestionDdlAdapterContractTest {
    abstract IngestionDdlAdapter adapter();
    abstract String quoteFor(String name);
    abstract String expectedString256Type();
    abstract String expectedTimestampType();

    @Test
    void createTableWithMixedTypes() {
        var cols = List.of(
            new MappingColumn("$.id", "id", InferredType.INTEGER_64, false, List.of("1"), false),
            new MappingColumn("$.name", "name", InferredType.STRING_256, false, List.of("a"), false),
            new MappingColumn("$.created", "created", InferredType.TIMESTAMP, false, List.of("2026-05-12T00:00:00Z"), true)
        );
        String ddl = adapter().generateCreateTable("public", "orders", cols);
        assertThat(ddl).contains(quoteFor("orders"));
        assertThat(ddl).contains(expectedString256Type());
        assertThat(ddl).contains(expectedTimestampType());
        assertThat(ddl).contains("NOT NULL");
    }

    @Test
    void insertStatementExcludesSkippedColumns() {
        var cols = List.of(
            new MappingColumn("$.id", "id", InferredType.INTEGER_64, false, List.of(), false),
            new MappingColumn("$.x", "x", InferredType.STRING_64, false, List.of(), true),
            new MappingColumn("$.skipme", "skipme", InferredType.STRING_64, true, List.of(), true)
        );
        String insert = adapter().generateInsert("public", "orders", cols);
        assertThat(insert).contains("INSERT INTO");
        assertThat(insert).contains("(?, ?)");
        assertThat(insert).doesNotContain("skipme");
    }

    @Test
    void supportsCorrectKind() {
        assertThat(adapter().supports(expectedKind())).isTrue();
        assertThat(adapter().supports("other_db")).isFalse();
    }

    abstract String expectedKind();
}

class MysqlIngestionDdlAdapterTest extends IngestionDdlAdapterContractTest {
    private final MysqlIngestionDdlAdapter adapter = new MysqlIngestionDdlAdapter();
    @Override IngestionDdlAdapter adapter() { return adapter; }
    @Override String quoteFor(String name) { return "`" + name + "`"; }
    @Override String expectedString256Type() { return "VARCHAR(256)"; }
    @Override String expectedTimestampType() { return "DATETIME"; }
    @Override String expectedKind() { return "mysql"; }
}

class PostgresIngestionDdlAdapterTest extends IngestionDdlAdapterContractTest {
    private final PostgresIngestionDdlAdapter adapter = new PostgresIngestionDdlAdapter();
    @Override IngestionDdlAdapter adapter() { return adapter; }
    @Override String quoteFor(String name) { return "\"" + name + "\""; }
    @Override String expectedString256Type() { return "VARCHAR(256)"; }
    @Override String expectedTimestampType() { return "TIMESTAMP"; }
    @Override String expectedKind() { return "postgresql"; }
}

class H2IngestionDdlAdapterTest extends IngestionDdlAdapterContractTest {
    private final H2IngestionDdlAdapter adapter = new H2IngestionDdlAdapter();
    @Override IngestionDdlAdapter adapter() { return adapter; }
    @Override String quoteFor(String name) { return "\"" + name + "\""; }
    @Override String expectedString256Type() { return "VARCHAR(256)"; }
    @Override String expectedTimestampType() { return "TIMESTAMP"; }
    @Override String expectedKind() { return "h2"; }
}

class SqliteIngestionDdlAdapterTest extends IngestionDdlAdapterContractTest {
    private final SqliteIngestionDdlAdapter adapter = new SqliteIngestionDdlAdapter();
    @Override IngestionDdlAdapter adapter() { return adapter; }
    @Override String quoteFor(String name) { return "\"" + name + "\""; }
    @Override String expectedString256Type() { return "TEXT"; }
    @Override String expectedTimestampType() { return "TEXT"; }
    @Override String expectedKind() { return "sqlite"; }
}
