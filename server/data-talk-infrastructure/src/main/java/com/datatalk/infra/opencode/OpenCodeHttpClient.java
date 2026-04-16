package com.datatalk.infra.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Thin HTTP wrapper around the OpenCode server. Exposes only the three
 * operations Plan A needs: session creation, tool registration, and message
 * send (fire-and-forget; events arrive via the separate /event stream).
 */
public class OpenCodeHttpClient {

    private final WebClient wc;
    private final ObjectMapper om;

    public OpenCodeHttpClient(String baseUrl, ObjectMapper om) {
        this.wc = WebClient.builder().baseUrl(baseUrl).build();
        this.om = om;
    }

    public String createSession() {
        String body = wc.post().uri("/session")
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            JsonNode node = om.readTree(body);
            return node.path("id").asText();
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse OpenCode /session response", e);
        }
    }

    public void registerTool(String name, String description,
                             Map<String, Object> parameters, String callbackUrl) {
        wc.post().uri("/plugin/register-tool")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "name", name,
                "description", description,
                "parameters", parameters,
                "callbackUrl", callbackUrl
            ))
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public void sendMessage(String sessionId, Map<String, Object> requestBody) {
        wc.post().uri("/session/{id}/message", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public void abort(String sessionId) {
        wc.post().uri("/session/{id}/abort", sessionId)
            .retrieve()
            .toBodilessEntity()
            .block();
    }
}
