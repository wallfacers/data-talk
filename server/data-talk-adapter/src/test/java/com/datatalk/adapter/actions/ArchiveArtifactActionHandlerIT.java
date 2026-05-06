package com.datatalk.adapter.actions;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.fileartifact.SessionWorkdirService;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DataTalkApplication.class)
class ArchiveArtifactActionHandlerIT {

    private static final String SESSION_ID = "ses_arc";
    private static final String CONNECTION_ID = "conn_xyz";

    @TempDir
    static Path TMP;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("datatalk.workdir.data-talk-root", () -> TMP.toString());
    }

    @Autowired
    ArchiveArtifactAction action;

    @Autowired
    SessionWorkdirService workdir;

    @Autowired
    ApplicationContext springCtx;

    private Path sessionDir;

    @BeforeEach
    void setUp() throws IOException {
        // Clean and recreate the session directory
        workdir.delete(SESSION_ID);
        sessionDir = workdir.getOrCreate(SESSION_ID, CONNECTION_ID);
    }

    @Test
    void happy_path_md_file_returns_ok_and_candidate_id() throws Exception {
        Files.writeString(sessionDir.resolve("happy-report.md"), "# Report\n");

        Map<String, Object> result = invoke(Map.of(
                "path", "happy-report.md",
                "kind", "report",
                "title", "Happy Report",
                "summary", "A happy path test"
        ));

        assertThat(result).containsEntry("ok", true);
        assertThat(result).containsEntry("status", "candidate");
        assertThat((String) result.get("fileArtifactId")).startsWith("file_artifact_");
        assertThat((String) result.get("physicalPath")).endsWith("happy-report.md");
        assertThat(result).doesNotContainKey("warn");
        assertThat(result).doesNotContainKey("error");
    }

    @Test
    void path_traversal_rejected() throws Exception {
        Map<String, Object> result = invoke(Map.of(
                "path", "../foo.md",
                "kind", "report"
        ));

        assertThat(result).containsEntry("ok", false);
        assertThat(result).containsEntry("error", "path_outside_session_dir");
    }

    @Test
    void absolute_path_rejected() throws Exception {
        Map<String, Object> result = invoke(Map.of(
                "path", "/etc/passwd",
                "kind", "report"
        ));

        assertThat(result).containsEntry("ok", false);
        assertThat(result).containsEntry("error", "path_outside_session_dir");
    }

    @Test
    void underscore_prefixed_directory_rejected() throws Exception {
        Map<String, Object> result = invoke(Map.of(
                "path", "_systemdir/foo.md",
                "kind", "report"
        ));

        assertThat(result).containsEntry("ok", false);
        assertThat(result).containsEntry("error", "path_is_system");
    }

    @Test
    void symlink_pointing_outside_rejected() throws Exception {
        // Create a file outside the session dir
        Path outsideFile = TMP.resolve("outside-target.md");
        Files.writeString(outsideFile, "outside content\n");

        // Create a symlink inside the session dir pointing outside
        Files.createSymbolicLink(sessionDir.resolve("evil-link.md"), outsideFile);

        Map<String, Object> result = invoke(Map.of(
                "path", "evil-link.md",
                "kind", "report"
        ));

        assertThat(result).containsEntry("ok", false);
        Set<String> expectedErrors = Set.of("path_contains_symlink", "path_outside_session_dir");
        assertThat(expectedErrors).contains((String) result.get("error"));
    }

    @Test
    void directory_not_file_rejected() throws Exception {
        Files.createDirectory(sessionDir.resolve("a-subdir"));

        Map<String, Object> result = invoke(Map.of(
                "path", "a-subdir",
                "kind", "report"
        ));

        assertThat(result).containsEntry("ok", false);
        assertThat(result).containsEntry("error", "path_is_directory");
    }

    @Test
    void missing_file_rejected() throws Exception {
        Map<String, Object> result = invoke(Map.of(
                "path", "missing.md",
                "kind", "report"
        ));

        assertThat(result).containsEntry("ok", false);
        assertThat(result).containsEntry("error", "path_not_found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void already_archived_returns_ok_with_warn() throws Exception {
        Files.writeString(sessionDir.resolve("orders-er.md"), "# ER\n");

        // First call creates CANDIDATE
        Map<String, Object> first = invoke(Map.of(
                "path", "orders-er.md",
                "kind", "er_diagram",
                "title", "Orders ER"
        ));
        assertThat(first).containsEntry("ok", true);
        String fid = (String) first.get("fileArtifactId");
        String physicalPath = (String) first.get("physicalPath");

        // Mark as archived via FileArtifactRepository
        FileArtifactRepository repo = springCtx.getBean(FileArtifactRepository.class);
        repo.markArchived(fid, CONNECTION_ID, physicalPath);

        // Second call should return ok=true with warn
        Map<String, Object> second = invoke(Map.of(
                "path", "orders-er.md",
                "kind", "er_diagram",
                "title", "Orders ER"
        ));

        assertThat(second).containsEntry("ok", true);
        assertThat(second).containsEntry("warn", "already_archived");
        assertThat(second).containsEntry("fileArtifactId", fid);
    }

    private Map<String, Object> invoke(Map<String, Object> input) throws Exception {
        return (Map<String, Object>) action.handle(
                new ActionContext(SESSION_ID, "call-1", CONNECTION_ID, "oc-1"),
                input).toCompletableFuture().get();
    }
}
