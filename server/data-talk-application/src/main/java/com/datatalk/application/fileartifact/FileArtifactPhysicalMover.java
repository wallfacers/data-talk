package com.datatalk.application.fileartifact;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Atomic file move with .v2/.v3 versioning + double-stat TOCTOU defense.
 *
 * <p>Spec §A.3. Used by archive / discard / reattach use cases. No DB access.
 *
 * <p>Versioning rule: the suffix is inserted before the LAST dot
 * ({@code orders.md → orders.v2.md}, {@code foo.tar.gz → foo.tar.v2.gz},
 * {@code README → README.v2}). Multi-extension files are deliberately
 * simplified — accepted in spec §A.3.
 */
@Component
public class FileArtifactPhysicalMover {

    private static final int MAX_VERSION_ATTEMPTS = 999;

    public static final class TocTouChanged extends RuntimeException {
        public TocTouChanged(String msg) { super(msg); }
    }

    public static final class DiskFull extends RuntimeException {
        public DiskFull(String msg, Throwable cause) { super(msg, cause); }
    }

    public static final class MvFailed extends RuntimeException {
        public MvFailed(String msg, Throwable cause) { super(msg, cause); }
    }

    public static final class SourceMissing extends RuntimeException {
        public SourceMissing(String msg) { super(msg); }
    }

    /**
     * Move {@code src} to {@code dstDir / dstFilename}. If a file already
     * exists at the target path, append {@code .v2}, {@code .v3}, … before
     * the last dot until a free slot is found.
     *
     * @return the actual target path used (may be a versioned filename).
     */
    public Path mv(Path src, Path dstDir, String dstFilename) {
        if (!Files.exists(src, LinkOption.NOFOLLOW_LINKS)) {
            throw new SourceMissing("source file missing: " + src);
        }
        try {
            Files.createDirectories(dstDir);
        } catch (IOException e) {
            throw new MvFailed("failed to create dst dir: " + dstDir, e);
        }

        Path dst = freeTarget(dstDir, dstFilename);

        BasicFileAttributes before;
        try {
            before = Files.readAttributes(src, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new SourceMissing("failed to stat source: " + src);
        }

        try {
            betweenStats(src);
        } catch (IOException e) {
            // Test seam — production code is a no-op
        }

        BasicFileAttributes after;
        try {
            after = Files.readAttributes(src, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new SourceMissing("source vanished during move: " + src);
        }

        if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
            throw new TocTouChanged("source attributes changed between stats: " + src);
        }

        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                // Fallback: cross-FS move. NOT REPLACE_EXISTING (already avoided collision).
                Files.move(src, dst);
            } catch (IOException ee) {
                throw classify(ee, src, dst);
            }
        } catch (IOException e) {
            throw classify(e, src, dst);
        }
        return dst;
    }

    /**
     * Test seam: subclasses can mutate the source between the two stats to
     * exercise TOCTOU defense. Production code does not override.
     */
    protected void betweenStats(Path target) throws IOException {
        // no-op in production
    }

    static Path freeTarget(Path dstDir, String filename) {
        Path candidate = dstDir.resolve(filename);
        if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return candidate;
        }
        int v = 2;
        while (true) {
            Path versioned = dstDir.resolve(applyVersionSuffix(filename, v));
            if (!Files.exists(versioned, LinkOption.NOFOLLOW_LINKS)) {
                return versioned;
            }
            v++;
            if (v > MAX_VERSION_ATTEMPTS) {
                throw new MvFailed("too many version collisions for " + filename, null);
            }
        }
    }

    static String applyVersionSuffix(String filename, int version) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) {
            return filename + ".v" + version;
        }
        String stem = filename.substring(0, dot);
        String ext = filename.substring(dot);   // includes the dot
        return stem + ".v" + version + ext;
    }

    private static RuntimeException classify(IOException e, Path src, Path dst) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("no space") || msg.contains("disk full") || msg.contains("quota")) {
            return new DiskFull("disk full / quota: " + src + " -> " + dst, e);
        }
        return new MvFailed("mv failed: " + src + " -> " + dst, e);
    }
}