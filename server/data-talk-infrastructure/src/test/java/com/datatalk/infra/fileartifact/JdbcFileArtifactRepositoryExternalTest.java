package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFileArtifactRepositoryExternalTest {

    private FileArtifactRepository repo;

    @BeforeEach
    void setup(@TempDir Path tmp) {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");
        // Apply V14 (creates table) + V18 (adds external column + dashboard kind)
        new FlywayWrapper().migrate(url);
        var ds = new DriverManagerDataSource(url);
        repo = new JdbcFileArtifactRepository(new JdbcTemplate(ds), new ObjectMapper());
    }

    @Test
    void insert_and_round_trip_external_row() {
        FileArtifact row = newExternalRow("d1", "/data/dashboards/d1.dashboard.json");
        repo.insert(row);
        FileArtifact loaded = repo.findById("d1").orElseThrow();
        assertThat(loaded.external()).isTrue();
        assertThat(loaded.physicalPath()).isEqualTo("/data/dashboards/d1.dashboard.json");
    }

    @Test
    void findExternalRowsByDir_returns_only_external_rows_under_dir() {
        repo.insert(newExternalRow("d1", "/data/dashboards/d1.dashboard.json"));
        repo.insert(newExternalRow("d2", "/data/dashboards/d2.dashboard.json"));
        repo.insert(newExternalRow("e1", "/elsewhere/e1.dashboard.json"));
        // managed (external=false) row should not be returned
        repo.insert(newManagedRow("m1", "/data/dashboards/m1.txt"));

        List<FileArtifact> rows = repo.findExternalRowsByDir("/data/dashboards");
        assertThat(rows).extracting(FileArtifact::id)
                        .containsExactlyInAnyOrder("d1", "d2");
    }

    private static FileArtifact newExternalRow(String id, String path) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.DASHBOARD, null, null,
                Path.of(path).getFileName().toString(), path,
                10L, "application/json", null, null,
                now, now, now, Map.of(), true);
    }

    private static FileArtifact newManagedRow(String id, String path) {
        Instant now = Instant.now();
        return new FileArtifact(
                id, FileArtifactScope.WORKSPACE, FileArtifactStatus.ARCHIVED,
                FileArtifactKind.OTHER, null, "conn1",
                Path.of(path).getFileName().toString(), path,
                10L, null, null, null,
                now, now, now, Map.of(), false);
    }

    /** Helper to apply migrations against a fresh SQLite DB without depending on Flyway library. */
    private static class FlywayWrapper {
        void migrate(String url) {
            try {
                // Apply V14 first (creates file_artifact table)
                String v14 = new String(getClass().getClassLoader()
                        .getResourceAsStream("db/migration/V14__file_artifact.sql").readAllBytes());
                String v18 = new String(getClass().getClassLoader()
                        .getResourceAsStream("db/migration/V18__file_artifact_dashboard.sql").readAllBytes());

                try (var c = java.sql.DriverManager.getConnection(url);
                     var s = c.createStatement()) {
                    for (String sql : com.datatalk.infra.persistence.SqlScriptSplitter.split(v14)) {
                        s.executeUpdate(sql);
                    }
                    for (String sql : com.datatalk.infra.persistence.SqlScriptSplitter.split(v18)) {
                        s.executeUpdate(sql);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
