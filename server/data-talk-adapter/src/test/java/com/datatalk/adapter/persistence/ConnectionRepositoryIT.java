package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ConnectionRepositoryIT {

    @Autowired ConnectionRepository repo;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM session_data_contexts");
        jdbc.update("DELETE FROM sessions");
        repo.deleteAll();
    }

    @Test
    void update_overwrites_fields_and_keeps_id() {
        repo.insert(new ConnectionRecord("c1", "测试数据源", "mysql", "h1", 3306, "db1", "u1",
            new byte[]{1}, null, 100, 3000, null, null, null, 1, true, null, false, null, null, null));
        repo.update(new ConnectionRecord("c1", "更新数据源", "postgres", "h2", 5432, "db2", "u2",
            new byte[]{2}, null, 100, 5000, null, null, null, 1, true, null, false, null, null, null));
        var rec = repo.findById("c1").orElseThrow();
        assertThat(rec.name()).isEqualTo("更新数据源");
        assertThat(rec.host()).isEqualTo("h2");
        assertThat(rec.port()).isEqualTo(5432);
        assertThat(rec.kind()).isEqualTo("postgres");
        assertThat(rec.connectTimeout()).isEqualTo(5000);
    }

    @Test
    void deleteById_returns_true_when_deleted_false_when_absent() {
        repo.insert(new ConnectionRecord("c1", "测试数据源", "mysql", "h", 1, "d", "u", new byte[]{1}, null, 1, 3000, null, null, null, 1, true, null, false, null, null, null));
        assertThat(repo.deleteById("c1")).isTrue();
        assertThat(repo.deleteById("c1")).isFalse();
    }
}
