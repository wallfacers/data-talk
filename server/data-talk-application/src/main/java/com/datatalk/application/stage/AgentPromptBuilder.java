package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Renders the {@code {{STAGE_TAB_DIGEST}}} placeholder in the AGENTS.md template
 * with a compact snapshot of recently-touched tabs for the AI agent context.
 */
@Component
public class AgentPromptBuilder {
    private static final String PLACEHOLDER = "{{STAGE_TAB_DIGEST}}";
    private static final int MAX_TABS = 10;
    private static final int MAX_TITLE_CHARS = 80;
    private static final int MAX_RENDERED_CHARS = 1_500;

    private final StageTabRepository repo;

    public AgentPromptBuilder(StageTabRepository repo) {
        this.repo = repo;
    }

    public String render(String template) {
        if (!template.contains(PLACEHOLDER)) return template;
        String digest = renderDigest();
        if (digest.length() > MAX_RENDERED_CHARS) {
            digest = digest.substring(0, MAX_RENDERED_CHARS - 3) + "...";
        }
        return template.replace(PLACEHOLDER, digest);
    }

    private String renderDigest() {
        List<StageTab> recent = repo.recentByLastTouched(MAX_TABS);
        int active = repo.countActive();
        int archived = repo.countArchived();
        StringBuilder sb = new StringBuilder("## Open Tabs Snapshot\n\n");
        if (recent.isEmpty()) {
            sb.append("No persisted tabs yet.\n");
        } else {
            sb.append("Recently-touched tabs (top ").append(recent.size())
              .append(" by lastTouchedAt, archived excluded):\n");
            int i = 1;
            for (StageTab t : recent) {
                sb.append(i++).append(". ").append(t.id()).append("  ")
                  .append(escape(t.title())).append("  (")
                  .append(orDash(t.databaseName())).append(" · ").append(orDash(t.schemaName()))
                  .append(")\n");
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
}
