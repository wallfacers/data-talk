package com.datatalk.controller;

import com.datatalk.application.opencode.OpenCodeBridgeStatus;
import com.datatalk.service.QueryApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QueryControllerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-24T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void healthReturnsOpenCodeBridgeSnapshot() {
        OpenCodeBridgeStatus bridgeStatus = new OpenCodeBridgeStatus(clock);
        bridgeStatus.markDegraded("plugin not loaded", "OpenCode MCP bridge degraded");
        QueryController controller = new QueryController(mock(QueryApplicationService.class), bridgeStatus);

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .containsEntry("status", "degraded")
            .containsEntry("timestamp", "2026-04-24T00:00:00Z")
            .containsEntry("message", "OpenCode MCP bridge degraded")
            .containsEntry("reason", "plugin not loaded");
    }
}
