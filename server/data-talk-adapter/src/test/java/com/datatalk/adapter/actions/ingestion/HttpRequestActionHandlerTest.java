package com.datatalk.adapter.actions.ingestion;

import com.datatalk.application.ingestion.IngestionPayloadFetcher;
import com.datatalk.application.ingestion.IngestionPayloadFetcher.FetchResult;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HttpRequestActionHandlerTest {

    @Mock IngestionPayloadFetcher fetcher;
    HttpRequestActionHandler handler;

    ActionContext ctx = new ActionContext("sess-1", "call-1", "conn-1", "oc-1");

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        handler = new HttpRequestActionHandler(fetcher);
    }

    // ───────── happy path ─────────

    @Test
    @SuppressWarnings("unchecked")
    void happyPathReturnsJobId() throws Exception {
        when(fetcher.fetch(any())).thenReturn(
            new FetchResult("ing_1", "fa_1", 100, 5000L, 2, "fetched"));

        Map<String, Object> input = Map.of(
            "url", "https://api.example.com/data",
            "method", "GET",
            "payloadFormat", "JSON");

        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("jobId")).isEqualTo("ing_1");
        assertThat(result.get("status")).isEqualTo("fetched");
        assertThat(result.get("payloadArtifactId")).isEqualTo("fa_1");
        assertThat(result.get("rowsFetched")).isEqualTo(100);
        assertThat(result.get("bytesFetched")).isEqualTo(5000L);
        assertThat(result.get("pagesFetched")).isEqualTo(2);
        assertThat(result.get("error")).isNull();
        assertThat(result.get("userHint")).isNull();
    }

    // ───────── SSRF blocked ─────────

    @Test
    @SuppressWarnings("unchecked")
    void ssrfBlockedReturnsUserHint() throws Exception {
        when(fetcher.fetch(any())).thenThrow(
            new IllegalArgumentException("host denied by SSRF rule: localhost"));

        Map<String, Object> input = Map.of(
            "url", "http://localhost/x",
            "method", "GET",
            "payloadFormat", "JSON");

        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        assertThat(result.get("jobId")).isNull();
        assertThat(result.get("payloadArtifactId")).isNull();
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_SSRF_BLOCKED");
        assertThat(error.get("reason")).contains("denied");
        assertThat(result.get("userHint")).asString().isNotBlank();
    }

    // ───────── payload too large ─────────

    @Test
    @SuppressWarnings("unchecked")
    void payloadTooLargeReturnsError() throws Exception {
        when(fetcher.fetch(any())).thenThrow(
            new IllegalStateException("Payload exceeds maximum size of 524288000 bytes"));

        Map<String, Object> input = Map.of(
            "url", "https://api.example.com/big",
            "method", "GET",
            "payloadFormat", "JSON");

        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_PAYLOAD_TOO_LARGE");
        assertThat(result.get("userHint")).asString().isNotBlank();
    }

    // ───────── generic failure ─────────

    @Test
    @SuppressWarnings("unchecked")
    void genericFailureReturnsError() throws Exception {
        when(fetcher.fetch(any())).thenThrow(
            new RuntimeException("Connection refused"));

        Map<String, Object> input = Map.of(
            "url", "https://api.example.com/down",
            "method", "GET",
            "payloadFormat", "JSON");

        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_FETCH_FAILED");
    }

    // ───────── HTML unsupported format ─────────

    @Test
    @SuppressWarnings("unchecked")
    void htmlUnsupportedReturnsError() throws Exception {
        when(fetcher.fetch(any())).thenThrow(
            new UnsupportedOperationException("HTML payload parsing is not yet implemented (P3)"));

        Map<String, Object> input = Map.of(
            "url", "https://example.com/page",
            "method", "GET",
            "payloadFormat", "HTML");

        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_FORMAT_UNSUPPORTED");
    }

    // ───────── input schema is defined ─────────

    @Test
    void inputSchemaIsDefined() {
        var schema = handler.inputSchema();
        assertThat(schema).containsKey("type");
        assertThat(schema).containsKey("properties");
    }

    @Test
    void outputSchemaIsDefined() {
        var schema = handler.outputSchema();
        assertThat(schema).containsKey("type");
        assertThat(schema).containsKey("properties");
    }

    @Test
    void sideEffectsCreateArtifact() {
        assertThat(handler.sideEffects()).containsExactly(
            com.datatalk.domain.action.OntologyEffect.CREATE_ARTIFACT);
    }
}
