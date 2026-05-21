package com.datatalk.adapter.api;

import com.datatalk.dto.AiCurrentModelDto;
import com.datatalk.dto.AiModelPatchRequest;
import com.datatalk.dto.AiModelsDto;
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

    @DeleteMapping("/providers/{id}/credentials")
    public ResponseEntity<Void> deleteCredentials(@PathVariable String id) {
        svc.deleteCredentials(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/models")
    public AiModelsDto listModels() {
        return svc.listModels();
    }

    @PatchMapping("/models/{providerId}/{modelId:.+}")
    public ResponseEntity<Void> patchModel(@PathVariable String providerId,
                                           @PathVariable String modelId,
                                           @RequestBody AiModelPatchRequest body) {
        svc.setModelEnabled(providerId, modelId, body.enabled());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/current-model")
    public AiCurrentModelDto getCurrentModel() {
        return new AiCurrentModelDto(svc.getCurrentModel());
    }

    @PatchMapping("/current-model")
    public ResponseEntity<Void> patchCurrentModel(@RequestBody AiCurrentModelDto body) {
        svc.setCurrentModel(body.modelId());
        return ResponseEntity.noContent().build();
    }
}
