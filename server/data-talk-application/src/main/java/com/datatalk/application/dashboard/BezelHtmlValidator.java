package com.datatalk.application.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class BezelHtmlValidator {

    private static final List<String> ALLOWED_CDN = List.of("https://cdn.jsdelivr.net/");

    private static final List<Required> REQUIRED = List.of(
        new Required("csp_meta", Pattern.compile("<meta\\s+http-equiv\\s*=\\s*[\"']Content-Security-Policy[\"']", Pattern.CASE_INSENSITIVE)),
        new Required("bezel_origin_placeholder", Pattern.compile("__BEZEL_SERVER_ORIGIN__")),
        new Required("json_hash_meta", Pattern.compile("<meta\\s+name\\s*=\\s*[\"']__JSON_HASH__[\"']", Pattern.CASE_INSENSITIVE)),
        new Required("bezel_config", Pattern.compile("window\\.__BEZEL_CONFIG__\\s*="))
    );

    // frame-ancestors is NOT required — browsers ignore it in <meta> tags;
    // the iframe is already sandboxed by the host page.

    private static final List<Forbidden> FORBIDDEN = List.of(
        new Forbidden("inline_event_handler", Pattern.compile("\\bon[a-z]+\\s*=", Pattern.CASE_INSENSITIVE)),
        new Forbidden("unsafe_eval", Pattern.compile("unsafe-eval")),
        new Forbidden("wildcard_in_default_src", Pattern.compile("default-src[^;]*\\*")),
        new Forbidden("frame_ancestors_in_meta", Pattern.compile("frame-ancestors\\s+"))
    );

    private static final Pattern SCRIPT_SRC = Pattern.compile("<script[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public record Result(boolean ok, List<String> errors) {}
    private record Required(String name, Pattern pat) {}
    private record Forbidden(String name, Pattern pat) {}

    public static Result validate(String html) {
        List<String> errors = new ArrayList<>();
        for (Required r : REQUIRED) {
            if (!r.pat.matcher(html).find()) errors.add("missing required: " + r.name);
        }
        for (Forbidden f : FORBIDDEN) {
            if (f.pat.matcher(html).find()) errors.add("forbidden present: " + f.name);
        }
        var m = SCRIPT_SRC.matcher(html);
        while (m.find()) {
            String src = m.group(1);
            boolean ok = false;
            for (String p : ALLOWED_CDN) if (src.startsWith(p)) { ok = true; break; }
            if (!ok) errors.add("non-whitelisted script src: " + src);
        }
        return new Result(errors.isEmpty(), errors);
    }

    private BezelHtmlValidator() {}
}
