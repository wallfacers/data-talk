package com.datatalk.adapter.config;

import com.datatalk.application.opencode.OpenCodeEventLoop;
import com.datatalk.application.opencode.OpenCodeEventTranslator;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.infra.opencode.OpenCodeConfig;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Configuration
public class OpenCodeGatewayBeans {

    private final OpenCodeHttpClient client;
    private final OpenCodeConfig.OpenCodeProperties props;
    private final ActionRegistry registry;
    private final ObjectMapper om;
    private final OpenCodeEventTranslator translator;
    private final SessionBusRegistry buses;
    private final OpenCodeSessionMap sessionMap;
    private final String opencodeBaseUrl;
    private OpenCodeGateway gateway;
    private OpenCodeEventLoop eventLoop;

    public OpenCodeGatewayBeans(OpenCodeHttpClient client,
                                OpenCodeConfig.OpenCodeProperties props,
                                ActionRegistry registry,
                                ObjectMapper om,
                                OpenCodeEventTranslator translator,
                                SessionBusRegistry buses,
                                OpenCodeSessionMap sessionMap,
                                @Value("${datatalk.opencode.base-url:http://localhost:4096}") String opencodeBaseUrl) {
        this.client = client;
        this.props = props;
        this.registry = registry;
        this.om = om;
        this.translator = translator;
        this.buses = buses;
        this.sessionMap = sessionMap;
        this.opencodeBaseUrl = opencodeBaseUrl;
    }

    @Bean
    public OpenCodeGateway openCodeGateway() {
        this.gateway = new OpenCodeGateway(
            registry,
            (name, desc, params, cb) -> client.registerTool(name, desc, params, cb),
            (ocSid, body) -> client.sendMessage(ocSid, body),
            client::createSession,
            props.callbackBase()
        );
        return gateway;
    }

    @Bean
    public OpenCodeEventLoop openCodeEventLoop() {
        this.eventLoop = new OpenCodeEventLoop(
            opencodeBaseUrl, om, translator, buses, sessionMap, null);
        return eventLoop;
    }

    /**
     * Registers tools shortly after startup and starts the OpenCode SSE event loop.
     * If OpenCode is unreachable (Plan A scope: fine), the app keeps running in degraded mode.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        try {
            gateway.registerTools();
        } catch (Exception e) {
            // Plan B: retry with backoff. Plan A logs and continues.
            System.err.println("OpenCode tool registration failed (degraded mode): " + e.getMessage());
        }
        try {
            eventLoop.start();
        } catch (Exception e) {
            System.err.println("OpenCode SSE event loop failed to start (degraded mode): " + e.getMessage());
        }
    }
}
