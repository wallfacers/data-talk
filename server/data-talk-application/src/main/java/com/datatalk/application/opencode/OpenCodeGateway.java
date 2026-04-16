package com.datatalk.application.opencode;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.domain.action.ActionDescriptor;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Pushes every registered DataTalk ACTION to OpenCode as a plugin tool, and
 * manages the data-talk session to open-code session id mapping.
 *
 * <p>Plan A wires {@link ToolPusher} and {@link MessageSender} to real HTTP
 * calls via Spring configuration (see {@code OpenCodeGatewayBeans} in the
 * adapter module — added in Task 26). Unit tests inject stubs.</p>
 */
public class OpenCodeGateway {

    public interface ToolPusher {
        void push(String name, String description,
                  Map<String, Object> parameters, String callbackUrl);
    }

    public interface MessageSender {
        void send(String openCodeSessionId, Map<String, Object> requestBody);
    }

    private final ActionRegistry registry;
    private final ToolPusher pusher;
    private final MessageSender sender;
    private final Supplier<String> sessionCreator;
    private final String callbackBase;

    public OpenCodeGateway(ActionRegistry registry, ToolPusher pusher,
                           MessageSender sender, Supplier<String> sessionCreator,
                           String callbackBase) {
        this.registry = registry;
        this.pusher = pusher;
        this.sender = sender;
        this.sessionCreator = sessionCreator;
        this.callbackBase = callbackBase;
    }

    public void registerTools() {
        for (ActionDescriptor d : registry.all()) {
            pusher.push(
                d.id(),
                d.description(),
                d.inputSchema(),
                callbackBase + "/api/opencode-tool/" + d.id()
            );
        }
    }

    public String createOpenCodeSession() {
        return sessionCreator.get();
    }

    public void forwardUserMessage(String openCodeSessionId, Map<String, Object> requestBody) {
        sender.send(openCodeSessionId, requestBody);
    }
}
