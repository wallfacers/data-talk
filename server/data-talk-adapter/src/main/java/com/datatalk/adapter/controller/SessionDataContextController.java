package com.datatalk.adapter.controller;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.SessionDataContextRecord;
import com.datatalk.application.session.ConnectionTargetDiscoveryService;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.application.session.UseTargetResolver;
import com.datatalk.dto.ConnectionTargetsDto;
import com.datatalk.dto.ResolveUseTargetRequest;
import com.datatalk.dto.ResolveUseTargetResponse;
import com.datatalk.dto.SessionDataContextDto;
import com.datatalk.dto.SessionDataContextUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/sessions/{id}/data-context")
public class SessionDataContextController {

    private final SessionDataContextService service;
    private final UseTargetResolver resolver;
    private final ConnectionTargetDiscoveryService discovery;
    private final Translator translator;

    public SessionDataContextController(
        SessionDataContextService service,
        UseTargetResolver resolver,
        ConnectionTargetDiscoveryService discovery,
        Translator translator
    ) {
        this.service = service;
        this.resolver = resolver;
        this.discovery = discovery;
        this.translator = translator;
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

    @PostMapping("/resolve-use")
    public ResolveUseTargetResponse resolveUse(
        @PathVariable("id") String sessionId,
        @RequestBody ResolveUseTargetRequest req
    ) {
        var result = resolver.resolve(sessionId, req == null ? null : req.target());
        return new ResolveUseTargetResponse(
            result.status(),
            result.context() == null ? null : toDto(result.context()),
            result.matchedTarget() == null ? null : toOptionDto(result.matchedTarget()),
            result.candidates().stream().map(SessionDataContextController::toOptionDto).toList(),
            result.suggestions().stream().map(SessionDataContextController::toOptionDto).toList(),
            result.message()
        );
    }

    @PostMapping("/validate")
    public SessionDataContextDto validate(@PathVariable("id") String sessionId) {
        return toDto(service.validate(sessionId));
    }

    @GetMapping("/targets")
    public ConnectionTargetsDto targets(
        @PathVariable("id") String sessionId,
        @RequestParam(value = "connectionId", required = false) String requestedConnectionId
    ) {
        String connectionId = requestedConnectionId;
        if (connectionId == null || connectionId.isBlank()) {
            connectionId = service.get(sessionId).connectionId();
        }
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException(translator.get("error.connection.active_required"));
        }

        var discovered = discovery.discover(connectionId);
        return new ConnectionTargetsDto(
            discovered.connectionId(),
            discovered.connectionName(),
            discovered.databaseNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
            discovered.schemaNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
        );
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

    private static ResolveUseTargetResponse.TargetOptionDto toOptionDto(UseTargetResolver.TargetOption option) {
        return new ResolveUseTargetResponse.TargetOptionDto(
            option.level(),
            option.connectionId(),
            option.connectionName(),
            option.database(),
            option.schema(),
            option.label()
        );
    }
}
