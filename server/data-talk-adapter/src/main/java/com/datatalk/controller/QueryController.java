package com.datatalk.controller;

import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.dto.QueryResponseDto;
import com.datatalk.exception.ConnectionNotFoundException;
import com.datatalk.exception.SqlExecutionException;
import com.datatalk.service.QueryApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryApplicationService queryApplicationService;

    public QueryController(QueryApplicationService queryApplicationService) {
        this.queryApplicationService = queryApplicationService;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 执行 SQL 查询
     */
    @PostMapping("/query")
    public ResponseEntity<?> executeQuery(@Valid @RequestBody ExecuteSqlCommand command) {
        try {
            QueryResponseDto response = queryApplicationService.executeQuery(command);
            return ResponseEntity.ok(response);
        } catch (ConnectionNotFoundException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "code", "CONNECTION_NOT_FOUND"));
        } catch (SqlExecutionException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "code", "QUERY_FAILED"));
        }
    }
}
