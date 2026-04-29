package com.datatalk.application.fileartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manages per-session work directories under the OpenCode cwd.
 */
@Service
public class SessionWorkdirService {

    private static final Logger log = LoggerFactory.getLogger(SessionWorkdirService.class);
    private static final String META_FILENAME = ".meta.json";

    private final SessionWorkdirRoot root;
    private final ObjectMapper json;

    public SessionWorkdirService(SessionWorkdirRoot root, ObjectMapper json) {
        this.root = root;
        this.json = json;
    }

    public Path getOrCreate(String sessionId, String connectionId) {
        Path dir = root.sessionDir(sessionId);
        try {
            Files.createDirectories(dir);
            Path meta = dir.resolve(META_FILENAME);
            if (!Files.exists(meta)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("sessionId", sessionId);
                body.put("connectionId", connectionId);
                body.put("createdAt", Instant.now().toString());
                Files.writeString(meta, json.writerWithDefaultPrettyPrinter().writeValueAsString(body));
            }
            return dir.toRealPath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session workdir " + dir, e);
        }
    }

    public Path require(String sessionId) {
        Path dir = root.sessionDir(sessionId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Session workdir does not exist: " + dir);
        }
        try {
            return dir.toRealPath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve realpath of " + dir, e);
        }
    }

    public void delete(String sessionId) {
        Path dir = root.sessionDir(sessionId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
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
        return "./sessions/" + sessionId + "/";
    }
}
