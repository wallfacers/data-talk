package com.datatalk.application.fileartifact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileArtifactPhysicalMoverTest {

    @TempDir
    Path tmp;

    Path src;
    Path dstDir;
    FileArtifactPhysicalMover mover;

    @BeforeEach
    void setUp() {
        src = tmp.resolve("src");
        dstDir = tmp.resolve("dst");
        mover = new FileArtifactPhysicalMover();
    }

    @Test
    void mv_moves_file_to_target_when_no_collision() throws IOException {
        Files.createDirectories(src);
        Path file = src.resolve("orders.md");
        Files.writeString(file, "# orders\n");

        Path moved = mover.mv(file, dstDir, "orders.md");

        assertThat(moved).isEqualTo(dstDir.resolve("orders.md"));
        assertThat(Files.exists(moved)).isTrue();
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void mv_creates_dst_dir_if_missing() throws IOException {
        Files.createDirectories(src);
        Path file = src.resolve("a.md");
        Files.writeString(file, "x");

        assertThat(Files.exists(dstDir)).isFalse();
        mover.mv(file, dstDir, "a.md");
        assertThat(Files.exists(dstDir)).isTrue();
    }

    @Test
    void mv_applies_v2_suffix_on_collision() throws IOException {
        Files.createDirectories(src);
        Files.createDirectories(dstDir);
        Files.writeString(dstDir.resolve("orders.md"), "old");
        Path file = src.resolve("orders.md");
        Files.writeString(file, "new");

        Path moved = mover.mv(file, dstDir, "orders.md");

        assertThat(moved).isEqualTo(dstDir.resolve("orders.v2.md"));
        assertThat(Files.readString(moved)).isEqualTo("new");
        assertThat(Files.readString(dstDir.resolve("orders.md"))).isEqualTo("old");
    }

    @Test
    void mv_applies_v3_then_v4_when_v2_exists() throws IOException {
        Files.createDirectories(src);
        Files.createDirectories(dstDir);
        Files.writeString(dstDir.resolve("orders.md"), "v1");
        Files.writeString(dstDir.resolve("orders.v2.md"), "v2");
        Files.writeString(dstDir.resolve("orders.v3.md"), "v3");
        Path file = src.resolve("orders.md");
        Files.writeString(file, "new");

        Path moved = mover.mv(file, dstDir, "orders.md");

        assertThat(moved).isEqualTo(dstDir.resolve("orders.v4.md"));
    }

    @Test
    void mv_preserves_extension_with_dot_split_on_last_dot() throws IOException {
        Files.createDirectories(src);
        Files.createDirectories(dstDir);
        Files.writeString(dstDir.resolve("foo.tar.gz"), "old");
        Path file = src.resolve("foo.tar.gz");
        Files.writeString(file, "new");

        Path moved = mover.mv(file, dstDir, "foo.tar.gz");

        // spec §A.3: split by LAST dot only — accept simplification
        assertThat(moved).isEqualTo(dstDir.resolve("foo.tar.v2.gz"));
    }

    @Test
    void mv_with_no_extension_appends_v2() throws IOException {
        Files.createDirectories(src);
        Files.createDirectories(dstDir);
        Files.writeString(dstDir.resolve("README"), "old");
        Path file = src.resolve("README");
        Files.writeString(file, "new");

        Path moved = mover.mv(file, dstDir, "README");

        assertThat(moved).isEqualTo(dstDir.resolve("README.v2"));
    }

    @Test
    void mv_throws_TocTouChanged_when_size_differs_between_stats() throws IOException {
        Files.createDirectories(src);
        Path file = src.resolve("race.md");
        Files.writeString(file, "x");

        FileArtifactPhysicalMover slowMover = new FileArtifactPhysicalMover() {
            @Override
            protected void betweenStats(Path target) throws IOException {
                Files.writeString(target, "longer payload");
            }
        };

        assertThatThrownBy(() -> slowMover.mv(file, dstDir, "race.md"))
                .isInstanceOf(FileArtifactPhysicalMover.TocTouChanged.class);
    }

    @Test
    void mv_throws_SourceMissing_when_source_does_not_exist() {
        Path missing = src.resolve("nope.md");
        assertThatThrownBy(() -> mover.mv(missing, dstDir, "nope.md"))
                .isInstanceOf(FileArtifactPhysicalMover.SourceMissing.class);
    }

    @Test
    void mv_throws_MvFailed_when_version_limit_exceeded() throws IOException {
        Files.createDirectories(src);
        Files.createDirectories(dstDir);
        Files.writeString(dstDir.resolve("orders.md"), "v1");
        // Create v2 through v999 files (just create v999 to trigger limit)
        for (int i = 2; i <= 999; i++) {
            Files.writeString(dstDir.resolve("orders.v" + i + ".md"), "v" + i);
        }
        Path file = src.resolve("orders.md");
        Files.writeString(file, "new");

        assertThatThrownBy(() -> mover.mv(file, dstDir, "orders.md"))
                .isInstanceOf(FileArtifactPhysicalMover.MvFailed.class)
                .hasMessageContaining("too many version collisions");
    }
}