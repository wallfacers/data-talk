package com.datatalk.adapter.controller;

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
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ScriptControllerTest {

    private ScriptRunService runService;
    private ScriptTokenStore tokenStore;
    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        runService = mock(ScriptRunService.class);
        tokenStore = mock(ScriptTokenStore.class);
        when(runService.tokenStore()).thenReturn(tokenStore);
        mvc = standaloneSetup(new ScriptController(runService)).build();
    }

    @Test
    void runPrepare_returnsRunIdAndToken() throws Exception {
        when(runService.prepareRun(eq("print(1)"), eq(ScriptLanguage.PYTHON),
            eq("conn-1"), any(), any(), any()))
            .thenReturn(new ScriptRunService.RunPrepareResult("run-abc", "token-xyz"));

        mvc.perform(post("/api/script/run-prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scriptContent": "print(1)", "language": "python", "connectionId": "conn-1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run-abc"))
            .andExpect(jsonPath("$.token").value("token-xyz"));
    }

    @Test
    void runPrepare_missingRequiredFields_returns400() throws Exception {
        mvc.perform(post("/api/script/run-prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scriptContent": "print(1)"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("MISSING_REQUIRED_FIELDS"));
    }

    @Test
    void runPrepare_withOptionalFields_passesThemThrough() throws Exception {
        when(runService.prepareRun(eq("code"), eq(ScriptLanguage.JAVASCRIPT),
            eq("conn-2"), eq("MyName"), eq("user"), eq("sess-1")))
            .thenReturn(new ScriptRunService.RunPrepareResult("run-1", "tok-1"));

        mvc.perform(post("/api/script/run-prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scriptContent": "code",
                      "language": "javascript",
                      "connectionId": "conn-2",
                      "name": "MyName",
                      "createdByKind": "user",
                      "sessionId": "sess-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run-1"))
            .andExpect(jsonPath("$.token").value("tok-1"));

        verify(runService).prepareRun("code", ScriptLanguage.JAVASCRIPT,
            "conn-2", "MyName", "user", "sess-1");
    }

    @Test
    void complete_successfulRun_returnsStatus() throws Exception {
        ScriptRun completed = new ScriptRun(
            "run-1", "code", ScriptLanguage.PYTHON, ScriptStatus.COMPLETED,
            0, "stdout", "conn-1", null, 0,
            null, "ai", null, null,
            Instant.now(), Instant.now(), 5000L
        );
        when(runService.findById("run-1")).thenReturn(Optional.of(completed));

        mvc.perform(post("/api/script/run-1/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"exitCode": 0, "stdoutText": "stdout"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run-1"))
            .andExpect(jsonPath("$.status").value("completed"));

        verify(runService).completeRun("run-1", 0, "stdout", null);
    }

    @Test
    void complete_failedRun_returnsFailedStatus() throws Exception {
        ScriptRun failed = new ScriptRun(
            "run-1", "code", ScriptLanguage.PYTHON, ScriptStatus.FAILED,
            1, null, "conn-1", null, 0,
            null, "ai", null, null,
            Instant.now(), Instant.now(), 5000L
        );
        when(runService.findById("run-1")).thenReturn(Optional.of(failed));

        mvc.perform(post("/api/script/run-1/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"exitCode": 1, "errorMessage": "Error occurred"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("failed"));

        verify(runService).completeRun("run-1", 1, null, "Error occurred");
    }

    @Test
    void complete_invalidRunId_returns404() throws Exception {
        doThrow(new NoSuchElementException()).when(runService).completeRun(eq("bad-id"), anyInt(), any(), any());

        mvc.perform(post("/api/script/bad-id/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"exitCode": 0}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("RUN_NOT_FOUND"));
    }

    @Test
    void listRuns_returnsResults() throws Exception {
        ScriptRun run = new ScriptRun(
            "run-1", "print(1)", ScriptLanguage.PYTHON, ScriptStatus.COMPLETED,
            0, "stdout", "conn-1", "tbl1", 100,
            "MyScript", "ai", "session-1",
            null, Instant.now(), Instant.now(), 5000L
        );
        when(runService.listRuns("conn-1", 50)).thenReturn(List.of(run));

        mvc.perform(get("/api/script/runs")
                .param("connectionId", "conn-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runs").isArray())
            .andExpect(jsonPath("$.runs[0].id").value("run-1"))
            .andExpect(jsonPath("$.runs[0].status").value("completed"))
            .andExpect(jsonPath("$.runs[0].language").value("python"))
            .andExpect(jsonPath("$.runs[0].exitCode").value(0))
            .andExpect(jsonPath("$.runs[0].connectionId").value("conn-1"))
            .andExpect(jsonPath("$.runs[0].targetTable").value("tbl1"))
            .andExpect(jsonPath("$.runs[0].rowsWritten").value(100))
            .andExpect(jsonPath("$.runs[0].name").value("MyScript"));
    }

    @Test
    void listRuns_emptyResults_returnsEmptyArray() throws Exception {
        when(runService.listRuns("conn-1", 50)).thenReturn(List.of());

        mvc.perform(get("/api/script/runs")
                .param("connectionId", "conn-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runs").isArray())
            .andExpect(jsonPath("$.runs").isEmpty());
    }

    @Test
    void listRuns_withCustomLimit() throws Exception {
        when(runService.listRuns(null, 10)).thenReturn(List.of());

        mvc.perform(get("/api/script/runs")
                .param("limit", "10"))
            .andExpect(status().isOk());

        verify(runService).listRuns(null, 10);
    }

    @Test
    void runPrepare_illegalLanguage_returns400() throws Exception {
        mvc.perform(post("/api/script/run-prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scriptContent": "code", "language": "invalid", "connectionId": "conn-1"}
                    """))
            .andExpect(status().isBadRequest());
    }
}
