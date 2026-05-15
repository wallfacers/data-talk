package com.datatalk.infra.semantic;

import com.datatalk.application.semantic.SemanticModelRepository;
import com.datatalk.domain.semantic.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@Repository
public class FsSemanticModelRepository implements SemanticModelRepository {

    private static final Logger log = LoggerFactory.getLogger(FsSemanticModelRepository.class);
    private static final String SEMANTIC_DIR = ".data-talk/semantic";
    private static final String TRASH_DIR = ".data-talk/_trash/semantic";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    /** JSONL — one record per line, no indentation. */
    private final ObjectMapper jsonMapper = new ObjectMapper();
    /** For human-readable sidecars (e.g. _index.json) only. */
    private final ObjectMapper indentedJsonMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path homeDir;

    public FsSemanticModelRepository() {
        this(Path.of(System.getProperty("user.home")));
    }

    public FsSemanticModelRepository(Path homeDir) {
        this.homeDir = homeDir;
        yamlMapper.findAndRegisterModules();
        jsonMapper.findAndRegisterModules();
        indentedJsonMapper.findAndRegisterModules();
        try {
            Files.createDirectories(semanticRoot());
            Files.createDirectories(trashRoot());
        } catch (IOException e) {
            log.warn("Failed to create semantic root dirs: {}", e.getMessage());
        }
    }

    private Path semanticRoot() { return homeDir.resolve(SEMANTIC_DIR); }
    private Path trashRoot() { return homeDir.resolve(TRASH_DIR); }
    private Path connectionDir(String connectionId) { return semanticRoot().resolve(connectionId); }

