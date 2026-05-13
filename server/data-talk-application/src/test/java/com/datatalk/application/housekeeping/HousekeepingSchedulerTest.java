package com.datatalk.application.housekeeping;

import com.datatalk.application.fileartifact.FileArtifactReconciler;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HousekeepingSchedulerTest {

    @TempDir Path workdir;
    FileArtifactReconciler reconciler = mock(FileArtifactReconciler.class);
    FileArtifactRepository fileArtifactRepo = mock(FileArtifactRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-05-07T03:00:00Z"), ZoneOffset.UTC);
    HousekeepingScheduler scheduler;

    @BeforeEach
    void setUp() {
        System.setProperty("DATA_TALK_WORKDIR", workdir.toString());
        scheduler = new HousekeepingScheduler(reconciler, fileArtifactRepo, clock);
        System.clearProperty("DATA_TALK_WORKDIR");
    }

    @Test
    void cleanupTrash_deletes_files_older_than_7_days() throws IOException {
        Path trashDir = workdir.resolve("_trash");
        Files.createDirectories(trashDir);
        Path oldFile = trashDir.resolve("ses_a__fa_1__report.md");
        Files.writeString(oldFile, "old");
        Files.setLastModifiedTime(oldFile, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-04-01T00:00:00Z")));
        Path recentFile = trashDir.resolve("ses_b__fa_2__orders.md");
        Files.writeString(recentFile, "recent");
        Files.setLastModifiedTime(recentFile, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-05-06T00:00:00Z")));

        scheduler.cleanupTrash();

        assertThat(Files.exists(oldFile)).isFalse();
        assertThat(Files.exists(recentFile)).isTrue();
    }

    @Test
    void cleanupLegacyNow_recursively_deletes_legacy_contents() throws IOException {
        Path legacy = workdir.resolve("_legacy");
        Path v146 = legacy.resolve("v1.4.6");
        Files.createDirectories(v146);
        Files.writeString(legacy.resolve("note.md"), "x");
        Files.writeString(v146.resolve("package.json"), "{}");
        Files.writeString(v146.resolve("lock.json"), "{}");

        int removed = scheduler.cleanupLegacyNow();

        assertThat(removed).isEqualTo(3);
        assertThat(Files.exists(legacy)).isTrue();      // root preserved
        assertThat(Files.exists(v146)).isFalse();        // subdirs gone
        assertThat(Files.list(legacy).count()).isEqualTo(0L);
    }

    @Test
    void cleanupTrashNow_deletes_all_files_regardless_of_age() throws IOException {
        Path trashDir = workdir.resolve("_trash");
        Files.createDirectories(trashDir);
        Path oldFile = trashDir.resolve("ses_a__fa_1__report.md");
        Files.writeString(oldFile, "old");
        Files.setLastModifiedTime(oldFile, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-04-01T00:00:00Z")));
        Path recentFile = trashDir.resolve("ses_b__fa_2__orders.md");
        Files.writeString(recentFile, "recent");
        Files.setLastModifiedTime(recentFile, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-05-06T00:00:00Z")));

        int removed = scheduler.cleanupTrashNow();

        assertThat(removed).isEqualTo(2);
        assertThat(Files.exists(oldFile)).isFalse();
        assertThat(Files.exists(recentFile)).isFalse();
        verify(fileArtifactRepo).deleteDiscardedById("fa_1");
        verify(fileArtifactRepo).deleteDiscardedById("fa_2");
    }

    @Test
    void rotateOpencodeBackups_keeps_5_recent_and_within_7_days() throws IOException {
        Path opencodeDir = workdir.resolve("opencode");
        Files.createDirectories(opencodeDir);
        for (int i = 0; i < 10; i++) {
            Path f = opencodeDir.resolve("opencode.json.dt-bak-" + i);
            Files.writeString(f, "bak" + i);
            Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.from(
                    clock.instant().minus(java.time.Duration.ofDays(i))));
        }
        scheduler.rotateOpencodeBackups();
        long remaining = Files.list(opencodeDir)
                .filter(p -> p.getFileName().toString().startsWith("opencode.json.dt-bak-"))
                .count();
        assertThat(remaining).isBetween(5L, 8L);
    }

    @Test
    void runNightly_writes_housekeeping_log() throws IOException {
        scheduler.runNightly();

        Path logFile = workdir.resolve("housekeeping.log");
        assertThat(Files.exists(logFile)).isTrue();
        String content = Files.readString(logFile);
        assertThat(content).contains("\"task\":\"rotate-backup\"");
        assertThat(content).contains("\"task\":\"cleanupTrash\"");
        assertThat(content).contains("\"task\":\"reconcile\"");
    }

    @Test
    void runNightly_calls_reconciler() {
        scheduler.runNightly();
        verify(reconciler).runFullReconcile();
    }
}
