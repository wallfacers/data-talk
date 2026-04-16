package com.datatalk.adapter.config;

import com.datatalk.application.opencode.OpenCodeEventLoop;
import com.datatalk.application.opencode.OpenCodeEventTranslator;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.infra.opencode.OpenCodeConfig;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.datatalk.infra.opencode.process.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@EnableConfigurationProperties(OpenCodeServeProperties.class)
public class OpenCodeGatewayBeans {

    private final OpenCodeHttpClient client;
    private final OpenCodeConfig.OpenCodeProperties props;
    private final ActionRegistry registry;
    private final ObjectMapper om;
    private final OpenCodeEventTranslator translator;
    private final SessionBusRegistry buses;
    private final OpenCodeSessionMap sessionMap;
    private final OpenCodeServeProperties serveProps;
    private final OpenCodeProcessManager processManager;
    private OpenCodeGateway gateway;
    private OpenCodeEventLoop eventLoop;

    public OpenCodeGatewayBeans(OpenCodeHttpClient client,
                                OpenCodeConfig.OpenCodeProperties props,
                                ActionRegistry registry,
                                ObjectMapper om,
                                OpenCodeEventTranslator translator,
                                SessionBusRegistry buses,
                                OpenCodeSessionMap sessionMap,
                                OpenCodeServeProperties serveProps,
                                @Value("${datatalk.opencode.required:false}") boolean required,
                                @Value("${datatalk.opencode.base-url:http://localhost:4096}") String defaultBaseUrl) {
        this.client = client;
        this.props = props;
        this.registry = registry;
        this.om = om;
        this.translator = translator;
        this.buses = buses;
        this.sessionMap = sessionMap;
        this.serveProps = serveProps;

        Path homeDir = Paths.get(System.getProperty("user.home"));
        OpenCodeBinaryResolver resolver = new OpenCodeBinaryResolver();
        OpenCodePortAllocator allocator = new OpenCodePortAllocator();
        eventLoop = new OpenCodeEventLoop(
            defaultBaseUrl, om, translator, buses, sessionMap, null);

        this.processManager = new OpenCodeProcessManager(
            serveProps, resolver, allocator,
            homeDir, client, eventLoop, required, defaultBaseUrl);
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
    public OpenCodeEventLoop openCodeEventLoopBean() {
        return eventLoop;
    }

    @Bean
    public OpenCodeProcessManager openCodeProcessManager() {
        return processManager;
    }

    /**
     * Registers tools and starts the OpenCode SSE event loop after startup.
     * If the embedded server is enabled and running, it's required for tool registration.
     * If the embedded server is disabled, requires an external OpenCode instance.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        // If embedded serve is enabled but manager didn't start (failure), skip
        if (serveProps.isEnabled() && !processManager.isRunning()) {
            System.err.println("OpenCode embedded server failed to start - skipping tool registration (degraded mode)");
            return;
        }

        try {
            gateway.registerTools();
        } catch (Exception e) {
            System.err.println("OpenCode tool registration failed (degraded mode): " + e.getMessage());
        }
        try {
            eventLoop.start();
        } catch (Exception e) {
            System.err.println("OpenCode SSE event loop failed to start (degraded mode): " + e.getMessage());
        }
    }
}