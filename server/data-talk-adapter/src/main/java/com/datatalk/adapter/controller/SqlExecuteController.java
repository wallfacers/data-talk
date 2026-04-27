package com.datatalk.adapter.controller;

import com.datatalk.adapter.dto.SqlExecuteRequest;
import com.datatalk.adapter.dto.SqlExecuteResult;
import com.datatalk.adapter.dto.SqlExecuteResultItem;
import com.datatalk.adapter.dto.SqlRiskBlockedDto;
import com.datatalk.application.sql.SqlExecuteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/sql")
public class SqlExecuteController {

    private final SqlExecuteService service;

    public SqlExecuteController(SqlExecuteService service) {
        this.service = service;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody SqlExecuteRequest req) {
        try {
            SqlExecuteService.Result r = service.execute(
                req.connectionId(),
                req.sql(),
                req.source(),
                req.sessionId(),
                req.database(),
                req.schema()
            );
            var items = r.results().stream()
                .map(item -> new SqlExecuteResultItem(
                    item.resultId(),
                    item.kind(),
                    item.title(),
                    item.statementIndex(),
                    item.statementText(),
                    item.columns(),
                    item.rows(),
                    item.rowCount(),
                    item.executionMs(),
                    item.truncated(),
                    item.affectedRows(),
                    item.errorMessage()
                ))
                .toList();
            return ResponseEntity.ok(
                SqlExecuteResult.executed(r.resolvedContext(), r.contextNotice(), items)
            );
        } catch (SqlExecuteService.SqlRiskBlockedException e) {
            return ResponseEntity.unprocessableEntity()
                .body(new SqlRiskBlockedDto(e.risk().riskLevel(), e.risk().riskReason()));
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }
}
