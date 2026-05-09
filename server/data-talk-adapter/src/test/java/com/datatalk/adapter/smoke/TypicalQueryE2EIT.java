package com.datatalk.adapter.smoke;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = DataTalkApplication.class)
class TypicalQueryE2EIT {

    private static final String DATABASE_NAME =
        "mem:typical-query-e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Autowired ConnectionService conn;
    @Autowired SessionRepository sessRepo;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    @BeforeAll
    void seed() throws Exception {
        datatalkJdbc.update("DELETE FROM session_data_contexts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");
        try (var c = DriverManager.getConnection("jdbc:h2:" + DATABASE_NAME, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS users");
            st.execute("CREATE TABLE users (id SERIAL PRIMARY KEY, name TEXT)");
            st.execute("INSERT INTO users(name) VALUES('Alice'),('Bob')");
        }
        String connectionId = conn.create("E2E Test Connection", ConnectionKind.H2, "local", 0,
            DATABASE_NAME, "sa", "", null, null, null, null, null, null, null, null, null);
        sessRepo.upsert(new SessionRecord("s-e2e", connectionId, "E2E Test", false, null, 0L, 0L, false));
    }

    @Test
    void smokeTestConnectionExists() {
        var conns = conn.list();
        assertThat(conns).isNotEmpty();
    }
}
