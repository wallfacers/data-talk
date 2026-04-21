package com.datatalk.adapter.controller;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SecretVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
            .andExpect(jsonPath("$.columns", hasItems("ID", "NAME")))
            .andExpect(jsonPath("$.rowCount", is(3)));
    }

    @Test
    void select_returns_200_with_columns_and_rows() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"SELECT * FROM items ORDER BY id","source":"user"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.columns", hasItems("ID", "NAME")))
            .andExpect(jsonPath("$.rowCount", is(3)))
            .andExpect(jsonPath("$.truncated", is(true)));  // max-rows=3, table has 4 rows
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
            .andExpect(jsonPath("$.contextNotice", containsString("REPORTING")));
    }

    @Test
    void ai_source_skips_risk_check_and_executes() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"SELECT 1","source":"ai"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rowCount", is(1)));
    }

    @Test
    void high_risk_user_sql_returns_422() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"DELETE FROM items","source":"user"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.riskLevel", is("HIGH")))
            .andExpect(jsonPath("$.riskReason", notNullValue()));
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
}
