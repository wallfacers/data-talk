package com.datatalk.adapter.api;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import com.datatalk.exception.ConnectionNotFoundException;
import com.datatalk.exception.SqlExecutionException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Translator translator;

    public GlobalExceptionHandler(Translator translator) {
        this.translator = translator;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "error", "NOT_FOUND",
            "message", e.getMessage() != null ? e.getMessage() : translator.get("error.not_found")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "BAD_REQUEST",
            "message", e.getMessage() != null ? e.getMessage() : translator.get("error.bad_request")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableRequest(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "BAD_REQUEST",
            "message", translator.get("error.request_body_required")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> methodNotAllowed(
            HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        HttpHeaders headers = new HttpHeaders();
        if (supported != null) headers.setAllow(supported);
        log.warn("405 method not allowed: {} {} (supported={})",
            req.getMethod(), req.getRequestURI(), supported);
        return new ResponseEntity<>(headers, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(ConnectionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> connectionNotFound(ConnectionNotFoundException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", e.getMessage(),
            "message", translator.get(DataTalkErrorCodes.CONNECTION_MISSING),
            "code", "CONNECTION_NOT_FOUND"));
    }

    @ExceptionHandler(SqlExecutionException.class)
    public ResponseEntity<Map<String, Object>> sqlExecutionFailed(SqlExecutionException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "error", e.getMessage(),
            "message", translator.get("error.bad_request"),
            "code", "QUERY_FAILED"));
    }

    @ExceptionHandler(DataTalkException.class)
    public ResponseEntity<Map<String, Object>> dataTalkException(DataTalkException e) {
        HttpStatus status = resolveStatus(e.code());
        return ResponseEntity.status(status).body(Map.of(
            "error", e.getMessage(),
            "message", translator.getOrDefault(e.code(), e.getMessage()),
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
            "message", translator.get(DataTalkErrorCodes.UPSTREAM_UNAVAILABLE),
            "code", DataTalkErrorCodes.UPSTREAM_UNAVAILABLE));
    }

    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<Map<String, Object>> connectFailure(WebClientException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "OPENCODE_UNAVAILABLE",
            "message", translator.get(DataTalkErrorCodes.UPSTREAM_UNAVAILABLE),
            "code", DataTalkErrorCodes.UPSTREAM_UNAVAILABLE));
    }
}
