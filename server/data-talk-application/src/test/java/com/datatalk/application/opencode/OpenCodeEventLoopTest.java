package com.datatalk.application.opencode;

import com.datatalk.application.channel.PendingFileUploadEchoRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.FileUploadPart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class OpenCodeEventLoopTest {

    WireMockServer wm;

    @BeforeEach void up() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }
    @AfterEach void down() { wm.stop(); }

    @Test
    void parsesSseFramesIntoOcEvents() {
        // OpenCode 1.4.7 /global/event wraps each frame as
        // {"directory":"...","payload":{"type":"...","properties":{...}}}
        // and carries the event name inside payload.type (no SSE event: field).
        String sse = """
            data: {"directory":"/tmp","payload":{"type":"server.connected","properties":{}}}

            data: {"directory":"/tmp","payload":{"type":"message.part.delta","properties":{"sessionID":"oc-1","partID":"p1","field":"text","delta":"hi"}}}

            """;
        wm.stubFor(get(urlEqualTo("/global/event"))
            .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));

        List<OcEvent> received = new ArrayList<>();
        OpenCodeSessionMap map = new OpenCodeSessionMap();
        map.bind("dt-1", "oc-1");

        SessionBus mockBus = Mockito.mock(SessionBus.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        when(buses.getOrCreate("dt-1")).thenReturn(mockBus);

        OpenCodeEventTranslator tr = new OpenCodeEventTranslator(Mockito.mock(SessionTitleSyncer.class));
        OpenCodeEventLoop loop = new OpenCodeEventLoop(
            "http://localhost:" + wm.port(), new ObjectMapper(), tr, buses, map, received::add, null, null);

        loop.start();
        await().atMost(Duration.ofSeconds(3)).until(() -> received.size() >= 2);
        loop.stop();

        assertThat(received.get(0)).isInstanceOf(OcEvent.ServerConnected.class);
        assertThat(received.get(1)).isInstanceOf(OcEvent.MessagePartDelta.class);
    }

    @Test
    void messagePartDeltaWithoutSessionIdRoutesViaPartIdBinding() {
        // OpenCode 1.4.7's message.part.delta frames carry only partID/field/delta.
        // To reach the right SessionBus they must inherit the sessionID from the
        // preceding message.part.updated for the same partID. Regression: prior
        // to the partToOpenCodeSession map, every delta was dropped because
        // extractSessionId returned null.
        String sse = """
            data: {"directory":"/tmp","payload":{"type":"message.part.updated","properties":{"part":{"id":"p1","sessionID":"oc-1","type":"text","text":""}}}}

            data: {"directory":"/tmp","payload":{"type":"message.part.delta","properties":{"partID":"p1","field":"text","delta":"hi"}}}

            data: {"directory":"/tmp","payload":{"type":"message.part.delta","properties":{"partID":"p1","field":"text","delta":" world"}}}

            """;
        wm.stubFor(get(urlEqualTo("/global/event"))
            .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));

        OpenCodeSessionMap map = new OpenCodeSessionMap();
        map.bind("dt-1", "oc-1");

        SessionBus mockBus = Mockito.mock(SessionBus.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        when(buses.getOrCreate("dt-1")).thenReturn(mockBus);

        OpenCodeEventTranslator tr = new OpenCodeEventTranslator(Mockito.mock(SessionTitleSyncer.class));
        List<OcEvent> received = new ArrayList<>();
        OpenCodeEventLoop loop = new OpenCodeEventLoop(
            "http://localhost:" + wm.port(), new ObjectMapper(), tr, buses, map, received::add, null, null);

        loop.start();
        await().atMost(Duration.ofSeconds(3)).until(() -> received.size() >= 3);
        loop.stop();

        ArgumentCaptor<DtEvent> published = ArgumentCaptor.forClass(DtEvent.class);
        Mockito.verify(mockBus, Mockito.atLeast(2)).publish(published.capture());
        List<DtEvent.MessagePartDelta> deltas = published.getAllValues().stream()
            .filter(DtEvent.MessagePartDelta.class::isInstance)
            .map(DtEvent.MessagePartDelta.class::cast)
            .toList();
        assertThat(deltas).extracting(DtEvent.MessagePartDelta::delta).containsExactly("hi", " world");
    }

    @Test
    void messagePartRemovedRoutesBeforeBindingIsCleared() {
        String sse = """
            data: {"directory":"/tmp","payload":{"type":"message.part.updated","properties":{"part":{"id":"p1","sessionID":"oc-1","type":"text","text":"hello"}}}}

            data: {"directory":"/tmp","payload":{"type":"message.part.removed","properties":{"sessionID":"oc-1","partID":"p1"}}}

            """;
        wm.stubFor(get(urlEqualTo("/global/event"))
            .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));

        OpenCodeSessionMap map = new OpenCodeSessionMap();
        map.bind("dt-1", "oc-1");

        SessionBus mockBus = Mockito.mock(SessionBus.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        when(buses.getOrCreate("dt-1")).thenReturn(mockBus);

        OpenCodeEventTranslator tr = new OpenCodeEventTranslator(Mockito.mock(SessionTitleSyncer.class));
        List<OcEvent> received = new ArrayList<>();
        OpenCodeEventLoop loop = new OpenCodeEventLoop(
            "http://localhost:" + wm.port(), new ObjectMapper(), tr, buses, map, received::add, null, null);

        loop.start();
        await().atMost(Duration.ofSeconds(3)).until(() -> received.size() >= 2);
        loop.stop();

        ArgumentCaptor<DtEvent> published = ArgumentCaptor.forClass(DtEvent.class);
        Mockito.verify(mockBus, Mockito.atLeast(2)).publish(published.capture());
        assertThat(published.getAllValues())
            .filteredOn(DtEvent.MessagePartRemoved.class::isInstance)
            .singleElement()
            .extracting(e -> ((DtEvent.MessagePartRemoved) e).partId())
            .isEqualTo("p1");
    }

    @Test
    void evictsStalePartBindingsOnPeriodicCleanup() {
        AtomicLong now = new AtomicLong(1_000L);
        OpenCodeSessionMap sessionMap = new OpenCodeSessionMap();
        sessionMap.bind("dt-1", "oc-1");
        SessionBus mockBus = Mockito.mock(SessionBus.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        when(buses.getOrCreate("dt-1")).thenReturn(mockBus);
        OpenCodeEventLoop loop = new OpenCodeEventLoop(
            "http://test",
            new ObjectMapper(),
            new OpenCodeEventTranslator(Mockito.mock(SessionTitleSyncer.class)),
            buses,
            sessionMap,
            null,
            null,
            null,
            Duration.ofMillis(100),
            Duration.ofMillis(10),
            now::get
        );

        ReflectionTestUtils.invokeMethod(loop, "handleOcEvent",
            "message.part.updated",
            """
                {"type":"message.part.updated","properties":{"part":{"id":"p1","sessionID":"oc-1","type":"text","text":""}}}
                """);
        assertThat(partBindings(loop)).hasSize(1);

        now.addAndGet(200L);
        ReflectionTestUtils.invokeMethod(loop, "handleOcEvent",
            "server.connected",
            """
                {"type":"server.connected","properties":{}}
                """);

        assertThat(partBindings(loop)).isEmpty();
    }

    @Test
    void warnsWhenDroppingOrphanSessionEvent(CapturedOutput output) {
        OpenCodeEventLoop loop = new OpenCodeEventLoop(
            "http://test",
            new ObjectMapper(),
            new OpenCodeEventTranslator(Mockito.mock(SessionTitleSyncer.class)),
            Mockito.mock(SessionBusRegistry.class),
            new OpenCodeSessionMap(),
            null,
            null,
            null
        );

        ReflectionTestUtils.invokeMethod(loop, "handleOcEvent",
            "session.updated",
            """
                {"type":"session.updated","properties":{"info":{"id":"oc-orphan","title":"ghost","time":{"updated":1}}}}
                """);

        assertThat(output).contains("orphan OpenCode session event dropped");
        assertThat(output).contains("session.updated");
        assertThat(output).contains("oc-orphan");
    }

    @Test
    void sessionUpdatedReachesSessionBus() {
        // OpenCode 1.4.7 wraps payload under properties and uses time.updated
        // (millis epoch) instead of a monotonic version integer.
        String sse = """
            data: {"directory":"/tmp","payload":{"type":"session.updated","properties":{"sessionID":"oc-1","info":{"id":"oc-1","title":"AI 标题","time":{"created":1000,"updated":2000}}}}}

            """;
        wm.stubFor(get(urlEqualTo("/global/event"))
            .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));

        List<OcEvent> received = new ArrayList<>();
        OpenCodeSessionMap map = new OpenCodeSessionMap();
        map.bind("dt-1", "oc-1");

        SessionBus mockBus = Mockito.mock(SessionBus.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        when(buses.getOrCreate("dt-1")).thenReturn(mockBus);

        SessionTitleSyncer syncer = Mockito.mock(SessionTitleSyncer.class);
        when(syncer.apply("oc-1", "AI 标题")).thenReturn(true);
        OpenCodeEventTranslator tr = new OpenCodeEventTranslator(syncer);
        OpenCodeEventLoop loop = new OpenCodeEventLoop(
            "http://localhost:" + wm.port(), new ObjectMapper(), tr, buses, map, received::add, null, null);

        loop.start();
        await().atMost(Duration.ofSeconds(3)).until(() -> !received.isEmpty());
        loop.stop();

        assertThat(received.get(0)).isInstanceOf(OcEvent.SessionUpdated.class);
        Mockito.verify(syncer).apply("oc-1", "AI 标题");
        Mockito.verify(mockBus).publish(any(DtEvent.SessionMetaUpdated.class));
    }

    @Test
    void userMessageCreatedDrainsAndEchoesPendingFileUploadParts() {
        // SSE stream: a user message.updated arrives — translator first-seen logic
        // emits MessageCreated. The event loop must then drain the pending
        // FileUploadPart enqueued by ChannelService and publish a synthetic
        // MessagePartCreated carrying that part with the OpenCode messageID.
        String sse = """
            data: {"directory":"/tmp","payload":{"type":"message.updated","properties":{"info":{"id":"msg_user_42","sessionID":"oc-1","role":"user","time":{"created":1000}}}}}

            """;
        wm.stubFor(get(urlEqualTo("/global/event"))
            .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));

        OpenCodeSessionMap map = new OpenCodeSessionMap();
        map.bind("dt-1", "oc-1");

        SessionBus mockBus = Mockito.mock(SessionBus.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        when(buses.getOrCreate("dt-1")).thenReturn(mockBus);

        PendingFileUploadEchoRegistry registry = new PendingFileUploadEchoRegistry();
        FileUploadPart stashed = new FileUploadPart(
            "prt_local_1", "dt-1", "", "file-xyz", "photo.png", "image/png", 2048L, Map.of());
        registry.enqueue("dt-1", List.of(stashed));

        OpenCodeEventTranslator tr = new OpenCodeEventTranslator(Mockito.mock(SessionTitleSyncer.class));
        OpenCodeEventLoop loop = new OpenCodeEventLoop(
            "http://localhost:" + wm.port(), new ObjectMapper(), tr, buses, map, null, registry, null);

        loop.start();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            ArgumentCaptor<DtEvent> c = ArgumentCaptor.forClass(DtEvent.class);
            Mockito.verify(mockBus, Mockito.atLeast(2)).publish(c.capture());
            assertThat(c.getAllValues())
                .filteredOn(DtEvent.MessagePartCreated.class::isInstance)
                .isNotEmpty();
        });
        loop.stop();

        ArgumentCaptor<DtEvent> captor = ArgumentCaptor.forClass(DtEvent.class);
        Mockito.verify(mockBus, Mockito.atLeast(2)).publish(captor.capture());
        DtEvent.MessagePartCreated echoed = captor.getAllValues().stream()
            .filter(DtEvent.MessagePartCreated.class::isInstance)
            .map(DtEvent.MessagePartCreated.class::cast)
            .findFirst()
            .orElseThrow();
        assertThat(echoed.part().path("type").asText()).isEqualTo("file_upload");
        assertThat(echoed.part().path("messageID").asText()).isEqualTo("msg_user_42");
        assertThat(echoed.part().path("fileId").asText()).isEqualTo("file-xyz");
        assertThat(echoed.part().path("filename").asText()).isEqualTo("photo.png");

        // Registry must drain to empty after a successful echo.
        assertThat(registry.drainNext("dt-1")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> partBindings(OpenCodeEventLoop loop) {
        return (Map<String, ?>) ReflectionTestUtils.getField(loop, "partToOpenCodeSession");
    }
}
