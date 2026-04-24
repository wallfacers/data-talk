package com.datatalk.infra.channel;

import com.datatalk.application.channel.HistoryService;
import com.datatalk.application.channel.SyntheticSessionMessageService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.PayloadRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class HistoryController {

    private final HistoryService svc;
    private final SyntheticSessionMessageService syntheticMessages;
    private final Translator translator;
    private final ObjectMapper objectMapper;

    public HistoryController(
        HistoryService svc,
        SyntheticSessionMessageService syntheticMessages,
        Translator translator,
        ObjectMapper objectMapper
    ) {
        this.svc = svc;
        this.syntheticMessages = syntheticMessages;
        this.translator = translator;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/messages")
    public JsonNode messages(@PathVariable String sessionId) {
        return svc.getMessages(sessionId);
    }

    @PostMapping("/messages/bang-query")
    public BangQueryMessageResponse createBangQueryMessage(@PathVariable String sessionId,
                                                           @RequestBody BangQueryMessageCreateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException(translator.get("error.request_body_required"));
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
    public Map<String, List<ArtifactDto>> artifacts(@PathVariable String sessionId) {
        return Map.of("artifacts", svc.getArtifacts(sessionId).stream()
            .map(this::toArtifactDto)
            .toList());
    }

    private ArtifactDto toArtifactDto(ArtifactRecord artifact) {
        return new ArtifactDto(
            artifact.id(),
            artifact.version(),
            artifact.sessionId(),
            artifact.kind(),
            artifact.producedBy(),
            artifact.supersedesId(),
            artifact.supersedesVersion(),
            artifact.pinned(),
            artifact.createdAt(),
            artifact.originMessageId(),
            artifact.originPartId(),
            payloadFromRef(artifact.payloadRef())
        );
    }

    private JsonNode payloadFromRef(String payloadRef) {
        if (hasPrefix(payloadRef, PayloadRef.INLINE_PREFIX)) {
            String json = payloadRef.substring(PayloadRef.INLINE_PREFIX.length());
            try {
                return objectMapper.readTree(json);
            } catch (Exception e) {
                return objectMapper.createObjectNode().put("raw", json);
            }
        }
        if (hasPrefix(payloadRef, PayloadRef.HANDLE_PREFIX)) {
            String handle = payloadRef.substring(PayloadRef.HANDLE_PREFIX.length());
            return objectMapper.valueToTree(Map.of("handle", handle));
        }
        return objectMapper.nullNode();
    }

    private static boolean hasPrefix(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public record BangQueryMessageCreateRequest(String text, long createdAt) {}

    public record BangQueryMessageResponse(String id, String sessionId, long createdAt, String kind) {}

    public record ArtifactDto(
        String id,
        int version,
        String sessionId,
        String kind,
        String producedBy,
        String supersedesId,
        Integer supersedesVersion,
        boolean pinned,
        long createdAt,
        String originMessageId,
        String originPartId,
        JsonNode payload
    ) {}
}
