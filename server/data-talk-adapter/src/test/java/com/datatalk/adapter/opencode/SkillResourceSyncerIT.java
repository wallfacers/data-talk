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
    void syncDataIngestionSkill() {
        SkillResourceSyncer syncer = new SkillResourceSyncer();
        syncer.syncSkill("data-ingestion", tempDir);

        Path skillDir = tempDir.resolve(".opencode/skills/data-ingestion");
        assertThat(skillDir).isDirectory();
        assertThat(skillDir.resolve("SKILL.md")).isRegularFile();
        assertThat(skillDir.resolve("recipes/basic-rest-fetch.md")).isRegularFile();
        assertThat(skillDir.resolve("examples/example1.json")).isRegularFile();

        Path marker = tempDir.resolve(".opencode/.data-ingestion-skill-synced");
        assertThat(marker).isRegularFile();
    }
}
