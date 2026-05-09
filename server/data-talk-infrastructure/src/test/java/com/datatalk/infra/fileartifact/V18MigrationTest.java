package com.datatalk.infra.fileartifact;

import com.datatalk.infra.persistence.SqlScriptSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class V18MigrationTest {

    @Test
    void v18_adds_dashboard_kind_and_external_column(@TempDir Path tmp) throws Exception {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");

        List<String> migrationFiles = List.of(
                "V1__init.sql", "V2__ai_prefs.sql", "V3__cascade_session_delete.sql",
                "V4__session_title_locked.sql", "V5__connection_connect_timeout.sql",
                "V6__connection_test_status.sql", "V7__connection_name.sql",
                "V8__drop_messages.sql", "V9__synthetic_bang_query_messages.sql",
                "V10__session_data_context.sql", "V11__artifact_origin.sql",
                "V12__stage_tabs.sql", "V13__stage_tabs_workspace_only.sql",
                "V14__file_artifact.sql", "V15__oracle_connection_fields.sql",
                "V16__sqlserver_connection_fields.sql", "V17__duckdb_readonly.sql",
                "V18__file_artifact_dashboard.sql"
        );

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            for (String filename : migrationFiles) {
                String sql = new String(
                        new ClassPathResource("db/migration/" + filename).getInputStream().readAllBytes());
                for (String statement : SqlScriptSplitter.split(sql)) {
                    s.executeUpdate(statement);
                }
            }

            // V18 should allow kind='dashboard' and external=1
            s.executeUpdate(
                "INSERT INTO file_artifact " +
                "(id,scope,status,kind,filename,physical_path,size_bytes,created_at,updated_at,external) " +
                "VALUES ('d1','workspace','archived','dashboard','x.json','/abs/x.json',10,1,1,1)"
            );

            // Existing 5 kinds should still be valid
            s.executeUpdate(
                "INSERT INTO file_artifact " +
                "(id,scope,status,kind,filename,physical_path,size_bytes,created_at,updated_at,external) " +
                "VALUES ('r1','session','temporary','report','r.md','/abs/r.md',5,1,1,0)"
            );

            // external column defaults to 0
            try (ResultSet rs = s.executeQuery("SELECT external FROM file_artifact WHERE id='r1'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }

            // CHECK constraint: external must be 0 or 1
            try {
                s.executeUpdate(
                    "INSERT INTO file_artifact " +
                    "(id,scope,status,kind,filename,physical_path,size_bytes,created_at,updated_at,external) " +
                    "VALUES ('bad','session','temporary','report','b.md','/abs/b.md',1,1,1,2)"
                );
                throw new AssertionError("CHECK constraint on external should reject value 2");
            } catch (java.sql.SQLException expected) {
                assertThat(expected.getMessage()).containsIgnoringCase("CHECK");
            }
        }
    }
}
