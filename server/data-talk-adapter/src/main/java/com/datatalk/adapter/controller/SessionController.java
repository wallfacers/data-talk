package com.datatalk.adapter.controller;

import com.datatalk.application.i18n.Translator;
import com.datatalk.dto.SessionCreateRequest;
import com.datatalk.dto.SessionDto;
import com.datatalk.dto.SessionRenameRequest;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.session.CreateSessionResult;
import com.datatalk.application.session.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService svc;
    private final Translator translator;

    public SessionController(SessionService svc, Translator translator) {
        this.svc = svc;
        this.translator = translator;
    }

    @GetMapping
    public List<SessionDto> list(@RequestParam(value = "connectionId", required = false) String connectionId) {
        return svc.list(connectionId).stream().map(SessionController::toDto).toList();
    }

    @PostMapping
    public SessionDto create(@RequestBody SessionCreateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException(translator.get("error.request_body_required"));
        }
        String connectionId = (req.connectionId() == null || req.connectionId().isBlank())
            ? null : req.connectionId();
        CreateSessionResult r = svc.create(connectionId, req.title());
        return toDto(r.record(), r.reusedEmpty());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDto> get(@PathVariable String id) {
        return svc.find(id)
            .map(SessionController::toDto)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SessionDto> rename(@PathVariable String id, @RequestBody SessionRenameRequest req) {
        try {
            SessionRecord rec = svc.rename(id, req == null ? null : req.title());
            return ResponseEntity.ok(toDto(rec));
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

    private static SessionDto toDto(SessionRecord r) {
        return toDto(r, false);
    }

    private static SessionDto toDto(SessionRecord r, boolean reusedEmpty) {
        return new SessionDto(r.id(), r.connectionId(), r.title(),
            r.hasEverSent(), r.createdAt(), r.updatedAt(), r.titleLocked(), reusedEmpty);
    }

}
