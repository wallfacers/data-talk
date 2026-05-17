package com.datatalk.adapter.opencode;

import com.datatalk.infra.opencode.process.SkillResourceSyncer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillResourceSyncerIT {

    @TempDir
    Path tempDir;

    @Test
    void syncBezelSkill() throws Exception {
        SkillResourceSyncer syncer = new SkillResourceSyncer();
        syncer.syncSkill("bezel", tempDir);

        Path skillDir = tempDir.resolve(".opencode/skills/bezel");
        assertThat(skillDir).isDirectory();
        assertThat(skillDir.resolve("SKILL.md")).isRegularFile();
        assertThat(skillDir.resolve("references/design-language.md")).isRegularFile();
        assertThat(skillDir.resolve("scripts/preview.py")).isRegularFile();
        assertThat(skillDir.resolve("assets/templates/01-multi-screen-dashboard.html")).isRegularFile();
        assertThat(skillDir.resolve("references/industries/01-multi-screen.md")).isRegularFile();
        assertThat(skillDir.resolve("references/styles/horizon.md")).isRegularFile();

        Path marker = tempDir.resolve(".opencode/.bezel-skill-synced");
        assertThat(marker).isRegularFile();
        assertThat(Files.readString(marker)).isNotBlank();
    }

    @Test
    void syncDataCollectionSkill() {
        SkillResourceSyncer syncer = new SkillResourceSyncer();
        syncer.syncSkill("data-collection", tempDir);

        Path skillDir = tempDir.resolve(".opencode/skills/data-collection");
        assertThat(skillDir).isDirectory();
        assertThat(skillDir.resolve("SKILL.md")).isRegularFile();
        assertThat(skillDir.resolve("recipes/python-rest-fetch.md")).isRegularFile();

        Path marker = tempDir.resolve(".opencode/.data-collection-skill-synced");
        assertThat(marker).isRegularFile();
    }
}
