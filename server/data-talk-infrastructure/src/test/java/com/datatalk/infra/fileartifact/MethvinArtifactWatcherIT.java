package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.FileWatchEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class MethvinArtifactWatcherIT {

    @TempDir
    Path root;

    MethvinArtifactWatcher watcher;
    List<FileWatchEvent> events;

    @BeforeEach
    void setUp() {
        watcher = new MethvinArtifactWatcher();
        events = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.close();
        }
    }

    @Test
    void emitsCreateEventWhenFileAppears() throws Exception {
        watcher.start(root, events::add);

        Path session = root.resolve("ses_x");
        Files.createDirectories(session);
        Files.writeString(session.resolve("foo.csv"), "id,val\n1,2\n");

        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(events).anyMatch(event -> event instanceof FileWatchEvent.Create
                        && event.path().getFileName().toString().equals("foo.csv")));
    }

    @Test
    void emitsModifyEventWhenFileChanges() throws Exception {
        Path session = root.resolve("ses_x");
        Files.createDirectories(session);
        Path file = session.resolve("foo.md");
        Files.writeString(file, "v1");
        watcher.start(root, events::add);
        events.clear();

        Files.writeString(file, "v2");

        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(events).anyMatch(event -> event instanceof FileWatchEvent.Modify
                        && event.path().getFileName().toString().equals("foo.md")));
    }

    @Test
    void emitsDeleteEventWhenFileIsRemoved() throws Exception {
        Path session = root.resolve("ses_x");
        Files.createDirectories(session);
        Path file = session.resolve("foo.txt");
        Files.writeString(file, "x");
        watcher.start(root, events::add);
        events.clear();

        Files.delete(file);

        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(events).anyMatch(event -> event instanceof FileWatchEvent.Delete
                        && event.path().getFileName().toString().equals("foo.txt")));
    }

    @Test
    void startIsNotReentrant() {
        watcher.start(root, events::add);

        assertThatThrownBy(() -> watcher.start(root, events::add))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeIsIdempotentBeforeAndAfterStart() {
        watcher.close();
        watcher.start(root, events::add);
        watcher.close();
        watcher.close();
    }

    @Test
    void symlinkEventsAreFilteredOut() throws Exception {
        Path victim = root.resolve("victim.md");
        Files.writeString(victim, "secret");
        Path session = root.resolve("ses_x");
        Files.createDirectories(session);
        watcher.start(root, events::add);
        try {
            Files.createSymbolicLink(session.resolve("link.md"), victim);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            org.junit.jupiter.api.Assumptions.abort("symlink not supported on this filesystem");
        }
        Files.writeString(session.resolve("ordinary.md"), "ok");

        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(events).anyMatch(event -> event.path().getFileName().toString().equals("ordinary.md")));
        await().pollDelay(ofMillis(500)).atMost(ofSeconds(2)).untilAsserted(() ->
                assertThat(events).noneMatch(event -> event.path().getFileName().toString().equals("link.md")));
    }
}
