package com.datatalk.application.ingestion;

import com.datatalk.application.fileartifact.FileArtifactIds;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.application.stage.SessionTitleLookup;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.ingestion.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.*;

/**
 * Fetches external HTTP data and stores it as a file artifact for later
 * schema inference and table creation.
 *
 * <p>Supports JSON, JSONL, and CSV payload formats. HTML is reserved for P3.
 * Pagination modes: NONE, PAGE, OFFSET, CURSOR.</p>
 */
@Service
public class IngestionPayloadFetcher {

    private static final Logger log = LoggerFactory.getLogger(IngestionPayloadFetcher.class);

    private final HttpFetchClient http;
    private final IngestionUrlValidator urlValidator;
    private final IngestionCredentialService credentialService;
    private final IngestionCredentialRepository credentialRepo;
    private final IngestionJobRepository jobRepo;
    private final FileArtifactService artifactService;
    private final IngestionConfig config;
    private final IngestionEventPublisher eventPublisher;
    private final IngestionRunRegistry runRegistry;
    private final SessionTitleLookup sessionTitleLookup;
    private final ObjectMapper om;

    public IngestionPayloadFetcher(HttpFetchClient http,
                                   IngestionUrlValidator urlValidator,
                                   IngestionCredentialService credentialService,
                                   IngestionCredentialRepository credentialRepo,
                                   IngestionJobRepository jobRepo,
                                   FileArtifactService artifactService,
                                   IngestionConfig config,
                                   IngestionEventPublisher eventPublisher,
                                   IngestionRunRegistry runRegistry,
                                   SessionTitleLookup sessionTitleLookup,
                                   ObjectMapper om) {
        this.http = http;
        this.urlValidator = urlValidator;
        this.credentialService = credentialService;
        this.credentialRepo = credentialRepo;
        this.jobRepo = jobRepo;
        this.artifactService = artifactService;
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.runRegistry = runRegistry;
        this.sessionTitleLookup = sessionTitleLookup;
        this.om = om;
    }

    /**
     * Materializes a creator label such as {@code "AI · Q2 reporting"} at write time.
     * The result is frozen on the ingestion_job row so future renames of the session
     * do not retroactively change ingestion history (and deletes do not blank it).
     */
    private String resolveCreatorLabel(CreatorKind kind, String sessionId) {
        if (kind == CreatorKind.AI) {
            if (sessionId == null || sessionId.isBlank()) return "AI";
            try {
                var titles = sessionTitleLookup.titlesByIds(java.util.List.of(sessionId));
                String title = titles.get(sessionId);
                return (title != null && !title.isBlank()) ? "AI · " + title : "AI";
            } catch (Exception e) {
                return "AI";
            }
        }
        return kind.dbValue();
    }

    // ───────── request / result records ─────────

    public record FetchRequest(
        String url,
        String method,
        Map<String, String> headers,
        Map<String, String> queryParams,
        String body,
        String credentialId,
        PayloadFormat format,
        PaginationSpec pagination,
        String htmlSelector,
        Long timeoutMs
    ) {}

    public record FetchResult(
        String jobId,
        String payloadArtifactId,
        int rowsFetched,
        long bytesFetched,
        int pagesFetched,
        String status,
        PayloadFormat format
    ) {}

    // ───────── main entry point ─────────

    public FetchResult fetch(FetchRequest request, String sessionId) {
        return fetch(request, sessionId, CreatorKind.AI, null);
    }

