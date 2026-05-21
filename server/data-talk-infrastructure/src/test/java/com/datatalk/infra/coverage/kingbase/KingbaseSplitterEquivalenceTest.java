package com.datatalk.infra.coverage.kingbase;

import com.datatalk.sql.PostgresJdbcSqlStatementSplitter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class KingbaseSplitterEquivalenceTest {
    private final PostgresJdbcSqlStatementSplitter splitter = new PostgresJdbcSqlStatementSplitter();

    @Test void singleStatement() {
        List<String> parts = splitter.split("SELECT * FROM pg_tables");
        assertThat(parts).hasSize(1);
    }
    @Test void semicolonDelimitedMultiStatement() {
        List<String> parts = splitter.split("SELECT 1; SELECT 2;");
        assertThat(parts).hasSize(2);
    }
    @Test void stringLiteralSemicolonEscaped() {
        List<String> parts = splitter.split("INSERT INTO t VALUES ('a;b'); SELECT 1");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).trim()).isEqualTo("INSERT INTO t VALUES ('a;b')");
    }
    @Test void commentBlockSemicolonIgnored() {
        List<String> parts = splitter.split("SELECT 1 /* a;b */ ; SELECT 2");
        assertThat(parts).hasSize(2);
    }
    @Test void emptyStatementsFiltered() {
        List<String> parts = splitter.split(";;SELECT 1;;;");
        assertThat(parts).hasSize(1);
    }
    @Test void dollarQuotedBlockRoundTrip() {
        List<String> parts = splitter.split("DO $$ BEGIN RAISE NOTICE 'x'; END $$;");
        assertThat(parts).hasSize(1);
    }
}
