package com.datatalk.application.fileartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Manages per-session work directories under the OpenCode cwd.
 */
@Service
public class SessionWorkdirService {

    private static final Logger log = LoggerFactory.getLogger(SessionWorkdirService.class);
    private static final String META_FILENAME = ".meta.json";
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final SessionWorkdirRoot root;
    private final ObjectMapper json;

    public SessionWorkdirService(SessionWorkdirRoot root, ObjectMapper json) {
        this.root = root;
        this.json = json;
    }

    public Path getOrCreate(String sessionId, String connectionId) {
        String safeSessionId = requireSafeSessionId(sessionId);
        Path dir = root.sessionDir(safeSessionId);
        try {
            ensureManagedRoots();
            ensureNotSymlink(dir);
            if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(dir);
                } catch (FileAlreadyExistsException ignored) {
                    // Re-check below; another thread may have created it.
                }
            }
            ensureDirectory(dir);
            ensureRealChild(root.sessionsRoot(), dir);
            Path meta = dir.resolve(META_FILENAME);
            ensureNotSymlink(meta);
            if (!Files.exists(meta)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("sessionId", safeSessionId);
                body.put("connectionId", connectionId);
                body.put("createdAt", Instant.now().toString());
                Files.writeString(
                        meta,
                        json.writerWithDefaultPrettyPrinter().writeValueAsString(body),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            }
            return dir.toRealPath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session workdir " + dir, e);
        }
    }

    public Path require(String sessionId) {
        String safeSessionId = requireSafeSessionId(sessionId);
        Path dir = root.sessionDir(safeSessionId);
        ensureExistingManagedRootsAreNotSymlinks();
        ensureNotSymlink(dir);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Session workdir does not exist: " + dir);
        }
        try {
            ensureRealChild(root.sessionsRoot(), dir);
            return dir.toRealPath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve realpath of " + dir, e);
        }
    }

    public void delete(String sessionId) {
        String safeSessionId = requireSafeSessionId(sessionId);
        Path dir = root.sessionDir(safeSessionId);
        ensureExistingManagedRootsAreNotSymlinks();
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(dir)) {
            try {
                Files.deleteIfExists(dir);
            } catch (IOException e) {
                log.warn("Failed to delete symlinked session workdir {}: {}", dir, e.toString());
            }
            return;
        }
        try {
            ensureRealChild(root.sessionsRoot(), dir);
        } catch (IOException e) {
            log.warn("Refusing to delete session workdir outside managed root {}: {}", dir, e.toString());
            return;
        }
        try (Stream<Path> walk = Files.walk(dir.toRealPath())) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete {}: {}", path, e.toString());
                }
            });
        } catch (IOException e) {
            log.warn("Failed to walk session workdir for delete {}: {}", dir, e.toString());
        }
    }

    public SessionWorkdirRoot root() {
        return root;
    }

    public String relativeForPrompt(String sessionId) {
        return "./sessions/" + requireSafeSessionId(sessionId) + "/";
    }

    public static String requireSafeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || !SAFE_SESSION_ID.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("unsafe session id: " + sessionId);
        }
        Path path = Path.of(sessionId);
        if (path.isAbsolute() || path.getNameCount() != 1 || ".".equals(sessionId) || "..".equals(sessionId)) {
            throw new IllegalArgumentException("unsafe session id: " + sessionId);
        }
        return sessionId;
    }

    private void ensureManagedRoots() throws IOException {
        createManagedDirectory(root.dataTalkRoot());
        createManagedDirectory(root.opencodeCwd());
        ensureRealChild(root.dataTalkRoot(), root.opencodeCwd());
        createManagedDirectory(root.sessionsRoot());
        ensureRealChild(root.opencodeCwd(), root.sessionsRoot());
    }

    private void createManagedDirectory(Path dir) throws IOException {
        ensureNotSymlink(dir);
        Files.createDirectories(dir);
        ensureNotSymlink(dir);
        ensureDirectory(dir);
    }

    private void ensureExistingManagedRootsAreNotSymlinks() {
        ensureNotSymlink(root.dataTalkRoot());
        ensureNotSymlink(root.opencodeCwd());
        ensureNotSymlink(root.sessionsRoot());
    }

    private static void ensureNotSymlink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalStateException("Managed workdir path must not be a symlink: " + path);
        }
    }

    private static void ensureDirectory(Path path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Managed workdir path is not a directory: " + path);
        }
    }

    private static void ensureRealChild(Path parent, Path child) throws IOException {
        Path realParent = parent.toRealPath();
        Path realChild = child.toRealPath();
        if (!realChild.startsWith(realParent)) {
            throw new IllegalStateException("Managed workdir path escapes root: " + child);
        }
    }
}
