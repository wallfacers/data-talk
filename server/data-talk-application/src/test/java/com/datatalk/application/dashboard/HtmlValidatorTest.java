package com.datatalk.application.dashboard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlValidatorTest {

    private static final String VALID_HTML = """
        <!DOCTYPE html><html><head>
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src __BEZEL_SERVER_ORIGIN__">
        <meta name="bezel-json-hash" content="abc123">
        </head><body>
        <script type="application/json" id="__BEZEL_CONFIG__">{"widgets":[]}</script>
        <script src="__BEZEL_SERVER_ORIGIN__/bezel/scheduler.js"></script>
        </body></html>
        """;

    @Test
    void validHtml_passes() {
        HtmlValidator.ValidationResult result = HtmlValidator.validate(VALID_HTML);
        assertThat(result.ok()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void missingCspMeta_fails() {
        String html = """
            <!DOCTYPE html><html><head>
            <meta name="bezel-json-hash" content="abc123">
            </head><body>
            <script type="application/json" id="__BEZEL_CONFIG__">{"widgets":[]}</script>
            <script src="__BEZEL_SERVER_ORIGIN__/bezel/scheduler.js"></script>
            </body></html>
            """;

        HtmlValidator.ValidationResult result = HtmlValidator.validate(html);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).contains("missing required: csp_meta");
    }

    @Test
    void missingBezelServerOrigin_fails() {
        String html = """
            <!DOCTYPE html><html><head>
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self'">
            <meta name="bezel-json-hash" content="abc123">
            </head><body>
            <script type="application/json" id="__BEZEL_CONFIG__">{"widgets":[]}</script>
            <script src="/bezel/scheduler.js"></script>
            </body></html>
            """;

        HtmlValidator.ValidationResult result = HtmlValidator.validate(html);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).contains("missing required: bezel_origin_placeholder");
    }

    @Test
    void inlineEventHandler_rejected() {
        String html = VALID_HTML.replace(
            "</body>",
            "<div onclick=\"alert('x')\"></div></body>");

        HtmlValidator.ValidationResult result = HtmlValidator.validate(html);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).contains("forbidden: inline_event_handler");
    }

    @Test
    void unsafeEval_rejected() {
        String html = VALID_HTML.replace(
            "script-src __BEZEL_SERVER_ORIGIN__",
            "script-src __BEZEL_SERVER_ORIGIN__ 'unsafe-eval'");

        HtmlValidator.ValidationResult result = HtmlValidator.validate(html);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).contains("forbidden: unsafe_eval");
    }

    @Test
    void wildcardInDefaultSrc_rejected() {
        String html = VALID_HTML.replace(
            "default-src 'none'",
            "default-src *");

        HtmlValidator.ValidationResult result = HtmlValidator.validate(html);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).contains("forbidden: wildcard_in_default_src");
    }

    @Test
    void nonWhitelistedScriptSrc_rejected() {
        String html = VALID_HTML.replace(
            "</body>",
            "<script src=\"https://evil.example.com/payload.js\"></script></body>");

        HtmlValidator.ValidationResult result = HtmlValidator.validate(html);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors().stream()
            .anyMatch(e -> e.startsWith("non-whitelisted script src:")))
            .isTrue();
    }

    @Test
    void whitelistedJsdCdnScriptSrc_passes() {
        String html = VALID_HTML.replace(
            "</body>",
            "<script src=\"https://cdn.jsdelivr.net/echarts@5/echarts.min.js\"></script></body>");

        HtmlValidator.ValidationResult result = HtmlValidator.validate(html);
        assertThat(result.ok()).isTrue();
    }

    @Test
    void frameAncestors_rejected() {
        String html = VALID_HTML.replace(
            "script-src __BEZEL_SERVER_ORIGIN__",
            "script-src __BEZEL_SERVER_ORIGIN__; frame-ancestors 'none'");

        HtmlValidator.ValidationResult result = HtmlValidator.validate(html);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).contains("forbidden: frame_ancestors_in_meta");
    }

    @Test
    void jsOnReadyStateChange_notRejected() {
        String html = VALID_HTML.replace(
            "</body>",
            "<script>var xhr = new XMLHttpRequest(); xhr.onreadystatechange = function() {};</script></body>");

        HtmlValidator.ValidationResult result = HtmlValidator.validate(html);
        assertThat(result.ok()).isTrue();
    }
}
