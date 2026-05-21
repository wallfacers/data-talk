package com.datatalk.application.stage;

import com.datatalk.application.connection.ActiveConnectionSummaryProvider;
import com.datatalk.application.history.SqlExecutionHistoryProvider;
import com.datatalk.application.history.SqlExecutionRecord;
import com.datatalk.application.semantic.SemanticModelDigester;
import com.datatalk.domain.stage.StageTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentPromptBuilderTest {

    private StageTabRepository repo;
    private SessionTitleLookup lookup;
    private ActiveSessionDirProvider activeDir;
    private SemanticModelDigester semanticDigester;
    private ConnectionIdProvider connectionIdProvider;
    private AgentPromptBuilder builder;

    @BeforeEach
    void setUp() {
        repo = mock(StageTabRepository.class);
        lookup = mock(SessionTitleLookup.class);
        activeDir = mock(ActiveSessionDirProvider.class);
        semanticDigester = mock(SemanticModelDigester.class);
        connectionIdProvider = () -> Optional.empty();
        when(lookup.titlesByIds(anyList())).thenReturn(Map.of());
        builder = new AgentPromptBuilder(repo, lookup, activeDir, semanticDigester, connectionIdProvider);
    }

    private StageTab mkTab(String id, String title) {
        return new StageTab(id, "query_editor",
            title, null, null, null, null, 1, false, false, null,
            System.currentTimeMillis(), System.currentTimeMillis());
    }

    @Test
    void replacesPlaceholderWithDigest() {
        long now = System.currentTimeMillis();
        StageTab tab = new StageTab("t1", "query_editor",
            "My Query", "conn-1", "analytics", "public", "sess-1", 4, false, false, null,
            now - 60_000, now - 12_000);
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(tab));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);
        when(lookup.titlesByIds(List.of("sess-1"))).thenReturn(Map.of("sess-1", "April Weekly"));

        String result = builder.render("Header\n{{STAGE_TAB_DIGEST}}\nFooter");

        assertThat(result)
            .contains("## Open Tabs Snapshot")
            .contains("query_editor `t1` \"My Query\"")
            .contains("conn=conn-1 db=analytics schema=public")
            .contains("fromSession=\"April Weekly\"")
            .containsPattern("lastTouched=1[23]s ago")
            .contains("version=4")
            .contains("1 active, 0 archived")
            .contains("datatalk_ui_find")
            .doesNotContain("{{STAGE_TAB_DIGEST}}");
        assertThat(result).startsWith("Header\n");
        assertThat(result).endsWith("\nFooter");
        verify(lookup).titlesByIds(List.of("sess-1"));
    }

    @Test
    void escapesPromptInjectionInTitles() {
        StageTab malicious = new StageTab("t1", "query_editor",
            "```evil:::{{inject}}", null, null, null, null, 1, false, false, null,
            0, 0);
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(malicious));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);

        String result = builder.render("{{STAGE_TAB_DIGEST}}");
        assertThat(result).doesNotContain("```");
        assertThat(result).doesNotContain(":::");
        assertThat(result).doesNotContain("{{inject}}");
        assertThat(result).contains("''");
        assertThat(result).contains("..");
        assertThat(result).contains("{ {");
    }

    @Test
    void rendersDeletedFallbackAndUnknownLastTouchedForAbnormalTimestamp() {
        StageTab orphan = new StageTab("t1", "query_editor",
            "Detached Query", null, null, null, null, 3, false, false, null,
            System.currentTimeMillis(), 0);
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(orphan));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);

        String result = builder.render("{{STAGE_TAB_DIGEST}}");

        assertThat(result)
            .contains("fromSession=\"(deleted)\"")
            .contains("lastTouched=unknown")
            .contains("version=3");
    }

    @Test
    void truncatesTitleAt80Chars() {
        String longTitle = "A".repeat(120);
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(mkTab("t1", longTitle)));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);

        String result = builder.render("{{STAGE_TAB_DIGEST}}");
        // Title should be truncated: 80 chars + "..." in the escaped output.
        assertThat(result).contains("...");
        assertThat(result).doesNotContain("…");
        assertThat(result).doesNotContain("A".repeat(120));
    }

    @Test
    void leavesInputUntouchedWhenNoPlaceholder() {
        String plain = "Just a normal template with no placeholder.";
        String result = builder.render(plain);
        assertThat(result).isEqualTo(plain);
    }

    @Test
    void replacesActiveSessionDirWhenSessionPresent() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_abc"));

        String result = builder.render("Subdir is {{ACTIVE_SESSION_DIR}}.");

        assertThat(result).isEqualTo("Subdir is ./sessions/ses_abc/.");
    }

    @Test
    void replacesActiveSessionDirWithSentinelWhenNoActiveSession() {
        when(activeDir.currentSessionId()).thenReturn(Optional.empty());

        String result = builder.render("Subdir is {{ACTIVE_SESSION_DIR}}.");

        assertThat(result).isEqualTo("Subdir is <no active session>.");
    }

    @Test
    void replacesStageDigestAndActiveSessionDirIndependently() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_x"));
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of());
        when(repo.countActive()).thenReturn(0);
        when(repo.countArchived()).thenReturn(0);

        String result = builder.render("Tabs:\n{{STAGE_TAB_DIGEST}}\nDir={{ACTIVE_SESSION_DIR}}");

        assertThat(result)
            .contains("## Open Tabs Snapshot")
            .contains("No persisted tabs yet.")
            .contains("Dir=./sessions/ses_x/")
            .doesNotContain("{{STAGE_TAB_DIGEST}}")
            .doesNotContain("{{ACTIVE_SESSION_DIR}}");
    }

    @Test
    void leavesInputUntouchedWhenNoPlaceholdersEvenWithActiveSession() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_x"));

        String plain = "Just a normal template with no placeholders.";
        String result = builder.render(plain);

        assertThat(result).isEqualTo(plain);
    }

    @Test
    void render_replaces_both_placeholder_occurrences_when_active_session_present() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_xyz"));
        String tpl = """
                <!-- file-artifact-section:begin -->
                ## Output Files & Artifacts

                Your current session has a dedicated working subdirectory at:

                  {{ACTIVE_SESSION_DIR}}

                **Rules**:
                - Always write into your session subdirectory ({{ACTIVE_SESSION_DIR}}), not the parent cwd
                <!-- file-artifact-section:end -->
                """;
        String result = builder.render(tpl);
        assertThat(result).doesNotContain("{{ACTIVE_SESSION_DIR}}");
        long occurrences = result.lines().filter(l -> l.contains("./sessions/ses_xyz/")).count();
        assertThat(occurrences).isEqualTo(2L);
    }

    @Test
    void render_replaces_both_placeholder_occurrences_with_sentinel_when_no_session() {
        when(activeDir.currentSessionId()).thenReturn(Optional.empty());
        String tpl = """
                Your current session has a dedicated working subdirectory at:

                  {{ACTIVE_SESSION_DIR}}

                **Rules**:
                - Always write into your session subdirectory ({{ACTIVE_SESSION_DIR}}), not the parent cwd
                """;
        String result = builder.render(tpl);
        assertThat(result).doesNotContain("{{ACTIVE_SESSION_DIR}}");
        long occurrences = result.lines().filter(l -> l.contains("<no active session>")).count();
        assertThat(occurrences).isEqualTo(2L);
    }

    @Test
    void render_replaces_semantic_model_digest_when_connection_present() {
        AgentPromptBuilder b = new AgentPromptBuilder(repo, lookup, activeDir,
            semanticDigester, () -> Optional.of("conn_42"));
        when(semanticDigester.digest("conn_42")).thenReturn("## Semantic Model Snapshot\nDomains: orders");

        String result = b.render("Pre\n{{SEMANTIC_MODEL_DIGEST}}\nPost");

        assertThat(result)
            .contains("## Semantic Model Snapshot")
            .contains("Domains: orders")
            .doesNotContain("{{SEMANTIC_MODEL_DIGEST}}");
        verify(semanticDigester).digest("conn_42");
    }

    @Test
    void render_replaces_semantic_model_digest_with_sentinel_when_no_connection() {
        AgentPromptBuilder b = new AgentPromptBuilder(repo, lookup, activeDir,
            semanticDigester, () -> Optional.empty());

        String result = b.render("X{{SEMANTIC_MODEL_DIGEST}}Y");

        assertThat(result)
            .contains("<no semantic model — please bind a connection>")
            .doesNotContain("{{SEMANTIC_MODEL_DIGEST}}");
        verifyNoInteractions(semanticDigester);
    }

    @Test
    void render_replaces_all_three_placeholders_independently() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_z"));
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of());
        when(repo.countActive()).thenReturn(0);
        when(repo.countArchived()).thenReturn(0);
        AgentPromptBuilder b = new AgentPromptBuilder(repo, lookup, activeDir,
            semanticDigester, () -> Optional.of("conn_X"));
        when(semanticDigester.digest("conn_X")).thenReturn("SEM_DIGEST_BODY");

        String result = b.render(
            "Tabs:\n{{STAGE_TAB_DIGEST}}\nDir={{ACTIVE_SESSION_DIR}}\nSem={{SEMANTIC_MODEL_DIGEST}}");

        assertThat(result)
            .contains("## Open Tabs Snapshot")
            .contains("Dir=./sessions/ses_z/")
            .contains("Sem=SEM_DIGEST_BODY")
            .doesNotContain("{{STAGE_TAB_DIGEST}}")
            .doesNotContain("{{ACTIVE_SESSION_DIR}}")
            .doesNotContain("{{SEMANTIC_MODEL_DIGEST}}");
    }

    private AgentPromptBuilder builderWith(ActiveConnectionSummaryProvider connSummary,
                                            SqlExecutionHistoryProvider history) {
        return new AgentPromptBuilder(repo, lookup, activeDir, semanticDigester,
            () -> Optional.empty(), connSummary, history);
    }

    @Test
    void renders_active_connection_summary_when_present() {
        ActiveConnectionSummaryProvider connSummary = () -> Optional.of(
            new ActiveConnectionSummaryProvider.ConnectionSummary(
                "conn_42", "mysql", "shop", "public",
                List.of("SELECT 1", "SELECT count(*) FROM orders")
            ));
        SqlExecutionHistoryProvider history = mock(SqlExecutionHistoryProvider.class);
        AgentPromptBuilder b = builderWith(connSummary, history);

        String result = b.render("Conn:\n{{ACTIVE_CONNECTION_SUMMARY}}");

        assertThat(result)
            .contains("connection=conn_42")
            .contains("kind=mysql")
            .contains("db=shop")
            .contains("schema=public")
            .contains("SELECT 1")
            .contains("SELECT count(*) FROM orders")
            .doesNotContain("{{ACTIVE_CONNECTION_SUMMARY}}");
    }

    @Test
    void renders_active_connection_sentinel_when_absent() {
        ActiveConnectionSummaryProvider connSummary = Optional::empty;
        SqlExecutionHistoryProvider history = mock(SqlExecutionHistoryProvider.class);
        AgentPromptBuilder b = builderWith(connSummary, history);

        String result = b.render("Conn:\n{{ACTIVE_CONNECTION_SUMMARY}}");

        assertThat(result)
            .contains("<no active connection>")
            .doesNotContain("{{ACTIVE_CONNECTION_SUMMARY}}");
    }

    @Test
    void renders_recent_failed_queries_digest_when_present() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_q"));
        SqlExecutionHistoryProvider history = mock(SqlExecutionHistoryProvider.class);
        when(history.recentFailures(eq("ses_q"), anyInt())).thenReturn(List.of(
            SqlExecutionRecord.failure("ses_q", "conn", "shop", null,
                "SELECT * FROM no_such", "TABLE_NOT_FOUND", "no such table: no_such",
                1_700_000_000_000L, 12L),
            SqlExecutionRecord.failure("ses_q", "conn", "shop", null,
                "SELECT bad_col FROM users", "UNKNOWN_COLUMN", "unknown column: bad_col",
                1_700_000_001_000L, 5L)
        ));
        ActiveConnectionSummaryProvider connSummary = Optional::empty;
        AgentPromptBuilder b = builderWith(connSummary, history);

        String result = b.render("Fails:\n{{RECENT_FAILED_QUERIES_DIGEST}}");

        assertThat(result)
            .contains("recent failed queries")
            .contains("SELECT * FROM no_such")
            .contains("TABLE_NOT_FOUND")
            .contains("no such table: no_such")
            .contains("SELECT bad_col FROM users")
            .contains("UNKNOWN_COLUMN")
            .doesNotContain("{{RECENT_FAILED_QUERIES_DIGEST}}");
    }

    @Test
    void renders_recent_failed_queries_sentinel_when_empty() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_q"));
        SqlExecutionHistoryProvider history = mock(SqlExecutionHistoryProvider.class);
        when(history.recentFailures(anyString(), anyInt())).thenReturn(List.of());
        ActiveConnectionSummaryProvider connSummary = Optional::empty;
        AgentPromptBuilder b = builderWith(connSummary, history);

        String result = b.render("Fails:\n{{RECENT_FAILED_QUERIES_DIGEST}}");

        assertThat(result)
            .contains("<no recent failures>")
            .doesNotContain("{{RECENT_FAILED_QUERIES_DIGEST}}");
    }

    @Test
    void renders_recent_failed_queries_sentinel_when_no_active_session() {
        when(activeDir.currentSessionId()).thenReturn(Optional.empty());
        SqlExecutionHistoryProvider history = mock(SqlExecutionHistoryProvider.class);
        ActiveConnectionSummaryProvider connSummary = Optional::empty;
        AgentPromptBuilder b = builderWith(connSummary, history);

        String result = b.render("{{RECENT_FAILED_QUERIES_DIGEST}}");

        assertThat(result)
            .contains("<no recent failures>")
            .doesNotContain("{{RECENT_FAILED_QUERIES_DIGEST}}");
        // The history provider must not be called when there is no active session.
        verifyNoInteractions(history);
    }

    @Test
    void deprecated_five_arg_constructor_returns_sentinels_for_new_placeholders() {
        // Existing wiring that still constructs AgentPromptBuilder via the 5-arg
        // (no Provider) constructor should not break — new placeholders render to
        // sentinels.
        AgentPromptBuilder b = new AgentPromptBuilder(repo, lookup, activeDir,
            semanticDigester, () -> Optional.empty());

        String result = b.render(
            "{{ACTIVE_CONNECTION_SUMMARY}}|{{RECENT_FAILED_QUERIES_DIGEST}}");

        assertThat(result)
            .contains("<no active connection>")
            .contains("<no recent failures>")
            .doesNotContain("{{ACTIVE_CONNECTION_SUMMARY}}")
            .doesNotContain("{{RECENT_FAILED_QUERIES_DIGEST}}");
    }

    @Test
    void renders_all_five_placeholders_independently() {
        when(activeDir.currentSessionId()).thenReturn(Optional.of("ses_z"));
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of());
        when(repo.countActive()).thenReturn(0);
        when(repo.countArchived()).thenReturn(0);

        ActiveConnectionSummaryProvider connSummary = () -> Optional.of(
            new ActiveConnectionSummaryProvider.ConnectionSummary(
                "conn_X", "postgresql", "main", null, List.of()));
        SqlExecutionHistoryProvider history = mock(SqlExecutionHistoryProvider.class);
        when(history.recentFailures(anyString(), anyInt())).thenReturn(List.of());

        AgentPromptBuilder b = new AgentPromptBuilder(repo, lookup, activeDir,
            semanticDigester, () -> Optional.of("conn_X"), connSummary, history);
        when(semanticDigester.digest("conn_X")).thenReturn("SEM_BODY");

        String result = b.render(
            "Tabs:{{STAGE_TAB_DIGEST}}\nDir={{ACTIVE_SESSION_DIR}}\n"
            + "Sem={{SEMANTIC_MODEL_DIGEST}}\n"
            + "Conn={{ACTIVE_CONNECTION_SUMMARY}}\n"
            + "Fail={{RECENT_FAILED_QUERIES_DIGEST}}");

        assertThat(result)
            .doesNotContain("{{STAGE_TAB_DIGEST}}")
            .doesNotContain("{{ACTIVE_SESSION_DIR}}")
            .doesNotContain("{{SEMANTIC_MODEL_DIGEST}}")
            .doesNotContain("{{ACTIVE_CONNECTION_SUMMARY}}")
            .doesNotContain("{{RECENT_FAILED_QUERIES_DIGEST}}")
            .contains("Dir=./sessions/ses_z/")
            .contains("Sem=SEM_BODY")
            .contains("connection=conn_X")
            .contains("<no recent failures>");
    }
}
