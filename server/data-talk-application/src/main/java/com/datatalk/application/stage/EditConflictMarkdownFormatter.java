package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders user/AI-friendly markdown bodies for stage edit conflict responses.
 */
public final class EditConflictMarkdownFormatter {

    private static final int TOTAL_HARD_CAP = 3_000;
    private static final int CODE_BLOCK_BUDGET = 1_150;
    private static final int HEAD_LINES = 8;
    private static final int TAIL_LINES = 8;
    private static final String TRUNCATED_SUFFIX = "\n...(truncated)...\n";

    private EditConflictMarkdownFormatter() {}

    public static String versionConflict(StageTab tab, int requestedBase, int actualVersion, long lastTouchedDeltaMs) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Edit failed: version drifted on tab `").append(tab.id()).append("`\n");
        markdown.append(tabLine(tab)).append('\n');
        markdown.append("Reason: version_conflict\n\n");
        markdown.append("Your `baseVersion=").append(requestedBase)
            .append("` does not match the current version=").append(actualVersion).append(".\n\n");
        markdown.append(hint(lastTouchedDeltaMs, actualVersion));
        markdown.append('\n');
        markdown.append(suggestedReadStep(tab.id()));
        return capTotal(markdown.toString());
    }

    public static String anchorNotFound(StageTab tab, int editIndex, String oldText,
                                        Integer requestedBase, int actualVersion, long lastTouchedDeltaMs) {
        boolean versionDrifted = requestedBase != null && requestedBase != actualVersion;
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Edit failed: no matching text on tab `").append(tab.id()).append("`\n");
        markdown.append(tabLine(tab)).append('\n');
        markdown.append("Reason: anchor_not_found\n\n");
        markdown.append("The `oldText` of your edit#").append(editIndex)
            .append(" was not found in the current content:\n");
        markdown.append(codeBlock(languageFor(tab.type()), truncateCodeBlock(normalize(oldText), HEAD_LINES, TAIL_LINES)));
        markdown.append('\n');
        markdown.append("Current version=").append(actualVersion).append(".\n\n");
        markdown.append(versionDrifted
            ? "Another session changed this tab " + humanizeDelta(lastTouchedDeltaMs)
                + " (version is now " + actualVersion + "); the text you anchored to may no longer exist. "
                + "Re-read the live content and base `oldText` on it."
            : "The snippet you provided does not appear in the editor — most likely whitespace or wording "
                + "that does not match the live text. Re-read the content and copy `oldText` from it verbatim "
                + "(indentation may differ; non-whitespace tokens must match).");
        markdown.append('\n');
        markdown.append(suggestedReadStep(tab.id()));
        return capTotal(markdown.toString());
    }

    public static String anchorAmbiguous(StageTab tab, int editIndex, int matchCount, int actualVersion) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Edit failed: ambiguous match on tab `").append(tab.id()).append("`\n");
        markdown.append(tabLine(tab)).append('\n');
        markdown.append("Reason: anchor_ambiguous\n\n");
        markdown.append("The `oldText` of your edit#").append(editIndex)
            .append(" matches ").append(matchCount).append(" locations in the current content.\n\n");
        markdown.append("Include more surrounding context in `oldText` so it identifies exactly one location, "
            + "or pass `hint.line` with the 1-based line of the intended match.");
        markdown.append('\n');
        markdown.append("Current version=").append(actualVersion).append(".\n\n");
        markdown.append(suggestedReadStep(tab.id()));
        return capTotal(markdown.toString());
    }

    public static String tabNotFound(String tabId) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Edit failed: tab not found\n");
        markdown.append("Tab: `").append(tabId).append("`\n");
        markdown.append("Reason: tab_not_found\n\n");
        markdown.append("It may have been trashed by another session. Use `datatalk_ui_find` to discover currently-existing tabs.\n\n");
        markdown.append("**Suggested next step:** use `datatalk_ui_find` or `datatalk_ui_read` only after confirming the tab still exists.\n");
        return capTotal(markdown.toString());
    }

    public static String tabArchived(StageTab tab) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Edit failed: tab is archived\n");
        markdown.append(tabLine(tab)).append('\n');
        markdown.append("Reason: tab_archived\n\n");
        markdown.append("Archived tabs are read-only. Call `workspace.archive(target=")
            .append(tab.id())
            .append(", archived=false)` first if you need to edit, then retry.\n");
        return capTotal(markdown.toString());
    }

    static String humanizeDelta(long deltaMs) {
        long safeDeltaMs = Math.max(0L, deltaMs);
        if (safeDeltaMs < 1_000L) {
            return "moments ago";
        }

        long seconds = safeDeltaMs / 1_000L;
        if (seconds < 60L) {
            return seconds + "s ago";
        }

        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes == 1L ? "a minute ago" : minutes + "m ago";
        }

        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours == 1L ? "an hour ago" : hours + "h ago";
        }

        long days = hours / 24L;
        return days == 1L ? "a day ago" : days + "d ago";
    }

    static String truncateCodeBlock(String text, int headLines, int tailLines) {
        String normalized = normalize(text);
        boolean trailingNewline = normalized.endsWith("\n");
        List<String> lines = splitRealLines(normalized, trailingNewline);
        if (normalized.length() <= CODE_BLOCK_BUDGET && lines.size() <= headLines + tailLines) {
            return normalized;
        }

        if (lines.size() <= headLines + tailLines) {
            return normalized.substring(0, Math.min(normalized.length(), CODE_BLOCK_BUDGET));
        }

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < headLines; i++) {
            truncated.append(lines.get(i)).append('\n');
        }
        truncated.append("... ").append(lines.size() - headLines - tailLines)
            .append(" lines elided. Read with datatalk_ui_read for full content. ...\n");
        for (int i = Math.max(headLines, lines.size() - tailLines); i < lines.size(); i++) {
            truncated.append(lines.get(i));
            if (i < lines.size() - 1 || trailingNewline) {
                truncated.append('\n');
            }
        }

        String result = truncated.toString();
        if (result.length() <= CODE_BLOCK_BUDGET) {
            return result;
        }
        return result.substring(0, CODE_BLOCK_BUDGET);
    }

    private static String tabLine(StageTab tab) {
        return "Tab: `" + escapeInline(tab.id()) + "` (" + escapeInline(tab.title()) + ")";
    }

    private static String hint(long deltaMs, int actualVersion) {
        return "Likely cause: another session edited this tab " + humanizeDelta(deltaMs)
            + "; version is now " + actualVersion + ".";
    }

    private static String suggestedReadStep(String tabId) {
        return "**Suggested next step:** call `datatalk_ui_read({ object: \"query_editor\", target: \"" +
            escapeJson(tabId) + "\", mode: \"state\" })` and re-plan the edit against the latest content.\n";
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String languageFor(String tabType) {
        return switch (tabType == null ? "" : tabType) {
            case "query_editor" -> "sql";
            case "markdown_note" -> "markdown";
            default -> "text";
        };
    }

    private static String codeBlock(String language, String body) {
        String fence = codeFence(body);
        StringBuilder block = new StringBuilder();
        block.append(fence).append(language).append('\n');
        block.append(body);
        if (!body.endsWith("\n")) {
            block.append('\n');
        }
        block.append(fence).append('\n');
        return block.toString();
    }

    private static String capTotal(String markdown) {
        if (markdown.length() <= TOTAL_HARD_CAP) {
            return markdown;
        }
        int keep = TOTAL_HARD_CAP - TRUNCATED_SUFFIX.length();
        if (keep <= 0) {
            return TRUNCATED_SUFFIX.substring(0, TOTAL_HARD_CAP);
        }
        return markdown.substring(0, keep) + TRUNCATED_SUFFIX;
    }

    private static List<String> splitRealLines(String normalized, boolean trailingNewline) {
        String[] rawLines = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>(List.of(rawLines));
        if (trailingNewline && !lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static String codeFence(String body) {
        int longestRun = 0;
        int currentRun = 0;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '`') {
                currentRun += 1;
                longestRun = Math.max(longestRun, currentRun);
            } else {
                currentRun = 0;
            }
        }
        return "`".repeat(Math.max(3, longestRun + 1));
    }

    private static String escapeInline(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace("```", "'''")
            .replace('`', '\'');
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }
}
