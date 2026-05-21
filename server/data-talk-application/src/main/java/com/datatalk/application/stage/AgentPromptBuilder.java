package com.datatalk.application.stage;

import com.datatalk.application.connection.ActiveConnectionSummaryProvider;
import com.datatalk.application.history.SqlExecutionHistoryProvider;
import com.datatalk.application.history.SqlExecutionRecord;
import com.datatalk.application.semantic.SemanticModelDigester;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders dynamic placeholders in the AGENTS.md template:
 *   {{STAGE_TAB_DIGEST}}, {{ACTIVE_SESSION_DIR}}, {{SEMANTIC_MODEL_DIGEST}},
 *   {{ACTIVE_CONNECTION_SUMMARY}}, {{RECENT_FAILED_QUERIES_DIGEST}}.
 */
@Component
public class AgentPromptBuilder {
    private static final String PLACEHOLDER_STAGE_DIGEST = "{{STAGE_TAB_DIGEST}}";
    private static final String PLACEHOLDER_ACTIVE_DIR = "{{ACTIVE_SESSION_DIR}}";
    private static final String PLACEHOLDER_SEMANTIC_DIGEST = "{{SEMANTIC_MODEL_DIGEST}}";
    private static final String PLACEHOLDER_ACTIVE_CONNECTION_SUMMARY = "{{ACTIVE_CONNECTION_SUMMARY}}";
    private static final String PLACEHOLDER_RECENT_FAILED_QUERIES_DIGEST = "{{RECENT_FAILED_QUERIES_DIGEST}}";
    private static final String NO_ACTIVE_SENTINEL = "<no active session>";
    private static final String NO_ACTIVE_CONNECTION_SENTINEL = "<no active connection>";
    private static final String NO_RECENT_FAILURES_SENTINEL = "<no recent failures>";
    private static final int MAX_TABS = 10;
    private static final int MAX_TITLE_CHARS = 80;
    private static final int MAX_RENDERED_CHARS = 1_500;
    private static final int MAX_FAILED_QUERIES = 3;
    private static final int MAX_SQL_PREVIEW_CHARS = 200;
    private static final int MAX_ERROR_MESSAGE_CHARS = 160;
    private static final ObjectMapper OM = new ObjectMapper();

    private final StageTabRepository repo;
    private final SessionTitleLookup lookup;
    private final ActiveSessionDirProvider activeDir;
    private final SemanticModelDigester semanticDigester;
    private final ConnectionIdProvider connectionIdProvider;
    private final ActiveConnectionSummaryProvider connectionSummaryProvider;
    private final SqlExecutionHistoryProvider executionHistoryProvider;

    @Autowired
    public AgentPromptBuilder(StageTabRepository repo,
                              SessionTitleLookup lookup,
                              ActiveSessionDirProvider activeDir,
                              SemanticModelDigester semanticDigester,
                              ConnectionIdProvider connectionIdProvider,
                              ActiveConnectionSummaryProvider connectionSummaryProvider,
                              SqlExecutionHistoryProvider executionHistoryProvider) {
        this.repo = repo;
        this.lookup = lookup;
        this.activeDir = activeDir;
        this.semanticDigester = semanticDigester;
        this.connectionIdProvider = connectionIdProvider;
        this.connectionSummaryProvider = connectionSummaryProvider;
        this.executionHistoryProvider = executionHistoryProvider;
    }

    /** @deprecated kept for backward compatibility with existing wiring. */
    @Deprecated
    public AgentPromptBuilder(StageTabRepository repo,
                              SessionTitleLookup lookup,
                              ActiveSessionDirProvider activeDir,
                              SemanticModelDigester semanticDigester,
                              ConnectionIdProvider connectionIdProvider) {
        this(repo, lookup, activeDir, semanticDigester, connectionIdProvider,
            Optional::empty, EMPTY_HISTORY_PROVIDER);
    }

    public AgentPromptBuilder(StageTabRepository repo, SessionTitleLookup lookup) {
        this(repo, lookup, () -> Optional.empty(), null, () -> Optional.empty(),
            Optional::empty, EMPTY_HISTORY_PROVIDER);
    }

    private static final SqlExecutionHistoryProvider EMPTY_HISTORY_PROVIDER =
        new SqlExecutionHistoryProvider() {
            @Override
            public List<SqlExecutionRecord> recentFailures(String sessionId, int limit) {
                return List.of();
            }

            @Override
            public List<SqlExecutionRecord> recentSuccesses(String sessionId, int limit) {
                return List.of();
            }
        };

