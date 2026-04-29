package com.datatalk.domain.fileartifact;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileArtifactTest {

    @Test
    void status_dbValue_round_trip() {
        for (FileArtifactStatus status : FileArtifactStatus.values()) {
            assertThat(FileArtifactStatus.fromDb(status.dbValue())).isEqualTo(status);
        }
    }

    @Test
    void scope_dbValue_round_trip() {
        for (FileArtifactScope scope : FileArtifactScope.values()) {
            assertThat(FileArtifactScope.fromDb(scope.dbValue())).isEqualTo(scope);
        }
    }

    @Test
    void kind_dbValue_round_trip() {
        for (FileArtifactKind kind : FileArtifactKind.values()) {
            assertThat(FileArtifactKind.fromDb(kind.dbValue())).isEqualTo(kind);
        }
    }

    @Test
    void status_has_exactly_four_states() {
        assertThat(FileArtifactStatus.values())
            .containsExactly(
                FileArtifactStatus.TEMPORARY,
                FileArtifactStatus.CANDIDATE,
                FileArtifactStatus.ARCHIVED,
                FileArtifactStatus.DISCARDED);
    }

    @Test
    void file_artifact_record_constructs() {
        Instant now = Instant.parse("2026-04-29T00:00:00Z");

        FileArtifact artifact = new FileArtifact(
            "file_artifact_01",
            FileArtifactScope.SESSION,
            FileArtifactStatus.TEMPORARY,
            FileArtifactKind.OTHER,
            "ses_abc",
            null,
            "sample.csv",
            "/home/u/.data-talk/opencode/sessions/ses_abc/sample.csv",
            2_100_000L,
            "text/csv",
            null,
            null,
            now,
            now,
            null,
            Map.of());

        assertThat(artifact.id()).isEqualTo("file_artifact_01");
        assertThat(artifact.sessionIdOpt()).contains("ses_abc");
        assertThat(artifact.connectionIdOpt()).isEmpty();
        assertThat(artifact.archivedAtOpt()).isEmpty();
        assertThat(artifact.mimeTypeOpt()).contains("text/csv");
        assertThat(artifact.titleOpt()).isEmpty();
        assertThat(artifact.summaryOpt()).isEmpty();
        assertThat(artifact.metadata()).isEmpty();
    }
}
