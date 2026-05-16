package com.datatalk.application.housekeeping;

import com.datatalk.application.fileartifact.FileArtifactReconciler;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.semantic.SemanticModelLoader;
import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.application.upload.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HousekeepingSchedulerTest {

    @TempDir Path workdir;
    FileArtifactReconciler reconciler = mock(FileArtifactReconciler.class);
    FileArtifactRepository fileArtifactRepo = mock(FileArtifactRepository.class);
    SemanticModelRepository semanticRepo = mock(SemanticModelRepository.class);
    SemanticModelLoader semanticLoader = mock(SemanticModelLoader.class);
    UploadedFileRepository uploadedFileRepo = mock(UploadedFileRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-05-07T03:00:00Z"), ZoneOffset.UTC);
    HousekeepingScheduler scheduler;

    @BeforeEach
    void setUp() {
        System.setProperty("DATA_TALK_WORKDIR", workdir.toString());
        scheduler = new HousekeepingScheduler(reconciler, fileArtifactRepo, semanticRepo, semanticLoader, uploadedFileRepo, clock);
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

    @Test
    void compactPatches_compacts_files_with_more_than_200_lines() throws IOException {
        Path semanticDir = workdir.resolve("semantic/conn_a");
        Files.createDirectories(semanticDir);
        Path patchFile = semanticDir.resolve("orders.patches.jsonl");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 201; i++) sb.append("{\"op\":\"INC_HIT_COUNT\",\"vq_id\":\"x\",\"delta\":1}\n");
        Files.writeString(patchFile, sb.toString());

        com.datatalk.domain.semantic.SemanticModel m = new com.datatalk.domain.semantic.SemanticModel(
            "orders", 1, "d",
            java.util.List.of(new com.datatalk.domain.semantic.Entity("orders", "fact",
                new com.datatalk.domain.semantic.Entity.Physical(null, null, "orders"),
                java.util.List.of("id"), java.util.List.of(), "")),
            java.util.List.of(), java.util.List.of(), java.util.List.of(),
            java.util.Map.of(), java.util.List.of(), Instant.now(), "test");
        when(semanticRepo.loadDomain("conn_a", "orders")).thenReturn(java.util.Optional.of(m));

        int compacted = scheduler.compactPatches();

        assertThat(compacted).isEqualTo(1);
        verify(semanticLoader).compact("conn_a", "orders", m);
    }

    @Test
    void compactPatches_skips_files_with_200_or_fewer_lines() throws IOException {
        Path semanticDir = workdir.resolve("semantic/conn_b");
        Files.createDirectories(semanticDir);
        Path patchFile = semanticDir.resolve("orders.patches.jsonl");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("{\"op\":\"INC_HIT_COUNT\",\"vq_id\":\"x\",\"delta\":1}\n");
        Files.writeString(patchFile, sb.toString());

        int compacted = scheduler.compactPatches();

        assertThat(compacted).isEqualTo(0);
        verifyNoInteractions(semanticLoader);
    }

    @Test
    void compactPatches_returns_zero_when_semantic_dir_missing() {
        int compacted = scheduler.compactPatches();
        assertThat(compacted).isEqualTo(0);
    }

    @Test
    void cleanupExpiredPending_moves_files_older_than_30_days_to_trash() throws IOException {
        Path pendingDir = workdir.resolve("semantic/conn_c/pending");
        Files.createDirectories(pendingDir);
        Path expired = pendingDir.resolve("legacy.model.yaml");
        Files.writeString(expired, "name: legacy");
        Files.setLastModifiedTime(expired, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-03-01T00:00:00Z")));   // > 30 days before fixed clock 2026-05-07
        Path recent = pendingDir.resolve("fresh.model.yaml");
        Files.writeString(recent, "name: fresh");
        Files.setLastModifiedTime(recent, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-05-05T00:00:00Z")));   // within 30 days

        int cleaned = scheduler.cleanupExpiredPending();

        assertThat(cleaned).isEqualTo(1);
        assertThat(Files.exists(expired)).isFalse();
        assertThat(Files.exists(recent)).isTrue();

        Path trashSemantic = workdir.resolve("_trash/semantic");
        long movedCount;
        try (Stream<Path> s = Files.list(trashSemantic)) {
            movedCount = s.filter(p -> p.getFileName().toString().contains("expired-legacy.model.yaml")).count();
        }
        assertThat(movedCount).isEqualTo(1L);
    }

    @Test
    void cleanupExpiredPending_returns_zero_when_no_pending_dirs() {
        int cleaned = scheduler.cleanupExpiredPending();
        assertThat(cleaned).isEqualTo(0);
    }

    @Test
    void cleanupSemanticTrash_physically_deletes_dirs_older_than_30_days() throws IOException {
        Path trashDir = workdir.resolve("_trash/semantic");
        Files.createDirectories(trashDir);
        Path oldEntry = trashDir.resolve("1000-conn_old");
        Files.createDirectories(oldEntry);
        Files.writeString(oldEntry.resolve("orders.model.yaml"), "x");
        Files.setLastModifiedTime(oldEntry, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-01-01T00:00:00Z")));
        Path freshEntry = trashDir.resolve("2000-conn_new");
        Files.createDirectories(freshEntry);
        Files.writeString(freshEntry.resolve("orders.model.yaml"), "y");
        Files.setLastModifiedTime(freshEntry, java.nio.file.attribute.FileTime.from(
                Instant.parse("2026-05-01T00:00:00Z")));

        int removed = scheduler.cleanupSemanticTrash();

        assertThat(removed).isEqualTo(1);
        assertThat(Files.exists(oldEntry)).isFalse();
        assertThat(Files.exists(freshEntry)).isTrue();
    }

    @Test
    void cleanupSemanticTrash_returns_zero_when_dir_missing() {
        int removed = scheduler.cleanupSemanticTrash();
        assertThat(removed).isEqualTo(0);
    }
}
