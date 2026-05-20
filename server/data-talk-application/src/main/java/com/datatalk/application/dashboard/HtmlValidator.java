package com.datatalk.application.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates compiled dashboard HTML for security and structural correctness.
 * Merges and supersedes the old BezelHtmlValidator.
 */
public final class HtmlValidator {

    private static final Pattern CSP_META = Pattern.compile(
        "<meta\\s+http-equiv\\s*=\\s*[\"']Content-Security-Policy[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEZEL_ORIGIN = Pattern.compile("__BEZEL_SERVER_ORIGIN__");
    private static final Pattern BEZEL_CONFIG = Pattern.compile(
        "<script\\s+type\\s*=\\s*[\"']application/json[\"']\\s+id\\s*=\\s*[\"']__BEZEL_CONFIG__[\"']");
    private static final Pattern JSON_HASH_META = Pattern.compile(
        "<meta\\s+name\\s*=\\s*[\"']bezel-json-hash[\"']", Pattern.CASE_INSENSITIVE);
    // Scheduler script reference: must load external scheduler.js from bezel server
    private static final Pattern SCHEDULER_SCRIPT = Pattern.compile(
        "<script[^>]+src\\s*=\\s*[\"'][^\"]*scheduler\\.js[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIG_TYPE = Pattern.compile(
        "widgets[\"']?\\s*:\\s*\\[\\s*\\]|[\"']?type[\"']?\\s*:\\s*[\"'](?:chart|kpi|table|markdown|filter|section|divider|image)[\"']");

    private static final Pattern INLINE_EVENT = Pattern.compile("<[^>]+\\bon[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("<(script|style)[^>]*>.*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UNSAFE_EVAL = Pattern.compile("unsafe-eval");
    private static final Pattern WILDCARD_DEFAULT = Pattern.compile("default-src[^;]*\\*");
    private static final Pattern FRAME_ANCESTORS = Pattern.compile("frame-ancestors\\s+");

    private static final Pattern SCRIPT_SRC = Pattern.compile(
        "<script[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    // Chart widget baseOption fingerprint: a chart config entry must have non-null baseOption
    private static final Pattern CHART_BASE_OPTION_FINGERPRINT = Pattern.compile(
        "\"type\"\\s*:\\s*\"chart\"[^}]*\"baseOption\"\\s*:\\s*\\{");

    public record ValidationResult(boolean ok, List<String> errors) {}

    public static ValidationResult validate(String html) {
        List<String> errors = new ArrayList<>();

        // Required patterns
        if (!CSP_META.matcher(html).find()) errors.add("missing required: csp_meta");
        if (!BEZEL_ORIGIN.matcher(html).find()) errors.add("missing required: bezel_origin_placeholder");
        if (!JSON_HASH_META.matcher(html).find()) errors.add("missing required: json_hash_meta");
        if (!BEZEL_CONFIG.matcher(html).find()) errors.add("missing required: bezel_config");
        if (!SCHEDULER_SCRIPT.matcher(html).find()) errors.add("missing required: scheduler_script");
        if (!CONFIG_TYPE.matcher(html).find()) errors.add("missing required: widget_config_type_field");

        // Forbidden patterns (strip script/style content to avoid JS false positives)
        String htmlWithoutScripts = SCRIPT_OR_STYLE.matcher(html).replaceAll("");
        if (INLINE_EVENT.matcher(htmlWithoutScripts).find()) errors.add("forbidden: inline_event_handler");
        if (UNSAFE_EVAL.matcher(html).find()) errors.add("forbidden: unsafe_eval");
        if (WILDCARD_DEFAULT.matcher(html).find()) errors.add("forbidden: wildcard_in_default_src");
        if (FRAME_ANCESTORS.matcher(html).find()) errors.add("forbidden: frame_ancestors_in_meta");

        // External script whitelist
        var m = SCRIPT_SRC.matcher(html);
        while (m.find()) {
            String src = m.group(1);
            if (!src.contains("__BEZEL_SERVER_ORIGIN__") && !src.startsWith("https://cdn.jsdelivr.net/")) {
                errors.add("non-whitelisted script src: " + src);
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private HtmlValidator() {}
}
