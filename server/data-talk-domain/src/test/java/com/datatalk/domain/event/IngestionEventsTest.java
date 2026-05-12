package com.datatalk.domain.event;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IngestionEventsTest {
    @Test void allEightEventsExist() {
        DtEvent[] events = {
            new DtEvent.IngestionJobCreated("j1", "https://x"),
            new DtEvent.IngestionPayloadFetched("j1", "fa1", 100, 1024L),
            new DtEvent.IngestionMappingProposed("j1", "m1", 5),
            new DtEvent.IngestionJobConfirmed("j1", "ict_abc"),
            new DtEvent.IngestionWriteStarted("j1", "orders"),
            new DtEvent.IngestionWriteProgress("j1", 50, 100),
            new DtEvent.IngestionCompleted("j1", "orders", 100, 1234L),
            new DtEvent.IngestionFailed("j1", "fetch", "timeout")
        };
        assertThat(events).hasSize(8);
        for (var e : events) {
            assertThat(e).isInstanceOf(DtEvent.class);
        }
    }
}
