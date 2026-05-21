package com.datatalk.application.fileartifact;

import java.io.IOException;
import java.nio.file.Path;

public final class AtomicFileWriterBridge {
    private AtomicFileWriterBridge() {}
    public static void write(Path target, byte[] bytes) throws IOException {
        AtomicFileWriter.writeAtomically(target, bytes);
    }
}
