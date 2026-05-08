package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File-system backed store for dashboard JSON documents.
 * Each dashboard is stored as {@code <baseDir>/<id>.dashboard.json}.
 */
public class DashboardStore {

    private static final String SUFFIX = ".dashboard.json";

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
        Files.writeString(file, mapper.writeValueAsString(doc));
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
        // Sanitize id to prevent path traversal
        String safeId = id.replaceAll("[^a-zA-Z0-9_]", "");
        return baseDir.resolve(safeId + SUFFIX);
    }
}
