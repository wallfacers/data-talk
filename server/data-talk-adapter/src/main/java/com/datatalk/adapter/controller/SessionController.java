package com.datatalk.adapter.controller;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.session.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService svc;

    public SessionController(SessionService svc) { this.svc = svc; }

    @GetMapping
    public List<SessionDto> list(@RequestParam(value = "connectionId", required = false) String connectionId) {
        return svc.list(connectionId).stream().map(SessionDto::from).toList();
    }

    @PostMapping
    public SessionDto create(@RequestBody CreateSessionRequest req) {
        if (req == null || req.connectionId() == null || req.connectionId().isBlank()) {
            throw new IllegalArgumentException("connectionId is required");
        }
        SessionRecord rec = svc.create(req.connectionId(), req.title());
        return SessionDto.from(rec);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDto> get(@PathVariable String id) {
        return svc.find(id)
            .map(SessionDto::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SessionDto> rename(@PathVariable String id, @RequestBody RenameRequest req) {
        try {
            SessionRecord rec = svc.rename(id, req == null ? null : req.title());
            return ResponseEntity.ok(SessionDto.from(rec));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        try {
            svc.delete(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record CreateSessionRequest(String connectionId, String title) {}

    public record RenameRequest(String title) {}

    public record SessionDto(
        String id,
        String connectionId,
        String title,
        boolean hasEverSent,
        long createdAt,
        long updatedAt
    ) {
        public static SessionDto from(SessionRecord r) {
            return new SessionDto(r.id(), r.connectionId(), r.title(),
                r.hasEverSent(), r.createdAt(), r.updatedAt());
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
