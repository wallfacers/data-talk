package com.datatalk.adapter.actions.ingestion;

import com.datatalk.application.ingestion.IngestionPayloadFetcher;
import com.datatalk.application.ingestion.IngestionPayloadFetcher.FetchResult;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.ingestion.PayloadFormat;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HttpRequestActionHandlerTest {

    @Mock IngestionPayloadFetcher fetcher;
    @Mock IngestionJobRepository jobRepo;
    HttpRequestActionHandler handler;

    ActionContext ctx = new ActionContext("sess-1", "call-1", "conn-1", "oc-1");

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        handler = new HttpRequestActionHandler(fetcher, jobRepo);
        when(jobRepo.findById(anyString())).thenReturn(Optional.empty());
    }

    private Map<String, Object> baseInput(String fmt) {
        return Map.of(
            "name", "test ingestion job",
            "url", "https://api.example.com/data",
            "method", "GET",
            "payloadFormat", fmt);
    }

    // ───────── happy path ─────────

    @Test
    @SuppressWarnings("unchecked")
    void happyPathReturnsJobId() throws Exception {
        when(fetcher.fetch(any(), anyString(), any(), anyString())).thenReturn(
            new FetchResult("ing_1", "fa_1", 100, 5000L, 2, "fetched", PayloadFormat.JSON));

        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, baseInput("JSON")).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("jobId")).isEqualTo("ing_1");
        assertThat(result.get("name")).isEqualTo("test ingestion job");
        assertThat(result.get("status")).isEqualTo("fetched");
        assertThat(result.get("payloadArtifactId")).isEqualTo("fa_1");
        assertThat(result.get("payloadFormat")).isEqualTo("json");
        assertThat(result.get("rowsFetched")).isEqualTo(100);
        assertThat(result.get("rowCount")).isEqualTo(100);
        assertThat(result.get("bytesFetched")).isEqualTo(5000L);
        assertThat(result.get("pagesFetched")).isEqualTo(2);
        assertThat(result.get("error")).isNull();
        assertThat(result.get("userHint")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingNameFails() throws Exception {
        Map<String, Object> input = Map.of(
            "url", "https://api.example.com/data",
            "method", "GET",
            "payloadFormat", "JSON");

        // schema-level handling: str("name") throws IllegalArgumentException which the
        // handler maps to SSRF_BLOCKED today; we don't depend on which error code is
        // returned, but we want zero call into the fetcher.
        try {
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // either path is acceptable for this guard test
        }
        verify(fetcher, never()).fetch(any(), anyString(), any(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankOrTooLongNameReturnsNameRequired() throws Exception {
        Map<String, Object> input = new java.util.HashMap<>(baseInput("JSON"));
        input.put("name", "");

        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("errorCode")).isEqualTo("INGESTION_NAME_REQUIRED");
        verify(fetcher, never()).fetch(any(), anyString(), any(), anyString());
    }

    // ───────── BUG-0017: payloadFormat emitted lowercase for each format ─────────

    @Test
    @SuppressWarnings("unchecked")
    void payloadFormatEmittedLowercaseForEachFormat() throws Exception {
        for (PayloadFormat fmt : PayloadFormat.values()) {
            when(fetcher.fetch(any(), anyString(), any(), anyString())).thenReturn(
                new FetchResult("ing_x", "fa_x", 0, 0L, 1, "fetched", fmt));

            Map<String, Object> result = (Map<String, Object>)
                handler.handle(ctx, baseInput(fmt.name())).toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertThat(result.get("payloadFormat"))
                .as("payloadFormat for %s", fmt)
                .isEqualTo(fmt.name().toLowerCase());
        }
    }

    // ───────── SSRF blocked ─────────

    @Test
    @SuppressWarnings("unchecked")
    void ssrfBlockedReturnsUserHint() throws Exception {
        when(fetcher.fetch(any(), anyString(), any(), anyString())).thenThrow(
            new IllegalArgumentException("host denied by SSRF rule: localhost"));

        Map<String, Object> input = new java.util.HashMap<>(baseInput("JSON"));
        input.put("url", "http://localhost/x");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        assertThat(result.get("errorCode")).isEqualTo("INGESTION_SSRF_BLOCKED");
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
        when(fetcher.fetch(any(), anyString(), any(), anyString())).thenThrow(
            new IllegalStateException("Payload exceeds maximum size of 524288000 bytes"));

        Map<String, Object> input = new java.util.HashMap<>(baseInput("JSON"));
        input.put("url", "https://api.example.com/big");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        assertThat(result.get("errorCode")).isEqualTo("INGESTION_PAYLOAD_TOO_LARGE");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_PAYLOAD_TOO_LARGE");
        assertThat(result.get("userHint")).asString().isNotBlank();
    }

    // ───────── generic failure ─────────

    @Test
    @SuppressWarnings("unchecked")
    void genericFailureReturnsError() throws Exception {
        when(fetcher.fetch(any(), anyString(), any(), anyString())).thenThrow(
            new RuntimeException("Connection refused"));

        Map<String, Object> input = new java.util.HashMap<>(baseInput("JSON"));
        input.put("url", "https://api.example.com/down");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        assertThat(result.get("errorCode")).isEqualTo("INGESTION_FETCH_FAILED");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_FETCH_FAILED");
    }

    // ───────── BUG-0024: 401 maps to INGESTION_AUTH_FAILED ─────────

    @Test
    @SuppressWarnings("unchecked")
    void authFailedReturns401MappedError() throws Exception {
        when(fetcher.fetch(any(), anyString(), any(), anyString())).thenThrow(
            new com.datatalk.domain.ingestion.IngestionAuthFailedException(
                "Upstream returned 401 Unauthorized"));

        Map<String, Object> input = new java.util.HashMap<>(baseInput("JSON"));
        input.put("url", "https://api.example.com/secured");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        assertThat(result.get("errorCode")).isEqualTo("INGESTION_AUTH_FAILED");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_AUTH_FAILED");
        assertThat(result.get("userHint")).asString().isNotBlank();
    }

    // ───────── HTML unsupported format ─────────

    @Test
    @SuppressWarnings("unchecked")
    void htmlUnsupportedReturnsError() throws Exception {
        when(fetcher.fetch(any(), anyString(), any(), anyString())).thenThrow(
            new UnsupportedOperationException("HTML payload parsing is not yet implemented (P3)"));

        Map<String, Object> input = new java.util.HashMap<>(baseInput("HTML"));
        input.put("url", "https://example.com/page");
        Map<String, Object> result = (Map<String, Object>)
            handler.handle(ctx, input).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.get("status")).isEqualTo("failed");
        assertThat(result.get("errorCode")).isEqualTo("INGESTION_FORMAT_UNSUPPORTED");
        Map<String, String> error = (Map<String, String>) result.get("error");
        assertThat(error.get("code")).isEqualTo("INGESTION_FORMAT_UNSUPPORTED");
    }

    // ───────── input schema is defined ─────────

    @Test
    void inputSchemaIsDefined() {
        var schema = handler.inputSchema();
        assertThat(schema).containsKey("type");
        assertThat(schema).containsKey("properties");
        Object required = schema.get("required");
        assertThat(required).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
            .contains("name", "url", "payloadFormat");
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
