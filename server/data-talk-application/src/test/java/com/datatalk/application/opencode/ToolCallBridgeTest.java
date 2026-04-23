package com.datatalk.application.opencode;

import com.datatalk.application.session.ActionDispatcher;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallBridgeTest {

    @Test
    void buildsContextAndDelegatesToDispatcher() {
        ActionDispatcher disp = mock(ActionDispatcher.class);
        OpenCodeSessionMap map = new OpenCodeSessionMap();
        map.bind("dt-1", "oc-1");

        when(disp.dispatch(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(Map.of("echoed", "hi")));

        ToolCallBridge bridge = new ToolCallBridge(disp, map);
        Object out = bridge.handle("datatalk.test.tool", "call-1", "oc-1",
            Map.of("text", "hi")).toCompletableFuture().join();

        assertThat(((Map<?,?>) out).get("echoed")).isEqualTo("hi");

        ArgumentCaptor<ActionContext> ctx = ArgumentCaptor.forClass(ActionContext.class);
        verify(disp).dispatch(eq("datatalk.test.tool"), eq(Map.of("text", "hi")),
            eq("call-1"), ctx.capture());
        assertThat(ctx.getValue().sessionId()).isEqualTo("dt-1");
        assertThat(ctx.getValue().openCodeSessionId()).isEqualTo("oc-1");
    }
}
