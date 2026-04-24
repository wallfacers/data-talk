package com.datatalk.infra.opencode;

import com.datatalk.application.ai.OpenCodeProviderClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Thin HTTP wrapper around the OpenCode server. Session messaging remains the
 * core path, while MCP bootstrap/reconcile uses /config and /mcp helpers.
 */
public class OpenCodeHttpClient implements OpenCodeProviderClient {

    private volatile String baseUrl;
    private volatile WebClient wc;
    private final ExchangeStrategies strategies;
    private final ObjectMapper om;

    public OpenCodeHttpClient(String baseUrl, ObjectMapper om) {
        this.baseUrl = baseUrl;
        this.strategies = ExchangeStrategies.builder()
            .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
        this.wc = newClient(baseUrl);
        this.om = om;
    }

    public void setBaseUrl(String url) {
        this.baseUrl = url;
        this.wc = newClient(url);
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

    public JsonNode patchConfig(Map<String, Object> config) {
        String body = wc.patch().uri("/config")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(config)
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return readJson(body, "/config");
    }

    public JsonNode addMcpServer(String name, Map<String, Object> config) {
        String body = wc.post().uri("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "name", name,
                "config", config
            ))
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return readJson(body, "/mcp");
    }

    public JsonNode getMcpStatus() {
        String body = wc.get().uri("/mcp")
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return readJson(body, "/mcp");
    }

    private WebClient newClient(String url) {
        return WebClient.builder()
            .baseUrl(url)
            .exchangeStrategies(strategies)
            .build();
    }

    private JsonNode readJson(String body, String endpoint) {
        try {
            return om.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse OpenCode " + endpoint + " response", e);
        }
    }
}
