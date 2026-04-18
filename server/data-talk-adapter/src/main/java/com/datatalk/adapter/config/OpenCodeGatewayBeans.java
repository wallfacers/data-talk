package com.datatalk.adapter.config;

import com.datatalk.application.opencode.OpenCodeEventLoop;
import com.datatalk.application.opencode.OpenCodeEventTranslator;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.infra.opencode.OpenCodeConfig;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.datatalk.infra.opencode.process.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OpenCodeGatewayBeans.class);

    private final OpenCodeHttpClient client;
    private final OpenCodeConfig.OpenCodeProperties props;
    private final ActionRegistry registry;
    private final ObjectMapper om;
    private final OpenCodeEventTranslator translator;
    private final SessionBusRegistry buses;
    private final OpenCodeSessionMap sessionMap;
    private final SessionRepository sessionRepository;
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
                                SessionRepository sessionRepository,
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
        this.sessionRepository = sessionRepository;
        this.serveProps = serveProps;

        Path homeDir = Paths.get(System.getProperty("user.home"));
        OpenCodeBinaryResolver resolver = new OpenCodeBinaryResolver();
        OpenCodePortAllocator allocator = new OpenCodePortAllocator();
        eventLoop = new OpenCodeEventLoop(
            defaultBaseUrl, om, translator, buses, sessionMap, null);

        this.processManager = new OpenCodeProcessManager(
            serveProps, resolver, allocator,
            homeDir, client, eventLoop, required);
    }

    @Bean
    public OpenCodeGateway openCodeGateway() {
        this.gateway = new OpenCodeGateway(
            registry,
            (name, desc, params, cb) -> client.registerTool(name, desc, params, cb),
            (ocSid, body) -> client.sendMessage(ocSid, body),
            client::createSession,
            client::deleteSession,
            (ocSid, limit) -> client.listMessages(ocSid, limit),
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
        preloadSessionMap();

        if (!serveProps.isEnabled()) {
            log.info("OpenCode embedded server is disabled - skipping tool registration");
            return;
        }

        if (!processManager.isRunning()) {
            log.error("OpenCode embedded server failed to start - skipping tool registration (degraded mode)");
            return;
        }

        try {
            gateway.registerTools();
        } catch (Exception e) {
            log.error("OpenCode tool registration failed (degraded mode): {}", e.getMessage(), e);
        }
        try {
            eventLoop.start();
        } catch (Exception e) {
            log.error("OpenCode SSE event loop failed to start (degraded mode): {}", e.getMessage(), e);
        }
    }

    /**
     * Rehydrate DataTalk↔OpenCode session bindings from SQLite into the
     * in-memory {@link OpenCodeSessionMap}. Without this, a fresh backend
     * process starts with an empty map and {@code OpenCodeEventLoop} can't
     * reverse-lookup ocSid→dtSid until the user sends a new message —
     * meaning AI responses to existing sessions disappear after restart.
     */
    private void preloadSessionMap() {
        int loaded = 0;
        try {
            for (SessionRecord s : sessionRepository.listAll()) {
                if (s.openCodeSid() != null && !s.openCodeSid().isBlank()) {
                    sessionMap.bind(s.id(), s.openCodeSid());
                    loaded++;
                }
            }
        } catch (Exception e) {
            log.warn("[session-map] preload failed (continuing with empty map): {}", e.toString());
            return;
        }
        log.info("[session-map] preloaded {} DataTalk↔OpenCode bindings from DB", loaded);
    }
}