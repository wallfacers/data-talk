package com.datatalk.application.opencode;

import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OpenCodeEventLoopTest {

    WireMockServer wm;

    @BeforeEach void up() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }
    @AfterEach void down() { wm.stop(); }

    @Test
    void parsesSseFramesIntoOcEvents() {
        String sse = """
            event: server.connected
            data: {}

            event: message.part.delta
            data: {"partID":"p1","field":"text","delta":"hi"}

            """;
        wm.stubFor(get(urlEqualTo("/event"))
            .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));

        List<OcEvent> received = new ArrayList<>();
        OpenCodeSessionMap map = new OpenCodeSessionMap();
        map.bind("dt-1", "oc-1");

        SessionBus mockBus = Mockito.mock(SessionBus.class);
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        when(buses.getOrCreate("dt-1")).thenReturn(mockBus);

        OpenCodeEventTranslator tr = new OpenCodeEventTranslator(Mockito.mock(SessionTitleSyncer.class));
        OpenCodeEventLoop loop = new OpenCodeEventLoop(
            "http://localhost:" + wm.port(), new ObjectMapper(), tr, buses, map, received::add);

        loop.start();
        await().atMost(Duration.ofSeconds(3)).until(() -> received.size() >= 2);
        loop.stop();

        assertThat(received.get(0)).isInstanceOf(OcEvent.ServerConnected.class);
        assertThat(received.get(1)).isInstanceOf(OcEvent.MessagePartDelta.class);
    }

    @Test
    void sessionUpdatedReachesSessionBus() {
        String sse = """
            event: session.updated
            data: {"info":{"id":"oc-1","title":"AI 标题","version":2}}

            """;
        wm.stubFor(get(urlEqualTo("/event"))
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
            "http://localhost:" + wm.port(), new ObjectMapper(), tr, buses, map, received::add);

        loop.start();
        await().atMost(Duration.ofSeconds(3)).until(() -> !received.isEmpty());
        loop.stop();

        assertThat(received.get(0)).isInstanceOf(OcEvent.SessionUpdated.class);
        Mockito.verify(syncer).apply("oc-1", "AI 标题");
        Mockito.verify(mockBus).publish(any(DtEvent.SessionMetaUpdated.class));
    }
}
