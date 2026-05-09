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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DashboardArtifactService {

    private static final int MAX_PAYLOAD_BYTES = 256 * 1024; // 256 KB

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private final FileArtifactService fileArtifactService;
    private final SessionWorkdirRoot workdirRoot;
    private final DashboardSchemaValidator validator;
    private final JsonPatchApplier patchApplier;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DashboardArtifactService(
            FileArtifactService fileArtifactService,
            SessionWorkdirRoot workdirRoot,
            DashboardSchemaValidator validator,
            JsonPatchApplier patchApplier,
            ObjectMapper mapper,
            Clock clock) {
        this.fileArtifactService = fileArtifactService;
        this.workdirRoot = workdirRoot;
        this.validator = validator;
        this.patchApplier = patchApplier;
        this.mapper = mapper;
        this.clock = clock;
    }

    public record PromoteResult(String id, int version) {}
    public record PatchResult(int version) {}

    public PromoteResult promote(JsonNode dashboard) {
        return promote(dashboard, null);
    }

    public PromoteResult promote(JsonNode dashboard, String originSessionId) {
        int approxSize = dashboard.toString().length();
        if (approxSize > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException(approxSize, MAX_PAYLOAD_BYTES);
        }
        ValidationResult validation = validator.validate(dashboard);
        if (!validation.ok()) throw new ValidationException(validation);

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

        Path target = workdirRoot.dashboardsRoot().resolve(id + ".dashboard.json");
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

        return new PromoteResult(id, 1);
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

    public PatchResult patch(String id, int baseVersion, List<JsonPatchApplier.PatchOp> ops) {
        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            return doPatch(id, baseVersion, ops);
        }
    }

    private PatchResult doPatch(String id, int baseVersion, List<JsonPatchApplier.PatchOp> ops) {
        JsonNode current = load(id);
        JsonNode patched = patchApplier.apply(current, baseVersion, ops);

        ValidationResult validation = validator.validate(patched);
        if (!validation.ok()) throw new ValidationException(validation);

        ObjectNode mutable = (ObjectNode) patched;
        mutable.put("updatedAt", clock.millis());

        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(mutable);
        } catch (IOException e) {
            throw new DashboardPersistenceException("serialize failed: " + id, e);
        }
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException(bytes.length, MAX_PAYLOAD_BYTES);
        }
        try {
            fileArtifactService.replaceBytesAtomic(id, bytes);
        } catch (IOException e) {
            throw new DashboardPersistenceException("replaceBytesAtomic failed: " + id, e);
        } catch (FileArtifactNotFoundException e) {
            throw new DashboardNotFoundException(id);
        }
        return new PatchResult(mutable.get("version").asInt());
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
