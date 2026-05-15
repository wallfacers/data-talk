package com.datatalk.infra.oplog;

import com.datatalk.application.persistence.UndoLogRepository;
import com.datatalk.application.sql.ConnectionOpLogBus;
import com.datatalk.application.sql.ConnectionOpLogBusRegistry;
import com.datatalk.application.sql.ConnectionOpLogService;
import com.datatalk.domain.undo.BatchUndoResult;
import com.datatalk.domain.undo.UndoLogEntry;
import com.datatalk.infra.channel.SseHeartbeatScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConnectionOpLogControllerTest {

    private UndoLogRepository undoLogRepo;
    private ConnectionOpLogService opLogService;
    private ConnectionOpLogBusRegistry busRegistry;
    private SseHeartbeatScheduler heartbeat;
    private ObjectMapper om;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        undoLogRepo = mock(UndoLogRepository.class);
        opLogService = mock(ConnectionOpLogService.class);
        busRegistry = mock(ConnectionOpLogBusRegistry.class);
        heartbeat = mock(SseHeartbeatScheduler.class);
        om = new ObjectMapper();

        ConnectionOpLogController controller = new ConnectionOpLogController(
            undoLogRepo, opLogService, busRegistry, heartbeat, om, 30000L);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── GET /api/connections/{connId}/op-logs ────────────────────────────

    @Test
    void list_returnsPaginatedResults() throws Exception {
        UndoLogRepository.OpLogListItem item = new UndoLogRepository.OpLogListItem(
            "log-1", "sess-1", "Session Title", "conn-A",
            "mydb", "public", "users", "INSERT", 1, true,
            "active", Long.MAX_VALUE, 1000L, null
        );
        UndoLogRepository.PaginatedOpLog page = new UndoLogRepository.PaginatedOpLog(
            List.of(item), 1, 0, 50
        );

        when(undoLogRepo.findByConnectionId(eq("conn-A"), eq(0), eq(50), any()))
            .thenReturn(page);

        mvc.perform(get("/api/connections/conn-A/op-logs")
                .param("page", "0")
                .param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value("log-1"))
            .andExpect(jsonPath("$.items[0].sessionTitle").value("Session Title"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void list_passesFiltersCorrectly() throws Exception {
        UndoLogRepository.PaginatedOpLog emptyPage = new UndoLogRepository.PaginatedOpLog(
            List.of(), 0, 0, 50
        );

        when(undoLogRepo.findByConnectionId(anyString(), anyInt(), anyInt(), any()))
            .thenReturn(emptyPage);

        mvc.perform(get("/api/connections/conn-A/op-logs")
                .param("status", "active")
                .param("status", "undone")
                .param("operation", "INSERT")
                .param("table", "user")
                .param("from", "1000")
                .param("to", "2000")
                .param("q", "select"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.total").value(0));
    }

    // ── GET /api/connections/{connId}/op-logs/{id} ───────────────────────

    @Test
    void detail_returnsFullRecord() throws Exception {
        UndoLogEntry entry = new UndoLogEntry(
            "log-1", "sess-1", "conn-A", "mydb", "public",
            "users", "UPDATE", "UPDATE users SET name='Bob' WHERE id=1",
            "UPDATE users SET name='Alice' WHERE id=1",
            "[{\"id\":1,\"name\":\"Alice\"}]",
            1, true, "active", Long.MAX_VALUE, 1000L, null
        );

        when(undoLogRepo.findByIdAndConnectionId("log-1", "conn-A"))
            .thenReturn(Optional.of(entry));

        mvc.perform(get("/api/connections/conn-A/op-logs/log-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("log-1"))
            .andExpect(jsonPath("$.operation").value("UPDATE"))
            .andExpect(jsonPath("$.beforeState").value("[{\"id\":1,\"name\":\"Alice\"}]"))
            .andExpect(jsonPath("$.inverseSql").value("UPDATE users SET name='Alice' WHERE id=1"));
    }

    @Test
    void detail_returns404ForMissing() throws Exception {
        when(undoLogRepo.findByIdAndConnectionId("missing", "conn-A"))
            .thenReturn(Optional.empty());

        mvc.perform(get("/api/connections/conn-A/op-logs/missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void detail_returns404ForWrongConnection() throws Exception {
        when(undoLogRepo.findByIdAndConnectionId("log-1", "conn-WRONG"))
            .thenReturn(Optional.empty());

        mvc.perform(get("/api/connections/conn-WRONG/op-logs/log-1"))
            .andExpect(status().isNotFound());
    }

    // ── POST /api/connections/{connId}/op-logs/batch-undo ────────────────

    @Test
    void batchUndo_returnsResults() throws Exception {
        List<BatchUndoResult> results = List.of(
            new BatchUndoResult("log-1", "undone", 1, null),
            new BatchUndoResult("log-2", "not_found", 0, null)
        );

        when(opLogService.batchUndo("conn-A", List.of("log-1", "log-2")))
            .thenReturn(results);

        String body = om.writeValueAsString(Map.of("undoLogIds", List.of("log-1", "log-2")));

        mvc.perform(post("/api/connections/conn-A/op-logs/batch-undo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].id").value("log-1"))
            .andExpect(jsonPath("$.results[0].status").value("undone"))
            .andExpect(jsonPath("$.results[0].affectedRows").value(1))
            .andExpect(jsonPath("$.results[1].id").value("log-2"))
            .andExpect(jsonPath("$.results[1].status").value("not_found"));
    }

    @Test
    void batchUndo_returns400ForEmptyIds() throws Exception {
        String body = om.writeValueAsString(Map.of("undoLogIds", List.of()));

        mvc.perform(post("/api/connections/conn-A/op-logs/batch-undo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("undoLogIds must not be empty"));
    }

    @Test
    void batchUndo_returns400ForNullIds() throws Exception {
        String body = "{\"undoLogIds\":null}";

        mvc.perform(post("/api/connections/conn-A/op-logs/batch-undo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    // ── GET /api/connections/{connId}/op-logs/stream ─────────────────────

    @Test
    void stream_returns200AndDelegatesToController() throws Exception {
        ConnectionOpLogBus bus = new ConnectionOpLogBus("conn-A");
        when(busRegistry.getOrCreate("conn-A")).thenReturn(bus);
        ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
        when(heartbeat.register(any(org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class), anyLong()))
            .thenAnswer(inv -> mockFuture);

        // ResponseBodyEmitter is returned directly (not DeferredResult),
        // MockMvc dispatches it synchronously — just verify 200 OK.
        mvc.perform(get("/api/connections/conn-A/op-logs/stream"))
            .andExpect(status().isOk());
    }
}
