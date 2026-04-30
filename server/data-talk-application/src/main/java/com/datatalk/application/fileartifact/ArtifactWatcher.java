package com.datatalk.application.fileartifact;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Filesystem watcher port. Implementations own native watcher lifecycle details.
 */
public interface ArtifactWatcher extends AutoCloseable {

    void start(Path rootPath, Consumer<FileWatchEvent> listener);

    @Override
    void close();
}
