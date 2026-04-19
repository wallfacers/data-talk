package com.datatalk.adapter.api;

import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import com.datatalk.exception.ConnectionNotFoundException;
import com.datatalk.exception.SqlExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "error", "NOT_FOUND",
            "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "BAD_REQUEST",
            "message", e.getMessage()));
    }

    @ExceptionHandler(ConnectionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> connectionNotFound(ConnectionNotFoundException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", e.getMessage(),
            "code", "CONNECTION_NOT_FOUND"));
    }

    @ExceptionHandler(SqlExecutionException.class)
    public ResponseEntity<Map<String, Object>> sqlExecutionFailed(SqlExecutionException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "error", e.getMessage(),
            "code", "QUERY_FAILED"));
    }

    @ExceptionHandler(DataTalkException.class)
    public ResponseEntity<Map<String, Object>> dataTalkException(DataTalkException e) {
        HttpStatus status = resolveStatus(e.code());
        return ResponseEntity.status(status).body(Map.of(
            "error", e.getMessage(),
            "code", e.code()));
    }

    private static final Map<String, HttpStatus> CODE_STATUS_MAP = Map.of(
        DataTalkErrorCodes.ARTIFACT_SUPERSEDES_NOT_FOUND, HttpStatus.NOT_FOUND,
        DataTalkErrorCodes.ARTIFACT_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
        DataTalkErrorCodes.UPSTREAM_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
        DataTalkErrorCodes.CLIENT_ACTION_UNREACHABLE, HttpStatus.SERVICE_UNAVAILABLE
    );

    private HttpStatus resolveStatus(String code) {
        if (code == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        return CODE_STATUS_MAP.getOrDefault(code, HttpStatus.BAD_REQUEST);
    }

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
