package com.datatalk.adapter.config;

import com.datatalk.application.opencode.OpenCodeBridgeStatus;
import com.datatalk.application.opencode.OpenCodeEventLoop;
import com.datatalk.application.opencode.OpenCodeEventTranslator;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.datatalk.infra.opencode.OpenCodeMcpProperties;
import com.datatalk.infra.opencode.process.OpenCodeBinaryResolver;
import com.datatalk.infra.opencode.process.OpenCodeBootstrapReconciler;
import com.datatalk.infra.opencode.process.OpenCodeBootstrapWriter;
import com.datatalk.infra.opencode.process.OpenCodePortAllocator;
import com.datatalk.infra.opencode.process.SkillResourceSyncer;
import com.datatalk.infra.opencode.process.OpenCodeProcessManager;
import com.datatalk.infra.opencode.process.OpenCodeServeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@EnableConfigurationProperties(OpenCodeServeProperties.class)
public class OpenCodeGatewayBeans {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeGatewayBeans.class);

    private final OpenCodeHttpClient client;
    private final SessionRepository sessionRepository;
    private final OpenCodeSessionMap sessionMap;
    private final OpenCodeServeProperties serveProps;
    private final OpenCodeMcpProperties mcpProps;
    private final OpenCodeBootstrapReconciler bootstrapReconciler;
    private final OpenCodeBridgeStatus bridgeStatus;
    private final OpenCodeProcessManager processManager;
    private final OpenCodeEventLoop eventLoop;
    private final SkillResourceSyncer skillSyncer;
    private OpenCodeGateway gateway;

    public OpenCodeGatewayBeans(OpenCodeHttpClient client,
                                ObjectMapper om,
                                OpenCodeEventTranslator translator,
                                SessionBusRegistry buses,
                                OpenCodeSessionMap sessionMap,
                                SessionRepository sessionRepository,
                                OpenCodeServeProperties serveProps,
                                OpenCodeMcpProperties mcpProps,
                                OpenCodeBootstrapReconciler bootstrapReconciler,
                                OpenCodeBridgeStatus bridgeStatus,
                                @Value("${datatalk.opencode.required:false}") boolean required,
                                @Value("${datatalk.opencode.base-url:http://localhost:4096}") String defaultBaseUrl) {
        this.client = client;
        this.sessionRepository = sessionRepository;
        this.sessionMap = sessionMap;
        this.serveProps = serveProps;
        this.mcpProps = mcpProps;
        this.bootstrapReconciler = bootstrapReconciler;
        this.bridgeStatus = bridgeStatus;

        Path homeDir = Paths.get(System.getProperty("user.home"));
        OpenCodeBinaryResolver resolver = new OpenCodeBinaryResolver();
        OpenCodePortAllocator allocator = new OpenCodePortAllocator();
        this.skillSyncer = new SkillResourceSyncer();
        this.eventLoop = new OpenCodeEventLoop(defaultBaseUrl, om, translator, buses, sessionMap, null);
        this.processManager = new OpenCodeProcessManager(
            serveProps,
            resolver,
            allocator,
            homeDir,
            mcpProps.resolveConfigDir(),
            client,
            eventLoop,
            required
        );
    }

    @Bean
    public OpenCodeGateway openCodeGateway() {
        this.gateway = new OpenCodeGateway(
            (ocSid, body) -> client.sendMessage(ocSid, body),
            client::createSession,
            client::deleteSession,
            client::abort,
            (ocSid, limit) -> client.listMessages(ocSid, limit)
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

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup(ApplicationReadyEvent event) {
        preloadSessionMap();
        int serverPort = serverPort(event);

        if (serveProps.isEnabled()) {
            if (!startEmbedded(serverPort)) {
                return;
            }
        } else if (mcpProps.isEnabled()) {
            reconcileExternal(serverPort);
        } else {
            bridgeStatus.markOk("OpenCode MCP bridge disabled");
            log.info("OpenCode MCP bootstrap disabled (datatalk.mcp.enabled=false)");
        }

        try {
            String mode = serveProps.isEnabled() ? "embedded" : "external";
            log.info("Starting OpenCode SSE event loop against {} OpenCode at {}", mode, client.getBaseUrl());
            eventLoop.start();
        } catch (Exception e) {
            log.error("OpenCode SSE event loop failed to start (degraded mode): {}", e.getMessage(), e);
        }
    }

    private boolean startEmbedded(int serverPort) {
        try {
            Path opencodeCwd = OpenCodeProcessManager.opencodeWorkingDir(Paths.get(System.getProperty("user.home")));
            skillSyncer.syncSkill("bezel", opencodeCwd);
            skillSyncer.syncSkill("data-ingestion", opencodeCwd);
            skillSyncer.syncSkill("sql-execution", opencodeCwd);
            skillSyncer.syncSkill("query-editor-workflow", opencodeCwd);
            skillSyncer.syncSkill("ui-contract", opencodeCwd);
            skillSyncer.syncSkill("tab-management", opencodeCwd);
            skillSyncer.syncSkill("er-tabs", opencodeCwd);
            skillSyncer.syncSkill("concurrency-contract", opencodeCwd);
            skillSyncer.syncSkill("charts-and-dashboards", opencodeCwd);
            skillSyncer.syncSkill("artifacts-output", opencodeCwd);
            skillSyncer.syncSkill("connection-management", opencodeCwd);
            skillSyncer.syncSkill("sql-error-diagnostics", opencodeCwd);
            skillSyncer.syncSkill("database-dialects", opencodeCwd);

            if (mcpProps.isEnabled()) {
                bootstrapReconciler.writeManagedConfig(serverPort);
            } else {
                bridgeStatus.markOk("OpenCode MCP bridge disabled");
            }

            processManager.start();
            if (!processManager.isRunning()) {
                bridgeStatus.markDegraded("embedded OpenCode not running", "OpenCode embedded server unavailable");
                log.error("OpenCode embedded server failed to start");
                return false;
            }

            if (mcpProps.isEnabled()) {
                bootstrapReconciler.probeRuntimeStatus();
            }
            return true;
        } catch (IOException e) {
            bridgeStatus.markDegraded(e.getMessage(), "OpenCode MCP bootstrap write failed");
            log.error("Failed to write managed OpenCode bootstrap files: {}", e.getMessage(), e);
            return false;
        }
    }

    private void reconcileExternal(int serverPort) {
        try {
            OpenCodeBootstrapWriter.BootstrapArtifacts artifacts = bootstrapReconciler.writeManagedConfig(serverPort);
            if (!bootstrapReconciler.reconcileExternal(artifacts)) {
                log.warn("OpenCode MCP reconcile completed in degraded mode");
            }
        } catch (IOException e) {
            bridgeStatus.markDegraded(e.getMessage(), "OpenCode MCP bootstrap write failed");
            log.warn("Failed to write managed OpenCode bootstrap files for external OpenCode: {}", e.getMessage(), e);
        }
    }

    private int serverPort(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext web
            && web.getWebServer() != null) {
            return web.getWebServer().getPort();
        }
        return Integer.parseInt(event.getApplicationContext().getEnvironment().getProperty("server.port", "8080"));
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
