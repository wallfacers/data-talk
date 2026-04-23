package com.datatalk.application.chart;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.PayloadRef;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ChartArtifactServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ArtifactRepository artifacts;
    private SessionBusRegistry buses;
    private SessionBus bus;
    private IdGenerator ids;
    private ChartArtifactService service;

    @BeforeEach
    void setUp() {
        artifacts = mock(ArtifactRepository.class);
        buses = mock(SessionBusRegistry.class);
        bus = mock(SessionBus.class);
        ids = mock(IdGenerator.class);

        when(buses.getOrCreate("s1")).thenReturn(bus);
        when(ids.nextArtifactId()).thenReturn("art_new");

        service = new ChartArtifactService(
            artifacts,
            buses,
            objectMapper,
            Clock.fixed(Instant.ofEpochMilli(12_345L), ZoneId.of("UTC")),
            ids
        );
    }

    @Test
    void createInsertsArtifactAndPublishesRichPatch() throws Exception {
        var req = new ChartArtifactService.Request(
            "s1",
            Map.of("series", List.of(Map.of("type", "bar"))),
            "art_src",
            "msg_7",
            "part_2",
            "call_42"
        );
        when(artifacts.findLatestById("art_src")).thenReturn(Optional.empty());

        var result = service.createChartArtifact(req);

        assertThat(result.artifactId()).isEqualTo("art_new");
        assertThat(result.version()).isEqualTo(1);

        ArgumentCaptor<ArtifactRecord> recordCaptor = ArgumentCaptor.forClass(ArtifactRecord.class);
        verify(artifacts).insert(recordCaptor.capture());

        ArtifactRecord record = recordCaptor.getValue();
        assertThat(record.id()).isEqualTo("art_new");
        assertThat(record.version()).isEqualTo(1);
        assertThat(record.sessionId()).isEqualTo("s1");
        assertThat(record.kind()).isEqualTo("chart");
        assertThat(record.producedBy()).isEqualTo("call_42");
        assertThat(record.supersedesId()).isEqualTo("art_src");
        assertThat(record.supersedesVersion()).isNull();
        assertThat(record.originMessageId()).isEqualTo("msg_7");
        assertThat(record.originPartId()).isEqualTo("part_2");
        assertThat(record.payloadRef()).startsWith(PayloadRef.INLINE_PREFIX);

        String payloadJson = record.payloadRef().substring(PayloadRef.INLINE_PREFIX.length());
        Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {});
        assertThat(payload).containsEntry("sourceArtifactId", "art_src");
        assertThat(payload).containsKey("echartsOption");

        ArgumentCaptor<DtEvent> eventCaptor = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus).publish(eventCaptor.capture());

        DtEvent.OntologyUpdated event = (DtEvent.OntologyUpdated) eventCaptor.getValue();
        assertThat(event.objectType()).isEqualTo("datatalk.artifact");
        assertThat(event.id()).isEqualTo("art_new");
        assertThat(event.op()).isEqualTo("upsert");
        assertThat(event.patch())
            .containsEntry("kind", "chart")
            .containsEntry("version", 1)
            .containsEntry("producedBy", "call_42")
            .containsEntry("supersedesId", "art_src")
            .containsEntry("originMessageId", "msg_7")
            .containsEntry("originPartId", "part_2");
    }

    @Test
    void nullEchartsOptionRejected() {
        var req = new ChartArtifactService.Request(
            "s1",
            null,
            null,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> service.createChartArtifact(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("echartsOption must not be null");
    }

    @Test
    void nullSourceAndOriginAcceptedAndOmittedFromPatch() throws Exception {
        var req = new ChartArtifactService.Request(
            "s1",
            Map.of("series", List.of()),
            null,
            null,
            null,
            null
        );

        var result = service.createChartArtifact(req);

        assertThat(result.artifactId()).isEqualTo("art_new");
        assertThat(result.version()).isEqualTo(1);
        verify(artifacts, never()).findLatestById(anyString());

        ArgumentCaptor<ArtifactRecord> recordCaptor = ArgumentCaptor.forClass(ArtifactRecord.class);
        verify(artifacts).insert(recordCaptor.capture());
        ArtifactRecord record = recordCaptor.getValue();
        assertThat(record.producedBy()).isEqualTo("rest:chart");
        assertThat(record.supersedesId()).isNull();
        assertThat(record.supersedesVersion()).isNull();
        assertThat(record.originMessageId()).isNull();
        assertThat(record.originPartId()).isNull();

        String payloadJson = record.payloadRef().substring(PayloadRef.INLINE_PREFIX.length());
        Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {});
        assertThat(payload).containsEntry("sourceArtifactId", null);

        ArgumentCaptor<DtEvent> eventCaptor = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus).publish(eventCaptor.capture());
        DtEvent.OntologyUpdated event = (DtEvent.OntologyUpdated) eventCaptor.getValue();
        assertThat(event.patch())
            .containsEntry("kind", "chart")
            .containsEntry("version", 1)
            .containsEntry("producedBy", "rest:chart")
            .doesNotContainKeys("supersedesId", "originMessageId", "originPartId");
    }

    @Test
    void supersedesLookupWritesSupersedesFields() {
        when(artifacts.findLatestById("art_old")).thenReturn(Optional.of(new ArtifactRecord(
            "art_old",
            3,
            "s1",
            "chart",
            "call_x",
            "INLINE:{}",
            2,
            null,
            null,
            false,
            0L,
            null,
            null
        )));

        var req = new ChartArtifactService.Request(
            "s1",
            Map.of("series", List.of()),
            "art_old",
            null,
            null,
            null
        );

        service.createChartArtifact(req);

        ArgumentCaptor<ArtifactRecord> recordCaptor = ArgumentCaptor.forClass(ArtifactRecord.class);
        verify(artifacts).insert(recordCaptor.capture());
        ArtifactRecord record = recordCaptor.getValue();
        assertThat(record.supersedesId()).isEqualTo("art_old");
        assertThat(record.supersedesVersion()).isEqualTo(3);

        ArgumentCaptor<DtEvent> eventCaptor = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus).publish(eventCaptor.capture());
        DtEvent.OntologyUpdated event = (DtEvent.OntologyUpdated) eventCaptor.getValue();
        assertThat(event.patch()).containsEntry("supersedesId", "art_old");
    }
}
