package com.datatalk.application.fileartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SessionWorkdirServiceTest {

    @TempDir
    Path tmp;

    SessionWorkdirRoot root;
    SessionWorkdirService service;

    @BeforeEach
    void setUp() {
        root = new SessionWorkdirRoot(tmp, tmp.resolve("opencode"));
        service = new SessionWorkdirService(root, new ObjectMapper());
    }

    @Test
    void getOrCreateCreatesSessionDirectoryAndMetaFile() throws Exception {
        Path dir = service.getOrCreate("ses_abc", "conn_xyz");

        assertThat(dir).isDirectory();
        Path meta = dir.resolve(".meta.json");
        assertThat(meta).exists();
        assertThat(Files.readString(meta))
                .contains("\"sessionId\" : \"ses_abc\"")
                .contains("\"connectionId\" : \"conn_xyz\"")
                .contains("\"createdAt\"");
    }

    @Test
    void getOrCreateRejectsTraversalSessionId() {
        assertThatThrownBy(() -> service.getOrCreate("../escape", "conn_xyz"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe session id");
        assertThat(tmp.resolve("escape")).doesNotExist();
    }

    @Test
    void getOrCreateRejectsSymlinkedSessionDirectory() throws Exception {
        Path outside = tmp.resolve("outside");
        Files.createDirectories(outside);
        Files.createDirectories(root.sessionsRoot());
        createSymlinkOrSkip(root.sessionDir("ses_link"), outside);

        assertThatThrownBy(() -> service.getOrCreate("ses_link", "conn_xyz"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("symlink");
    }

    @Test
    void getOrCreateIsIdempotentAndKeepsExistingMeta() throws Exception {
        Path first = service.getOrCreate("ses_abc", "conn_xyz");
        Path meta = first.resolve(".meta.json");
        String original = Files.readString(meta);

        Path second = service.getOrCreate("ses_abc", "conn_changed");

        assertThat(second).isEqualTo(first);
        assertThat(Files.readString(meta)).isEqualTo(original);
    }

    @Test
    void requireReturnsExistingSessionDirectory() {
        Path dir = service.getOrCreate("ses_abc", "conn_xyz");

        assertThat(service.require("ses_abc")).isEqualTo(dir);
    }

    @Test
    void requireRejectsMissingSessionDirectory() {
        assertThatThrownBy(() -> service.require("ses_missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Session workdir does not exist");
    }

    @Test
    void requireRejectsTraversalSessionId() {
        assertThatThrownBy(() -> service.require("../escape"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe session id");
    }

    @Test
    void deleteRecursivelyRemovesSessionSubtree() throws Exception {
        Path dir = service.getOrCreate("ses_abc", "conn_xyz");
        Files.writeString(dir.resolve("sample.csv"), "id,val\n1,2");
        Files.createDirectories(dir.resolve("sub").resolve("nested"));
        Files.writeString(dir.resolve("sub/nested/report.md"), "# Report\n");

        service.delete("ses_abc");

        assertThat(dir).doesNotExist();
    }

    @Test
    void deleteIsTolerantOfMissingDirectory() {
        service.delete("ses_missing");
    }

    @Test
    void relativeForPromptRendersSessionSubdir() {
        assertThat(service.relativeForPrompt("ses_abc")).isEqualTo("./sessions/ses_abc/");
    }

    @Test
    void relativeForPromptRejectsUnsafeSessionId() {
        assertThatThrownBy(() -> service.relativeForPrompt("a/b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe session id");
    }

    private static void createSymlinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available: " + e);
        }
    }
}
