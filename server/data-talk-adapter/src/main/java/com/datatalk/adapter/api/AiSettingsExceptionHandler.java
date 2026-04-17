package com.datatalk.adapter.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@RestControllerAdvice(basePackageClasses = AiSettingsController.class)
public class AiSettingsExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Map<String, Object>> upstream(WebClientResponseException e) {
        if (e.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", "UPSTREAM_4XX",
                "message", e.getResponseBodyAsString()));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "OPENCODE_UNAVAILABLE",
            "message", e.getMessage()));
    }

    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<Map<String, Object>> connectFailure(WebClientException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "OPENCODE_UNAVAILABLE",
            "message", e.getMessage()));
    }
}
