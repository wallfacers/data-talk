package com.datatalk.adapter.controller;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.dto.SessionDataContextUpdateRequest;
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
import static org.hamcrest.Matchers.containsString;
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

    @Autowired
    ConnectionService connections;

    @Autowired
    SessionRepository sessions;

    @Autowired
    SessionDataContextService sessionDataContexts;

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
    void promoteEnrichesDefaultDatabaseSchemaFromSessionHeader() throws Exception {
        // Reproduces BUG: AI emits only defaultConnectionId. On a connection without a
        // configured databaseName that exposes multiple databases, widget SQL like
        // `SELECT ... FROM users` later trips TableContextAutoResolver's
        // multi-candidate guard. The server-side promote enrichment uses the chat
        // session's data-context (passed via X-DataTalk-Session-Id) to fill in the
        // missing fields BEFORE the dashboard is persisted.
        String suffix = Long.toString(System.nanoTime());
        String connectionId = connections.create(
            "enrich-conn-" + suffix, "h2", "localhost", 0,
            "<unused-database-name>", "sa", "", 3000,
            null, null, null, null, null, null, null, null);
        String sessionId = "promote-enrich-sess-" + suffix;
        sessions.upsert(new SessionRecord(sessionId, connectionId, "promote enrich", false, null, 1L, 1L, false));
        sessionDataContexts.set(sessionId, new SessionDataContextUpdateRequest(
            connectionId, "test_store", "public", "schema"
        ));

        String body = """
            {
              "dashboard": {
                "schemaVersion": 2,
                "id": "dash_placeholder",
                "title": "Enrichment Test",
                "theme": "industry-neutral",
                "renderer": "bezel",
                "defaultConnectionId": "%s",
                "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                "parameters": [],
                "widgets": [],
                "layout": { "engine": "free" },
                "version": 999
              }
            }
            """.formatted(connectionId);

        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .header(DashboardController.SESSION_ID_HEADER, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(promoteResponse).get("id").asText();

        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaultConnectionId").value(connectionId))
            .andExpect(jsonPath("$.defaultDatabase").value("test_store"))
            .andExpect(jsonPath("$.defaultSchema").value("public"));
    }

    @Test
    void promoteWithoutSessionHeaderSkipsEnrichment() throws Exception {
        // No header → no enrichment, defaults remain null/absent. Guards against the
        // backstop overreaching and silently rewriting AI-emitted JSON when the
        // client deliberately didn't supply a session context.
        String body = """
            {
              "dashboard": {
                "schemaVersion": 2,
                "id": "dash_placeholder",
                "title": "No Session Header",
                "theme": "industry-neutral",
                "renderer": "bezel",
                "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                "parameters": [],
                "widgets": [],
                "layout": { "engine": "free" },
                "version": 999
              }
            }
            """;

        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(promoteResponse).get("id").asText();

        JsonNode dash = mapper.readTree(
            mvc.perform(get("/api/dashboards/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(dash.path("defaultDatabase").isMissingNode() || dash.path("defaultDatabase").isNull())
            .as("defaultDatabase should remain unset without session header")
            .isTrue();
        assertThat(dash.path("defaultSchema").isMissingNode() || dash.path("defaultSchema").isNull())
            .as("defaultSchema should remain unset without session header")
            .isTrue();
    }

    @Test
    void widgetDataAcceptsNullOriginCorsPreflightForSandboxedIframe() throws Exception {
        // The bezel dashboard iframe uses sandbox="allow-scripts" (no allow-same-origin),
        // which gives it a null origin. Browsers refuse to send POSTs there without a
        // successful CORS preflight. The global CorsFilter on /api/** does not allow
        // null origin, so a separate credential-less mapping must claim this path.
        mvc.perform(options("/api/dashboards/dash_anything/widgets/w_x/data")
                .header("Origin", "null")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "null"))
            .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void promoteEnrichmentDoesNotOverrideExplicitJsonValues() throws Exception {
        // When the AI did set defaultDatabase explicitly, the backstop must not
        // clobber it — explicit JSON wins.
        String suffix = Long.toString(System.nanoTime());
        String connectionId = connections.create(
            "explicit-conn-" + suffix, "h2", "localhost", 0,
            "<unused>", "sa", "", 3000,
            null, null, null, null, null, null, null, null);
        String sessionId = "promote-explicit-sess-" + suffix;
        sessions.upsert(new SessionRecord(sessionId, connectionId, "explicit", false, null, 1L, 1L, false));
        sessionDataContexts.set(sessionId, new SessionDataContextUpdateRequest(
            connectionId, "session_db", "session_schema", "schema"
        ));

        String body = """
            {
              "dashboard": {
                "schemaVersion": 2,
                "id": "dash_placeholder",
                "title": "Explicit Wins",
                "theme": "industry-neutral",
                "renderer": "bezel",
                "defaultConnectionId": "%s",
                "defaultDatabase": "ai_picked_db",
                "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                "parameters": [],
                "widgets": [],
                "layout": { "engine": "free" },
                "version": 999
              }
            }
            """.formatted(connectionId);

        String promoteResponse = mvc.perform(post("/api/dashboards/promote")
                .header(DashboardController.SESSION_ID_HEADER, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(promoteResponse).get("id").asText();

        mvc.perform(get("/api/dashboards/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaultDatabase").value("ai_picked_db"))
            .andExpect(jsonPath("$.defaultSchema").value("session_schema"));
    }

    @Test
    void serveHtmlPreservesUtf8AndRewritesRelativeEndpoints() throws Exception {
        String html = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
            <meta charset="utf-8">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; connect-src __BEZEL_SERVER_ORIGIN__">
            <meta name="__JSON_HASH__" content="sha256:test">
            <title>电商运营监控</title>
            </head>
            <body>
            <h1>GMV 总额</h1>
            <script>
            window.__BEZEL_CONFIG__ = { "dashboardId": "dash_test_store_ecommerce", "widgets": [
              { "id": "w_a", "type": "chart", "endpoint": "/api/dashboards/dash_test_store_ecommerce/widgets/w_a/data", "baseOption": {} },
              { "id": "w_b", "type": "chart", "endpoint": '/api/dashboards/dash_test_store_ecommerce/widgets/w_b/data', "baseOption": {} }
            ]};
            </script>
            <script>(function(){cfg.widgets.forEach(function(w){if(w.type === 'chart'){}});})();</script>
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

    @Test
    void serveHtmlRewritesEchartsCdnToLocalAsset() throws Exception {
        // AI-emitted HTML pulls echarts from jsdelivr (the only CDN whitelisted by
        // BezelHtmlValidator). In sandboxed iframes on a slow/offline link this
        // stalls the iframe for many seconds, producing a long blank screen.
        // serveHtml must rewrite the URL in BOTH the CSP and the <script src> so the
        // iframe loads the same build from the local /bezel/ static path instead.
        String html = """
            <!doctype html>
            <html>
            <head>
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js 'unsafe-inline'; connect-src __BEZEL_SERVER_ORIGIN__">
            <meta name="__JSON_HASH__" content="sha256:cdn">
            <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
            </head>
            <body>
            <script>window.__BEZEL_CONFIG__ = { "dashboardId": "dash_cdn_seed", "widgets": [] };</script>
            </body>
            </html>
            """;
        String body = """
            {
              "dashboard": {
                "schemaVersion": 2,
                "id": "dash_placeholder",
                "title": "cdn",
                "theme": "industry-neutral",
                "renderer": "bezel",
                "refresh": { "defaultIntervalMs": 10000, "pauseOnHidden": true },
                "parameters": [],
                "widgets": [],
                "layout": { "engine": "free" },
                "version": 1
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
        String decoded = new String(res.getContentAsByteArray(), StandardCharsets.UTF_8);

        // jsdelivr URL must be gone in both occurrences (script tag and CSP).
        assertThat(decoded).doesNotContain("https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js");
        // Both occurrences must point at the local origin-relative path.
        assertThat(decoded).contains("script-src http://localhost").contains("/bezel/echarts.min.js");
        assertThat(decoded).contains("<script src=\"http://localhost").contains("/bezel/echarts.min.js\"");
    }
}
