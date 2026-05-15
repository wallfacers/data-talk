package com.datatalk.infra.oplog;

import com.datatalk.application.persistence.UndoLogRepository;
import com.datatalk.application.sql.ConnectionOpLogBus;
import com.datatalk.application.sql.ConnectionOpLogBusRegistry;
import com.datatalk.application.sql.ConnectionOpLogService;
import com.datatalk.infra.channel.SseHeartbeatScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

@RestController
@RequestMapping("/api/connections/{connectionId}/op-logs")
public class ConnectionOpLogController {

    private final UndoLogRepository undoLogRepo;
    private final ConnectionOpLogService opLogService;
    private final ConnectionOpLogBusRegistry busRegistry;
    private final SseHeartbeatScheduler heartbeat;
    private final ObjectMapper om;
    private final long heartbeatIntervalMs;

    public ConnectionOpLogController(UndoLogRepository undoLogRepo,
                                     ConnectionOpLogService opLogService,
                                     ConnectionOpLogBusRegistry busRegistry,
                                     SseHeartbeatScheduler heartbeat,
                                     ObjectMapper om,
                                     @Value("${app.sse.heartbeat-interval-ms:30000}") long heartbeatIntervalMs) {
        this.undoLogRepo = undoLogRepo;
        this.opLogService = opLogService;
        this.busRegistry = busRegistry;
        this.heartbeat = heartbeat;
        this.om = om;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    // ── 3.1 + 3.2: Paginated list ──────────────────────────────────────

    @GetMapping
    public UndoLogRepository.PaginatedOpLog list(
        @PathVariable String connectionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) List<String> status,
        @RequestParam(required = false) List<String> operation,
        @RequestParam(required = false) Long from,
        @RequestParam(required = false) Long to,
        @RequestParam(required = false) String q
    ) {
        UndoLogRepository.OpLogFilters filters =
            new UndoLogRepository.OpLogFilters(status, operation, from, to, q);
        return undoLogRepo.findByConnectionId(connectionId, page, size, filters);
    }

    // ── 3.3: Detail ────────────────────────────────────────────────────

    @GetMapping("/{undoLogId}")
    public ResponseEntity<?> detail(@PathVariable String connectionId,
                                    @PathVariable String undoLogId) {
        return undoLogRepo.findByIdAndConnectionId(undoLogId, connectionId)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ── 3.4: Batch undo ────────────────────────────────────────────────

    @PostMapping("/batch-undo")
    public ResponseEntity<?> batchUndo(@PathVariable String connectionId,
                                       @RequestBody BatchUndoRequest request) {
        if (request.undoLogIds() == null || request.undoLogIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "undoLogIds must not be empty"));
        }
        List<?> results = opLogService.batchUndo(connectionId, request.undoLogIds());
        return ResponseEntity.ok(Map.of("results", results));
    }

    // ── 3.5: SSE stream ────────────────────────────────────────────────

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter stream(@PathVariable String connectionId) {
        ConnectionOpLogBus bus = busRegistry.getOrCreate(connectionId);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        ScheduledFuture<?> hb = heartbeat.register(emitter, heartbeatIntervalMs);

        String subscriberId = "oplog-" + System.nanoTime();
        bus.subscribe(subscriberId, frame -> {
            try {
                emitter.send(frame.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM);
            } catch (Exception e) {
                // client disconnected — cleaned up by onCompletion
            }
        });

        Runnable onDisconnect = () -> {
            hb.cancel(false);
            bus.unsubscribe(subscriberId);
            busRegistry.onUnsubscribe(connectionId);
        };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError(ex -> onDisconnect.run());

        return emitter;
    }

    record BatchUndoRequest(List<String> undoLogIds) {}
}
