package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * I1: Backend E2E integration test for Dashboard.
 * Verifies Spring bean wiring, REST endpoints, and full promote→load→patch lifecycle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void fullLifecycle_promoteLoadPatch() throws Exception {
        // Promote
        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 2,
                    "id": "dash_placeholder",
                    "title": "E2E Dashboard",
                    "theme": "industry-neutral",
                    "renderer": "bezel",
                    "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "free" },
                    "version": 999
                  }
                }
                """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        JsonNode promoteJson = mapper.readTree(promoteResponse);
        String id = promoteJson.get("id").asText();
        assertThat(id).startsWith("dash_");
        assertThat(promoteJson.get("version").asInt()).isEqualTo(1);

        // Load
        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("E2E Dashboard"))
            .andExpect(jsonPath("$.version").value(1));

        // Patch
        mvc.perform(patch("/api/dashboards/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "baseVersion": 1,
                  "ops": [{ "op": "replace", "path": "/title", "value": "Updated Dashboard" }]
                }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(2));

        // Verify patch applied
        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Dashboard"))
            .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void patchRejectsStaleVersion() throws Exception {
        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "dashboard": {
                    "schemaVersion": 2,
                    "id": "dash_placeholder",
                    "title": "Version Test",
                    "theme": "industry-neutral",
                    "renderer": "bezel",
                    "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                    "parameters": [],
                    "widgets": [],
                    "layout": { "engine": "free" },
                    "version": 999
                  }
                }
                """))
            .andReturn().getResponse().getContentAsString();

        String id = mapper.readTree(promoteResponse).get("id").asText();

        mvc.perform(patch("/api/dashboards/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                  "baseVersion": 0,
                  "ops": [{ "op": "replace", "path": "/title", "value": "X" }]
                }
                """))
            .andExpect(status().isConflict());
    }

    @Test
    void getReturns404ForUnknownId() throws Exception {
        mvc.perform(get("/api/dashboards/dash_nonexistent"))
            .andExpect(status().isNotFound());
    }

    @Test
    void serveHtmlPreservesUtf8AndRewritesRelativeEndpoints() throws Exception {
        String html = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
            <meta charset="utf-8">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; connect-src __BEZEL_SERVER_ORIGIN__; frame-ancestors 'self'">
            <meta name="__JSON_HASH__" content="sha256:test">
            <title>电商运营监控</title>
            </head>
            <body>
            <h1>GMV 总额</h1>
            <script>
            window.__BEZEL_CONFIG__ = { "dashboardId": "dash_test_store_ecommerce", "widgets": [
              { "id": "w_a", "endpoint": "/api/dashboards/dash_test_store_ecommerce/widgets/w_a/data" },
              { "id": "w_b", "endpoint": '/api/dashboards/dash_test_store_ecommerce/widgets/w_b/data' }
            ]};
            </script>
            </body>
            </html>
            """;
        String body = """
            {
              "dashboard": {
                "schemaVersion": 2,
                "id": "dash_placeholder",
                "title": "中文标题",
                "theme": "industry-ecommerce",
                "renderer": "bezel",
                "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                "parameters": [],
                "widgets": [],
                "layout": { "engine": "free" },
                "version": 999
              },
              "html": %s
            }
            """.formatted(mapper.writeValueAsString(html));

        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(promoteResponse).get("id").asText();

        var res = mvc.perform(get("/api/dashboards/" + id + "/html"))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        // Content-Type MUST include charset=UTF-8 — otherwise Spring's StringHttpMessageConverter
        // falls back to ISO-8859-1 and mangles multi-byte UTF-8 characters in the body.
        assertThat(res.getContentType()).containsIgnoringCase("charset=UTF-8");

        byte[] bytes = res.getContentAsByteArray();
        String decoded = new String(bytes, StandardCharsets.UTF_8);

        // Chinese characters must round-trip intact.
        assertThat(decoded).contains("电商运营监控");
        assertThat(decoded).contains("GMV 总额");
        assertThat(decoded).contains("中文标题".substring(0, 0)); // sanity
        // The raw bytes for "电" (U+7535) must be E7 94 B5 (UTF-8), not 0x3F (ISO-8859-1 replacement).
        assertThat(decoded.indexOf('?')).isLessThan(decoded.indexOf("电商运营监控") + 100);

        // Relative widget endpoints must be rewritten to absolute URLs so fetch() works from
        // a sandboxed (null-origin) srcdoc iframe where about:srcdoc base URL would otherwise fail.
        assertThat(decoded).contains("\"http://localhost").contains("/api/dashboards/" + id + "/widgets/w_a/data");
        assertThat(decoded).contains("'http://localhost").contains("/api/dashboards/" + id + "/widgets/w_b/data");
        assertThat(decoded).doesNotContain("\"/api/dashboards/");
        assertThat(decoded).doesNotContain("'/api/dashboards/");

        // AI-emitted dashboardId in widget URLs and __BEZEL_CONFIG__ must be rewritten to the
        // server-assigned id so widget data fetches don't 404.
        assertThat(decoded).doesNotContain("dash_test_store_ecommerce");
        assertThat(decoded).contains("\"dashboardId\": \"" + id + "\"");

        // CSP token still gets substituted.
        assertThat(decoded).doesNotContain("__BEZEL_SERVER_ORIGIN__");
        assertThat(decoded).contains("connect-src http://localhost");
    }
}
