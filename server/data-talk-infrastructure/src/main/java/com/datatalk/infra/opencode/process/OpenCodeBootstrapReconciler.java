package com.datatalk.infra.opencode.process;

import com.datatalk.application.opencode.OpenCodeBridgeStatus;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class OpenCodeBootstrapReconciler {

    private final OpenCodeBootstrapWriter writer;
    private final OpenCodeHttpClient client;
    private final OpenCodeBridgeStatus bridgeStatus;

    public OpenCodeBootstrapReconciler(OpenCodeBootstrapWriter writer,
                                       OpenCodeHttpClient client,
                                       OpenCodeBridgeStatus bridgeStatus) {
        this.writer = writer;
        this.client = client;
        this.bridgeStatus = bridgeStatus;
    }

    public OpenCodeBootstrapWriter.BootstrapArtifacts writeManagedConfig(int serverPort) throws IOException {
        return writer.write(serverPort);
    }

    public boolean reconcileExternal(OpenCodeBootstrapWriter.BootstrapArtifacts artifacts) {
        try {
            client.patchConfig(Map.of(
                "mcp", Map.of("datatalk", artifacts.mcpConfig())
            ));
            client.addMcpServer("datatalk", artifacts.mcpConfig());
            return probeRuntimeStatus();
        } catch (Exception e) {
            bridgeStatus.markDegraded(safeMessage(e), "OpenCode MCP reconcile failed");
            return false;
        }
    }

    public boolean probeRuntimeStatus() {
        try {
            JsonNode status = client.getMcpStatus();
            String runtimeStatus = status.path("datatalk").path("status").asText("");
            if ("connected".equalsIgnoreCase(runtimeStatus)) {
                bridgeStatus.markOk("OpenCode MCP bridge ready");
                return true;
            }

            String reason = runtimeStatus.isBlank()
                ? "datatalk MCP runtime status missing"
                : "datatalk MCP runtime status: " + runtimeStatus;
            bridgeStatus.markDegraded(reason, "OpenCode MCP bridge degraded");
            return false;
        } catch (Exception e) {
            bridgeStatus.markDegraded(safeMessage(e), "OpenCode MCP health probe failed");
            return false;
        }
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "unknown bootstrap error";
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage();
        }
        return error.getClass().getSimpleName();
    }
}
