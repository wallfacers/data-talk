package com.datatalk.infra.connection;

import com.datatalk.dto.ConnectionCreateRequest;
import com.datatalk.dto.ConnectionCreatedDto;
import com.datatalk.dto.ConnectionDto;
import com.datatalk.dto.ConnectionTargetsDto;
import com.datatalk.dto.ConnectionTestResultDto;
import com.datatalk.dto.ConnectionUpdateRequest;
import com.datatalk.application.connection.ConnectionContextRefreshService;
import com.datatalk.application.connection.ConnectionInUseException;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.session.ConnectionTargetDiscoveryService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService svc;
    private final ConnectionContextRefreshService contextRefreshService;
    private final ConnectionTargetDiscoveryService discovery;

    public ConnectionController(
        ConnectionService svc,
        ConnectionContextRefreshService contextRefreshService,
        ConnectionTargetDiscoveryService discovery
    ) {
        this.svc = svc;
        this.contextRefreshService = contextRefreshService;
        this.discovery = discovery;
    }

    @PostMapping
    public ResponseEntity<ConnectionCreatedDto> create(@RequestBody ConnectionCreateRequest body) {
        try {
            String id = svc.create(body.name(), body.kind(), body.host(), body.port(),
                body.databaseName(), body.username(), body.password(), body.connectTimeout());
            return ResponseEntity.status(HttpStatus.CREATED).body(new ConnectionCreatedDto(id));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("connections", svc.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable String id, @RequestBody ConnectionUpdateRequest body) {
        try {
            svc.update(id, body.name(), body.kind(), body.host(), body.port(),
                body.databaseName(), body.username(), body.password(), body.connectTimeout());
            contextRefreshService.refreshByConnectionId(id);
            return ResponseEntity.noContent().build();
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable String id) {
        try {
            return svc.deleteById(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
        } catch (ConnectionInUseException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ConnectionTestResultDto> test(@PathVariable String id) {
        try {
            var r = svc.testConnection(id);
            return ResponseEntity.ok(new ConnectionTestResultDto(r.ok(), r.latencyMs(), r.reason()));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/targets")
    public ResponseEntity<ConnectionTargetsDto> targets(@PathVariable String id) {
        try {
            var result = discovery.discover(id);
            return ResponseEntity.ok(new ConnectionTargetsDto(
                result.connectionId(),
                result.connectionName(),
                result.databaseNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                result.schemaNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
            ));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
