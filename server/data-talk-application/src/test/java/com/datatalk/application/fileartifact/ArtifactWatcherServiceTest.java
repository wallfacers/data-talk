package com.datatalk.application.fileartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class ArtifactWatcherServiceTest {

    @TempDir
    Path tmp;

    SessionWorkdirRoot root;
    SessionWorkdirService workdir;
    ArtifactWatcher watcher;
    FileArtifactService artifactService;
    FileArtifactReconciler reconciler;
    ArtifactWatcherService service;
    AtomicReference<Consumer<FileWatchEvent>> listener;

    @BeforeEach
    void setUp() throws Exception {
        root = new SessionWorkdirRoot(tmp, tmp.resolve("opencode"));
        workdir = new SessionWorkdirService(root, new ObjectMapper());
        Files.createDirectories(root.sessionsRoot());
        watcher = mock(ArtifactWatcher.class);
        artifactService = mock(FileArtifactService.class);
        reconciler = mock(FileArtifactReconciler.class);
        listener = new AtomicReference<>();
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<FileWatchEvent> captured = (Consumer<FileWatchEvent>) inv.getArgument(1);
            listener.set(captured);
            return null;
        }).when(watcher).start(any(), any());
        service = new ArtifactWatcherService(watcher, artifactService, workdir, reconciler);
        service.start();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void startWatchesCanonicalSessionsRoot() throws Exception {
        verify(watcher).start(eq(root.sessionsRoot().toRealPath()), any());
    }

    @Test
    void startCanonicalizesSessionsRootAndAcceptsCanonicalEventPaths() throws Exception {
        service.close();

        Path realDataRoot = tmp.resolve("real-data");
        Files.createDirectories(realDataRoot);
        Path linkedDataRoot = tmp.resolve("linked-data");
        createSymlinkOrSkip(linkedDataRoot, realDataRoot);

        SessionWorkdirRoot linkedRoot = new SessionWorkdirRoot(linkedDataRoot, linkedDataRoot.resolve("opencode"));
        SessionWorkdirService linkedWorkdir = new SessionWorkdirService(linkedRoot, new ObjectMapper());
        ArtifactWatcher linkedWatcher = mock(ArtifactWatcher.class);
        AtomicReference<Consumer<FileWatchEvent>> linkedListener = new AtomicReference<>();
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<FileWatchEvent> captured = (Consumer<FileWatchEvent>) inv.getArgument(1);
            linkedListener.set(captured);
            return null;
        }).when(linkedWatcher).start(any(), any());
        service = new ArtifactWatcherService(linkedWatcher, artifactService, linkedWorkdir, reconciler);

        service.start();

        Path canonicalSessionsRoot = linkedRoot.sessionsRoot().toRealPath();
        verify(linkedWatcher).start(eq(canonicalSessionsRoot), any());

        Path sessionDir = canonicalSessionsRoot.resolve("ses_linked");
        Files.createDirectories(sessionDir);
        Path file = sessionDir.resolve("foo.csv");
        Files.writeString(file, "id,val\n1,2\n");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(2)));

        linkedListener.get().accept(new FileWatchEvent.Create(file, Instant.now()));

        verify(artifactService, timeout(2_000)).recordDetected(eq("ses_linked"), eq(file), any());
    }

    @Test
    void createEventDispatchesDetectedWithSessionId() throws Exception {
        Path file = stableFile("ses_abc", "foo.csv", "x");
        Path canonicalFile = file.toRealPath();

        listener.get().accept(new FileWatchEvent.Create(file, Instant.now()));

        verify(artifactService, timeout(2_000)).recordDetected(eq("ses_abc"), eq(canonicalFile), any());
    }

    @Test
    void modifyEventDispatchesModified() throws Exception {
        Path file = stableFile("ses_abc", "foo.md", "x");
        Path canonicalFile = file.toRealPath();

        listener.get().accept(new FileWatchEvent.Modify(file, Instant.now()));

        verify(artifactService, timeout(2_000)).recordModified(eq(canonicalFile), any());
    }

    @Test
    void deleteEventDispatchesDeletedEvenWhenFileIsAbsent() throws Exception {
        Path file = root.sessionsRoot().toRealPath().resolve("ses_abc").resolve("gone.md");

        listener.get().accept(new FileWatchEvent.Delete(file, Instant.now()));

        verify(artifactService, timeout(2_000)).recordDeleted(file);
    }

    @Test
    void renameEventDispatchesDeleteAndCreate() throws Exception {
        Path oldPath = root.sessionsRoot().toRealPath().resolve("ses_abc").resolve("old.md");
        Path newPath = stableFile("ses_abc", "new.md", "x");
        Path canonicalNewPath = newPath.toRealPath();

        listener.get().accept(new FileWatchEvent.Rename(newPath, oldPath, Instant.now()));

        verify(artifactService, timeout(2_000)).recordDeleted(oldPath);
        verify(artifactService, timeout(2_000)).recordDetected(eq("ses_abc"), eq(canonicalNewPath), any());
    }

    @Test
    void overflowRunsFullReconcile() {
        listener.get().accept(new FileWatchEvent.Overflow(root.sessionsRoot(), Instant.now()));

        verify(reconciler, timeout(2_000)).runFullReconcile();
    }

    @Test
    void hiddenAndTempFilesAreIgnored() throws Exception {
        Path hidden = stableFile("ses_abc", ".hidden.md", "x");
        Path partial = stableFile("ses_abc", "foo.csv.partial", "x");

        listener.get().accept(new FileWatchEvent.Create(hidden, Instant.now()));
        listener.get().accept(new FileWatchEvent.Create(partial, Instant.now()));

        Thread.sleep(500);
        verify(artifactService, never()).recordDetected(any(), any(), any());
    }

    @Test
    void userFileEndingWithMetaJsonIsNotIgnored() throws Exception {
        Path file = stableFile("ses_abc", "foo.meta.json", "{}");
        Path canonicalFile = file.toRealPath();

        listener.get().accept(new FileWatchEvent.Create(file, Instant.now()));

        verify(artifactService, timeout(2_000)).recordDetected(eq("ses_abc"), eq(canonicalFile), any());
    }

    @Test
    void fileDirectlyUnderSessionsRootIsIgnored() throws Exception {
        Path file = root.sessionsRoot().resolve("orphan.md");
        Files.writeString(file, "x");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(2)));

        listener.get().accept(new FileWatchEvent.Create(file, Instant.now()));

        Thread.sleep(500);
        verify(artifactService, never()).recordDetected(any(), any(), any());
    }

    @Test
    void repeatedModifyEventsForSamePathAreDebounced() throws Exception {
        Path file = stableFile("ses_abc", "foo.md", "x");
        Path canonicalFile = file.toRealPath();

        for (int i = 0; i < 5; i++) {
            listener.get().accept(new FileWatchEvent.Modify(file, Instant.now()));
            Thread.sleep(20);
        }

        await().atMost(ofSeconds(2)).untilAsserted(() ->
                verify(artifactService, org.mockito.Mockito.times(1)).recordModified(eq(canonicalFile), any()));
    }

    @Test
    void inferSessionIdUsesFirstCanonicalPathSegmentUnderSessionsRoot() throws Exception {
        Path file = root.sessionsRoot().toRealPath().resolve("ses_target").resolve("sub").resolve("file.md");

        assertThat(service.inferSessionId(file)).isEqualTo("ses_target");
    }

    private Path stableFile(String sessionId, String filename, String body) throws Exception {
        Path session = root.sessionDir(sessionId);
        Files.createDirectories(session);
        Path file = session.resolve(filename);
        Files.writeString(file, body);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(2)));
        return file;
    }

    private static void createSymlinkOrSkip(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            assumeTrue(false, "symbolic links are not available: " + e);
        }
    }
}
