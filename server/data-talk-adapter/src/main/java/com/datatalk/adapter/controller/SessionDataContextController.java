package com.datatalk.adapter.controller;

import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.dto.SessionDataContextDto;
import com.datatalk.dto.SessionDataContextUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/sessions/{id}/data-context")
public class SessionDataContextController {

    private final SessionDataContextService service;

    public SessionDataContextController(SessionDataContextService service) {
        this.service = service;
    }

    @GetMapping
    public SessionDataContextDto get(@PathVariable("id") String sessionId) {
        return toDto(service.get(sessionId));
    }

    @PutMapping
    public SessionDataContextDto put(
        @PathVariable("id") String sessionId,
        @RequestBody SessionDataContextUpdateRequest req
    ) {
        return toDto(service.set(sessionId, req));
    }

    private static SessionDataContextDto toDto(SessionDataContextRecord record) {
        return new SessionDataContextDto(
            record.sessionId(),
            record.connectionId(),
            record.connectionNameSnapshot(),
            record.databaseName(),
            record.schemaName(),
            record.selectedLevel(),
            record.updatedAt()
        );
    }
}
