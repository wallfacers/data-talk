package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Applies a subset of RFC 6902 JSON Patch operations (add/remove/replace)
 * with matchKey path extension for array element addressing.
 *
 * Atomic: on any operation failure the original document is unchanged.
 */
@Component
public class JsonPatchApplier {

    private final ObjectMapper mapper;

    public JsonPatchApplier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static final Set<String> DANGEROUS_SEGMENTS = Set.of(
        "__proto__", "constructor", "prototype"
    );

    public record PatchOp(String op, String path, JsonNode value) {}

    public static final class VersionConflictException extends RuntimeException {
        private final int expected;
        private final int actual;
        public VersionConflictException(int expected, int actual) {
            super("Version conflict: expected " + expected + " but was " + actual);
            this.expected = expected;
            this.actual = actual;
        }
        public int getExpected() { return expected; }
        public int getActual() { return actual; }
    }

    public static final class PatchRejectException extends RuntimeException {
        public PatchRejectException(String message) {
            super(message);
        }
    }

    /**
     * Apply patch operations to a dashboard JSON document.
     *
     * @param original    the original document (not modified)
     * @param baseVersion expected current version for optimistic locking
     * @param ops         list of patch operations
     * @return patched document with bumped version
     */
    public JsonNode apply(JsonNode original, int baseVersion, List<PatchOp> ops) {
        // Version check
        int currentVersion = original.path("version").asInt(0);
        if (currentVersion != baseVersion) {
            throw new VersionConflictException(baseVersion, currentVersion);
        }

        // Deep copy for atomicity
        ObjectNode working = original.deepCopy();

        try {
            for (PatchOp op : ops) {
                if (op.op() == null || op.path() == null) {
                    throw new PatchRejectException("op and path must not be null");
                }
                applyOp(working, op);
            }
        } catch (PatchRejectException e) {
            throw e;
        } catch (Exception e) {
            throw new PatchRejectException("Patch operation failed: " + e.getMessage());
        }

        // Bump version
        working.put("version", currentVersion + 1);

        return working;
    }

    private void applyOp(ObjectNode root, PatchOp op) {
        if (op.path == null || op.path.isEmpty()) {
            throw new PatchRejectException("Path must not be null or empty");
        }

        String[] segments = parsePath(op.path);

        // Reject dangerous segments (prototype pollution defense)
        for (String seg : segments) {
            if (DANGEROUS_SEGMENTS.contains(seg)) {
                throw new PatchRejectException("Dangerous path segment rejected: " + seg);
            }
        }

        if (segments.length == 0) {
            throw new PatchRejectException("Empty path");
        }

        // Navigate to the parent, apply the final operation
        JsonNode parent = root;
        for (int i = 0; i < segments.length - 1; i++) {
            parent = navigate(parent, segments[i]);
        }

        String lastSegment = segments[segments.length - 1];
        PatchPath parsed = PatchPath.parse(lastSegment);

        switch (op.op) {
            case "replace" -> applyReplace(parent, parsed, op.value);
            case "add" -> applyAdd(parent, parsed, op.value);
            case "remove" -> applyRemove(parent, parsed);
            default -> throw new PatchRejectException("Unsupported op: " + op.op);
        }
    }

    private JsonNode navigate(JsonNode current, String segment) {
        PatchPath parsed = PatchPath.parse(segment);
        if (parsed instanceof PatchPath.Plain p) {
            return current.path(p.field());
        } else if (parsed instanceof PatchPath.Index idx) {
            if (!current.isArray()) throw new PatchRejectException("Expected array at segment: " + segment);
            return current.path(idx.index());
        } else if (parsed instanceof PatchPath.Append) {
            throw new PatchRejectException("Cannot navigate through '-' append marker");
        } else if (parsed instanceof PatchPath.MatchKey mk) {
            if (!current.isArray()) throw new PatchRejectException("MatchKey requires array: " + segment);
            return findInArray((ArrayNode) current, mk.key(), mk.value());
        }
        throw new PatchRejectException("Unknown path segment: " + segment);
    }

