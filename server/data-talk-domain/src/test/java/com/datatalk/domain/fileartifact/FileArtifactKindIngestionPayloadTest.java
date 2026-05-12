package com.datatalk.domain.fileartifact;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FileArtifactKindIngestionPayloadTest {
    @Test void hasIngestionPayloadValue() {
        assertThat(FileArtifactKind.valueOf("INGESTION_PAYLOAD"))
            .isEqualTo(FileArtifactKind.INGESTION_PAYLOAD);
    }
    @Test void persistenceCodeIsLowercase() {
        assertThat(FileArtifactKind.INGESTION_PAYLOAD.dbValue()).isEqualTo("ingestion_payload");
    }
}
