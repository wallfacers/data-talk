package com.datatalk.adapter.api;

import com.datatalk.application.ai.AiSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiSettingsController {

    private final AiSettingsService svc;

    public AiSettingsController(AiSettingsService svc) {
        this.svc = svc;
    }

    @GetMapping("/providers")
    public JsonNode listProviders() {
        return svc.listProviders();
    }

    @GetMapping("/providers/auth")
    public JsonNode providerAuth() {
        return svc.providerAuth();
    }

    @PutMapping("/providers/{id}/credentials")
    public ResponseEntity<Void> putCredentials(@PathVariable String id,
                                               @RequestBody Map<String, Object> body) {
        svc.putCredentials(id, body);
        return ResponseEntity.noContent().build();
    }
}
