package com.datatalk.adapter.controller;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SecretVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    static final String CONN_ID = "c-sql-it";

    @BeforeEach
    void setUp() throws Exception {
        connRepo.deleteAll();
        // H2 in-memory DB: databaseName field is used by JdbcUrlBuilder for H2 URL suffix
        var cr = new ConnectionRecord(CONN_ID, "IT DB", "h2",
            "localhost", 0, "mem:sqlit;DB_CLOSE_DELAY=-1", "sa", vault.seal(""),
            null, System.currentTimeMillis(), 10, null, null);
        connRepo.insert(cr);
        // Seed test table in the H2 in-memory database
        try (var c = DriverManager.getConnection("jdbc:h2:mem:sqlit;DB_CLOSE_DELAY=-1", "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS items");
            st.execute("CREATE TABLE items(id INT, name VARCHAR(50))");
            st.execute("INSERT INTO items VALUES(1,'a'),(2,'b'),(3,'c'),(4,'d')");
        }
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
