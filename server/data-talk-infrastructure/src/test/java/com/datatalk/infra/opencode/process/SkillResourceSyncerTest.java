package com.datatalk.infra.opencode.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillResourceSyncerTest {

    @TempDir
    Path tempDir;

    @Test
    void syncSkill_copiesFromClasspathToTarget() {
        SkillResourceSyncer syncer = new SkillResourceSyncer();

        syncer.syncSkill("test-skill", tempDir);

        Path skillDir = tempDir.resolve(".opencode/skills/test-skill");
        assertThat(skillDir).isDirectory();
        assertThat(skillDir.resolve("SKILL.md")).isRegularFile();
        assertThat(skillDir.resolve("references")).isDirectory();
        assertThat(skillDir.resolve("references/guide.md")).isRegularFile();
    }

    @Test
    void syncSkill_writesMarkerFile() throws Exception {
        SkillResourceSyncer syncer = new SkillResourceSyncer();

        syncer.syncSkill("test-skill", tempDir);

        Path marker = tempDir.resolve(".opencode/.test-skill-skill-synced");
        assertThat(marker).isRegularFile();
        assertThat(Files.readString(marker)).isNotBlank();
    }

    @Test
    void syncSkill_skipsWhenMarkerMatches() throws Exception {
        SkillResourceSyncer syncer = new SkillResourceSyncer();

        // First sync
        syncer.syncSkill("test-skill", tempDir);
        Path skillDir = tempDir.resolve(".opencode/skills/test-skill");
        Path marker = tempDir.resolve(".opencode/.test-skill-skill-synced");
        String markerContent = Files.readString(marker);

        // Modify a file to detect if re-sync happens
        Files.writeString(skillDir.resolve("SKILL.md"), "MODIFIED");

        // Second sync should skip (marker matches)
        syncer.syncSkill("test-skill", tempDir);

        assertThat(Files.readString(skillDir.resolve("SKILL.md"))).isEqualTo("MODIFIED");
        assertThat(Files.readString(marker)).isEqualTo(markerContent);
    }

    @Test
    void syncSkill_reinstallsWhenMarkerDiffers() throws Exception {
        SkillResourceSyncer syncer = new SkillResourceSyncer();

        // First sync
        syncer.syncSkill("test-skill", tempDir);
        Path skillDir = tempDir.resolve(".opencode/skills/test-skill");
        Path marker = tempDir.resolve(".opencode/.test-skill-skill-synced");

        // Corrupt marker to force re-sync
        Files.writeString(marker, "stale-hash");

        // Second sync should reinstall
        syncer.syncSkill("test-skill", tempDir);

        assertThat(Files.readString(skillDir.resolve("SKILL.md"))).contains("name: test-skill");
    }

    @Test
    void syncSkill_skipsWhenSkillNotOnClasspath() {
        SkillResourceSyncer syncer = new SkillResourceSyncer();

        // Should not throw for a non-existent skill
        syncer.syncSkill("nonexistent-skill", tempDir);

        assertThat(tempDir.resolve(".opencode/skills/nonexistent-skill")).doesNotExist();
    }
}