    public String render(String template) {
        String result = template;
        if (result.contains(PLACEHOLDER_STAGE_DIGEST)) {
            String digest = renderDigest();
            if (digest.length() > MAX_RENDERED_CHARS) {
                digest = digest.substring(0, MAX_RENDERED_CHARS - 3) + "...";
            }
            result = result.replace(PLACEHOLDER_STAGE_DIGEST, digest);
        }
        if (result.contains(PLACEHOLDER_ACTIVE_DIR)) {
            String value = activeDir.currentSessionId()
                .map(sid -> "./sessions/" + sid + "/")
                .orElse(NO_ACTIVE_SENTINEL);
            result = result.replace(PLACEHOLDER_ACTIVE_DIR, value);
        }
        if (result.contains(PLACEHOLDER_SEMANTIC_DIGEST)) {
            String value = connectionIdProvider.currentConnectionId()
                .map(cid -> semanticDigester != null ? semanticDigester.digest(cid) : "<no semantic model — please bind a connection>")
                .orElse("<no semantic model — please bind a connection>");
            result = result.replace(PLACEHOLDER_SEMANTIC_DIGEST, value);
        }
        if (result.contains(PLACEHOLDER_ACTIVE_CONNECTION_SUMMARY)) {
            String value = renderConnectionSummary();
            if (value.length() > MAX_RENDERED_CHARS) {
                value = value.substring(0, MAX_RENDERED_CHARS - 3) + "...";
            }
            result = result.replace(PLACEHOLDER_ACTIVE_CONNECTION_SUMMARY, value);
        }
        if (result.contains(PLACEHOLDER_RECENT_FAILED_QUERIES_DIGEST)) {
            String value = renderRecentFailures();
            if (value.length() > MAX_RENDERED_CHARS) {
                value = value.substring(0, MAX_RENDERED_CHARS - 3) + "...";
            }
            result = result.replace(PLACEHOLDER_RECENT_FAILED_QUERIES_DIGEST, value);
        }
        return result;
    }

    private String renderConnectionSummary() {
        Optional<ActiveConnectionSummaryProvider.ConnectionSummary> summary = connectionSummaryProvider.summary();
        if (summary.isEmpty()) return NO_ACTIVE_CONNECTION_SENTINEL;
        ActiveConnectionSummaryProvider.ConnectionSummary s = summary.get();
        StringBuilder sb = new StringBuilder("connection=").append(orDash(s.connectionId()))
            .append(" kind=").append(orDash(s.kind()))
            .append(" db=").append(orDash(s.database()))
            .append(" schema=").append(orDash(s.schema()));
        List<String> recent = s.recentSuccessfulQueries() == null ? List.of() : s.recentSuccessfulQueries();
        if (recent.isEmpty()) {
            sb.append("\nrecent successful queries: (none)");
        } else {
            sb.append("\nrecent successful queries:");
            int i = 1;
            for (String sql : recent) {
                sb.append("\n  ").append(i++).append(". ").append(escape(snippet(sql, MAX_SQL_PREVIEW_CHARS)));
            }
        }
        return sb.toString();
    }

    private String renderRecentFailures() {
        Optional<String> sessionId = activeDir.currentSessionId();
        if (sessionId.isEmpty()) return NO_RECENT_FAILURES_SENTINEL;
        List<SqlExecutionRecord> failures = executionHistoryProvider
            .recentFailures(sessionId.get(), MAX_FAILED_QUERIES);
        if (failures.isEmpty()) return NO_RECENT_FAILURES_SENTINEL;
        StringBuilder sb = new StringBuilder("recent failed queries (most recent first):");
        int i = 1;
        for (SqlExecutionRecord r : failures) {
            sb.append("\n  ").append(i++).append(". sql=").append(escape(snippet(r.sqlText(), MAX_SQL_PREVIEW_CHARS)))
              .append("\n     error=").append(escape(snippet(
                  r.errorCode() != null ? r.errorCode() + ": " + (r.errorMessage() == null ? "" : r.errorMessage())
                                        : (r.errorMessage() == null ? "" : r.errorMessage()),
                  MAX_ERROR_MESSAGE_CHARS)));
        }
        return sb.toString();
    }

