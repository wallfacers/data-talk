package com.datatalk.adapter.agents;

import com.datatalk.infra.opencode.process.SkillResourceSyncer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the data-talk-adapter classpath ships skills/exploring-data/SKILL.md
 * and that the SkillResourceSyncer materializes it under .opencode/skills/exploring-data/.
 */
class ExploringDataSkillSyncTest {

    @TempDir
    Path tempDir;

    @Test
    void syncSkill_materializesExploringDataSkill() throws Exception {
        SkillResourceSyncer syncer = new SkillResourceSyncer();

        syncer.syncSkill("exploring-data", tempDir);

        Path skillDir = tempDir.resolve(".opencode/skills/exploring-data");
        assertThat(skillDir).isDirectory();

        Path skillFile = skillDir.resolve("SKILL.md");
        assertThat(skillFile).isRegularFile();

        String content = Files.readString(skillFile);
        assertThat(content)
            .contains("name: exploring-data")
            .contains("Pre-Action Exploration Protocol")
            .contains("datatalk_schema_search")
            .contains("3 times")
            .doesNotContainPattern("skills/[a-z0-9-]+/SKILL\\.md")
            .doesNotContain(".opencode/skills/");

        Path marker = tempDir.resolve(".opencode/.exploring-data-skill-synced");
        assertThat(marker).isRegularFile();
        assertThat(Files.readString(marker)).isNotBlank();
    }
}
