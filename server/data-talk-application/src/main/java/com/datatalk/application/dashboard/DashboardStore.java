package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * File-system backed store for dashboard JSON documents.
 * Each dashboard is stored as {@code <baseDir>/<id>.dashboard.json}.
 */
@Component
public class DashboardStore {

    private static final String SUFFIX = ".dashboard.json";
    private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final Path baseDir;
    private final ObjectMapper mapper;

    public DashboardStore(Path baseDir, ObjectMapper mapper) {
        this.baseDir = baseDir;
        this.mapper = mapper;
    }

    public void init() throws IOException {
        Files.createDirectories(baseDir);
    }

    public void save(String id, JsonNode doc) throws IOException {
        Path file = resolve(id);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temp, mapper.writeValueAsString(doc));
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public JsonNode load(String id) throws IOException {
        Path file = resolve(id);
        if (!Files.exists(file)) {
            return null;
        }
        return mapper.readTree(Files.readString(file));
    }

    public boolean exists(String id) {
        return Files.exists(resolve(id));
    }

    private Path resolve(String id) {
        if (!SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid dashboard id: " + id);
        }
        Path resolved = baseDir.resolve(id + SUFFIX).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Path traversal detected for id: " + id);
        }
        return resolved;
    }
}
