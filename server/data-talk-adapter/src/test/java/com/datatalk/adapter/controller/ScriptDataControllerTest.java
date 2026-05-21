package com.datatalk.adapter.controller;

import com.datatalk.application.script.ScriptDataBatchService;
import com.datatalk.application.script.ScriptDataWriteService;
import com.datatalk.application.script.ScriptRunRepository;
import com.datatalk.application.script.ScriptRunService;
import com.datatalk.application.script.ScriptTokenStore;
import com.datatalk.domain.script.ScriptLanguage;
import com.datatalk.domain.script.ScriptRun;
import com.datatalk.domain.script.ScriptStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ScriptDataControllerTest {

    private ScriptRunService runService;
    private ScriptDataWriteService writeService;
    private ScriptDataBatchService batchService;
    private ScriptRunRepository runRepository;
    private ScriptTokenStore tokenStore;
    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        runService = mock(ScriptRunService.class);
        writeService = mock(ScriptDataWriteService.class);
        batchService = mock(ScriptDataBatchService.class);
        runRepository = mock(ScriptRunRepository.class);
        tokenStore = mock(ScriptTokenStore.class);
        when(runService.tokenStore()).thenReturn(tokenStore);
        mvc = standaloneSetup(new ScriptDataController(runService, writeService, batchService, runRepository)).build();
    }

    // ── Token validation helpers ────────────────────────────────────

    private void mockValidToken() {
        when(tokenStore.validate(eq("valid-token"), eq("conn-1")))
            .thenReturn(Optional.of(new ScriptTokenStore.TokenEntry("run-1", "conn-1", Instant.now())));
    }

    private void mockRun(String runId, int rowsWritten, String targetTable) {
        ScriptRun run = new ScriptRun(
            runId, "code", ScriptLanguage.PYTHON, ScriptStatus.RUNNING,
            null, null, "conn-1", targetTable, rowsWritten,
            null, "ai", null, null,
            Instant.now(), null, null
        );
        when(runService.findById(runId)).thenReturn(Optional.of(run));
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
    }

    // ── POST /api/script-data/write tests ───────────────────────────

    @Test
    void write_validToken_insertsAndReturnsResult() throws Exception {
        mockValidToken();
        mockRun("run-1", 0, null);

        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(writeService.write(eq("conn-1"), eq("users"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "users", List.of("id")));

        mvc.perform(post("/api/script-data/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1",
                      "tableName": "users",
                      "rows": [{"id": 1}],
                      "createTable": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rowsInserted").value(1))
            .andExpect(jsonPath("$.tableName").value("users"))
            .andExpect(jsonPath("$.columnsCreated[0]").value("id"));

        verify(writeService).write("conn-1", "users", rows, true);
        verify(runService).updateRowsWritten(eq("run-1"), eq(1));
        // First write creates the table -> targetTable should be written back
        verify(runRepository).updateTargetTable("run-1", "users");
    }

    @Test
    void write_invalidToken_returns401() throws Exception {
        when(tokenStore.validate(eq("bad-token"), eq("conn-1")))
            .thenReturn(Optional.empty());

        mvc.perform(post("/api/script-data/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "bad-token",
                      "connectionId": "conn-1",
                      "tableName": "users",
                      "rows": [{"id": 1}]
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("SCRIPT_TOKEN_INVALID"));
    }

    @Test
    void write_nullToken_returns401() throws Exception {
        mvc.perform(post("/api/script-data/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "connectionId": "conn-1",
                      "tableName": "users",
                      "rows": [{"id": 1}]
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("SCRIPT_TOKEN_INVALID"));
    }

    @Test
    void write_connectionIdMismatch_returns401() throws Exception {
        when(tokenStore.validate(eq("valid-token"), eq("wrong-conn")))
            .thenReturn(Optional.empty());

        mvc.perform(post("/api/script-data/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "wrong-conn",
                      "tableName": "users",
                      "rows": [{"id": 1}]
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("SCRIPT_TOKEN_INVALID"));
    }

    @Test
    void write_missingTableOrRows_returns400() throws Exception {
        mockValidToken();

        mvc.perform(post("/api/script-data/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("MISSING_TABLE_OR_ROWS"));
    }

    @Test
    void write_emptyRows_returns400() throws Exception {
        mockValidToken();

        mvc.perform(post("/api/script-data/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1",
                      "tableName": "users",
                      "rows": []
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("MISSING_TABLE_OR_ROWS"));
    }

    @Test
    void write_accumulatesRowsWritten() throws Exception {
        mockValidToken();
        mockRun("run-1", 5, null); // existing run has 5 rows already

        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(writeService.write(eq("conn-1"), eq("users"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "users", List.of("id")));

        mvc.perform(post("/api/script-data/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1",
                      "tableName": "users",
                      "rows": [{"id": 1}],
                      "createTable": true
                    }
                    """))
            .andExpect(status().isOk());

        // 5 existing + 1 new = 6
        verify(runService).updateRowsWritten(eq("run-1"), eq(6));
    }

    @Test
    void write_createTableFalse_doesNotWriteBackTargetTable() throws Exception {
        mockValidToken();
        mockRun("run-1", 0, null);

        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(writeService.write(eq("conn-1"), eq("users"), eq(rows), eq(false)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "users", List.of()));

        mvc.perform(post("/api/script-data/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1",
                      "tableName": "users",
                      "rows": [{"id": 1}],
                      "createTable": false
                    }
                    """))
            .andExpect(status().isOk());

        // createTable=false and no columnsCreated -> should NOT update targetTable
        verify(runRepository, never()).updateTargetTable(anyString(), anyString());
    }

    @Test
    void write_existingTargetTable_doesNotOverwrite() throws Exception {
        mockValidToken();
        // Run already has a targetTable set
        mockRun("run-1", 0, "existing_table");

        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(writeService.write(eq("conn-1"), eq("new_table"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataWriteService.WriteResult(1, "new_table", List.of("id")));

        mvc.perform(post("/api/script-data/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1",
                      "tableName": "new_table",
                      "rows": [{"id": 1}],
                      "createTable": true
                    }
                    """))
            .andExpect(status().isOk());

        // targetTable is already set -> should NOT overwrite
        verify(runRepository, never()).updateTargetTable(anyString(), anyString());
    }

    // ── POST /api/script-data/batch tests ───────────────────────────

    @Test
    void batch_validToken_writesInBatch() throws Exception {
        mockValidToken();
        mockRun("run-1", 0, null);

        List<Map<String, Object>> rows = List.of(Map.of("id", 1), Map.of("id", 2));
        when(batchService.writeBatch(isNull(), eq("conn-1"), eq("users"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataBatchService.BatchResult("session-1", 2, 2));

        mvc.perform(post("/api/script-data/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1",
                      "tableName": "users",
                      "rows": [{"id": 1}, {"id": 2}],
                      "createTable": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("session-1"))
            .andExpect(jsonPath("$.rowsInserted").value(2))
            .andExpect(jsonPath("$.totalRowsInserted").value(2));

        // First batch (sessionId=null) with createTable=true -> targetTable writeback
        verify(runRepository).updateTargetTable("run-1", "users");
    }

    @Test
    void batch_withSessionId_addsToExistingSession() throws Exception {
        mockValidToken();
        mockRun("run-1", 2, null);

        List<Map<String, Object>> rows = List.of(Map.of("id", 3));
        when(batchService.writeBatch(eq("session-1"), eq("conn-1"), eq("users"), eq(rows), eq(false)))
            .thenReturn(new ScriptDataBatchService.BatchResult("session-1", 1, 3));

        mvc.perform(post("/api/script-data/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1",
                      "sessionId": "session-1",
                      "tableName": "users",
                      "rows": [{"id": 3}],
                      "createTable": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("session-1"))
            .andExpect(jsonPath("$.totalRowsInserted").value(3));

        // Subsequent batch (sessionId != null) -> should NOT update targetTable
        verify(runRepository, never()).updateTargetTable(anyString(), anyString());
    }

    @Test
    void batch_firstBatchWithExistingTargetTable_doesNotOverwrite() throws Exception {
        mockValidToken();
        mockRun("run-1", 0, "already_set");

        List<Map<String, Object>> rows = List.of(Map.of("id", 1));
        when(batchService.writeBatch(isNull(), eq("conn-1"), eq("new_table"), eq(rows), eq(true)))
            .thenReturn(new ScriptDataBatchService.BatchResult("session-1", 1, 1));

        mvc.perform(post("/api/script-data/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1",
                      "tableName": "new_table",
                      "rows": [{"id": 1}],
                      "createTable": true
                    }
                    """))
            .andExpect(status().isOk());

        verify(runRepository, never()).updateTargetTable(anyString(), anyString());
    }

    @Test
    void batch_invalidToken_returns401() throws Exception {
        when(tokenStore.validate(eq("bad-token"), eq("conn-1")))
            .thenReturn(Optional.empty());

        mvc.perform(post("/api/script-data/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "bad-token",
                      "connectionId": "conn-1",
                      "tableName": "users",
                      "rows": [{"id": 1}]
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("SCRIPT_TOKEN_INVALID"));
    }

    @Test
    void batch_missingRows_returns400() throws Exception {
        mockValidToken();

        mvc.perform(post("/api/script-data/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("MISSING_ROWS"));
    }

    @Test
    void batch_emptyRows_returns400() throws Exception {
        mockValidToken();

        mvc.perform(post("/api/script-data/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "valid-token",
                      "connectionId": "conn-1",
                      "tableName": "users",
                      "rows": []
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("MISSING_ROWS"));
    }

    // ── POST /api/script-data/batch/close tests ─────────────────────

    @Test
    void batchClose_returnsTotalRows() throws Exception {
        when(batchService.closeSession("session-1"))
            .thenReturn(new ScriptDataBatchService.BatchSession(
                "session-1", "conn-1", "users", 100, Instant.now()
            ));

        mvc.perform(post("/api/script-data/batch/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId": "session-1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRowsInserted").value(100))
            .andExpect(jsonPath("$.tableName").value("users"));
    }

    @Test
    void batchClose_sessionNotFound_returns404() throws Exception {
        when(batchService.closeSession("nonexistent")).thenReturn(null);

        mvc.perform(post("/api/script-data/batch/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId": "nonexistent"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
    }

    @Test
    void batchClose_missingSessionId_returns400() throws Exception {
        mvc.perform(post("/api/script-data/batch/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("MISSING_SESSION_ID"));
    }
}
