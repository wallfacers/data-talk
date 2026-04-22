package com.datatalk.infra.opencode;

import com.datatalk.application.ai.OpenCodeProviderClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Thin HTTP wrapper around the OpenCode server. Exposes only the three
 * operations Plan A needs: session creation, tool registration, and message
 * send (fire-and-forget; events arrive via the separate /event stream).
 */
public class OpenCodeHttpClient implements OpenCodeProviderClient {

    private volatile String baseUrl;
    private final WebClient wc;
    private final ObjectMapper om;

    public OpenCodeHttpClient(String baseUrl, ObjectMapper om) {
        this.baseUrl = baseUrl;
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
        this.wc = WebClient.builder()
            .baseUrl(baseUrl)
            .exchangeStrategies(strategies)
            .build();
        this.om = om;
    }

    public void setBaseUrl(String url) {
        this.baseUrl = url;
    }

    public String getBaseUrl() {
        return this.baseUrl;
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

    public void deleteSession(String sessionId) {
        wc.delete().uri("/session/{id}", sessionId)
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public boolean abort(String sessionId) {
        String body = wc.post().uri("/session/{id}/abort", sessionId)
            .retrieve()
            .bodyToMono(String.class)
            .block();
        if (body == null || body.isBlank()) {
            // Keep compatibility with older OpenCode builds that returned 204.
            return true;
        }
        String trimmed = body.trim();
        if ("true".equalsIgnoreCase(trimmed)) return true;
        if ("false".equalsIgnoreCase(trimmed)) return false;
        try {
            JsonNode node = om.readTree(trimmed);
            if (node.isBoolean()) return node.booleanValue();
            if (node.has("result") && node.get("result").isBoolean()) {
                return node.get("result").booleanValue();
            }
        } catch (Exception ignored) {
            // Fall through and treat unknown payload as success for compatibility.
        }
        return true;
    }

    public JsonNode listMessages(String openCodeSessionId, Integer limit) {
        String body = wc.get()
            .uri(uriBuilder -> uriBuilder
                .path("/session/{id}/message")
                .queryParamIfPresent("limit", java.util.Optional.ofNullable(limit))
                .build(openCodeSessionId))
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            return om.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse OpenCode /session/"
                + openCodeSessionId + "/message response", e);
        }
    }

    public JsonNode listProviders() {
        String body = wc.get().uri("/provider")
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            return om.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse OpenCode /provider response", e);
        }
    }

    public JsonNode getProviderAuth() {
        String body = wc.get().uri("/provider/auth")
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            return om.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse OpenCode /provider/auth response", e);
        }
    }

    public void putAuth(String providerId, Map<String, Object> payload) {
        wc.put().uri("/auth/{id}", providerId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public void deleteAuth(String providerId) {
        wc.delete().uri("/auth/{id}", providerId)
            .retrieve()
            .toBodilessEntity()
            .block();
    }
}
