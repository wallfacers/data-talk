package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ReadSchemaActionIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @Autowired ConnectionService conn;
    @Autowired ReadSchemaAction action;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeAll
    void seedDb() throws Exception {
        try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             var st = c.createStatement()) {
            st.execute("CREATE TABLE users (id SERIAL PRIMARY KEY, name TEXT, created_at TIMESTAMP)");
            st.execute("CREATE TABLE orders (id SERIAL PRIMARY KEY, user_id INT REFERENCES users(id))");
        }
        conn.deleteAll();
        conn.create("pg-test", "postgresql", pg.getHost(), pg.getFirstMappedPort(),
            pg.getDatabaseName(), pg.getUsername(), pg.getPassword());
    }

    @Test
    @SuppressWarnings("unchecked")
    void readSchemaReturnsBothTables() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-1", "pg-test", "oc-1"),
            Map.of("connectionId", "pg-test")
        ).toCompletableFuture().get();

        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name"))
            .containsExactlyInAnyOrder("users", "orders");
    }
}
