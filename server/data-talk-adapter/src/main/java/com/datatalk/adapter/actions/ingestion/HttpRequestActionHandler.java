package com.datatalk.adapter.actions.ingestion;

import com.datatalk.application.ingestion.IngestionPayloadFetcher;
import com.datatalk.application.ingestion.IngestionPayloadFetcher.FetchRequest;
import com.datatalk.application.ingestion.IngestionPayloadFetcher.FetchResult;
import com.datatalk.domain.action.*;
import com.datatalk.domain.ingestion.IngestionAuthFailedException;
import com.datatalk.domain.ingestion.PaginationSpec;
import com.datatalk.domain.ingestion.PaginationType;
import com.datatalk.domain.ingestion.PayloadFormat;
import com.datatalk.domain.ingestion.TerminationHint;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.http_request",
    executor = Executor.SERVER,
    description = "action.http_request.description",
    timeoutMs = 120_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.MISC }
)
public class HttpRequestActionHandler implements ActionHandler<Map, Map> {

    private final IngestionPayloadFetcher fetcher;

    public HttpRequestActionHandler(IngestionPayloadFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("url", "payloadFormat"),
            "properties", Map.of(
                "url", Map.of("type", "string", "description", "Target URL to fetch"),
                "method", Map.of("type", "string", "description", "HTTP method", "default", "GET"),
                "headers", Map.of("type", "object", "description", "Request headers"),
                "queryParams", Map.of("type", "object", "description", "Query parameters"),
                "body", Map.of("type", "string", "description", "Request body"),
                "credentialId", Map.of("type", "string", "description", "ID of stored credential"),
                "payloadFormat", Map.of("type", "string", "enum", List.of("JSON", "JSONL", "CSV", "HTML")),
                "pagination", Map.of("type", "object", "description", "Pagination spec"),
                "htmlSelector", Map.of("type", "string", "description", "CSS selector for HTML parsing"),
                "timeoutMs", Map.of("type", "integer", "description", "Per-request timeout in ms")
            )
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("jobId", Map.of("type", "string"));
        props.put("payloadArtifactId", Map.of("type", "string"));
        props.put("status", Map.of("type", "string"));
        props.put("payloadFormat", Map.of("type", "string"));
        props.put("rowsFetched", Map.of("type", "integer"));
        props.put("rowCount", Map.of("type", "integer"));
        props.put("bytesFetched", Map.of("type", "integer"));
        props.put("pagesFetched", Map.of("type", "integer"));
        props.put("errorCode", Map.of("type", "string"));
        props.put("error", Map.of("type", "object"));
        props.put("userHint", Map.of("type", "string"));
        return Map.of(
            "type", "object",
            "required", List.of("status"),
            "properties", props
        );
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.CREATE_ARTIFACT);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        try {
            FetchRequest req = buildRequest(input);
            FetchResult result = fetcher.fetch(req, ctx.sessionId());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jobId", result.jobId());
            out.put("payloadArtifactId", result.payloadArtifactId());
            out.put("status", result.status());
            if (result.format() != null) {
                out.put("payloadFormat", result.format().name().toLowerCase());
            }
            out.put("rowsFetched", result.rowsFetched());
            out.put("rowCount", result.rowsFetched());
            out.put("bytesFetched", result.bytesFetched());
            out.put("pagesFetched", result.pagesFetched());
            return CompletableFuture.completedFuture(out);

        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_SSRF_BLOCKED", e.getMessage(),
                    "URL is blocked by SSRF deny list. Use a public HTTPS endpoint."));
        } catch (IllegalStateException e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_PAYLOAD_TOO_LARGE", e.getMessage(),
                    "Payload exceeds size cap. Narrow the request."));
        } catch (UnsupportedOperationException e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_FORMAT_UNSUPPORTED", e.getMessage(),
                    "This payload format is not yet supported."));
        } catch (IngestionAuthFailedException e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_AUTH_FAILED", e.getMessage(),
                    "Check the credential's secret matches the upstream's expected value."));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                errorNode("INGESTION_FETCH_FAILED", e.getMessage(),
                    "Check the URL, credentialId, and pagination params."));
        }
    }

    @SuppressWarnings("unchecked")
    private FetchRequest buildRequest(Map input) {
        String url = str(input, "url");
        String method = str(input, "method", "GET");
        Map<String, String> headers = (Map<String, String>) input.get("headers");
        Map<String, String> queryParams = (Map<String, String>) input.get("queryParams");
        String body = str(input, "body", null);
        String credentialId = str(input, "credentialId", null);
        String formatStr = str(input, "payloadFormat");
        PayloadFormat format = PayloadFormat.valueOf(formatStr.toUpperCase());
        PaginationSpec pagination = buildPagination((Map<String, Object>) input.get("pagination"));
        String htmlSelector = str(input, "htmlSelector", null);
        Long timeoutMs = input.get("timeoutMs") != null
            ? ((Number) input.get("timeoutMs")).longValue() : null;

        return new FetchRequest(url, method, headers, queryParams, body,
            credentialId, format, pagination, htmlSelector, timeoutMs);
    }

    @SuppressWarnings("unchecked")
    private PaginationSpec buildPagination(Map<String, Object> paginationMap) {
        if (paginationMap == null || paginationMap.isEmpty()) return null;
        String typeStr = String.valueOf(paginationMap.get("type"));
        PaginationType type = PaginationType.valueOf(typeStr.toUpperCase());
        Map<String, Object> params = (Map<String, Object>) paginationMap.getOrDefault("params", Map.of());
        int maxPages = paginationMap.get("maxPages") != null
            ? ((Number) paginationMap.get("maxPages")).intValue() : 10;
        Map<String, Object> hintMap = (Map<String, Object>) paginationMap.get("terminationHint");
        TerminationHint hint = null;
        if (hintMap != null) {
            hint = new TerminationHint(
                com.datatalk.domain.ingestion.TerminationHintType.valueOf(
                    String.valueOf(hintMap.get("type")).toUpperCase()),
                str(hintMap, "jsonPath", null));
        }
        return new PaginationSpec(type, params, maxPages, hint);
    }

    private Map<String, Object> errorNode(String code, String reason, String userHint) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "failed");
        // BUG-0015: surface errorCode at the top level so MCP callers / E2E tests
        // can branch on `res.result.errorCode` without unwrapping the nested error.
        out.put("errorCode", code);
        Map<String, String> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("reason", reason);
        out.put("error", error);
        out.put("userHint", userHint);
        return out;
    }

    private static String str(Map input, String key) {
        Object v = input.get(key);
        if (v == null) throw new IllegalArgumentException("missing required field: " + key);
        return String.valueOf(v);
    }

    private static String str(Map input, String key, String defaultVal) {
        Object v = input.get(key);
        return v != null ? String.valueOf(v) : defaultVal;
    }
}
