package com.datatalk.repository;

import com.datatalk.application.preference.UserPreferencesService;
import com.datatalk.domain.preference.UserPreferences;
import com.datatalk.entity.DbConnection;
import com.datatalk.entity.DbType;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicSqlExecutionRepositoryTest {

    @Test
    void execute_serializes_unsafe_bigint_cells_as_strings() throws Exception {
        String databaseName = "mem:dynamic-sql-exec;DB_CLOSE_DELAY=-1";
        try (var c = DriverManager.getConnection("jdbc:h2:" + databaseName, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE TABLE t(id BIGINT)");
            st.execute("INSERT INTO t(id) VALUES (9007199254740993)");
        }

        var userPrefsService = mock(UserPreferencesService.class);
        when(userPrefsService.getPreferences()).thenReturn(UserPreferences.DEFAULT);
        DynamicSqlExecutionRepository repository = new DynamicSqlExecutionRepository(userPrefsService);
        DbConnection connection = new DbConnection(
            "c-dynamic",
            "dynamic",
            DbType.H2,
            "localhost",
            0,
            databaseName,
            "sa",
            "",
            Instant.now()
        );

        var result = repository.execute(connection, "SELECT id FROM t", null);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0)).isEqualTo(Map.of("id", "9007199254740993"));
    }
}
