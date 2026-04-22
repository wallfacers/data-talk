package com.datatalk.adapter.controller;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SecretVault;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "datatalk.sql.max-rows=3")
class SqlExecuteControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ConnectionRepository connRepo;
    @Autowired SecretVault vault;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    static final String CONN_ID = "c-sql-it";
    static final String POSTGRES_CONN_ID = "c-sql-it-postgres";

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM session_data_contexts");
        jdbc.update("DELETE FROM sessions");
        connRepo.deleteAll();
        // H2 in-memory DB: databaseName field is used by JdbcUrlBuilder for H2 URL suffix
        var cr = new ConnectionRecord(CONN_ID, "IT DB", "h2",
            "localhost", 0, "mem:sqlit;DB_CLOSE_DELAY=-1", "sa", vault.seal(""),
            null, System.currentTimeMillis(), 10, null, null);
        connRepo.insert(cr);
        // Seed test table in the H2 in-memory database
        try (var c = DriverManager.getConnection("jdbc:h2:mem:sqlit;DB_CLOSE_DELAY=-1", "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE TABLE items(id INT, name VARCHAR(50))");
            st.execute("INSERT INTO items VALUES(1,'a'),(2,'b'),(3,'c'),(4,'d')");
        }
    }

    @Test
    void session_context_can_supply_connection_for_execute_endpoint() throws Exception {
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 0, NULL, ?, ?, 0)
            """, "s-sql-it", null, "SQL Context", now, now);
        jdbc.update("""
            INSERT INTO session_data_contexts(session_id, connection_id, connection_name_snapshot, database_name, schema_name, selected_level, updated_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """, "s-sql-it", CONN_ID, "IT DB", "mem:sqlit;DB_CLOSE_DELAY=-1", "PUBLIC", "schema", now);

        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId":"s-sql-it","sql":"SELECT * FROM items ORDER BY id","source":"user"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resolvedContext.connectionId", is(CONN_ID)))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].kind", is("result_set")))
            .andExpect(jsonPath("$.results[0].columns", hasItems("ID", "NAME")))
            .andExpect(jsonPath("$.results[0].rowCount", is(3)));
    }

    @Test
    void select_returns_200_with_result_set_and_rows() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"SELECT * FROM items ORDER BY id","source":"user"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].kind", is("result_set")))
            .andExpect(jsonPath("$.results[0].columns", hasItems("ID", "NAME")))
            .andExpect(jsonPath("$.results[0].rowCount", is(3)))
            .andExpect(jsonPath("$.results[0].truncated", is(true)));  // max-rows=3, table has 4 rows
    }

    @Test
    void execute_auto_locates_missing_schema_and_returns_resolved_context() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:h2:mem:sqlit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE SCHEMA reporting");
            st.execute("CREATE TABLE reporting.items(id INT, name VARCHAR(50))");
            st.execute("INSERT INTO reporting.items VALUES(1,'a'),(2,'b'),(3,'c'),(4,'d')");
        }
        connRepo.deleteAll();
        connRepo.insert(new ConnectionRecord(CONN_ID, "IT DB", "h2",
            "localhost", 0, "mem:sqlit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", vault.seal(""),
            null, System.currentTimeMillis(), 10, null, null));

        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at, title_locked)
            VALUES(?, ?, ?, 0, NULL, ?, ?, 0)
            """, "s-auto-locate", CONN_ID, "SQL Auto Locate", now, now);
        jdbc.update("""
            INSERT INTO session_data_contexts(session_id, connection_id, connection_name_snapshot, database_name, schema_name, selected_level, updated_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """, "s-auto-locate", CONN_ID, "IT DB", "mem:sqlit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", null, "database", now);

        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId":"s-auto-locate","sql":"SELECT * FROM items ORDER BY id","source":"user"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resolvedContext.schema", is("REPORTING")))
            .andExpect(jsonPath("$.contextNotice", containsString("REPORTING")))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].kind", is("result_set")));
    }

    @Test
    void ai_source_skips_risk_check_and_executes() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"DELETE FROM items WHERE id = 4; INSERT INTO items VALUES(5,'e')","source":"ai"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].kind", is("dml_summary")))
            .andExpect(jsonPath("$.results[0].affectedRows", is(2)));
    }

    @Test
    void execute_returns_ordered_results_with_dml_summary_and_error_items() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "connectionId":"%s",
                      "source":"ai",
                      "sql":"SELECT id, name FROM items WHERE id <= 2 ORDER BY id; UPDATE items SET name = 'z' WHERE id = 1; DELETE FROM items WHERE id = 4; SELECT missing FROM items; SELECT id, name FROM items WHERE id = 1"
                    }
                    """.formatted(CONN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results", hasSize(3)))
            .andExpect(jsonPath("$.results[0].kind", is("result_set")))
            .andExpect(jsonPath("$.results[0].statementIndex", is(1)))
            .andExpect(jsonPath("$.results[0].resultId", not(isEmptyOrNullString())))
            .andExpect(jsonPath("$.results[0].columns", hasItems("ID", "NAME")))
            .andExpect(jsonPath("$.results[0].rowCount", is(2)))
            .andExpect(jsonPath("$.results[1].kind", is("dml_summary")))
            .andExpect(jsonPath("$.results[1].statementIndex", is(2)))
            .andExpect(jsonPath("$.results[1].affectedRows", is(2)))
            .andExpect(jsonPath("$.results[2].kind", is("error")))
            .andExpect(jsonPath("$.results[2].statementIndex", is(4)))
            .andExpect(jsonPath("$.results[2].statementText", containsString("SELECT missing FROM items")))
            .andExpect(jsonPath("$.results[2].errorMessage", not(isEmptyOrNullString())));

        try (var c = DriverManager.getConnection("jdbc:h2:mem:sqlit;DB_CLOSE_DELAY=-1", "sa", "");
             var st = c.createStatement();
             var rsCount = st.executeQuery("SELECT COUNT(*) FROM items")) {
            rsCount.next();
            org.assertj.core.api.Assertions.assertThat(rsCount.getInt(1)).isEqualTo(4);
        }
        try (var c = DriverManager.getConnection("jdbc:h2:mem:sqlit;DB_CLOSE_DELAY=-1", "sa", "");
             var st = c.createStatement();
             var rsName = st.executeQuery("SELECT name FROM items WHERE id = 1 ORDER BY id")) {
            rsName.next();
            org.assertj.core.api.Assertions.assertThat(rsName.getString(1)).isEqualTo("a");
        }
    }

    @Test
    void execute_localizes_result_titles_for_zh_cn() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "connectionId":"%s",
                      "source":"ai",
                      "sql":"SELECT id, name FROM items WHERE id <= 2 ORDER BY id; UPDATE items SET name = 'z' WHERE id = 1; DELETE FROM items WHERE id = 4; SELECT missing FROM items"
                    }
                    """.formatted(CONN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results", hasSize(3)))
            .andExpect(jsonPath("$.results[0].title", is("结果集 1")))
            .andExpect(jsonPath("$.results[1].title", is("DML 摘要 2-3")))
            .andExpect(jsonPath("$.results[2].title", is("错误 4")));
    }

    @Test
    void postgres_procedural_scripts_execute_without_splitting_inner_semicolons() throws Exception {
        Assumptions.assumeTrue(
            DockerClientFactory.instance().isDockerAvailable(),
            "Docker required for PostgreSQL procedural integration test"
        );

        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            connRepo.insert(new ConnectionRecord(
                POSTGRES_CONN_ID,
                "PG IT DB",
                "postgresql",
                postgres.getHost(),
                postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                postgres.getDatabaseName(),
                postgres.getUsername(),
                vault.seal(postgres.getPassword()),
                null,
                System.currentTimeMillis(),
                10,
                null,
                null
            ));
            try (var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var st = c.createStatement()) {
                st.execute("DROP TABLE IF EXISTS procedural_items");
                st.execute("CREATE TABLE procedural_items(id INT PRIMARY KEY, name TEXT NOT NULL)");
            }

            mvc.perform(post("/api/sql/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "connectionId":"%s",
                          "source":"ai",
                          "sql":"DO $$ BEGIN INSERT INTO procedural_items(id, name) VALUES (1, 'alpha'); INSERT INTO procedural_items(id, name) VALUES (2, 'beta'); END $$; SELECT id, name FROM procedural_items ORDER BY id"
                        }
                        """.formatted(POSTGRES_CONN_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].kind", is("dml_summary")))
                .andExpect(jsonPath("$.results[0].statementIndex", is(1)))
                .andExpect(jsonPath("$.results[0].statementText", containsString("INSERT INTO procedural_items(id, name) VALUES (1, 'alpha')")))
                .andExpect(jsonPath("$.results[0].statementText", containsString("INSERT INTO procedural_items(id, name) VALUES (2, 'beta')")))
                .andExpect(jsonPath("$.results[1].kind", is("result_set")))
                .andExpect(jsonPath("$.results[1].statementIndex", is(2)))
                .andExpect(jsonPath("$.results[1].rowCount", is(2)))
                .andExpect(jsonPath("$.results[1].rows[0][0]", is(1)))
                .andExpect(jsonPath("$.results[1].rows[0][1]", is("alpha")))
                .andExpect(jsonPath("$.results[1].rows[1][0]", is(2)))
                .andExpect(jsonPath("$.results[1].rows[1][1]", is("beta")));
        }
    }

    @Test
    void high_risk_user_sql_returns_422_and_blocks_the_whole_batch_before_execution() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"DELETE FROM items WHERE id = 1; INSERT INTO items VALUES (99,'x')","source":"user"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.riskLevel", is("HIGH")))
            .andExpect(jsonPath("$.riskReason", notNullValue()));
        try (var c = DriverManager.getConnection("jdbc:h2:mem:sqlit;DB_CLOSE_DELAY=-1", "sa", "");
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM items")) {
            rs.next();
            org.assertj.core.api.Assertions.assertThat(rs.getInt(1)).isEqualTo(4);
        }
    }

    @Test
    void unknown_connection_returns_400() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"no-such","sql":"SELECT 1","source":"ai"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalid_source_returns_400() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"SELECT 1","source":"system"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("source")));
    }

    @Test
    void invalid_source_returns_localized_message() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"SELECT 1","source":"system"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("source 必须是 user 或 ai")));
    }

    @Test
    void missing_source_returns_400() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"SELECT 1"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("source")));
    }
}