    private void applyReplace(JsonNode parent, PatchPath lastParsed, JsonNode value) {
        if (lastParsed instanceof PatchPath.Plain p) {
            if (!parent.isObject()) throw new PatchRejectException("Cannot replace field on non-object");
            if (!((ObjectNode) parent).has(p.field())) {
                throw new PatchRejectException("Field not found: " + p.field());
            }
            ((ObjectNode) parent).set(p.field(), value);
        } else if (lastParsed instanceof PatchPath.Index idx) {
            if (!parent.isArray()) throw new PatchRejectException("Index requires array");
            ((ArrayNode) parent).set(idx.index(), value);
        } else {
            throw new PatchRejectException("Unsupported path for replace");
        }
    }

    private void applyAdd(JsonNode parent, PatchPath lastParsed, JsonNode value) {
        if (lastParsed instanceof PatchPath.Plain p) {
            if (parent.isObject()) {
                ((ObjectNode) parent).set(p.field(), value);
            } else {
                throw new PatchRejectException("Cannot add field to non-object");
            }
        } else if (lastParsed instanceof PatchPath.Append) {
            if (!parent.isArray()) throw new PatchRejectException("Append requires array");
            ((ArrayNode) parent).add(value);
        } else if (lastParsed instanceof PatchPath.Index idx) {
            if (!parent.isArray()) throw new PatchRejectException("Index requires array");
            ((ArrayNode) parent).insert(idx.index(), value);
        } else {
            throw new PatchRejectException("Unsupported path for add");
        }
    }

    private void applyRemove(JsonNode parent, PatchPath lastParsed) {
        if (lastParsed instanceof PatchPath.Plain p) {
            if (parent.isObject()) {
                ((ObjectNode) parent).remove(p.field());
            } else {
                throw new PatchRejectException("Cannot remove field from non-object");
            }
        } else if (lastParsed instanceof PatchPath.MatchKey mk) {
            if (!parent.isArray()) throw new PatchRejectException("MatchKey remove requires array");
            int idx = findIndexInArray((ArrayNode) parent, mk.key(), mk.value());
            if (idx < 0) {
                throw new PatchRejectException("No element found with " + mk.key() + "=" + mk.value());
            }
            ((ArrayNode) parent).remove(idx);
        } else if (lastParsed instanceof PatchPath.Index idx) {
            if (!parent.isArray()) throw new PatchRejectException("Index remove requires array");
            ((ArrayNode) parent).remove(idx.index());
        } else {
            throw new PatchRejectException("Unsupported path for remove");
        }
    }

    private JsonNode findInArray(ArrayNode array, String key, String value) {
        for (JsonNode element : array) {
            if (element.path(key).asText("").equals(value)) {
                return element;
            }
        }
        throw new PatchRejectException("No element found with " + key + "=" + value);
    }

    private int findIndexInArray(ArrayNode array, String key, String value) {
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).path(key).asText("").equals(value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Split a JSON Pointer path into segments, expanding field[key=value] into two segments.
     * E.g. /widgets[id=abc]/options/title -> ["widgets", "[id=abc]", "options", "title"]
     */
    static String[] parsePath(String path) {
        if (!path.startsWith("/")) {
            throw new PatchRejectException("Path must start with /");
        }
        String rest = path.substring(1);
        if (rest.isEmpty()) return new String[0];

        // Split by /
        String[] rawSegments = rest.split("/");

        // Expand field[key=value] into two segments: "field", "[key=value]"
        List<String> expanded = new ArrayList<>();
        for (String seg : rawSegments) {
            int bracketIdx = seg.indexOf('[');
            if (bracketIdx > 0) {
                expanded.add(seg.substring(0, bracketIdx));
                expanded.add(seg.substring(bracketIdx));
            } else {
                expanded.add(seg);
            }
        }
        return expanded.toArray(new String[0]);
    }
}
