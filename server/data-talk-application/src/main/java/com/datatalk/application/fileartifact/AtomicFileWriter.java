package com.datatalk.application.fileartifact;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

final class AtomicFileWriter {

    private AtomicFileWriter() {}

    static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ch.write(ByteBuffer.wrap(bytes));
                ch.force(true);
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel dirCh = FileChannel.open(target.getParent(), StandardOpenOption.READ)) {
                dirCh.force(true);
            } catch (IOException ignored) {}
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
