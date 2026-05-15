package com.datatalk.adapter.controller;

import com.datatalk.application.script.ScriptRunService;
import com.datatalk.application.script.ScriptTokenStore;
import com.datatalk.domain.script.ScriptRun;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/script")
public class ScriptController {

    private final ScriptRunService runService;

    public ScriptController(ScriptRunService runService) {
        this.runService = runService;
    }

    @PostMapping("/run-prepare")
    public ResponseEntity<?> runPrepare(@RequestBody Map<String, Object> body) {
        try {
            String scriptContent = (String) body.get("scriptContent");
            String language = (String) body.get("language");
            String connectionId = (String) body.get("connectionId");
            String name = (String) body.get("name");
            String createdByKind = (String) body.getOrDefault("createdByKind", "user");
            String sessionId = (String) body.get("sessionId");

            if (scriptContent == null || language == null || connectionId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "MISSING_REQUIRED_FIELDS"));
            }

            var result = runService.prepareRun(
                scriptContent,
                com.datatalk.domain.script.ScriptLanguage.fromDb(language),
                connectionId, name, createdByKind, sessionId
            );

            return ResponseEntity.ok(Map.of(
                "runId", result.runId(),
                "token", result.token()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{runId}/complete")
    public ResponseEntity<?> complete(@PathVariable String runId, @RequestBody Map<String, Object> body) {
        try {
            int exitCode = body.get("exitCode") != null ? ((Number) body.get("exitCode")).intValue() : 1;
            String stdoutText = (String) body.get("stdoutText");
            String errorMessage = (String) body.get("errorMessage");

            runService.completeRun(runId, exitCode, stdoutText, errorMessage);

            ScriptRun run = runService.findById(runId).orElseThrow();
            return ResponseEntity.ok(Map.of(
                "runId", runId,
                "status", run.status().dbValue()
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", "RUN_NOT_FOUND"));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
