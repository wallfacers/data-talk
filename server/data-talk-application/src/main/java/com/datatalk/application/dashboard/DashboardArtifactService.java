package com.datatalk.application.dashboard;

import com.datatalk.application.fileartifact.AtomicFileWriterBridge;
import com.datatalk.application.fileartifact.FileArtifactConflictException;
import com.datatalk.application.fileartifact.FileArtifactNotFoundException;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DashboardArtifactService {

    private static final Logger log = LoggerFactory.getLogger(DashboardArtifactService.class);

    private static final int MAX_PAYLOAD_BYTES = 256 * 1024; // 256 KB

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private final FileArtifactService fileArtifactService;
    private final SessionWorkdirRoot workdirRoot;
    private final DashboardSchemaValidator validator;
    private final DashboardCompiler compiler;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DashboardArtifactService(
            FileArtifactService fileArtifactService,
            SessionWorkdirRoot workdirRoot,
            DashboardSchemaValidator validator,
            DashboardCompiler compiler,
            ObjectMapper mapper,
            Clock clock) {
        this.fileArtifactService = fileArtifactService;
        this.workdirRoot = workdirRoot;
        this.validator = validator;
        this.compiler = compiler;
        this.mapper = mapper;
        this.clock = clock;
    }

    public record PromoteResult(String id, int version, String html) {}

    public PromoteResult promote(JsonNode dashboard, String originSessionId) {
        int approxSize = dashboard.toString().length();
        if (approxSize > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException(approxSize, MAX_PAYLOAD_BYTES);
        }
        ValidationResult validation = validator.validate(dashboard);
        if (!validation.ok()) throw new ValidationException(validation);

        // Compile JSON → HTML
        var compileResult = compiler.compile(dashboard);
        if (!compileResult.ok()) {
            throw new ValidationException(new ValidationResult(
                compileResult.errors().stream()
                    .map(e -> new ValidationResult.Error(e.path(), "compile", e.getMessage()))
                    .toList()));
        }
        String html = compileResult.html();

        String id = DashboardIds.newDashboardId();
        long now = clock.millis();
        ObjectNode mutable = dashboard.deepCopy();
        mutable.put("id", id);
        mutable.put("version", 1);
        mutable.put("createdAt", now);
        mutable.put("updatedAt", now);

        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(mutable);
        } catch (IOException e) {
            throw new DashboardPersistenceException("serialize failed: " + id, e);
        }
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException(bytes.length, MAX_PAYLOAD_BYTES);
        }

        Path target = workdirRoot.dashboardsRoot().resolve(id + ".dashboard.json").toAbsolutePath();
        try {
            AtomicFileWriterBridge.write(target, bytes);
        } catch (IOException e) {
            throw new DashboardPersistenceException("atomic write failed: " + id, e);
        }

        try {
            String connectionId = mutable.path("defaultConnectionId").asText(null);
            String title = mutable.path("title").asText(null);
            Map<String, Object> meta = originSessionId == null ? Map.of()
                                                                : Map.of("originSessionId", originSessionId);
            fileArtifactService.registerExternal(
                    id, FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                    connectionId, null,
                    target, title, null, meta);
        } catch (IOException | FileArtifactConflictException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            throw new DashboardPersistenceException("registerExternal failed: " + id, e);
        }

        // Store compiled HTML (with __BEZEL_SERVER_ORIGIN__ placeholder)
        storeHtmlArtifact(id, html.getBytes(StandardCharsets.UTF_8));

        return new PromoteResult(id, 1, html);
    }

    public PromoteResult promote(JsonNode dashboard) {
        return promote(dashboard, null);
    }

    public record UpdateResult(int version, String html) {}

    /**
     * Persist an updated dashboard: validate + compile the new JSON, then under the
     * per-dashboard lock write the new JSON (with bumped version + updatedAt, preserved
     * id/createdAt) and store the freshly compiled HTML. Returns the authoritative new version.
     */
    public UpdateResult update(String id, JsonNode newDashboard) {
        int approxSize = newDashboard.toString().length();
        if (approxSize > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException(approxSize, MAX_PAYLOAD_BYTES);
        }
        ValidationResult validation = validator.validate(newDashboard);
        if (!validation.ok()) throw new ValidationException(validation);

        var compileResult = compiler.compile(newDashboard);
        if (!compileResult.ok()) {
            throw new ValidationException(new ValidationResult(
                compileResult.errors().stream()
                    .map(e -> new ValidationResult.Error(e.path(), "compile", e.getMessage()))
                    .toList()));
        }
        String html = compileResult.html();

        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            JsonNode current = load(id); // throws DashboardNotFoundException if missing
            int newVersion = current.path("version").asInt(0) + 1;
            long now = clock.millis();
            ObjectNode mutable = newDashboard.deepCopy();
            mutable.put("id", id);
            mutable.put("version", newVersion);
            mutable.put("createdAt", current.path("createdAt").asLong(now));
            mutable.put("updatedAt", now);

            byte[] bytes;
            try {
                bytes = mapper.writeValueAsBytes(mutable);
            } catch (IOException e) {
                throw new DashboardPersistenceException("serialize failed: " + id, e);
            }
            if (bytes.length > MAX_PAYLOAD_BYTES) {
                throw new PayloadTooLargeException(bytes.length, MAX_PAYLOAD_BYTES);
            }

            Path target = workdirRoot.dashboardsRoot().resolve(id + ".dashboard.json").toAbsolutePath();
            try {
                AtomicFileWriterBridge.write(target, bytes);
            } catch (IOException e) {
                throw new DashboardPersistenceException("atomic write failed: " + id, e);
            }
            storeHtmlArtifact(id, html.getBytes(StandardCharsets.UTF_8));
            return new UpdateResult(newVersion, html);
        }
    }

    public Optional<byte[]> loadHtml(String dashboardId) {
        try {
            return Optional.of(fileArtifactService.readBytes(htmlArtifactId(dashboardId)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String htmlArtifactId(String dashboardId) { return dashboardId + ":html"; }

    private void storeHtmlArtifact(String dashboardId, byte[] bytes) {
        Path p = workdirRoot.dashboardsRoot().resolve(dashboardId + ".html").toAbsolutePath();
        try {
            Files.createDirectories(p.getParent());
            AtomicFileWriterBridge.write(p, bytes);
        } catch (IOException e) {
            throw new DashboardPersistenceException("Failed to store HTML artifact: " + dashboardId, e);
        }
        try {
            fileArtifactService.registerExternal(
                    htmlArtifactId(dashboardId), FileArtifactKind.DASHBOARD, FileArtifactScope.WORKSPACE,
                    null, null, p, null, null, null);
        } catch (IOException | FileArtifactConflictException e) {
            // already registered — ignore
        }
    }

    public JsonNode load(String id) {
        try {
            byte[] bytes = fileArtifactService.readBytes(id);
            return mapper.readTree(bytes);
        } catch (FileArtifactNotFoundException e) {
            throw new DashboardNotFoundException(id);
        } catch (IOException e) {
            throw new DashboardPersistenceException("load failed: " + id, e);
        }
    }

    public void storeCompiledHtml(String dashboardId, String html) {
        storeHtmlArtifact(dashboardId, html.getBytes(StandardCharsets.UTF_8));
    }

    public static final class PayloadTooLargeException extends RuntimeException {
        private final int size;
        private final int maxSize;
        public PayloadTooLargeException(int size, int maxSize) {
            super("Dashboard payload too large: " + size + " > " + maxSize);
            this.size = size; this.maxSize = maxSize;
        }
        public int getSize() { return size; }
        public int getMaxSize() { return maxSize; }
    }

    public static final class ValidationException extends RuntimeException {
        private final ValidationResult result;
        public ValidationException(ValidationResult result) {
            super("Dashboard validation failed: " + result.errors());
            this.result = result;
        }
        public ValidationResult getResult() { return result; }
    }

    public static final class DashboardNotFoundException extends RuntimeException {
        public DashboardNotFoundException(String id) { super("Dashboard not found: " + id); }
    }

    public static final class DashboardPersistenceException extends RuntimeException {
        public DashboardPersistenceException(String message, Throwable cause) { super(message, cause); }
    }
}
