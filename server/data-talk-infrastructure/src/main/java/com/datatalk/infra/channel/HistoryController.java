package com.datatalk.infra.channel;

import com.datatalk.application.channel.HistoryService;
import com.datatalk.application.channel.SyntheticSessionMessageService;
import com.datatalk.application.persistence.ArtifactRecord;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class HistoryController {

    private final HistoryService svc;
    private final SyntheticSessionMessageService syntheticMessages;

    public HistoryController(HistoryService svc, SyntheticSessionMessageService syntheticMessages) {
        this.svc = svc;
        this.syntheticMessages = syntheticMessages;
    }

    @GetMapping("/messages")
    public JsonNode messages(@PathVariable String sessionId) {
        return svc.getMessages(sessionId);
    }

    @PostMapping("/messages/bang-query")
    public BangQueryMessageResponse createBangQueryMessage(@PathVariable String sessionId,
                                                           @RequestBody BangQueryMessageCreateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("request body is required");
        }
        try {
            var record = syntheticMessages.createBangQueryUserMessage(sessionId, req.text(), req.createdAt());
            return new BangQueryMessageResponse(record.id(), record.sessionId(), record.createdAt(), record.kind());
        } catch (NoSuchElementException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/artifacts")
    public Map<String, List<ArtifactRecord>> artifacts(@PathVariable String sessionId) {
        return Map.of("artifacts", svc.getArtifacts(sessionId));
    }

    public record BangQueryMessageCreateRequest(String text, long createdAt) {}

    public record BangQueryMessageResponse(String id, String sessionId, long createdAt, String kind) {}
}
