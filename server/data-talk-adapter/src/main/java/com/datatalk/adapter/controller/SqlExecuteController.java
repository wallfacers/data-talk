package com.datatalk.adapter.controller;

import com.datatalk.adapter.dto.SqlExecuteRequest;
import com.datatalk.adapter.dto.SqlExecuteResult;
import com.datatalk.adapter.dto.SqlExecuteResultItem;
import com.datatalk.adapter.dto.SqlConfirmationPayload;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.sql.SqlExecuteService;
import com.datatalk.application.sql.UndoExecuteService;
import com.datatalk.domain.action.RiskLevel;
import com.datatalk.domain.undo.UndoResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/sql")
public class SqlExecuteController {

    private final SqlExecuteService service;
    private final UndoExecuteService undoService;
    private final Translator translator;

    public SqlExecuteController(SqlExecuteService service, UndoExecuteService undoService, Translator translator) {
        this.service = service;
        this.undoService = undoService;
        this.translator = translator;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody SqlExecuteRequest req) {
        try {
            RiskLevel ack = parseRiskAck(req.riskAck());
            var outcome = service.execute(
                req.connectionId(),
                req.sql(),
                req.source(),
                req.sessionId(),
                req.database(),
                req.schema(),
                req.confirmedFlag(),
                ack
            );
            return switch (outcome) {
                case SqlExecuteService.Executed e -> ResponseEntity.ok(
                    SqlExecuteResult.executed(e.resolvedContext(), e.contextNotice(), mapItems(e.results())));
                case SqlExecuteService.RequiresConfirmation r -> ResponseEntity.ok(
                    SqlExecuteResult.requiresConfirmation(
                        r.resolvedContext(), r.contextNotice(),
                        new SqlConfirmationPayload(r.level(), r.reason(), r.affectedObjects(), r.sqlPreview())));
                case SqlExecuteService.ConfirmationInvalid i -> ResponseEntity.ok(
                    SqlExecuteResult.confirmationInvalid(
                        i.resolvedContext(), i.contextNotice(),
                        new SqlExecuteResult.SqlConfirmationInvalid(i.reason(), i.ackedRisk(), i.currentRisk(), i.message())));
            };
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return ResponseEntity.badRequest().body(Map.of("message", translator.getOrDefault(e.getMessage(), e.getMessage())));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", translator.getOrDefault(e.getMessage(), e.getMessage())));
        }
    }

    private static RiskLevel parseRiskAck(String value) {
        if (value == null || value.isBlank()) return null;
        try { return RiskLevel.valueOf(value); }
        catch (IllegalArgumentException ex) { return null; }
    }

    @PostMapping("/undo")
    public ResponseEntity<?> undo(@RequestBody Map<String, Object> body) {
        String undoLogId = (String) body.get("undoLogId");
        boolean confirmed = Boolean.TRUE.equals(body.get("confirmed"));
        if (undoLogId == null || undoLogId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "undoLogId is required"));
        }
        try {
            UndoResult result = undoService.execute(undoLogId, confirmed);
            return switch (result) {
                case UndoResult.RequiresConfirmation rc -> ResponseEntity.ok(Map.of(
                    "status", "requires_confirmation",
                    "inverseSql", rc.inverseSql(),
                    "affectedRows", rc.affectedRows(),
                    "tableName", rc.tableName()
                ));
                case UndoResult.Undone u -> ResponseEntity.ok(Map.of(
                    "status", "undone",
                    "affectedRows", u.affectedRows()
                ));
                case UndoResult.Expired ex -> ResponseEntity.status(404).body(Map.of(
                    "status", "expired",
                    "message", translator.get("sql.undo.expired")
                ));
                case UndoResult.AlreadyUndone au -> ResponseEntity.status(409).body(Map.of(
                    "status", "already_undone",
                    "message", translator.get("sql.undo.already_undone")
                ));
                case UndoResult.NotFound nf -> ResponseEntity.status(404).body(Map.of(
                    "status", "not_found",
                    "message", translator.get("sql.undo.not_found")
                ));
            };
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    private static List<SqlExecuteResultItem> mapItems(List<SqlExecuteService.ResultItem> items) {
        return items.stream()
            .map(item -> new SqlExecuteResultItem(
                item.resultId(),
                item.kind(),
                item.title(),
                item.statementIndex(),
                item.statementText(),
                item.columns(),
                item.rows(),
                item.rowCount(),
                item.executionMs(),
                item.truncated(),
                item.affectedRows(),
                item.errorMessage(),
                item.undoLogId(),
                item.undoable()
            ))
            .toList();
    }
}
