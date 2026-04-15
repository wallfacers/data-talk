package com.datatalk.chatdb.controller;

import com.datatalk.chatdb.model.QueryRequest;
import com.datatalk.chatdb.model.QueryResponse;
import com.datatalk.chatdb.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 查询接口。MVP 阶段执行硬编码 SQL 查询 H2 演示库。
     */
    @PostMapping("/query")
    public ResponseEntity<?> executeQuery(@Valid @RequestBody QueryRequest request) {
        try {
            QueryResponse response = queryService.executeQuery(
                    request.getConnectionId(), request.getSql());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "INVALID_CONNECTION"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "code", "QUERY_FAILED"));
        }
    }
}
