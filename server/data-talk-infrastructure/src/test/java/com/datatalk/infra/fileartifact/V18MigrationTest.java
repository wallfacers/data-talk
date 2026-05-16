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

class FileArtifactDashboardKindTest {

    @Test
    void file_artifact_supports_dashboard_kind_and_external_column(@TempDir Path tmp) throws Exception {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            String sql = new String(
                    new ClassPathResource("db/migration/V1__init.sql").getInputStream().readAllBytes());
            for (String statement : SqlScriptSplitter.split(sql)) {
                s.executeUpdate(statement);
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
