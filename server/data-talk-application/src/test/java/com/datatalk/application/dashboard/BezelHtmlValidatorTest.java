package com.datatalk.application.dashboard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BezelHtmlValidatorTest {

    private static final String OK_HTML = """
        <!doctype html><html><head>
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://cdn.jsdelivr.net; connect-src __BEZEL_SERVER_ORIGIN__">
        <meta name="__JSON_HASH__" content="sha256:abc">
        <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
        </head><body><div id="w1"></div>
        <script>window.__BEZEL_CONFIG__={dashboardId:'x',widgets:[]};</script>
        </body></html>
        """;

    @Test void passesOnCompliantHtml() {
        var r = BezelHtmlValidator.validate(OK_HTML);
        assertThat(r.ok()).isTrue();
        assertThat(r.errors()).isEmpty();
    }

    @Test void failsWhenCspMetaMissing() {
        var r = BezelHtmlValidator.validate(OK_HTML.replace("<meta http-equiv=\"Content-Security-Policy\"", "<meta name=\"x\""));
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("missing required: csp_meta"));
    }

    @Test void failsWhenServerOriginPlaceholderMissing() {
        var r = BezelHtmlValidator.validate(OK_HTML.replace("__BEZEL_SERVER_ORIGIN__", "http://evil"));
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("bezel_origin_placeholder"));
    }

    @Test void failsOnInlineEventHandler() {
        var bad = OK_HTML.replace("<body>", "<body onclick=\"alert(1)\">");
        var r = BezelHtmlValidator.validate(bad);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("forbidden present: inline_event_handler"));
    }

    @Test void failsOnNonWhitelistedScriptSrc() {
        var bad = OK_HTML.replace("https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js",
                                  "https://evil.example.com/x.js");
        var r = BezelHtmlValidator.validate(bad);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("non-whitelisted script src"));
    }
}
