package com.datatalk.adapter.fileartifact;

import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.SessionWorkdirService;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.fileartifact.FileArtifactStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=never")
class FileArtifactWatcherE2EIT {

    @TempDir
    static Path workdirRoot;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("datatalk.workdir.data-talk-root", () -> workdirRoot.toString());
    }

    @Autowired
    FileArtifactRepository repo;

    @Autowired
    SessionWorkdirService workdir;

    @Test
    void csvDroppedIntoSessionDirAppearsAsTemporaryRow() throws Exception {
        Path session = workdir.getOrCreate("ses_watcher_e2e_csv", "conn_test");
        Path file = session.resolve("sample.csv");
        Files.writeString(file, "id,val\n1,2\n");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(2)));

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            FileArtifact row = repo.findBySession("ses_watcher_e2e_csv").stream()
                    .filter(candidate -> candidate.filename().equals("sample.csv"))
                    .findFirst()
                    .orElseThrow();
            assertThat(row.status()).isEqualTo(FileArtifactStatus.TEMPORARY);
        });
    }

    @Test
    void markdownFrontmatterDroppedIntoSessionDirAppearsAsCandidateRow() throws Exception {
        Path session = workdir.getOrCreate("ses_watcher_e2e_md", "conn_test");
        Path file = session.resolve("orders-er.md");
        Files.writeString(file, """
                ---
                artifact: true
                kind: er_diagram
                title: Orders
                ---

                # body
                """);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(2)));

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            FileArtifact row = repo.findBySession("ses_watcher_e2e_md").stream()
                    .filter(candidate -> candidate.filename().equals("orders-er.md"))
                    .findFirst()
                    .orElseThrow();
            assertThat(row.status()).isEqualTo(FileArtifactStatus.CANDIDATE);
            assertThat(row.title()).isEqualTo("Orders");
        });
    }
}
