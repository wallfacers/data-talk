package com.datatalk.application.fileartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
