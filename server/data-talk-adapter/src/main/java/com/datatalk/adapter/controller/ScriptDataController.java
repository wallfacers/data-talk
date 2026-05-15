package com.datatalk.adapter.controller;

import com.datatalk.application.script.ScriptDataBatchService;
import com.datatalk.application.script.ScriptDataWriteService;
import com.datatalk.application.script.ScriptRunService;
import com.datatalk.application.script.ScriptTokenStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/script-data")
public class ScriptDataController {

    private final ScriptRunService runService;
    private final ScriptDataWriteService writeService;
    private final ScriptDataBatchService batchService;

    public ScriptDataController(ScriptRunService runService, ScriptDataWriteService writeService,
                                 ScriptDataBatchService batchService) {
        this.runService = runService;
        this.writeService = writeService;
        this.batchService = batchService;
    }

    @PostMapping("/write")
    public ResponseEntity<?> write(@RequestBody Map<String, Object> body) {
        String token = (String) body.get("token");
        String connectionId = (String) body.get("connectionId");
        String tableName = (String) body.get("tableName");

        var validated = validateToken(token, connectionId);
        if (validated == null) {
            return ResponseEntity.status(401).body(Map.of("error", "SCRIPT_TOKEN_INVALID"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        boolean createTable = Boolean.TRUE.equals(body.get("createTable"));

        if (tableName == null || rows == null || rows.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_TABLE_OR_ROWS"));
        }

        try {
            ScriptDataWriteService.WriteResult result = writeService.write(connectionId, tableName, rows, createTable);
            runService.updateRowsWritten(validated.runId(),
                runService.findById(validated.runId()).orElseThrow().rowsWritten() + result.rowsInserted());
            return ResponseEntity.ok(Map.of(
                "rowsInserted", result.rowsInserted(),
                "tableName", result.tableName(),
                "columnsCreated", result.columnsCreated()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<?> batch(@RequestBody Map<String, Object> body) {
        String token = (String) body.get("token");
        String connectionId = (String) body.get("connectionId");
        String sessionId = (String) body.get("sessionId");
        String tableName = (String) body.get("tableName");

        var validated = validateToken(token, connectionId);
        if (validated == null) {
            return ResponseEntity.status(401).body(Map.of("error", "SCRIPT_TOKEN_INVALID"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        boolean createTable = Boolean.TRUE.equals(body.get("createTable"));

        if (rows == null || rows.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_ROWS"));
        }

        try {
            var result = batchService.writeBatch(sessionId, connectionId, tableName, rows, createTable);
            return ResponseEntity.ok(Map.of(
                "sessionId", result.sessionId(),
                "rowsInserted", result.rowsInserted(),
                "totalRowsInserted", result.totalRowsInserted()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/batch/close")
    public ResponseEntity<?> batchClose(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        if (sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_SESSION_ID"));
        }

        var session = batchService.closeSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(404).body(Map.of("error", "SESSION_NOT_FOUND"));
        }

        return ResponseEntity.ok(Map.of(
            "totalRowsInserted", session.totalRowsInserted(),
            "tableName", session.tableName()
        ));
    }

    private ScriptTokenStore.TokenEntry validateToken(String token, String connectionId) {
        if (token == null || connectionId == null) return null;
        return runService.tokenStore().validate(token, connectionId).orElse(null);
    }
}
