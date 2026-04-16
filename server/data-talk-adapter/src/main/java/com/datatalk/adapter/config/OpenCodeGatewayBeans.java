package com.datatalk.adapter.config;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.infra.opencode.OpenCodeConfig;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Configuration
public class OpenCodeGatewayBeans {

    private final OpenCodeHttpClient client;
    private final OpenCodeConfig.OpenCodeProperties props;
    private final ActionRegistry registry;
    private OpenCodeGateway gateway;

    public OpenCodeGatewayBeans(OpenCodeHttpClient client,
                                OpenCodeConfig.OpenCodeProperties props,
                                ActionRegistry registry) {
        this.client = client;
        this.props = props;
        this.registry = registry;
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

    /**
     * Registers tools shortly after startup. If OpenCode is unreachable (Plan A
     * scope: fine), the app keeps running in degraded mode.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        try {
            gateway.registerTools();
        } catch (Exception e) {
            // Plan B: retry with backoff. Plan A logs and continues.
            System.err.println("OpenCode tool registration failed (degraded mode): " + e.getMessage());
        }
    }
}
