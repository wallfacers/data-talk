package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import com.datatalk.domain.stage.StageTabScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPromptBuilderTest {

    private StageTabRepository repo;
    private AgentPromptBuilder builder;

    @BeforeEach
    void setUp() {
        repo = mock(StageTabRepository.class);
        builder = new AgentPromptBuilder(repo);
    }

    private StageTab mkTab(String id, String title) {
        return new StageTab(id, "query_editor", StageTabScope.WORKSPACE,
            title, null, null, null, null, 1, false, false, null,
            System.currentTimeMillis(), System.currentTimeMillis());
    }

    @Test
    void replacesPlaceholderWithDigest() {
        when(repo.recentByLastTouched(anyInt())).thenReturn(List.of(mkTab("t1", "My Query")));
        when(repo.countActive()).thenReturn(1);
        when(repo.countArchived()).thenReturn(0);

        String result = builder.render("Header\n{{STAGE_TAB_DIGEST}}\nFooter");

        assertThat(result)
            .contains("## Open Tabs Snapshot")
            .contains("t1")
            .contains("My Query")
            .contains("1 active, 0 archived")
            .contains("datatalk_ui_find")
            .doesNotContain("{{STAGE_TAB_DIGEST}}");
        assertThat(result).startsWith("Header\n");
        assertThat(result).endsWith("\nFooter");
    }

    @Test
    void escapesPromptInjectionInTitles() {
        StageTab malicious = new StageTab("t1", "query_editor", StageTabScope.WORKSPACE,
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
}