    public FetchResult fetch(FetchRequest request, String sessionId,
                              CreatorKind creatorKind, String name) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.url(), "url");
        Objects.requireNonNull(request.method(), "method");
        Objects.requireNonNull(request.format(), "format");
        Objects.requireNonNull(creatorKind, "creatorKind");

        // 1. SSRF URL validation
        urlValidator.validate(request.url());

        // 2. Create ingestion_job row (status=fetching)
        String jobId = "ing_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();
        String creatorLabel = resolveCreatorLabel(creatorKind, sessionId);
        jobRepo.save(new IngestionJob(
            jobId,
            name,           // name (MCP schema enforces required; legacy/internal callers may pass null)
            request.url(),
            request.method(),
            request.headers(),
            request.queryParams(),
            request.body(),
            request.credentialId(),
            request.pagination(),
            request.format(),
            null,           // payloadArtifactId
            IngestionJobStatus.toCode(new IngestionJobStatus.Fetching()),
            null,           // connectionId
            null,           // targetSchema
            null,           // targetTable
            null,           // mapping
            null,           // rowCount
            null,           // rowsInserted
            null,           // bytesFetched
            null,           // mappingHash
            creatorKind.dbValue(),
            sessionId,
            creatorLabel,
            now,            // heartbeatAt — fetching loop ticks on each page
            now,            // createdAt
            now,            // updatedAt
            null,           // completedAt
            null            // errorMessage
        ));

        eventPublisher.publish(sessionId, new DtEvent.IngestionJobCreated(jobId, request.url()));

        try (IngestionRunRegistry.RegistryEntry __ = runRegistry.register(jobId)) {
            // 3. Resolve credential
            Map<String, String> mergedHeaders = resolveHeaders(request);

            // 4. Format-specific fetch
            PayloadAccumulator acc = createAccumulator(request.format());
            long timeoutMs = request.timeoutMs() != null ? request.timeoutMs() : config.getFetchTimeoutMs();

            int pagesFetched = 0;
            String nextUrl = request.url();
            String cursorValue = null;
            int pageOrOffset = 1;
            java.util.concurrent.atomic.AtomicBoolean cancelled = runRegistry.getCancelled(jobId);

            while (nextUrl != null && pagesFetched < maxPages(request.pagination())) {
                if ((cancelled != null && cancelled.get()) || Thread.currentThread().isInterrupted()) {
                    throw new IngestionCancelledException(0);
                }

                // Build per-page query params for pagination
                Map<String, String> pageParams = buildPageParams(
                    request.queryParams(), request.pagination(), pageOrOffset, cursorValue);

                byte[] response = http.fetch(
                    nextUrl, request.method(), mergedHeaders,
                    pageParams, request.body(), timeoutMs);

                // Accumulate and check termination
                PageResult pageResult = acc.accumulate(response, pagesFetched == 0);
                pagesFetched++;

                // Refresh heartbeat after each successful page fetch
                jobRepo.updateHeartbeat(jobId, System.currentTimeMillis());

                // Check payload size limit
                if (acc.currentSize() > config.getPayloadMaxBytes()) {
                    throw new IllegalStateException(
                        "Payload exceeds maximum size of " + config.getPayloadMaxBytes() + " bytes");
                }

                // Determine next page
                nextUrl = resolveNextUrl(request, pageOrOffset, cursorValue, pageResult);
                if (nextUrl != null) {
                    if (request.pagination() != null && request.pagination().type() == PaginationType.CURSOR) {
                        cursorValue = pageResult.cursorValue();
                    }
                    pageOrOffset++;
                }
            }

            // 5. Final size check
            byte[] finalPayload = acc.toBytes();
            if (finalPayload.length > config.getPayloadMaxBytes()) {
                throw new IllegalStateException(
                    "Payload exceeds maximum size of " + config.getPayloadMaxBytes() + " bytes");
            }

            // 6. Write to staging file then atomic rename
            String artifactId = FileArtifactIds.next();
            Path payloadPath = writePayload(artifactId, finalPayload);

            // 7. Register external file_artifact
            artifactService.registerExternal(
                artifactId,
                FileArtifactKind.INGESTION_PAYLOAD,
                FileArtifactScope.WORKSPACE,
                null,   // connectionId — not known until mapping
                null,   // sessionId
                payloadPath,
                "Ingestion payload from " + request.url(),
                null,   // summary
                Map.of("jobId", jobId, "format", request.format().name(),
                       "sourceUrl", request.url()));

            // 8. Update job row (status=fetched)
            long updatedAt = System.currentTimeMillis();
            jobRepo.updatePayloadArtifact(jobId, artifactId, acc.totalRows(), finalPayload.length, updatedAt);
            jobRepo.updateStatus(jobId, IngestionJobStatus.toCode(new IngestionJobStatus.Fetched()),
                null, updatedAt);

            eventPublisher.publish(sessionId, new DtEvent.IngestionPayloadFetched(
                jobId, artifactId, acc.totalRows(), finalPayload.length));

            return new FetchResult(jobId, artifactId, acc.totalRows(), finalPayload.length,
                pagesFetched, IngestionJobStatus.toCode(new IngestionJobStatus.Fetched()),
                request.format());

        } catch (IngestionCancelledException ce) {
            // Cancellation flips status / cleans up in IngestionStopService; rethrow so the
            // caller doesn't treat the fetch as a generic failure.
            throw ce;
        } catch (Exception e) {
            long failedAt = System.currentTimeMillis();
            String msg = e.getMessage();
            jobRepo.updateStatus(jobId, IngestionJobStatus.toCode(new IngestionJobStatus.Failed(msg)),
                msg, failedAt);
            eventPublisher.publish(sessionId, new DtEvent.IngestionFailed(jobId, "fetch", msg));
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("fetch failed: " + msg, e);
        }
    }

    // ───────── credential resolution ─────────

    private Map<String, String> resolveHeaders(FetchRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (request.headers() != null) headers.putAll(request.headers());

        if (request.credentialId() != null && !request.credentialId().isBlank()) {
            String secret = credentialService.readSecret(request.credentialId());
            var cred = credentialRepo.findById(request.credentialId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "credential not found: " + request.credentialId()));

            switch (cred.scheme()) {
                case BEARER -> headers.put("Authorization", "Bearer " + secret);
                case BASIC -> {
                    // RFC 7617: Authorization: Basic <base64(username:password)>.
                    // Username lives in configNonSecret; password is the vault-sealed secret.
                    // If no explicit username (legacy), treat the secret as the already-concatenated form.
                    String username = cred.configNonSecret() != null
                        ? cred.configNonSecret().get("username") : null;
                    String credentialPair = (username != null && !username.isEmpty())
                        ? username + ":" + secret
                        : secret;
                    headers.put("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(
                            credentialPair.getBytes(StandardCharsets.UTF_8)));
                }
                case API_KEY_HEADER -> {
                    String headerName = cred.configNonSecret().get("headerName");
                    if (headerName != null) headers.put(headerName, secret);
                }
                case NONE -> {}
                case API_KEY_QUERY -> {} // handled in query params
            }
        }
        return headers;
    }

    // ───────── pagination helpers ─────────

    private int maxPages(PaginationSpec spec) {
        return spec != null ? spec.maxPages() : 1;
    }

    private Map<String, String> buildPageParams(Map<String, String> baseParams,
                                                 PaginationSpec spec,
                                                 int pageOrOffset,
                                                 String cursorValue) {
        Map<String, String> params = new LinkedHashMap<>();
        if (baseParams != null) params.putAll(baseParams);

        if (spec == null || spec.type() == PaginationType.NONE) return params;

        Map<String, Object> specParams = spec.params();
        switch (spec.type()) {
            case PAGE -> {
                String pageParam = getStr(specParams, "pageParam", "page");
                params.put(pageParam, String.valueOf(pageOrOffset));
            }
            case OFFSET -> {
                String offsetParam = getStr(specParams, "offsetParam", "offset");
                int offsetBase = ((Number) specParams.getOrDefault("offsetBase", 0)).intValue();
                int limit = ((Number) specParams.getOrDefault("limit", 100)).intValue();
                params.put(offsetParam, String.valueOf(offsetBase + (pageOrOffset - 1) * limit));
                String limitParam = getStr(specParams, "limitParam", "limit");
                params.put(limitParam, String.valueOf(limit));
            }
            case CURSOR -> {
                if (cursorValue != null) {
                    String cursorParam = getStr(specParams, "cursorParam", "cursor");
                    params.put(cursorParam, cursorValue);
                }
            }
            default -> {}
        }
        return params;
    }

    private String resolveNextUrl(FetchRequest request, int currentPage,
                                   String currentCursor, PageResult pageResult) {
        PaginationSpec spec = request.pagination();
        if (spec == null || spec.type() == PaginationType.NONE) return null;
        if (currentPage >= spec.maxPages()) return null;

        return switch (spec.type()) {
            // BUG-0019 / BUG-0020: PAGE and OFFSET pagination must stop when the upstream
            // returns an empty page. Without this guard, the loop continues until maxPages
            // and over-fetches by one (visible to tests as 5 hits instead of 4).
            case PAGE, OFFSET -> pageResult.rowCount() <= 0 ? null : request.url();
            // CURSOR termination relies on cursorValue extraction (see BUG-0021 for the
            // top-level "next" key fix in JsonAccumulator.extractCursor).
            case CURSOR -> pageResult.cursorValue() != null ? request.url() : null;
            default -> null;
        };
    }

    private static String getStr(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : defaultVal;
    }

    // ───────── payload writing ─────────

    private Path writePayload(String artifactId, byte[] payload) throws IOException {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "datatalk-ingestion");
        Files.createDirectories(dir);
        Path staging = dir.resolve(artifactId + ".staging");
        Path target = dir.resolve(artifactId + ".payload");
        Files.write(staging, payload);
        Files.move(staging, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    // ───────── format-specific accumulators ─────────

    private PayloadAccumulator createAccumulator(PayloadFormat format) {
        return switch (format) {
            case JSON -> new JsonAccumulator(om);
            case JSONL -> new JsonlAccumulator();
            case CSV -> new CsvAccumulator();
            // BUG-0026: HTML fetch path now keeps raw bytes per page so the artifact
            // can be parsed later by HtmlTablePayloadParser. The fetcher itself does
            // not parse HTML — pagination over HTML pages is unusual but supported.
            case HTML -> new HtmlAccumulator();
        };
    }

    private record PageResult(String cursorValue, int rowCount) {
        static PageResult of(int rowCount) { return new PageResult(null, rowCount); }
        static PageResult withCursor(String cursor, int rowCount) {
            return new PageResult(cursor, rowCount);
        }
    }

    private interface PayloadAccumulator {
        PageResult accumulate(byte[] response, boolean isFirstPage);
        int totalRows();
        long currentSize();
        byte[] toBytes();
    }

    // ── JSON: accumulate array elements into a single ArrayNode ──

    private static class JsonAccumulator implements PayloadAccumulator {
        private final ObjectMapper om;
        private final ArrayNode merged = new ObjectMapper().createArrayNode();
        private int rows = 0;

        JsonAccumulator(ObjectMapper om) { this.om = om; }

        @Override
        public PageResult accumulate(byte[] response, boolean isFirstPage) {
            try {
                JsonNode node = om.readTree(response);
                ArrayNode array;
                if (node.isArray()) {
                    array = (ArrayNode) node;
                } else if (node.isObject()) {
                    // Try common envelope patterns: data, results, items, records
                    JsonNode dataNode = extractDataArray(node);
                    array = dataNode != null ? (ArrayNode) dataNode : om.createArrayNode().add(node);
                } else {
                    array = om.createArrayNode().add(node);
                }
                int count = array.size();
                for (JsonNode element : array) merged.add(element);
                rows += count;

                // Extract cursor from response if present
                String cursor = extractCursor(node);
                return cursor != null ? PageResult.withCursor(cursor, count) : PageResult.of(count);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse JSON response", e);
            }
        }

        @Override public int totalRows() { return rows; }
        @Override public long currentSize() { return merged.toString().getBytes(StandardCharsets.UTF_8).length; }
        @Override public byte[] toBytes() { return merged.toString().getBytes(StandardCharsets.UTF_8); }

        private static JsonNode extractDataArray(JsonNode obj) {
            for (String key : new String[]{"data", "results", "items", "records", "rows"}) {
                JsonNode candidate = obj.get(key);
                if (candidate != null && candidate.isArray()) return candidate;
            }
            return null;
        }

        private static String extractCursor(JsonNode node) {
            if (!node.isObject()) return null;
            // BUG-0021: add `next` to the top-level search — common shape `{ items, next }`
            // (used by GitHub / fixtures) previously fell through to null because `next`
            // was only searched inside nested envelope objects.
            for (String key : new String[]{"next_cursor", "nextCursor", "cursor", "next_page_token", "nextPageToken", "next"}) {
                JsonNode candidate = node.get(key);
                if (candidate != null && candidate.isTextual()) return candidate.asText();
            }
            // Check nested: meta.next_cursor, pagination.cursor
            for (String parent : new String[]{"meta", "pagination", "page_info", "pageInfo"}) {
                JsonNode parentObj = node.get(parent);
                if (parentObj != null && parentObj.isObject()) {
                    for (String child : new String[]{"next_cursor", "nextCursor", "cursor", "next", "end_cursor", "endCursor"}) {
                        JsonNode candidate = parentObj.get(child);
                        if (candidate != null && candidate.isTextual()) return candidate.asText();
                    }
                }
            }
            return null;
        }
    }

    // ── JSONL: append line-by-line ──

    private static class JsonlAccumulator implements PayloadAccumulator {
        private final StringBuilder sb = new StringBuilder();
        private int rows = 0;

        @Override
        public PageResult accumulate(byte[] response, boolean isFirstPage) {
            String text = new String(response, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return PageResult.of(0);
            String[] lines = text.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(trimmed);
                    rows++;
                }
            }
            return PageResult.of(lines.length);
        }

        @Override public int totalRows() { return rows; }
        @Override public long currentSize() { return sb.length(); }
        @Override public byte[] toBytes() { return sb.toString().getBytes(StandardCharsets.UTF_8); }
    }

    // ── HTML: raw passthrough — parsing happens at infer time ──

    private static class HtmlAccumulator implements PayloadAccumulator {
        private final StringBuilder sb = new StringBuilder();

        @Override
        public PageResult accumulate(byte[] response, boolean isFirstPage) {
            String text = new String(response, StandardCharsets.UTF_8);
            if (sb.length() > 0) sb.append('\n');
            sb.append(text);
            // We don't parse HTML at fetch time; row count is unknown until infer.
            // Return non-zero so the pagination loop doesn't short-circuit on the
            // first (and typically only) page.
            return PageResult.of(text.isBlank() ? 0 : 1);
        }

        @Override public int totalRows() { return 0; /* parsed lazily */ }
        @Override public long currentSize() { return sb.length(); }
        @Override public byte[] toBytes() { return sb.toString().getBytes(StandardCharsets.UTF_8); }
    }

    // ── CSV: first page keeps header, subsequent pages strip header ──

    private static class CsvAccumulator implements PayloadAccumulator {
        private final StringBuilder sb = new StringBuilder();
        private int rows = 0;
        private boolean hasHeader = false;

        @Override
        public PageResult accumulate(byte[] response, boolean isFirstPage) {
            String text = new String(response, StandardCharsets.UTF_8);
            if (text.isBlank()) return PageResult.of(0);
            String[] lines = text.split("\n");

            int start = 0;
            if (!isFirstPage && hasHeader && lines.length > 0) {
                // Skip header row on subsequent pages
                start = 1;
            }

            for (int i = start; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(lines[i]); // preserve original line (not trimmed)
                    rows++;
                }
            }

            if (isFirstPage) hasHeader = true;
            return PageResult.of(lines.length - start);
        }

        @Override public int totalRows() { return rows; }
        @Override public long currentSize() { return sb.length(); }
        @Override public byte[] toBytes() { return sb.toString().getBytes(StandardCharsets.UTF_8); }
    }
}
