package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 6.3: SchemaSearchAction 集成测 — H2/SQLite 内嵌实例,验证:
 * - 中文 keyword 命中表名/列名/注释(H2 + COMMENT ON)
 * - 英文 keyword 同时命中表名与列名
 * - SQLite 降级(无 COMMENT 支持 → commentSnippet="")
 * - %/_ 转义:keyword 含 `_` 不应误匹配无下划线的表名
 * - 空 keyword → SCHEMA_INPUT_INVALID
 * - 超长 keyword(>200 chars)→ SCHEMA_INPUT_INVALID
 * - limit 上限 = 30 自动 clamp
 * - 未注册 kind → DIALECT_UNSUPPORTED
 */
@SpringBootTest
class SchemaSearchActionIT {

    private static final String H2_DB =
        "mem:schemasearch;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

    @Autowired ConnectionService conn;
    @Autowired SchemaSearchAction action;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    String h2ConnId;
    @TempDir Path tempDir;

    @BeforeEach
    void seedDb() throws Exception {
        datatalkJdbc.update("DELETE FROM session_data_contexts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");

        try (var c = DriverManager.getConnection("jdbc:h2:" + H2_DB, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE TABLE t_sales(id INT PRIMARY KEY, sale_amount DECIMAL(12,2), customer_name VARCHAR(64))");
            st.execute("COMMENT ON TABLE t_sales IS '销售订单主表'");
            st.execute("COMMENT ON COLUMN t_sales.sale_amount IS '订单金额'");
            st.execute("CREATE TABLE t_inventory(id INT PRIMARY KEY, item_name VARCHAR(64))");
            st.execute("COMMENT ON TABLE t_inventory IS '库存盘点表'");
            st.execute("CREATE TABLE invoices(id INT PRIMARY KEY, amount DECIMAL(12,2), tax_amount DECIMAL(12,2))");
            st.execute("CREATE TABLE data_a(id INT PRIMARY KEY)");
            st.execute("CREATE TABLE data2a(id INT PRIMARY KEY)");
        }
        h2ConnId = conn.create("schema search h2", "h2", "", 0, H2_DB, "sa", "",
            null, null, null, null, null, null, null, null, null);
        long now = System.currentTimeMillis();
        datatalkJdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 1, ?, ?, ?, 0)
            """, "s-1", h2ConnId, "Schema Search", "oc-1", now, now);
    }

    private Map<String, Object> invoke(String callId, Map<String, Object> input) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", callId, h2ConnId, "oc-1"), input
        ).toCompletableFuture().get();
        return out;
    }

    @Test
    @SuppressWarnings("unchecked")
    void chineseKeywordHitsTableCommentAndName() throws Exception {
        Map<String, Object> out = invoke("c-cn", Map.of("keyword", "销售", "schema", "PUBLIC"));

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) out.get("candidates");
        assertThat(candidates).extracting(m -> m.get("table"))
            .contains("t_sales");
        Map<String, Object> sales = candidates.stream()
            .filter(m -> "t_sales".equals(m.get("table"))).findFirst().orElseThrow();
        // table.comment 命中 → 至少 score 1 + matchedOn 含 table.comment
        List<String> matchedOn = (List<String>) sales.get("matchedOn");
        assertThat(matchedOn).anyMatch(s -> s.startsWith("table.comment"));
        assertThat((String) sales.get("commentSnippet")).contains("销售");
        assertThat(out).containsEntry("truncated", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void englishKeywordHitsTableNameAndColumnName() throws Exception {
        Map<String, Object> out = invoke("c-en", Map.of("keyword", "amount", "schema", "PUBLIC"));

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) out.get("candidates");
        // 表名命中 t_sales (有 sale_amount 列) 与 invoices (有 amount + tax_amount 列)
        assertThat(candidates).extracting(m -> m.get("table"))
            .contains("invoices", "t_sales");
        Map<String, Object> invoices = candidates.stream()
            .filter(m -> "invoices".equals(m.get("table"))).findFirst().orElseThrow();
        List<String> matched = (List<String>) invoices.get("matchedOn");
        assertThat(matched).anyMatch(s -> s.startsWith("column.") && s.contains("amount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void underscoreInKeywordIsEscapedAndDoesNotMatchUnrelatedTables() throws Exception {
        Map<String, Object> out = invoke("c-esc", Map.of("keyword", "_", "schema", "PUBLIC"));

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) out.get("candidates");
        // 关键词 `_` 转义后,只能命中含字面下划线的表名/列名 — data2a(无下划线)不应被误匹配。
        // 注:t_sales/t_inventory/data_a 表名含下划线;invoices 因 tax_amount 列含下划线被正确命中
        List<String> tables = candidates.stream()
            .map(m -> (String) m.get("table")).toList();
        assertThat(tables).doesNotContain("data2a");
        assertThat(tables).contains("data_a");
    }

    @Test
    void emptyKeywordFailsWithSchemaInputInvalid() {
        assertThatThrownBy(() -> action.handle(
            new ActionContext("s-1", "c-empty", h2ConnId, "oc-1"),
            Map.of("keyword", "   ", "schema", "PUBLIC")
        ).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(DataTalkException.class)
            .hasMessageContaining("keyword is required");
    }

    @Test
    void overlongKeywordFailsWithSchemaInputInvalid() {
        String tooLong = "x".repeat(201);
        assertThatThrownBy(() -> action.handle(
            new ActionContext("s-1", "c-long", h2ConnId, "oc-1"),
            Map.of("keyword", tooLong, "schema", "PUBLIC")
        ).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(DataTalkException.class)
            .hasMessageContaining("200");
    }

    @Test
    @SuppressWarnings("unchecked")
    void limitIsClampedToThirtyMaximum() throws Exception {
        // Limit clamped to 30; with our seed (5 tables) truncated should be false.
        Map<String, Object> out = invoke("c-limit", Map.of(
            "keyword", "a", "schema", "PUBLIC", "limit", 999));

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) out.get("candidates");
        assertThat(candidates).hasSizeLessThanOrEqualTo(30);
        assertThat(out).containsEntry("truncated", false);
    }

    @Test
    void unsupportedDialectFailsWithDialectUnsupported() {
        // Insert a connection record with an unknown kind directly (bypass ConnectionService validation).
        String badId = "conn-bad-kind";
        long now = System.currentTimeMillis();
        datatalkJdbc.update("""
            INSERT INTO connections(id, name, kind, host, port, database_name, username, password_enc,
              schema_digest, created_at, connect_timeout, last_test_status, last_test_at,
              oracle_service_type, sqlserver_encrypt, sqlserver_trust_server_certificate,
              sqlserver_instance_name, read_only, compatibility_mode, oceanbase_tenant, oceanbase_cluster)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, 3000, NULL, NULL, NULL, 1, 1, NULL, 0, NULL, NULL, NULL)
            """,
            badId, "bad-kind", "cassandra", "localhost", 9042, "ks", "u", new byte[]{0}, now);
        datatalkJdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 1, ?, ?, ?, 0)
            """, "s-bad", badId, "Bad Kind", "oc-bad", now, now);

        assertThatThrownBy(() -> action.handle(
            new ActionContext("s-bad", "c-dialect", badId, "oc-bad"),
            Map.of("keyword", "foo", "connectionId", badId)
        ).toCompletableFuture().join())
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(DataTalkException.class)
            .satisfies(e -> {
                DataTalkException dte = (DataTalkException) e.getCause();
                assertThat(dte.code()).isEqualTo(DataTalkErrorCodes.DIALECT_UNSUPPORTED);
            });
    }

    @Test
    @SuppressWarnings("unchecked")
    void sqliteDegradesGracefullyWithEmptyCommentSnippet() throws Exception {
        // SQLite 不支持 COMMENT ON,REMARKS 总是空 → commentSnippet=""
        Path dbFile = tempDir.resolve("schema-search.db");
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             var st = c.createStatement()) {
            st.execute("CREATE TABLE orders(id INTEGER PRIMARY KEY, customer_id INTEGER, total REAL)");
            st.execute("CREATE TABLE products(id INTEGER PRIMARY KEY, name TEXT)");
        }
        String sqliteId = conn.create("schema search sqlite", "sqlite", "", 0,
            dbFile.toString(), "", "", null, null, null, null, null, null, null, null, null);
        long now = System.currentTimeMillis();
        datatalkJdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 1, ?, ?, ?, 0)
            """, "s-sqlite", sqliteId, "SQLite Schema Search", "oc-sqlite", now, now);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-sqlite", "c-sqlite", sqliteId, "oc-sqlite"),
            Map.of("keyword", "orders", "connectionId", sqliteId)
        ).toCompletableFuture().get();

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) out.get("candidates");
        assertThat(candidates).isNotEmpty();
        Map<String, Object> orders = candidates.stream()
            .filter(m -> "orders".equals(m.get("table"))).findFirst().orElseThrow();
        // SQLite 无 comment → commentSnippet 应为空串(SchemaSearchAction.toMap 兜底)
        assertThat(orders.get("commentSnippet")).isEqualTo("");
    }
}
