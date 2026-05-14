package com.datatalk.infra.channel;

import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Exposes the authoritative OpenCode {@code SessionStatus} for a DataTalk session.
 * Used by the client on mount / after CTRL+R to reconcile the composer button's
 * streaming flag with the backend's real state (see BUG-0046).
 *
 * <p>Fail-open: any failure to reach OpenCode, an unmapped session, or an absent
 * map entry is reported as {@code idle}. The client treats idle as "show send
 * button" — equivalent to having no information.</p>
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class SessionStatusController {

    private static final Set<String> ALLOWED_TYPES = Set.of("idle", "busy", "retry");

    private final OpenCodeHttpClient client;
    private final OpenCodeSessionMap sessionMap;

    public SessionStatusController(OpenCodeHttpClient client, OpenCodeSessionMap sessionMap) {
        this.client = client;
        this.sessionMap = sessionMap;
    }

    @GetMapping("/status")
    public Map<String, String> status(@PathVariable String sessionId) {
        String openCodeSid = sessionMap.openCodeFor(sessionId);
        if (openCodeSid == null || openCodeSid.isBlank()) {
            return Map.of("type", "idle");
        }
        JsonNode statuses = client.getSessionStatuses();
        JsonNode entry = statuses.path(openCodeSid);
        if (entry.isMissingNode() || entry.isNull()) {
            return Map.of("type", "idle");
        }
        String type = entry.path("type").asText("idle");
        if (!ALLOWED_TYPES.contains(type)) {
            type = "idle";
        }
        return Map.of("type", type);
    }
}
