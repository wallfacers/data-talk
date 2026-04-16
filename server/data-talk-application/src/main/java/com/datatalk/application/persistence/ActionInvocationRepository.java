package com.datatalk.application.persistence;

/**
 * Stores and retrieves action invocation lifecycle records.
 */
public interface ActionInvocationRepository {

    /**
     * Records that an action invocation has started.
     */
    void start(String callId, String sessionId, String actionId, String inputJson, long ts);

    /**
     * Records that an action invocation completed successfully.
     */
    void complete(String callId, String outputJson, long ts);

    /**
     * Records that an action invocation failed.
     */
    void fail(String callId, String errorJson, long ts);
}
