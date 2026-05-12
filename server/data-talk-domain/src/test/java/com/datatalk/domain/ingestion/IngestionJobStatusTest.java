package com.datatalk.domain.ingestion;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IngestionJobStatusTest {
    @Test void roundTripsToPersistenceString() {
        for (var s : new IngestionJobStatus[] {
            new IngestionJobStatus.Pending(),
            new IngestionJobStatus.Fetching(),
            new IngestionJobStatus.Fetched(),
            new IngestionJobStatus.Mapping(),
            new IngestionJobStatus.AwaitingConfirm(),
            new IngestionJobStatus.Writing(),
            new IngestionJobStatus.Completed(),
            new IngestionJobStatus.Failed("err"),
            new IngestionJobStatus.Cancelled()
        }) {
            String code = IngestionJobStatus.toCode(s);
            assertThat(IngestionJobStatus.fromCode(code, null).getClass())
                .isEqualTo(s.getClass());
        }
    }
    @Test void exhaustiveSwitchCompilesAndCoversAll() {
        IngestionJobStatus s = new IngestionJobStatus.Fetched();
        String label = switch (s) {
            case IngestionJobStatus.Pending p -> "p";
            case IngestionJobStatus.Fetching f -> "f";
            case IngestionJobStatus.Fetched f -> "fd";
            case IngestionJobStatus.Mapping m -> "m";
            case IngestionJobStatus.AwaitingConfirm a -> "ac";
            case IngestionJobStatus.Writing w -> "w";
            case IngestionJobStatus.Completed c -> "c";
            case IngestionJobStatus.Failed f -> "fail";
            case IngestionJobStatus.Cancelled c -> "cancel";
        };
        assertThat(label).isEqualTo("fd");
    }
}
