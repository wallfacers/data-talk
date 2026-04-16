package com.datatalk.infra.connection;

import com.datatalk.application.connection.ConnectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService svc;
    public ConnectionController(ConnectionService svc) { this.svc = svc; }

    public record CreateBody(String id, String kind, String host, int port,
                             String database, String username, String password) {}

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody CreateBody body) {
        svc.create(body.id(), body.kind(), body.host(), body.port(),
            body.database(), body.username(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("connections", svc.list());
    }
}
