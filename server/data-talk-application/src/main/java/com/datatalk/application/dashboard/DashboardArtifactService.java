package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for dashboard lifecycle: promote, load, patch.
 * Uses file-system storage via {@link DashboardStore}.
 */
@Service
public class DashboardArtifactService {

    private static final int MAX_PAYLOAD_BYTES = 256 * 1024; // 256 KB

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private final DashboardStore store;
    private final DashboardSchemaValidator validator;
    private final JsonPatchApplier patchApplier;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DashboardArtifactService(
        DashboardStore store,
        DashboardSchemaValidator validator,
        JsonPatchApplier patchApplier,
        ObjectMapper mapper,
        Clock clock
    ) {
        this.store = store;
        this.validator = validator;
        this.patchApplier = patchApplier;
        this.mapper = mapper;
        this.clock = clock;
    }

    public record PromoteResult(String id, int version) {}
    public record PatchResult(int version) {}

    /**
     * Promote a new dashboard from a JSON payload.
     * Validates, assigns a fresh ID and version 1, persists.
     */
    public PromoteResult promote(JsonNode dashboard) {
        // Size check
        int size = dashboard.toString().length();
        if (size > MAX_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException(size, MAX_PAYLOAD_BYTES);
        }

        // Schema validation
        ValidationResult validation = validator.validate(dashboard);
        if (!validation.ok()) {
            throw new ValidationException(validation);
        }

        // Assign fresh ID and version 1
        String id = DashboardIds.newDashboardId();
        long now = clock.millis();

        ObjectNode mutable = dashboard.deepCopy();
        mutable.put("id", id);
        mutable.put("version", 1);
        mutable.put("createdAt", now);
        mutable.put("updatedAt", now);

        try {
            store.save(id, mutable);
        } catch (IOException e) {
            throw new DashboardPersistenceException("Failed to save dashboard " + id, e);
        }

        return new PromoteResult(id, 1);
    }

    /**
     * Load a dashboard by ID.
     */
    public JsonNode load(String id) {
        try {
            JsonNode doc = store.load(id);
            if (doc == null) {
                throw new DashboardNotFoundException(id);
            }
            return doc;
        } catch (IOException e) {
            throw new DashboardPersistenceException("Failed to load dashboard " + id, e);
        } catch (DashboardNotFoundException e) {
            throw e;
        }
    }

    /**
     * Patch an existing dashboard.
     * Validates baseVersion for optimistic locking, applies ops, validates result, persists.
     * Per-id synchronization prevents lost updates from concurrent patches.
     */
    public PatchResult patch(String id, int baseVersion, List<JsonPatchApplier.PatchOp> ops) {
        Object lock = locks.computeIfAbsent(id, k -> new Object());
        synchronized (lock) {
            return doPatch(id, baseVersion, ops);
        }
    }

    private PatchResult doPatch(String id, int baseVersion, List<JsonPatchApplier.PatchOp> ops) {
        JsonNode current = load(id);

        // Apply patch (includes version check); patchApplier.apply already deep-copies
        JsonNode patched = patchApplier.apply(current, baseVersion, ops);

        // Validate result
        ValidationResult validation = validator.validate(patched);
        if (!validation.ok()) {
            throw new ValidationException(validation);
        }

        // Update timestamp
        ObjectNode mutable = (ObjectNode) patched;
        mutable.put("updatedAt", clock.millis());

        try {
            store.save(id, mutable);
        } catch (IOException e) {
            throw new DashboardPersistenceException("Failed to save dashboard " + id, e);
        }

        return new PatchResult(mutable.get("version").asInt());
    }

    // Exception types

    public static final class PayloadTooLargeException extends RuntimeException {
        private final int size;
        private final int maxSize;
        public PayloadTooLargeException(int size, int maxSize) {
            super("Dashboard payload too large: " + size + " > " + maxSize);
            this.size = size;
            this.maxSize = maxSize;
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
        public DashboardNotFoundException(String id) {
            super("Dashboard not found: " + id);
        }
    }

    public static final class DashboardPersistenceException extends RuntimeException {
        public DashboardPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
