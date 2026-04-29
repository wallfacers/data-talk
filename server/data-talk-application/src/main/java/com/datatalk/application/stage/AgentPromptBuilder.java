package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders the {@code {{STAGE_TAB_DIGEST}}} placeholder in the AGENTS.md template
 * with a compact snapshot of recently-touched tabs for the AI agent context.
 */
@Component
public class AgentPromptBuilder {
    private static final String PLACEHOLDER_STAGE_DIGEST = "{{STAGE_TAB_DIGEST}}";
    private static final String PLACEHOLDER_ACTIVE_DIR = "{{ACTIVE_SESSION_DIR}}";
    private static final String NO_ACTIVE_SENTINEL = "<no active session>";
    private static final int MAX_TABS = 10;
    private static final int MAX_TITLE_CHARS = 80;
    private static final int MAX_RENDERED_CHARS = 1_500;

    private final StageTabRepository repo;
    private final SessionTitleLookup lookup;
    private final ActiveSessionDirProvider activeDir;

    @Autowired
    public AgentPromptBuilder(StageTabRepository repo,
                              SessionTitleLookup lookup,
                              ActiveSessionDirProvider activeDir) {
        this.repo = repo;
        this.lookup = lookup;
        this.activeDir = activeDir;
    }

    public AgentPromptBuilder(StageTabRepository repo, SessionTitleLookup lookup) {
        this(repo, lookup, () -> Optional.empty());
    }

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
        return result;
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
            long now = System.currentTimeMillis();
            int i = 1;
            for (StageTab t : recent) {
                sb.append(i++).append(". ")
                    .append(t.type()).append(" `").append(t.id()).append("` ")
                    .append("\"").append(escape(t.title())).append("\"\n");
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
