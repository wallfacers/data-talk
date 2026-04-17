package com.datatalk.application.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Port interface for interacting with the OpenCode AI provider registry.
 * Implemented by {@code OpenCodeHttpClient} in the infrastructure module.
 */
public interface OpenCodeProviderClient {

    /** Returns the full provider list (all + connected). */
    JsonNode listProviders();

    /** Returns the current provider auth configuration. */
    JsonNode getProviderAuth();

    /** Stores credentials for the given provider. */
    void putAuth(String providerId, Map<String, Object> payload);
}
