package com.datatalk.adapter.smoke;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

/**
 * End-to-end test for the typical query scenario.
 * Requires Docker for Testcontainers PostgreSQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TypicalQueryE2EIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @Autowired ConnectionService conn;
    @Autowired SessionRepository sessRepo;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeAll
    void seed() throws Exception {
        try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             var st = c.createStatement()) {
            st.execute("CREATE TABLE users (id SERIAL PRIMARY KEY, name TEXT)");
            st.execute("INSERT INTO users(name) VALUES('Alice'),('Bob')");
        }
        conn.deleteAll();
        String connectionId = conn.create("postgresql", pg.getHost(), pg.getFirstMappedPort(),
            pg.getDatabaseName(), pg.getUsername(), pg.getPassword(), null);
        sessRepo.upsert(new SessionRecord("s-e2e", connectionId, "E2E Test", false, null, 0L, 0L, false));
    }

    @Test
    void smokeTestConnectionExists() {
        // Placeholder: real e2e requires OpenCode WireMock orchestration
        var conns = conn.list();
        assert !conns.isEmpty();
    }
}
