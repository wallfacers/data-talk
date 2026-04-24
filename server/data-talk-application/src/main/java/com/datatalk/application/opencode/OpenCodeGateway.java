package com.datatalk.application.opencode;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Thin orchestration wrapper for OpenCode session lifecycle and message
 * forwarding. Tool registration is no longer part of this gateway; MCP
 * bootstrap/reconcile owns that responsibility.
 */
public class OpenCodeGateway {

    public interface MessageSender {
        void send(String openCodeSessionId, Map<String, Object> requestBody);
    }

    public interface SessionDeleter {
        void delete(String openCodeSessionId);
    }

    public interface SessionAborter {
        boolean abort(String openCodeSessionId);
    }

    public interface MessageLister {
        JsonNode list(String openCodeSessionId, Integer limit);
    }

    private final MessageSender sender;
    private final Supplier<String> sessionCreator;
    private final SessionDeleter deleter;
    private final SessionAborter aborter;
    private final MessageLister lister;

    public OpenCodeGateway(MessageSender sender,
                           Supplier<String> sessionCreator,
                           SessionDeleter deleter,
                           MessageLister lister) {
        this(
            sender,
            sessionCreator,
            deleter,
            openCodeSessionId -> false,
            lister
        );
    }

    public OpenCodeGateway(MessageSender sender,
                           Supplier<String> sessionCreator,
                           SessionDeleter deleter,
                           SessionAborter aborter,
                           MessageLister lister) {
        this.sender = sender;
        this.sessionCreator = sessionCreator;
        this.deleter = deleter;
        this.aborter = aborter;
        this.lister = lister;
    }

    public String createOpenCodeSession() {
        return sessionCreator.get();
    }

    public void forwardUserMessage(String openCodeSessionId, Map<String, Object> requestBody) {
        sender.send(openCodeSessionId, requestBody);
    }

    public void deleteOpenCodeSession(String openCodeSessionId) {
        deleter.delete(openCodeSessionId);
    }

    public boolean abortOpenCodeSession(String openCodeSessionId) {
        return aborter.abort(openCodeSessionId);
    }

    public JsonNode listMessages(String openCodeSessionId, Integer limit) {
        return lister.list(openCodeSessionId, limit);
    }
}
