package com.datatalk.application.fileartifact;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Application-layer abstraction of low-level filesystem watcher events.
 */
public sealed interface FileWatchEvent
        permits FileWatchEvent.Create,
                FileWatchEvent.Modify,
                FileWatchEvent.Delete,
                FileWatchEvent.Rename,
                FileWatchEvent.Overflow {

    Path path();

    Instant observedAt();

    record Create(Path path, Instant observedAt) implements FileWatchEvent {
    }

    record Modify(Path path, Instant observedAt) implements FileWatchEvent {
    }

    record Delete(Path path, Instant observedAt) implements FileWatchEvent {
    }

    record Rename(Path path, Path previousPath, Instant observedAt) implements FileWatchEvent {
    }

    record Overflow(Path path, Instant observedAt) implements FileWatchEvent {
    }
}
