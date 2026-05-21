package com.datatalk.infra.channel;

import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SessionStatusControllerTest {

    private OpenCodeHttpClient httpClient;
    private OpenCodeSessionMap sessionMap;
    private SessionStatusController controller;
    private ObjectMapper om;

    @BeforeEach
    void setUp() {
        httpClient = Mockito.mock(OpenCodeHttpClient.class);
        sessionMap = new OpenCodeSessionMap();
        controller = new SessionStatusController(httpClient, sessionMap);
        om = new ObjectMapper();
    }

    @Test
    void returns_busy_when_session_mapped_and_opencode_reports_busy() throws Exception {
        sessionMap.bind("dt-1", "oc-1");
        JsonNode payload = om.readTree("""
            {"oc-1":{"type":"busy"},"oc-other":{"type":"retry"}}
            """);
        when(httpClient.getSessionStatuses()).thenReturn(payload);

        Map<String, String> status = controller.status("dt-1");

        assertThat(status).containsEntry("type", "busy");
    }

    @Test
    void returns_retry_when_opencode_reports_retry() throws Exception {
        sessionMap.bind("dt-2", "oc-2");
        JsonNode payload = om.readTree("""
            {"oc-2":{"type":"retry","attempt":1,"message":"x","next":5}}
            """);
        when(httpClient.getSessionStatuses()).thenReturn(payload);

        Map<String, String> status = controller.status("dt-2");

        assertThat(status).containsEntry("type", "retry");
    }

    @Test
    void returns_idle_when_session_not_mapped() {
        Map<String, String> status = controller.status("dt-unmapped");

        assertThat(status).containsEntry("type", "idle");
        Mockito.verify(httpClient, Mockito.never()).getSessionStatuses();
    }

    @Test
    void returns_idle_when_mapped_session_missing_from_opencode_map() throws Exception {
        sessionMap.bind("dt-3", "oc-3");
        when(httpClient.getSessionStatuses()).thenReturn(om.readTree("{}"));

        Map<String, String> status = controller.status("dt-3");

        assertThat(status).containsEntry("type", "idle");
    }

    @Test
    void returns_idle_when_opencode_returns_unknown_type() throws Exception {
        sessionMap.bind("dt-4", "oc-4");
        when(httpClient.getSessionStatuses()).thenReturn(om.readTree("""
            {"oc-4":{"type":"weird"}}
            """));

        Map<String, String> status = controller.status("dt-4");

        assertThat(status).containsEntry("type", "idle");
    }

    @Test
    void returns_idle_when_opencode_unreachable() {
        sessionMap.bind("dt-5", "oc-5");
        when(httpClient.getSessionStatuses()).thenReturn(om.createObjectNode());

        Map<String, String> status = controller.status("dt-5");

        assertThat(status).containsEntry("type", "idle");
    }
}
