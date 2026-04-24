package com.datatalk.infra.opencode.process;

import com.datatalk.application.opencode.OpenCodeBridgeStatus;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.datatalk.infra.opencode.OpenCodeMcpProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenCodeBootstrapReconcilerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-24T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void reconcileExternalPatchesConfigAddsRuntimeMountAndMarksReady() throws Exception {
        OpenCodeBridgeStatus status = new OpenCodeBridgeStatus(clock);
        OpenCodeHttpClient client = mock(OpenCodeHttpClient.class);
        OpenCodeBootstrapReconciler reconciler = new OpenCodeBootstrapReconciler(writer(status), client, status);
        when(client.patchConfig(anyMap())).thenReturn(objectMapper.createObjectNode());
        when(client.addMcpServer(eq("datatalk"), anyMap())).thenReturn(objectMapper.createObjectNode());
        when(client.getMcpStatus()).thenReturn(objectMapper.readTree("""
            {"datatalk":{"status":"connected"}}
            """));

        OpenCodeBootstrapWriter.BootstrapArtifacts artifacts = reconciler.writeManagedConfig(8080);

        assertThat(reconciler.reconcileExternal(artifacts)).isTrue();

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).patchConfig(Map.of(
            "mcp", Map.of("datatalk", artifacts.mcpConfig())
        ));
        inOrder.verify(client).addMcpServer("datatalk", artifacts.mcpConfig());
        inOrder.verify(client).getMcpStatus();
        assertThat(status.snapshot().status()).isEqualTo("ok");
        assertThat(status.snapshot().message()).isEqualTo("OpenCode MCP bridge ready");
    }

    @Test
    void reconcileExternalMarksDegradedWhenRuntimeStatusIsNotConnected() throws Exception {
        OpenCodeBridgeStatus status = new OpenCodeBridgeStatus(clock);
        OpenCodeHttpClient client = mock(OpenCodeHttpClient.class);
        OpenCodeBootstrapReconciler reconciler = new OpenCodeBootstrapReconciler(writer(status), client, status);
        when(client.patchConfig(anyMap())).thenReturn(objectMapper.createObjectNode());
        when(client.addMcpServer(eq("datatalk"), anyMap())).thenReturn(objectMapper.createObjectNode());
        when(client.getMcpStatus()).thenReturn(objectMapper.readTree("""
            {"datatalk":{"status":"disconnected"}}
            """));

        OpenCodeBootstrapWriter.BootstrapArtifacts artifacts = reconciler.writeManagedConfig(8080);

        assertThat(reconciler.reconcileExternal(artifacts)).isFalse();
        assertThat(status.snapshot().status()).isEqualTo("degraded");
        assertThat(status.snapshot().reason()).contains("disconnected");
        verify(client).getMcpStatus();
    }

    private OpenCodeBootstrapWriter writer(OpenCodeBridgeStatus status) {
        return new OpenCodeBootstrapWriter(
            properties(),
            objectMapper,
            status,
            clock,
            () -> "Use datatalk_execute_sql"
        );
    }

    private OpenCodeMcpProperties properties() {
        OpenCodeMcpProperties properties = new OpenCodeMcpProperties();
        properties.setEnabled(true);
        properties.setConfigDir(tempDir.toString());
        return properties;
    }
}