    private static String snippet(String value, int max) {
        if (value == null) return "";
        String trimmed = value.strip();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max - 3) + "...";
    }

    private String renderDigest() {
        List<StageTab> recent = repo.recentByLastTouched(MAX_TABS);
        int active = repo.countActive();
        int archived = repo.countArchived();

        Map<String, String> sessionTitles = lookup.titlesByIds(
            recent.stream()
                .map(StageTab::originSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        StringBuilder sb = new StringBuilder("## Open Tabs Snapshot\n\n");
        if (recent.isEmpty()) {
            sb.append("No persisted tabs yet.\n");
        } else {
            sb.append("Recently-touched tabs (top ").append(recent.size())
              .append(" by lastTouchedAt, archived excluded):\n");
            Map<String, StageTabContent> erContent = erContentByTabId(recent);
            long now = System.currentTimeMillis();
            int i = 1;
            for (StageTab t : recent) {
                sb.append(i++).append(". ")
                    .append(t.type()).append(" `").append(t.id()).append("` ")
                    .append("\"").append(escape(t.title())).append("\"");
                String erStats = erStats(t, erContent.get(t.id()));
                if (!erStats.isBlank()) {
                    sb.append(" ").append(erStats);
                }
                sb.append("\n");
                sb.append("   conn=").append(orDash(t.connectionId()))
                    .append(" db=").append(orDash(t.databaseName()))
                    .append(" schema=").append(orDash(t.schemaName()))
                    .append("\n");
                String fromSession = t.originSessionId() == null
                    ? "(deleted)"
                    : sessionTitles.getOrDefault(t.originSessionId(), "(deleted)");
                sb.append("   fromSession=\"").append(escape(fromSession)).append("\"\n");
                sb.append("   lastTouched=").append(lastTouched(now, t.lastTouchedAt()))
                    .append(" version=").append(t.payloadVersion())
                    .append("\n");
            }
        }
        sb.append("\nTotal persisted tabs: ").append(active).append(" active, ").append(archived).append(" archived.\n");
        sb.append("Use `datatalk_ui_find` to locate tabs not listed above; the snapshot caps at ")
          .append(MAX_TABS).append(" entries to save tokens.\n");
        return sb.toString();
    }

    private Map<String, StageTabContent> erContentByTabId(List<StageTab> tabs) {
        List<String> ids = tabs.stream()
            .filter(t -> "er_inspector".equals(t.type()) || "er_designer".equals(t.type()))
            .map(StageTab::id)
            .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, StageTabContent> byId = new LinkedHashMap<>();
        for (StageTabContent content : repo.findContents(ids)) {
            byId.put(content.tabId(), content);
        }
        return byId;
    }

    private static String erStats(StageTab tab, StageTabContent content) {
        return switch (tab.type()) {
            case "er_inspector" -> erInspectorStats(tab, content);
            case "er_designer" -> erDesignerStats(content);
            default -> "";
        };
    }

    private static String erInspectorStats(StageTab tab, StageTabContent content) {
        int tableCount = 0;
        int relationCount = 0;
        String connectionId = tab.connectionId();
        if (content != null) {
            try {
                JsonNode root = OM.readTree(content.payloadJson());
                connectionId = nonBlank(root.path("connectionId").asText(null), connectionId);
                JsonNode tables = root.path("tablesSnapshot");
                if (tables.isArray()) {
                    tableCount = tables.size();
                    for (JsonNode table : tables) {
                        JsonNode fkOut = table.path("fkOut");
                        if (fkOut.isArray()) {
                            relationCount += fkOut.size();
                        }
                    }
                }
                JsonNode virtualRelations = root.path("virtualRelations");
                if (virtualRelations.isArray()) {
                    relationCount += virtualRelations.size();
                }
            } catch (Exception ignored) {
                // Keep the prompt render resilient; malformed payloads still get metadata below.
            }
        }
        return "(" + tableCount + " tables \u00b7 " + relationCount + " relations \u00b7 conn="
            + orDash(connectionId) + ")";
    }

    private static String erDesignerStats(StageTabContent content) {
        int tableCount = 0;
        int relationCount = 0;
        String target = "no target";
        if (content != null) {
            try {
                JsonNode root = OM.readTree(content.payloadJson());
                JsonNode tables = root.path("tables");
                if (tables.isArray()) {
                    tableCount = tables.size();
                }
                JsonNode relations = root.path("relations");
                if (relations.isArray()) {
                    relationCount = relations.size();
                }
                String targetConnectionId = root.path("targetConnectionId").asText(null);
                if (targetConnectionId != null && !targetConnectionId.isBlank()) {
                    String targetDatabase = root.path("targetDatabase").asText(null);
                    target = "target=" + targetConnectionId
                        + (targetDatabase == null || targetDatabase.isBlank() ? "" : "/" + targetDatabase);
                }
            } catch (Exception ignored) {
                // Keep the prompt render resilient; malformed payloads still get metadata below.
            }
        }
        return "(" + tableCount + " tables \u00b7 " + relationCount + " relations \u00b7 " + target + ")";
    }

    private static String nonBlank(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    static String escape(String title) {
        if (title == null) return "(untitled)";
        String t = title;
        if (t.length() > MAX_TITLE_CHARS) t = t.substring(0, MAX_TITLE_CHARS) + "...";
        return t.replace("\n", " ")
                .replace("\r", " ")
                .replace("```", "''")
                .replace("`", "'")
                .replace(":::", "..")
                .replace("<!--", "< !--")
                .replace("{{", "{ {")
                .replace("}}", "} }");
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static String lastTouched(long now, long lastTouchedAt) {
        if (lastTouchedAt <= 0L || lastTouchedAt > now) {
            return "unknown";
        }
        return EditConflictMarkdownFormatter.humanizeDelta(now - lastTouchedAt);
    }
}
