package com.datatalk.infra.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class V20MigrationIT {
    @Autowired JdbcTemplate jdbc;

    @Test void ingestionJobTableCreated() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='ingestion_job'",
            Integer.class)).isEqualTo(1);
    }

    @Test void ingestionCredentialTableCreated() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='ingestion_credential'",
            Integer.class)).isEqualTo(1);
    }

    @Test void fileArtifactKindCheckAcceptsIngestionPayload() {
        jdbc.update("INSERT INTO file_artifact (id, scope, status, kind, filename, physical_path, " +
            "size_bytes, created_at, updated_at, external) VALUES " +
            "('test-ip-1','workspace','temporary','ingestion_payload','p.json','/tmp/p.json',0,0,0,1)");
        assertThat(jdbc.queryForObject(
            "SELECT kind FROM file_artifact WHERE id='test-ip-1'", String.class))
            .isEqualTo("ingestion_payload");
        jdbc.update("DELETE FROM file_artifact WHERE id='test-ip-1'");
    }

    @Test void fileArtifactKindCheckStillAcceptsDashboard() {
        jdbc.update("INSERT INTO file_artifact (id, scope, status, kind, filename, physical_path, " +
            "size_bytes, created_at, updated_at, external) VALUES " +
            "('test-d-1','workspace','temporary','dashboard','d.json','/tmp/d.json',0,0,0,1)");
        jdbc.update("DELETE FROM file_artifact WHERE id='test-d-1'");
    }

    @Test void fileArtifactExternalIndexExists() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_file_artifact_external'",
            Integer.class)).isEqualTo(1);
    }
}
