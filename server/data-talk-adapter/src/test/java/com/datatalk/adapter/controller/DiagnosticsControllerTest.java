package com.datatalk.adapter.controller;

import com.datatalk.application.diagnostics.DiagnosticsService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;

@WebMvcTest(DiagnosticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class DiagnosticsControllerTest {

    @Autowired MockMvc mvc;

    @MockBean DiagnosticsService service;
    @MockBean Translator translator;

    @BeforeEach
    void stubTranslator() {
        when(translator.get("error.sql.required")).thenReturn("SQL is required");
    }

    @Test
    void explain_missingSql_returns400() throws Exception {
        mvc.perform(post("/api/sessions/s1/diagnostics/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("SQL is required"));
    }

    @Test
    void explain_blankSql_returns400() throws Exception {
        mvc.perform(post("/api/sessions/s1/diagnostics/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("SQL is required"));
    }

    @Test
    void explain_validSql_returns200WithPlan() throws Exception {
        ExplainPlan plan = new ExplainPlan("mysql", "EXPLAIN ...",
            List.of(new ExplainNode("SEQ SCAN", "users", ScanType.FULL_SCAN, 100, 5.0, null, List.of())),
            5.0, List.of());
        when(service.explain(eq("s1"), eq("SELECT * FROM users")))
            .thenReturn(DiagnosticResult.ok(plan));

        mvc.perform(post("/api/sessions/s1/diagnostics/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\":\"SELECT * FROM users\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dialect").value("mysql"))
            .andExpect(jsonPath("$.nodes[0].operation").value("SEQ SCAN"))
            .andExpect(jsonPath("$.nodes[0].table").value("users"));
    }

    @Test
    void explain_unsupportedDialect_returns200WithUnsupportedFlag() throws Exception {
        when(service.explain(eq("s1"), eq("SELECT 1")))
            .thenReturn(DiagnosticResult.unsupported("EXPLAIN not supported for dialect: sqlite"));

        mvc.perform(post("/api/sessions/s1/diagnostics/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\":\"SELECT 1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unsupported").value(true))
            .andExpect(jsonPath("$.reason").value("EXPLAIN not supported for dialect: sqlite"));
    }

    @Test
    void explain_diagnosticError_returns200WithError() throws Exception {
        when(service.explain(eq("s1"), eq("BAD SQL")))
            .thenReturn(DiagnosticResult.error("parse", "Syntax error at line 1"));

        mvc.perform(post("/api/sessions/s1/diagnostics/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\":\"BAD SQL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error.type").value("parse"))
            .andExpect(jsonPath("$.error.message").value("Syntax error at line 1"));
    }

    @Test
    void indexHints_validSql_returns200WithRecs() throws Exception {
        List<IndexRecommendation> recs = List.of(
            new IndexRecommendation("users", List.of("email"), "BTREE", Impact.HIGH, "Email lookup without index")
        );
        when(service.indexHints(eq("s1"), eq("SELECT * FROM users WHERE email = 'x'")))
            .thenReturn(DiagnosticResult.ok(recs));

        mvc.perform(post("/api/sessions/s1/diagnostics/index-hints")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\":\"SELECT * FROM users WHERE email = 'x'\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recommendations[0].table").value("users"))
            .andExpect(jsonPath("$.recommendations[0].columns[0]").value("email"))
            .andExpect(jsonPath("$.recommendations[0].impact").value("HIGH"))
            .andExpect(jsonPath("$.summary").exists());
    }

    @Test
    void indexHints_missingSql_returns400() throws Exception {
        mvc.perform(post("/api/sessions/s1/diagnostics/index-hints")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("SQL is required"));
    }

    @Test
    void indexHints_unsupported_returns200WithUnsupportedFlag() throws Exception {
        when(service.indexHints(eq("s1"), eq("SELECT 1")))
            .thenReturn(DiagnosticResult.unsupported("Index hints not supported"));

        mvc.perform(post("/api/sessions/s1/diagnostics/index-hints")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\":\"SELECT 1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unsupported").value(true));
    }
}
