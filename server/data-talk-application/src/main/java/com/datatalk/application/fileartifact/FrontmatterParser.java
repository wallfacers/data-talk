package com.datatalk.application.fileartifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal frontmatter extractor for watcher-created artifacts.
 */
public final class FrontmatterParser {

    public static final int MAX_HEAD_BYTES = 8 * 1024;

    private FrontmatterParser() {
    }

    public static Map<String, String> parse(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        Flavor flavor = flavorFor(file.getFileName().toString().toLowerCase(Locale.ROOT));
        if (flavor == Flavor.UNKNOWN) {
            return Map.of();
        }
        String head;
        try (InputStream in = Files.newInputStream(file)) {
            head = new String(in.readNBytes(MAX_HEAD_BYTES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Map.of();
        }
        return switch (flavor) {
            case MARKDOWN_TEXT -> parseMarkdown(head);
            case SQL -> parseSql(head);
            case UNKNOWN -> Map.of();
        };
    }

    public static boolean isArtifactDeclared(Map<String, String> parsed) {
        String value = parsed.get("artifact");
        if (value == null) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            default -> false;
        };
    }

    private enum Flavor {
        MARKDOWN_TEXT,
        SQL,
        UNKNOWN
    }

    private static Flavor flavorFor(String lowerName) {
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown") || lowerName.endsWith(".txt")) {
            return Flavor.MARKDOWN_TEXT;
        }
        if (lowerName.endsWith(".sql")) {
            return Flavor.SQL;
        }
        return Flavor.UNKNOWN;
    }

    private static Map<String, String> parseMarkdown(String head) {
        String text = stripBom(head);
        if (!text.startsWith("---")) {
            return Map.of();
        }
        int firstLine = text.indexOf('\n');
        if (firstLine < 0) {
            return Map.of();
        }
        String body = text.substring(firstLine + 1);
        int closing = indexOfLine(body, "---");
        if (closing < 0) {
            return Map.of();
        }
        return parseKeyValueLines(body.substring(0, closing));
    }

    private static Map<String, String> parseSql(String head) {
        String text = stripBom(head);
        StringBuilder yaml = new StringBuilder();
        boolean insideFence = false;
        boolean sawFence = false;
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.stripTrailing();
            if (line.isBlank()) {
                if (insideFence) {
                    yaml.append('\n');
                    continue;
                }
                break;
            }
            if (!line.startsWith("--")) {
                break;
            }
            String content = line.substring(2).stripLeading();
            if (content.equals("---")) {
                if (!insideFence) {
                    insideFence = true;
                    sawFence = true;
                    continue;
                }
                return parseKeyValueLines(yaml.toString());
            }
            yaml.append(content).append('\n');
        }
        return sawFence ? Map.of() : parseKeyValueLines(yaml.toString());
    }

    private static Map<String, String> parseKeyValueLines(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String rawLine : body.split("\n", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!key.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '\uFEFF' ? s.substring(1) : s;
    }

    private static int indexOfLine(String body, String marker) {
        int idx = 0;
        while (idx < body.length()) {
            int eol = body.indexOf('\n', idx);
            String line = eol < 0 ? body.substring(idx) : body.substring(idx, eol);
            if (line.strip().equals(marker)) {
                return idx;
            }
            if (eol < 0) {
                return -1;
            }
            idx = eol + 1;
        }
        return -1;
    }
}
