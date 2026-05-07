package com.datatalk.application.housekeeping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMigrationRunnerTest {

    @TempDir Path workdir;
    LegacyMigrationRunner runner;

    @BeforeEach
    void setUp() {
        System.setProperty("DATA_TALK_WORKDIR", workdir.toString());
        runner = new LegacyMigrationRunner();
        System.clearProperty("DATA_TALK_WORKDIR");
    }

    @Test
    void migrates_non_whitelist_files_to_legacy_dir() throws IOException {
        Path opencodeDir = workdir.resolve("opencode");
        Files.createDirectories(opencodeDir);
        Files.writeString(opencodeDir.resolve("datatalk-tools-test-report.md"), "# report");
        Files.writeString(opencodeDir.resolve("AGENTS.md"), "AGENTS"); // whitelisted
        Files.writeString(opencodeDir.resolve("orphan.csv"), "a,b,c");

        runner.onApplicationReady();

        Path legacyDir = workdir.resolve("_legacy");
        assertThat(Files.exists(legacyDir.resolve("datatalk-tools-test-report.md"))).isTrue();
        assertThat(Files.exists(legacyDir.resolve("orphan.csv"))).isTrue();
        assertThat(Files.exists(opencodeDir.resolve("AGENTS.md"))).isTrue(); // not moved
        assertThat(Files.exists(workdir.resolve(".legacy-migrated"))).isTrue();
    }

    @Test
    void skips_when_marker_exists() throws IOException {
        Files.createDirectories(workdir.resolve("opencode"));
        Files.writeString(workdir.resolve("opencode").resolve("extra.txt"), "extra");
        Files.writeString(workdir.resolve(".legacy-migrated"), "done");

        runner.onApplicationReady();

        assertThat(Files.exists(workdir.resolve("_legacy").resolve("extra.txt"))).isFalse();
    }

    @Test
    void handles_missing_opencode_dir() {
        runner.onApplicationReady();
        assertThat(Files.exists(workdir.resolve(".legacy-migrated"))).isTrue();
    }

    @Test
    void whitelist_covers_opencode_backups_and_sessions() throws IOException {
        Path opencodeDir = workdir.resolve("opencode");
        Files.createDirectories(opencodeDir);
        Files.writeString(opencodeDir.resolve("opencode.json.dt-bak-1"), "bak");
        Files.createDirectories(opencodeDir.resolve("sessions").resolve("ses_x"));
        Files.writeString(opencodeDir.resolve("sessions").resolve("ses_x").resolve("f.md"), "f");
        Files.createDirectories(opencodeDir.resolve("plugins"));

        runner.onApplicationReady();

        assertThat(Files.exists(opencodeDir.resolve("opencode.json.dt-bak-1"))).isTrue();
        assertThat(Files.exists(opencodeDir.resolve("sessions"))).isTrue();
        assertThat(Files.exists(opencodeDir.resolve("plugins"))).isTrue();
    }
}
