package com.datatalk.adapter.agents;

import com.datatalk.application.stage.AgentPromptBuilder;
import com.datatalk.application.stage.SessionTitleLookup;
import com.datatalk.application.stage.StageTabRepository;
import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPromptDigestTest {

    @Test
    void digest_includesOriginSessionTitle_lastTouched_version() {
        StageTabRepository repo = mock(StageTabRepository.class);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        long now = System.currentTimeMillis();
        StageTab t = new StageTab("qe-1", "query_editor", "users monthly",
            "conn_x", "analytics", "public", "sess-1",
            14, false, false, null, now - 60_000, now - 12_000);

        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(t));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);
        when(lookup.titlesByIds(List.of("sess-1"))).thenReturn(Map.of("sess-1", "April Weekly"));

        AgentPromptBuilder builder = new AgentPromptBuilder(repo, lookup);
        String md = builder.render("intro\n{{STAGE_TAB_DIGEST}}\noutro");

        assertThat(md).contains("query_editor `qe-1`");
        assertThat(md).contains("users monthly");
        assertThat(md).contains("conn=conn_x db=analytics schema=public");
        assertThat(md).contains("fromSession=\"April Weekly\"");
        assertThat(md).containsPattern("lastTouched=1[23]s ago");
        assertThat(md).contains("version=14");
        assertThat(md).doesNotContain("inWorkset=");
    }

    @Test
    void digest_originSessionDeleted_rendersDeleted() {
        StageTabRepository repo = mock(StageTabRepository.class);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        long now = System.currentTimeMillis();
        StageTab t = new StageTab("qe-2", "query_editor", "orphan tab",
            null, null, null, null,
            3, false, false, null, now, now);

        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(t));
        when(lookup.titlesByIds(List.of())).thenReturn(Map.of());
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);

        AgentPromptBuilder builder = new AgentPromptBuilder(repo, lookup);
        String md = builder.render("{{STAGE_TAB_DIGEST}}");

        assertThat(md).contains("fromSession=\"(deleted)\"");
    }

    @Test
    void fieldOrderIsStable() {
        StageTabRepository repo = mock(StageTabRepository.class);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        long now = System.currentTimeMillis();
        StageTab t = new StageTab("qe-3", "query_editor", "stable",
            "conn_x", "db1", "s1", "sess-1",
            5, false, false, null, now, now - 2_000);
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(t));
        when(lookup.titlesByIds(List.of("sess-1"))).thenReturn(Map.of("sess-1", "Title"));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);

        String md = new AgentPromptBuilder(repo, lookup).render("{{STAGE_TAB_DIGEST}}");
        int titleIdx = md.indexOf("\"stable\"");
        int connIdx = md.indexOf("conn=conn_x");
        int sessIdx = md.indexOf("fromSession=");
        int touchedIdx = md.indexOf("lastTouched=");
        int versionIdx = md.indexOf("version=");
        assertThat(titleIdx).isLessThan(connIdx);
        assertThat(connIdx).isLessThan(sessIdx);
        assertThat(sessIdx).isLessThan(touchedIdx);
        assertThat(touchedIdx).isLessThan(versionIdx);
    }

    @Test
    void digest_erInspectorRendersStatsLineFromPayload() {
        StageTabRepository repo = mock(StageTabRepository.class);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        long now = System.currentTimeMillis();
        StageTab t = new StageTab("er_inspector_a1b2", "er_inspector", "Order ER",
            "metadata-conn", "analytics", "public", "sess-1",
            7, false, false, null, now, now);
        String payload = """
            {
              "kind": "er_inspector",
              "connectionId": "prod-mysql",
              "selection": ["orders"],
              "tablesSnapshot": [
                {"name": "users", "fkOut": []},
                {"name": "orders", "fkOut": [{"fromColumn": "user_id", "toTable": "users", "toColumn": "id"}]}
              ],
              "virtualRelations": [
                {"id": "vr1", "from": {"table": "orders", "column": "user_email"}, "to": {"table": "users", "column": "email"}}
              ]
            }
            """;

        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(t));
        when(repo.findContents(List.of("er_inspector_a1b2")))
            .thenReturn(List.of(new StageTabContent("er_inspector_a1b2", payload, "", 7, now)));
        when(lookup.titlesByIds(List.of("sess-1"))).thenReturn(Map.of("sess-1", "ER Session"));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);

        String md = new AgentPromptBuilder(repo, lookup).render("{{STAGE_TAB_DIGEST}}");

        assertThat(md).contains("er_inspector `er_inspector_a1b2`");
        assertThat(md).contains("Order ER");
        assertThat(md).contains("(2 tables \u00b7 2 relations \u00b7 conn=prod-mysql)");
    }

    @Test
    void digest_erDesignerRendersStatsAndTargetLineFromPayload() {
        StageTabRepository repo = mock(StageTabRepository.class);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        long now = System.currentTimeMillis();
        StageTab t = new StageTab("er_designer_c3d4", "er_designer", "Order Draft",
            null, null, null, "sess-1",
            8, false, false, null, now, now);
        String payload = """
            {
              "kind": "er_designer",
              "dialect": "mysql",
              "targetConnectionId": "test-mysql",
              "targetDatabase": "test_db",
              "tables": [{"id": "t1"}, {"id": "t2"}, {"id": "t3"}],
              "relations": [{"id": "r1"}, {"id": "r2"}]
            }
            """;

        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(t));
        when(repo.findContents(List.of("er_designer_c3d4")))
            .thenReturn(List.of(new StageTabContent("er_designer_c3d4", payload, "", 8, now)));
        when(lookup.titlesByIds(List.of("sess-1"))).thenReturn(Map.of("sess-1", "Design Session"));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);

        String md = new AgentPromptBuilder(repo, lookup).render("{{STAGE_TAB_DIGEST}}");

        assertThat(md).contains("er_designer `er_designer_c3d4`");
        assertThat(md).contains("Order Draft");
        assertThat(md).contains("(3 tables \u00b7 2 relations \u00b7 target=test-mysql/test_db)");
    }

    @Test
    void digest_erInspectorTitleIsEscapedToPreventPromptInjection() {
        StageTabRepository repo = mock(StageTabRepository.class);
        SessionTitleLookup lookup = mock(SessionTitleLookup.class);
        long now = System.currentTimeMillis();
        StageTab t = new StageTab("er_inspector_escape", "er_inspector", "Title with `backticks` and\nnewline",
            "conn-1", null, null, null,
            1, false, false, null, now, now);

        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(t));
        when(repo.findContents(List.of("er_inspector_escape"))).thenReturn(List.of());
        when(lookup.titlesByIds(List.of())).thenReturn(Map.of());
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);

        String md = new AgentPromptBuilder(repo, lookup).render("{{STAGE_TAB_DIGEST}}");

        assertThat(md).contains("\"Title with 'backticks' and newline\"");
        assertThat(md).doesNotContain("Title with `backticks`");
    }
}
