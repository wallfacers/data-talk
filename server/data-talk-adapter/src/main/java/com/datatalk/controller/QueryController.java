package com.datatalk.controller;

import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.dto.QueryResponseDto;
import com.datatalk.exception.ConnectionNotFoundException;
import com.datatalk.exception.SqlExecutionException;
import com.datatalk.application.opencode.OpenCodeBridgeStatus;
import com.datatalk.service.QueryApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryApplicationService queryApplicationService;
    private final OpenCodeBridgeStatus bridgeStatus;

    public QueryController(QueryApplicationService queryApplicationService,
                           OpenCodeBridgeStatus bridgeStatus) {
        this.queryApplicationService = queryApplicationService;
        this.bridgeStatus = bridgeStatus;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        OpenCodeBridgeStatus.Snapshot snapshot = bridgeStatus.snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", snapshot.status());
        body.put("timestamp", snapshot.timestamp());
        body.put("message", snapshot.message());
        body.put("reason", snapshot.reason());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponseDto> executeQuery(@Valid @RequestBody ExecuteSqlCommand command) {
        QueryResponseDto response = queryApplicationService.executeQuery(command);
        return ResponseEntity.ok(response);
    }
}
