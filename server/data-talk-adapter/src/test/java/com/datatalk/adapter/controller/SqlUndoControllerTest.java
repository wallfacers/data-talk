package com.datatalk.adapter.controller;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.sql.SqlExecuteService;
import com.datatalk.application.sql.UndoExecuteService;
import com.datatalk.domain.undo.UndoResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SqlExecuteController.class)
@AutoConfigureMockMvc(addFilters = false)
class SqlUndoControllerTest {

    @Autowired MockMvc mvc;

    @MockBean SqlExecuteService sqlService;
    @MockBean UndoExecuteService undoService;
    @MockBean Translator translator;

    @Test
    void undo_missingUndoLogId_returns400() throws Exception {
        mvc.perform(post("/api/sql/undo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("undoLogId is required"));
    }

    @Test
    void undo_notConfirmed_returnsRequiresConfirmation() throws Exception {
        when(undoService.execute("log-1", false))
            .thenReturn(new UndoResult.RequiresConfirmation("DELETE FROM t WHERE id = 1", 3, "users"));

        mvc.perform(post("/api/sql/undo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"undoLogId\":\"log-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("requires_confirmation"))
            .andExpect(jsonPath("$.inverseSql").value("DELETE FROM t WHERE id = 1"))
            .andExpect(jsonPath("$.affectedRows").value(3))
            .andExpect(jsonPath("$.tableName").value("users"));
    }

    @Test
    void undo_confirmed_returnsUndone() throws Exception {
        when(undoService.execute("log-1", true))
            .thenReturn(new UndoResult.Undone(3));

        mvc.perform(post("/api/sql/undo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"undoLogId\":\"log-1\",\"confirmed\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("undone"))
            .andExpect(jsonPath("$.affectedRows").value(3));
    }

    @Test
    void undo_expired_returns404() throws Exception {
        when(translator.get("sql.undo.expired")).thenReturn("expired");
        when(undoService.execute("log-1", false))
            .thenReturn(new UndoResult.Expired());

        mvc.perform(post("/api/sql/undo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"undoLogId\":\"log-1\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("expired"));
    }

    @Test
    void undo_alreadyUndone_returns409() throws Exception {
        when(translator.get("sql.undo.already_undone")).thenReturn("already undone");
        when(undoService.execute("log-1", false))
            .thenReturn(new UndoResult.AlreadyUndone());

        mvc.perform(post("/api/sql/undo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"undoLogId\":\"log-1\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value("already_undone"));
    }

    @Test
    void undo_notFound_returns404() throws Exception {
        when(translator.get("sql.undo.not_found")).thenReturn("not found");
        when(undoService.execute("missing", false))
            .thenReturn(new UndoResult.NotFound());

        mvc.perform(post("/api/sql/undo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"undoLogId\":\"missing\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("not_found"));
    }
}