    @Override
    public List<String> listDomains(String connectionId) {
        Path dir = connectionDir(connectionId);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith(".model.yaml") && !name.startsWith("_"))
                .map(name -> name.substring(0, name.length() - ".model.yaml".length()))
                .toList();
        } catch (IOException e) {
            log.warn("Failed to list domains for {}: {}", connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<SemanticModel> loadDomain(String connectionId, String name) {
        Path yamlFile = connectionDir(connectionId).resolve(name + ".model.yaml");
        if (!Files.isRegularFile(yamlFile)) return Optional.empty();
        try {
            SemanticModel model = yamlMapper.readValue(yamlFile.toFile(), SemanticModel.class);
            return Optional.of(model);
        } catch (IOException e) {
            log.warn("Failed to load model {}/{}: {}", connectionId, name, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void saveDomain(String connectionId, SemanticModel model, String yamlText) {
        try {
            Files.createDirectories(connectionDir(connectionId));
            Path yamlFile = connectionDir(connectionId).resolve(model.name() + ".model.yaml");
            Files.writeString(yamlFile, yamlText, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            updateIndex(connectionId);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save model " + model.name(), e);
        }
    }

    @Override
    public void appendPatch(String connectionId, String domain, PatchOp patch) {
        try {
            Files.createDirectories(connectionDir(connectionId));
            Path patchFile = connectionDir(connectionId).resolve(domain + ".model.yaml.patches.jsonl");
            String line = jsonMapper.writeValueAsString(toPatchJson(patch)) + "\n";
            Files.writeString(patchFile, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append patch for " + domain, e);
        }
    }

    @Override
    public List<PatchOp> listPatches(String connectionId, String domain) {
        Path patchFile = connectionDir(connectionId).resolve(domain + ".model.yaml.patches.jsonl");
        if (!Files.isRegularFile(patchFile)) return List.of();
        List<PatchOp> patches = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(patchFile, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                try {
                    JsonNode node = jsonMapper.readTree(line);
                    PatchOp op = parsePatchOp(node);
                    patches.add(op);
                } catch (Exception e) {
                    log.warn("Skipping invalid patch line {} in {}: {}", i + 1, patchFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read patches for {}/{}: {}", connectionId, domain, e.getMessage());
        }
        return patches;
    }

    @Override
    public void compactPatches(String connectionId, String domain, SemanticModel model) {
        try {
            Path yamlFile = connectionDir(connectionId).resolve(domain + ".model.yaml");
            yamlMapper.writeValue(yamlFile.toFile(), model);
            Path patchFile = connectionDir(connectionId).resolve(domain + ".model.yaml.patches.jsonl");
            Files.writeString(patchFile, "", StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compact patches for " + domain, e);
        }
    }

    @Override
    public List<VerifiedQuery> listVerifiedQueries(String connectionId, int topK) {
        Path vqFile = connectionDir(connectionId).resolve("verified_queries.jsonl");
        if (!Files.isRegularFile(vqFile)) return List.of();
        List<VerifiedQuery> vqs = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(vqFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) continue;
                try {
                    VerifiedQuery vq = jsonMapper.readValue(line, VerifiedQuery.class);
                    vqs.add(vq);
                } catch (Exception e) {
                    log.warn("Skipping invalid VQ line in {}: {}", vqFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read VQs for {}: {}", connectionId, e.getMessage());
        }
        return vqs.stream()
            .sorted(Comparator.comparingInt(VerifiedQuery::hitCount).reversed())
            .limit(topK > 0 ? topK : Integer.MAX_VALUE)
            .toList();
    }

    @Override
    public String recordVerifiedQuery(String connectionId, VerifiedQuery vq) {
        try {
            Files.createDirectories(connectionDir(connectionId));
            Path vqFile = connectionDir(connectionId).resolve("verified_queries.jsonl");
            String line = jsonMapper.writeValueAsString(vq) + "\n";
            Files.writeString(vqFile, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return vq.id();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to record VQ " + vq.id(), e);
        }
    }

    @Override
    public void incHit(String connectionId, String vqId) {
        PatchOp patch = new PatchOp.IncHitCount("INC_HIT_COUNT", vqId, 1, "user", Instant.now());
        appendPatch(connectionId, "verified_queries", patch);
    }

    @Override
    public List<String> listPending(String connectionId) {
        Path pendingDir = connectionDir(connectionId).resolve("pending");
        if (!Files.isDirectory(pendingDir)) return List.of();
        try (Stream<Path> files = Files.list(pendingDir)) {
            return files
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith(".model.yaml"))
                .map(name -> name.substring(0, name.length() - ".model.yaml".length()))
                .toList();
        } catch (IOException e) {
            log.warn("Failed to list pending for {}: {}", connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public String savePending(String connectionId, String domain, String yamlText) {
        try {
            Path pendingDir = connectionDir(connectionId).resolve("pending");
            Files.createDirectories(pendingDir);
            Path pendingFile = pendingDir.resolve(domain + ".model.yaml");
            Files.writeString(pendingFile, yamlText, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return pendingFile.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save pending " + domain, e);
        }
    }

    @Override
    public void acceptPending(String connectionId, String domain) {
        try {
            Path pendingDir = connectionDir(connectionId).resolve("pending");
            Path pendingFile = pendingDir.resolve(domain + ".model.yaml");
            Path targetFile = connectionDir(connectionId).resolve(domain + ".model.yaml");
            if (!Files.isRegularFile(pendingFile)) {
                throw new IllegalStateException("Pending file not found: " + pendingFile);
            }
            // Bump version if target exists
            if (Files.isRegularFile(targetFile)) {
                SemanticModel existing = yamlMapper.readValue(targetFile.toFile(), SemanticModel.class);
                String content = Files.readString(pendingFile, StandardCharsets.UTF_8);
                // Bump version
                content = content.replaceFirst("version:\\s*\\d+", "version: " + (existing.version() + 1));
                Files.writeString(targetFile, content, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.move(pendingFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
            }
            updateIndex(connectionId);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to accept pending " + domain, e);
        }
    }

    @Override
    public void rejectPending(String connectionId, String domain) {
        try {
            Path pendingFile = connectionDir(connectionId).resolve("pending").resolve(domain + ".model.yaml");
            if (Files.isRegularFile(pendingFile)) {
                long ts = System.currentTimeMillis();
                Path trashDir = trashRoot().resolve(ts + "-rejected-" + domain);
                Files.createDirectories(trashDir);
                Files.move(pendingFile, trashDir.resolve(domain + ".model.yaml"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to reject pending " + domain, e);
        }
    }

    @Override
    public int countVerifiedQueriesByConnection(String connectionId) {
        return listVerifiedQueries(connectionId, Integer.MAX_VALUE).size();
    }

    @Override
    public void markStale(String connectionId, String vqId) {
        try {
            Path vqFile = connectionDir(connectionId).resolve("verified_queries.jsonl");
            if (!Files.isRegularFile(vqFile)) return;
            List<String> lines = Files.readAllLines(vqFile, StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>();
            for (String line : lines) {
                if (line.isBlank()) { updated.add(line); continue; }
                try {
                    JsonNode node = jsonMapper.readTree(line);
                    if (vqId.equals(node.path("id").asText(null))) {
                        // Rewrite with stale=true
                        Map<String, Object> map = jsonMapper.readValue(line, Map.class);
                        map.put("stale", true);
                        updated.add(jsonMapper.writeValueAsString(map));
                    } else {
                        updated.add(line);
                    }
                } catch (Exception e) {
                    updated.add(line);
                }
            }
            Files.write(vqFile, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to mark VQ {} as stale: {}", vqId, e.getMessage());
        }
    }

    @Override
    public void moveToTrash(String connectionId, long ts) {
        Path src = connectionDir(connectionId);
        if (!Files.isDirectory(src)) return;
        Path dst = trashRoot().resolve(ts + "-" + connectionId);
        try {
            Files.createDirectories(trashRoot());
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
            log.info("Moved semantic model {} to trash: {}", connectionId, dst);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to move " + connectionId + " to trash", e);
        }
    }

    @Override
    public void deleteAllByConnection(String connectionId) {
        try {
            Path activeDir = connectionDir(connectionId);
            if (Files.isDirectory(activeDir)) {
                deleteRecursively(activeDir);
            }
            // Also clean any trash entries for this connection
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(trashRoot(),
                    p -> p.getFileName().toString().endsWith("-" + connectionId))) {
                for (Path p : ds) {
                    deleteRecursively(p);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + connectionId, e);
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    deleteRecursively(file);
                }
            }
        }
        Files.deleteIfExists(dir);
    }

    private void updateIndex(String connectionId) {
        try {
            List<String> domains = listDomains(connectionId);
            Map<String, Object> index = new LinkedHashMap<>();
            index.put("connectionId", connectionId);
            index.put("domains", domains);
            index.put("lastScanned", Instant.now().toString());
            Path indexFile = connectionDir(connectionId).resolve("_index.json");
            indentedJsonMapper.writeValue(indexFile.toFile(), index);
        } catch (IOException e) {
            log.warn("Failed to update _index.json for {}: {}", connectionId, e.getMessage());
        }
    }

    private Map<String, Object> toPatchJson(PatchOp patch) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("op", patch.op());
        map.put("at", patch.at().toString());
        if (patch.by() != null) map.put("by", patch.by());
        switch (patch) {
            case PatchOp.AddVerifiedQuery avq -> {
                map.put("payload", avq.payload());
            }
            case PatchOp.IncHitCount ihc -> {
                map.put("vq_id", ihc.vqId());
                map.put("delta", ihc.delta());
            }
            case PatchOp.AddLiteralMapping alm -> {
                map.put("dimension", alm.dimension());
                map.put("map", alm.map());
            }
        }
        return map;
    }

    private PatchOp parsePatchOp(JsonNode node) {
        String op = node.path("op").asText();
        String by = node.path("by").asText(null);
        Instant at = Optional.ofNullable(node.path("at").asText(null))
            .map(Instant::parse).orElse(Instant.now());
        return switch (op) {
            case "ADD_VERIFIED_QUERY" -> {
                VerifiedQuery vq = jsonMapper.convertValue(node.path("payload"), VerifiedQuery.class);
                yield new PatchOp.AddVerifiedQuery(op, vq, by, at);
            }
            case "INC_HIT_COUNT" -> {
                String vqId = node.path("vq_id").asText();
                int delta = node.path("delta").asInt(1);
                yield new PatchOp.IncHitCount(op, vqId, delta, by, at);
            }
            case "ADD_LITERAL_MAPPING" -> {
                String dim = node.path("dimension").asText();
                @SuppressWarnings("unchecked")
                Map<String, String> map = jsonMapper.convertValue(node.path("map"), Map.class);
                yield new PatchOp.AddLiteralMapping(op, dim, map, by, at);
            }
            default -> throw new IllegalArgumentException("Unknown patch op: " + op);
        };
    }
}
