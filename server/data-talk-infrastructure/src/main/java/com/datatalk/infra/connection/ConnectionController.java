package com.datatalk.infra.connection;

import com.datatalk.dto.ConnectionCreateRequest;
import com.datatalk.dto.ConnectionCreatedDto;
import com.datatalk.dto.ConnectionDto;
import com.datatalk.dto.ConnectionTestResultDto;
import com.datatalk.dto.ConnectionUpdateRequest;
import com.datatalk.application.connection.ConnectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService svc;
    public ConnectionController(ConnectionService svc) { this.svc = svc; }

    @PostMapping
    public ResponseEntity<ConnectionCreatedDto> create(@RequestBody ConnectionCreateRequest body) {
        String id = svc.create(body.kind(), body.host(), body.port(),
            body.databaseName(), body.username(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ConnectionCreatedDto(id));
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("connections", svc.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable String id, @RequestBody ConnectionUpdateRequest body) {
        try {
            svc.update(id, body.kind(), body.host(), body.port(),
                body.databaseName(), body.username(), body.password());
            return ResponseEntity.noContent().build();
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return svc.deleteById(id)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
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
}
