package com.datatalk.application.opencode;

import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Subscribes to OpenCode's global /event SSE stream, parses frames into
 * {@link OcEvent}s, translates them via {@link OpenCodeEventTranslator},
 * and publishes the resulting {@code DtEvent}s to the correct SessionBus
 * based on the {@code OpenCodeSessionMap}.
 *
 * <p>Backoff: starts at 1s, doubles to 30s max, resets on successful stream.</p>
 */
public class OpenCodeEventLoop {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper om;
    private final OpenCodeEventTranslator translator;
    private final SessionBusRegistry buses;
    private final OpenCodeSessionMap sessionMap;
    private final Consumer<OcEvent> tap;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread worker;

    public OpenCodeEventLoop(String baseUrl, ObjectMapper om,
                             OpenCodeEventTranslator translator,
                             SessionBusRegistry buses,
                             OpenCodeSessionMap sessionMap,
                             Consumer<OcEvent> tap) {
        this.baseUrl = baseUrl;
        this.om = om;
        this.translator = translator;
        this.buses = buses;
        this.sessionMap = sessionMap;
        this.tap = tap == null ? e -> {} : tap;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        worker = new Thread(this::runLoop, "opencode-event-loop");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running.set(false);
        Thread w = worker;
        if (w != null) w.interrupt();
    }

    private void runLoop() {
        Duration backoff = Duration.ofSeconds(1);
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                openStream();
                backoff = Duration.ofSeconds(1); // reset on success
            } catch (Exception e) {
                if (!running.get()) return;
                backoff = backoff.multipliedBy(2);
                if (backoff.getSeconds() > 30) backoff = Duration.ofSeconds(30);
            }
            if (running.get() && backoff.toMillis() > 0) {
                try { Thread.sleep(backoff.toMillis()); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void openStream() throws Exception {
        URI uri = URI.create(baseUrl + "/event");
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Accept", "text/event-stream")
            .GET()
            .build();

        HttpResponse<InputStream> response = httpClient
            .send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Unexpected status " + response.statusCode());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
            String line;
            StringBuilder dataBuilder = new StringBuilder();
            String event = null;

            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // End of frame
                    if (event != null) {
                        handleOcEvent(event, dataBuilder.toString());
                    }
                    event = null;
                    dataBuilder.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    dataBuilder.append(line.substring(5).trim());
                }
            }
        }
    }

    private void handleOcEvent(String eventName, String json) {
        OcEvent oc = parseOcEvent(eventName, json);
        tap.accept(oc);

        String openCodeSessionId = extractSessionId(oc);
        if (openCodeSessionId == null) return;
        String dataTalkSessionId = sessionMap.dataTalkFor(openCodeSessionId);
        if (dataTalkSessionId == null) return;
        SessionBus bus = buses.getOrCreate(dataTalkSessionId);
        for (DtEvent dt : translator.translate(dataTalkSessionId, oc)) {
            bus.publish(dt);
        }
    }

    private OcEvent parseOcEvent(String name, String json) {
        try {
            JsonNode node = json.isEmpty() ? om.createObjectNode() : om.readTree(json);
            return switch (name) {
                case "server.connected" -> new OcEvent.ServerConnected();
                case "session.status"   -> new OcEvent.SessionStatus(
                    node.path("status").asText("idle"),
                    om.convertValue(node.path("retryInfo"), Map.class)
                );
                case "message.updated"  -> new OcEvent.MessageUpdated(
                    om.treeToValue(node.path("info"), Message.class));
                case "message.part.updated" -> new OcEvent.MessagePartUpdated(
                    om.treeToValue(node.path("part"), Part.class));
                case "message.part.delta"   -> new OcEvent.MessagePartDelta(
                    node.path("partID").asText(),
                    node.path("field").asText(),
                    node.path("delta").asText());
                case "message.part.removed" -> new OcEvent.MessagePartRemoved(
                    node.path("partID").asText());
                default -> new OcEvent.Unknown(name, om.convertValue(node, Map.class));
            };
        } catch (Exception e) {
            return new OcEvent.Unknown(name, Map.of("parseError", e.getMessage()));
        }
    }

    private static String extractSessionId(OcEvent e) {
        return switch (e) {
            case OcEvent.MessageUpdated m   -> m.message().sessionId();
            case OcEvent.MessagePartUpdated p -> p.part().sessionID();
            case OcEvent.SessionStatus s    -> null;
            default                         -> null;
        };
    }
}
