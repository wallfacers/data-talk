package com.datatalk.application.opencode;

import com.datatalk.application.channel.PendingFileUploadEchoRegistry;
import com.datatalk.application.persistence.UserMessageAttachmentRecord;
import com.datatalk.application.persistence.UserMessageAttachmentRepository;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.FileUploadPart;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Subscribes to OpenCode's /global/event SSE stream, parses frames into
 * {@link OcEvent}s, translates them via {@link OpenCodeEventTranslator},
 * and publishes the resulting {@code DtEvent}s to the correct SessionBus
 * based on the {@code OpenCodeSessionMap}.
 *
 * <p>Backoff: starts at 1s, doubles to 30s max, resets on successful stream.</p>
 */
public class OpenCodeEventLoop {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeEventLoop.class);
    private static final Duration DEFAULT_PART_BINDING_TTL = Duration.ofMinutes(30);
    private static final Duration DEFAULT_PART_BINDING_CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final HttpClient httpClient;
    private volatile String baseUrl;
    private final ObjectMapper om;
    private final OpenCodeEventTranslator translator;
    private final SessionBusRegistry buses;
    private final OpenCodeSessionMap sessionMap;
    private final Consumer<OcEvent> tap;
    private final PendingFileUploadEchoRegistry fileUploadEcho;
    private final UserMessageAttachmentRepository attachmentRepo;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread worker;

    /**
     * OpenCode's {@code message.part.delta} frame carries only {@code partID},
     * {@code field}, and {@code delta} — no {@code sessionID}. To route delta
     * events to the right SessionBus we remember the {@code partId →
     * openCodeSessionId} binding plus a last-seen timestamp, so stale entries
     * can be reaped even if {@code message.part.removed} goes missing.
     */
    private final Map<String, PartSessionBinding> partToOpenCodeSession = new ConcurrentHashMap<>();
    private final long partBindingTtlMillis;
    private final long partBindingCleanupIntervalMillis;
    private final LongSupplier nowMillisSupplier;
    private volatile long nextPartBindingCleanupAtMillis;

    public OpenCodeEventLoop(String baseUrl, ObjectMapper om,
                             OpenCodeEventTranslator translator,
                             SessionBusRegistry buses,
                             OpenCodeSessionMap sessionMap,
                             Consumer<OcEvent> tap,
                             PendingFileUploadEchoRegistry fileUploadEcho,
                             UserMessageAttachmentRepository attachmentRepo) {
        this(baseUrl, om, translator, buses, sessionMap, tap, fileUploadEcho, attachmentRepo,
            DEFAULT_PART_BINDING_TTL, DEFAULT_PART_BINDING_CLEANUP_INTERVAL, System::currentTimeMillis);
    }

    OpenCodeEventLoop(String baseUrl, ObjectMapper om,
                      OpenCodeEventTranslator translator,
                      SessionBusRegistry buses,
                      OpenCodeSessionMap sessionMap,
                      Consumer<OcEvent> tap,
                      PendingFileUploadEchoRegistry fileUploadEcho,
                      UserMessageAttachmentRepository attachmentRepo,
                      Duration partBindingTtl,
                      Duration partBindingCleanupInterval,
                      LongSupplier nowMillisSupplier) {
        this.baseUrl = baseUrl;
        this.om = om;
        this.translator = translator;
        this.buses = buses;
        this.sessionMap = sessionMap;
        this.tap = tap == null ? e -> {} : tap;
        this.fileUploadEcho = fileUploadEcho;
        this.attachmentRepo = attachmentRepo;
        this.partBindingTtlMillis = requirePositiveMillis(partBindingTtl, "partBindingTtl");
        this.partBindingCleanupIntervalMillis = requirePositiveMillis(partBindingCleanupInterval, "partBindingCleanupInterval");
        this.nowMillisSupplier = nowMillisSupplier;
        this.nextPartBindingCleanupAtMillis = nowMillisSupplier.getAsLong() + this.partBindingCleanupIntervalMillis;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public void setBaseUrl(String url) {
        this.baseUrl = url;
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
        log.info("[opencode-event-loop] starting; baseUrl={}", baseUrl);
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                openStream();
                backoff = Duration.ofSeconds(1); // reset on success
            } catch (Exception e) {
                if (!running.get()) return;
                log.warn("[opencode-event-loop] stream error (baseUrl={}), backing off {}s: {}",
                    baseUrl, backoff.getSeconds(), e.toString());
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
        URI uri = URI.create(baseUrl + "/global/event");
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
        log.info("[opencode-event-loop] subscribed to {}/global/event (status 200)", baseUrl);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
            String line;
            StringBuilder dataBuilder = new StringBuilder();

            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // End of frame
                    if (dataBuilder.length() > 0) {
                        handleFrame(dataBuilder.toString());
                    }
                    dataBuilder.setLength(0);
                } else if (line.startsWith("data:")) {
                    dataBuilder.append(line.substring(5).trim());
                }
                // Ignore other SSE fields (event:, id:, retry:, :comment) —
                // /global/event uses only the data: field and carries the
                // event name inside the JSON payload.
            }
        }
    }

    /**
     * OpenCode 1.4.7's {@code /global/event} wraps each frame as
     * {@code {"directory":"...","payload":{"type":"X","properties":{...}}}}.
     * Earlier releases (and WireMock stubs that predate 1.4.7) send the bare
     * {@code {"type":"X","properties":{...}}} directly. Support both: unwrap
     * {@code payload} when present, otherwise treat the root as the payload.
     */
    private void handleFrame(String json) {
        try {
            JsonNode root = om.readTree(json);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                payload = root;
            }
            String name = payload.path("type").asText("");
            if (name.isEmpty()) return;
            handleOcEvent(name, om.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("[opencode-event-loop] drop malformed frame: {}", e.toString());
        }
    }

    private void handleOcEvent(String eventName, String json) {
        OcEvent oc = parseOcEvent(eventName, json);
        tap.accept(oc);

        String openCodeSessionId = extractSessionId(oc);
        rememberPartToSession(oc);
        maybeCleanupPartBindings();
        if (openCodeSessionId == null) {
            return;
        }
        String dataTalkSessionId = sessionMap.dataTalkFor(openCodeSessionId);
        if (dataTalkSessionId == null) {
            removePartBindingsForSession(openCodeSessionId);
            log.warn("[opencode-event-loop] orphan OpenCode session event dropped: event={}, openCodeSid={}",
                eventName, openCodeSessionId);
            return;
        }
        SessionBus bus = buses.getOrCreate(dataTalkSessionId);
        List<DtEvent> events = translator.translate(dataTalkSessionId, oc);
        for (DtEvent dt : events) {
            bus.publish(dt);
            if (dt instanceof DtEvent.MessageCreated mc
                && mc.message().role() == Message.Role.USER) {
                publishPendingFileUploadEcho(bus, dataTalkSessionId, mc.message().id());
            }
        }
        if (oc instanceof OcEvent.SessionDeleted) {
            translator.forget(dataTalkSessionId);
            if (fileUploadEcho != null) {
                fileUploadEcho.forgetSession(dataTalkSessionId);
            }
        }
    }

    /**
     * After OpenCode echoes a user {@code message.created}, locally publish
     * the {@link FileUploadPart}s that {@code ChannelService} stashed before
     * forwarding (OpenCode rejects them due to Zod validation). Each part is
     * given the OpenCode-assigned {@code messageID} and surfaced as a
     * {@link DtEvent.MessagePartCreated} on the SessionBus so the frontend's
     * {@code chat-parts-store} can attach them to the user bubble.
     */
    private void publishPendingFileUploadEcho(SessionBus bus, String dataTalkSessionId, String messageId) {
        if (fileUploadEcho == null || messageId == null || messageId.isBlank()) {
            return;
        }
        List<FileUploadPart> stashed = fileUploadEcho.drainNext(dataTalkSessionId);
        long now = nowMillisSupplier.getAsLong();
        int position = 0;
        for (FileUploadPart raw : stashed) {
            Part withMid = raw.withMessageId(messageId);
            JsonNode partJson = om.valueToTree(withMid);
            bus.publish(new DtEvent.MessagePartCreated(partJson));
            if (attachmentRepo != null) {
                try {
                    attachmentRepo.insert(new UserMessageAttachmentRecord(
                        raw.id() == null || raw.id().isBlank() ? "prt_" + java.util.UUID.randomUUID() : raw.id(),
                        dataTalkSessionId,
                        messageId,
                        position,
                        om.writeValueAsString(partJson),
                        now
                    ));
                } catch (Exception e) {
                    log.warn("[opencode-event-loop] persist user message attachment failed: sid={}, mid={}, err={}",
                        dataTalkSessionId, messageId, e.toString());
                }
            }
            position++;
        }
    }

    /**
     * Maintain the {@code partId → openCodeSessionId} binding so that subsequent
     * {@link OcEvent.MessagePartDelta}s — which lack a sessionID — can be routed.
     */
    private void rememberPartToSession(OcEvent oc) {
        if (oc instanceof OcEvent.MessagePartUpdated p) {
            String pid = p.part().path("id").asText(null);
            String sid = p.part().path("sessionID").asText(null);
            if (pid != null && sid != null && !pid.isEmpty() && !sid.isEmpty()) {
                rememberPartBinding(pid, sid);
            }
        } else if (oc instanceof OcEvent.MessagePartRemoved r) {
            partToOpenCodeSession.remove(r.partId());
        } else if (oc instanceof OcEvent.SessionDeleted s) {
            String openCodeSid = s.info().id();
            if (openCodeSid != null && !openCodeSid.isEmpty()) {
                removePartBindingsForSession(openCodeSid);
            }
        }
    }

    private void rememberPartBinding(String partId, String openCodeSessionId) {
        partToOpenCodeSession.put(partId, new PartSessionBinding(openCodeSessionId, nowMillisSupplier.getAsLong()));
    }

    private void maybeCleanupPartBindings() {
        long now = nowMillisSupplier.getAsLong();
        if (now < nextPartBindingCleanupAtMillis) {
            return;
        }
        partToOpenCodeSession.entrySet().removeIf(e -> now - e.getValue().lastTouchedAtMillis() >= partBindingTtlMillis);
        nextPartBindingCleanupAtMillis = now + partBindingCleanupIntervalMillis;
    }

    private void removePartBindingsForSession(String openCodeSessionId) {
        partToOpenCodeSession.entrySet().removeIf(e -> openCodeSessionId.equals(e.getValue().openCodeSessionId()));
    }

    private String touchAndGetBoundSessionId(String partId) {
        if (partId == null || partId.isBlank()) {
            return null;
        }
        PartSessionBinding binding = partToOpenCodeSession.get(partId);
        if (binding == null) {
            return null;
        }
        partToOpenCodeSession.replace(partId, binding,
            new PartSessionBinding(binding.openCodeSessionId(), nowMillisSupplier.getAsLong()));
        return binding.openCodeSessionId();
    }

    private static long requirePositiveMillis(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return duration.toMillis();
    }

    /**
     * Parses an OpenCode 1.4.7 {@code /event} frame. All domain payload lives
     * under {@code properties.*}; the top-level {@code type} is duplicated for
     * convenience but only the event name passed in drives dispatch.
     *
     * <p>Package-private for tests in this module.</p>
     */
    OcEvent parseOcEvent(String name, String json) {
        try {
            JsonNode node = json == null || json.isEmpty() ? om.createObjectNode() : om.readTree(json);
            JsonNode props = node.path("properties");
            return switch (name) {
                case "server.connected" -> new OcEvent.ServerConnected();
                case "session.status"   -> new OcEvent.SessionStatus(
                    props.path("status").path("type").asText("idle"),
                    om.convertValue(props.path("retryInfo"), Map.class)
                );
                case "session.created"   -> new OcEvent.SessionCreated(parseSessionInfo(props));
                case "session.updated"   -> new OcEvent.SessionUpdated(parseSessionInfo(props));
                case "session.deleted"   -> new OcEvent.SessionDeleted(parseSessionInfo(props));
                case "session.idle"      -> new OcEvent.SessionIdle(parseSessionInfo(props));
                case "session.error"     -> new OcEvent.SessionError(
                    parseSessionInfo(props), extractErrorMessage(props.path("error")));
                case "session.compacted" -> new OcEvent.SessionCompacted(parseSessionInfo(props));
                case "session.diff"      -> new OcEvent.SessionDiff(
                    parseSessionInfo(props), om.convertValue(props, Map.class));
                case "message.updated"  -> new OcEvent.MessageUpdated(parseMessage(props.path("info")));
                case "message.part.updated" -> {
                    JsonNode part = props.path("part");
                    if (part.isMissingNode() || part.isNull()) {
                        yield new OcEvent.Unknown("message.part.updated", Map.of());
                    }
                    yield new OcEvent.MessagePartUpdated(part);
                }
                case "message.part.delta"   -> new OcEvent.MessagePartDelta(
                    props.path("partID").asText(),
                    props.path("field").asText(),
                    props.path("delta").asText());
                case "message.part.removed" -> new OcEvent.MessagePartRemoved(
                    props.path("partID").asText());
                case "question.asked"   -> new OcEvent.QuestionAsked(
                    props.path("id").asText(),
                    props.path("sessionID").asText(null),
                    props.path("questions"),
                    props.path("tool").path("messageID").asText(null),
                    props.path("tool").path("callID").asText(null));
                case "question.replied" -> new OcEvent.QuestionReplied(
                    props.path("sessionID").asText(null),
                    props.path("requestID").asText());
                case "question.rejected" -> new OcEvent.QuestionRejected(
                    props.path("sessionID").asText(null),
                    props.path("requestID").asText());
                default -> new OcEvent.Unknown(name, om.convertValue(props, Map.class));
            };
        } catch (Exception e) {
            return new OcEvent.Unknown(name, Map.of("parseError", e.getMessage()));
        }
    }

    /** OpenCode 1.4.7 sends {@code info} under {@code properties}; for {@code session.idle}
     *  it's missing so we synthesize from the sibling {@code sessionID}. */
    private SessionInfo parseSessionInfo(JsonNode props) {
        JsonNode info = props.path("info");
        if (info.isMissingNode() || info.isNull()) {
            String sid = props.path("sessionID").asText(null);
            return new SessionInfo(sid, null, 0L);
        }
        return new SessionInfo(
            info.path("id").asText(null),
            info.hasNonNull("title") ? info.path("title").asText() : null,
            info.path("time").path("updated").asLong(info.path("time").path("created").asLong(0L))
        );
    }

    /** 1.4.7 shape: {name: "UnknownError", data: {message: "..."}}. */
    private String extractErrorMessage(JsonNode err) {
        if (err.isMissingNode() || err.isNull()) return "";
        JsonNode msg = err.path("data").path("message");
        if (msg.isTextual() && !msg.asText().isBlank()) return msg.asText();
        return err.path("name").asText("");
    }

    /**
     * Reshapes OpenCode 1.4.7's {@code info} payload into a DataTalk {@link Message}.
     * Differences from DataTalk's domain shape:
     * <ul>
     *   <li>Wire uses {@code sessionID} (capital D); domain uses {@code sessionId}.</li>
     *   <li>Wire role is lowercase string ({@code "user"}); domain uses {@link Message.Role} enum.</li>
     *   <li>No {@code parts} on the wire — parts stream separately via {@code message.part.updated}.</li>
     *   <li>No {@code createdAt} — derived from {@code time.created}.</li>
     * </ul>
     */
    private Message parseMessage(JsonNode info) {
        if (info.isMissingNode() || info.isNull()) {
            return new Message(null, null, Message.Role.ASSISTANT, List.of(), 0L, null, null);
        }
        Message.Role role = Message.Role.valueOf(info.path("role").asText("assistant").toUpperCase());

        // OpenCode 1.4.7 两种形态：
        //   user 消息     → info.model.{providerID, modelID}（嵌套）
        //   assistant 消息 → info.{providerID, modelID}（扁平）
        // 嵌套优先，扁平兜底。
        String providerID = firstNonBlank(
            info.path("model").path("providerID").asText(null),
            info.path("providerID").asText(null)
        );
        String modelID = firstNonBlank(
            info.path("model").path("modelID").asText(null),
            info.path("modelID").asText(null)
        );

        return new Message(
            info.path("id").asText(null),
            info.path("sessionID").asText(null),
            role,
            List.of(),
            info.path("time").path("created").asLong(0L),
            providerID,
            modelID
        );
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private String extractSessionId(OcEvent e) {
        return switch (e) {
            case OcEvent.MessageUpdated m    -> m.message().sessionId();
            case OcEvent.MessagePartUpdated p -> p.part().path("sessionID").asText(null);
            case OcEvent.MessagePartDelta d  -> touchAndGetBoundSessionId(d.partId());
            case OcEvent.MessagePartRemoved r -> touchAndGetBoundSessionId(r.partId());
            case OcEvent.SessionCreated s    -> s.info().id();
            case OcEvent.SessionUpdated s    -> s.info().id();
            case OcEvent.SessionDeleted s    -> s.info().id();
            case OcEvent.SessionIdle s       -> s.info().id();
            case OcEvent.SessionError s      -> s.info().id();
            case OcEvent.SessionCompacted s  -> s.info().id();
            case OcEvent.SessionDiff s       -> s.info().id();
            case OcEvent.QuestionAsked q     -> q.sessionId();
            case OcEvent.QuestionReplied q   -> q.sessionId();
            case OcEvent.QuestionRejected q  -> q.sessionId();
            case OcEvent.SessionStatus s     -> null;
            default                          -> null;
        };
    }

    private record PartSessionBinding(String openCodeSessionId, long lastTouchedAtMillis) {}
}
