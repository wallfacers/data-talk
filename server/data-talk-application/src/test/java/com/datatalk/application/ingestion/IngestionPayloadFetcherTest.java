package com.datatalk.application.ingestion;

import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.fileartifact.FileArtifactScope;
import com.datatalk.domain.ingestion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionPayloadFetcherTest {

    @Mock HttpFetchClient http;
    @Mock IngestionUrlValidator urlValidator;
    @Mock IngestionCredentialService credService;
    @Mock IngestionCredentialRepository credRepo;
    @Mock IngestionJobRepository jobRepo;
    @Mock FileArtifactService artifactService;
    @Mock IngestionEventPublisher eventPublisher;

    IngestionConfig config = new IngestionConfig();
    ObjectMapper om = new ObjectMapper();
    IngestionPayloadFetcher fetcher;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        fetcher = new IngestionPayloadFetcher(
            http, urlValidator, credService, credRepo, jobRepo, artifactService, config, eventPublisher, om);
    }

    private IngestionPayloadFetcher.FetchRequest jsonGetRequest(String url) {
        return new IngestionPayloadFetcher.FetchRequest(
            url, "GET", Map.of(), Map.of(), null, null,
            PayloadFormat.JSON, null, null, 60000L);
    }

    // ───────── SSRF blocked ─────────

    @Test
    void ssrfDeniedUrlThrows() {
        doThrow(new IllegalArgumentException("host denied by SSRF rule: localhost"))
            .when(urlValidator).validate("http://localhost/x");

        assertThatThrownBy(() -> fetcher.fetch(jsonGetRequest("http://localhost/x"), "sess-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("denied");

        // Job should NOT be saved since SSRF check happens first
        verify(jobRepo, never()).save(any());
    }

    // ───────── happy path: single JSON fetch ─────────

    @Test
    void singleJsonFetchCreatesArtifactAndUpdatesJob() throws Exception {
        // Arrange
        byte[] responsePayload = """
            [{"id":1,"name":"alice"},{"id":2,"name":"bob"}]
            """.stripIndent().getBytes();
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenReturn(responsePayload);

        FileArtifact mockArtifact = mock(FileArtifact.class);
        when(artifactService.registerExternal(
            anyString(), eq(FileArtifactKind.INGESTION_PAYLOAD), eq(FileArtifactScope.WORKSPACE),
            isNull(), isNull(), any(Path.class), anyString(), isNull(), anyMap()))
            .thenReturn(mockArtifact);

        // Act
        var result = fetcher.fetch(jsonGetRequest("https://api.example.com/data"), "sess-1");

        // Assert
        assertThat(result.status()).isEqualTo("fetched");
        assertThat(result.jobId()).startsWith("ing_");
        assertThat(result.payloadArtifactId()).startsWith("file_artifact_");
        assertThat(result.rowsFetched()).isEqualTo(2);
        assertThat(result.pagesFetched()).isEqualTo(1);
        assertThat(result.bytesFetched()).isGreaterThan(0);

        verify(jobRepo).save(argThat(job ->
            job.status().equals("fetching") && job.sourceUrl().equals("https://api.example.com/data")));
        verify(jobRepo).updatePayloadArtifact(eq(result.jobId()), eq(result.payloadArtifactId()),
            eq(2), eq(result.bytesFetched()), anyLong());
        verify(jobRepo).updateStatus(eq(result.jobId()), eq("fetched"), isNull(), anyLong());
    }

    // ───────── JSON with envelope (data key) ─────────

    @Test
    void jsonFetchWithEnvelopeDataKey() throws Exception {
        byte[] responsePayload = """
            {"data": [{"x":1},{"x":2},{"x":3}], "total": 3}
            """.stripIndent().getBytes();
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenReturn(responsePayload);

        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        var result = fetcher.fetch(jsonGetRequest("https://api.example.com/envelope"), "sess-1");

        assertThat(result.rowsFetched()).isEqualTo(3);
    }

    // ───────── JSONL fetch ─────────

    @Test
    void jsonlFetchAccumulatesLines() throws Exception {
        byte[] responsePayload = """
            {"id":1}
            {"id":2}
            {"id":3}
            """.stripIndent().getBytes();
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenReturn(responsePayload);

        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/jsonl", "GET", Map.of(), Map.of(), null, null,
            PayloadFormat.JSONL, null, null, 60000L);
        var result = fetcher.fetch(request, "sess-1");

        assertThat(result.rowsFetched()).isEqualTo(3);
        assertThat(result.status()).isEqualTo("fetched");
    }

    // ───────── CSV fetch ─────────

    @Test
    void csvFetchKeepsHeaderOnFirstPage() throws Exception {
        byte[] responsePayload = """
            id,name
            1,alice
            2,bob
            """.stripIndent().getBytes();
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenReturn(responsePayload);

        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/csv", "GET", Map.of(), Map.of(), null, null,
            PayloadFormat.CSV, null, null, 60000L);
        var result = fetcher.fetch(request, "sess-1");

        assertThat(result.status()).isEqualTo("fetched");
        assertThat(result.rowsFetched()).isEqualTo(3); // header + 2 data rows
    }

    // ───────── payload too large ─────────

    @Test
    void payloadExceedingMaxBytesThrows() throws Exception {
        // Set a very small limit
        config.setPayloadMaxBytes(10L);

        byte[] responsePayload = """
            [{"id":1,"name":"this is way more than 10 bytes"}]
            """.stripIndent().getBytes();
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenReturn(responsePayload);

        assertThatThrownBy(() -> fetcher.fetch(jsonGetRequest("https://api.example.com/big"), "sess-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exceeds maximum size");

        // Job should be marked as failed
        verify(jobRepo).updateStatus(anyString(), eq("failed"), anyString(), anyLong());
    }

    // ───────── HTML throws UnsupportedOperationException ─────────

    @Test
    void htmlFormatThrowsNotImplemented() throws Exception {
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenReturn("<html><body>data</body></html>".getBytes());

        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/html", "GET", Map.of(), Map.of(), null, null,
            PayloadFormat.HTML, null, null, 60000L);

        assertThatThrownBy(() -> fetcher.fetch(request, "sess-1"))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("not yet implemented");
    }

    // ───────── credential injection ─────────

    @Test
    void bearerCredentialInjectedInHeader() throws Exception {
        when(credService.readSecret("cred_test")).thenReturn("my-token-123");
        when(credRepo.findById("cred_test")).thenReturn(Optional.of(
            new IngestionCredential("cred_test", "test", AuthScheme.BEARER,
                Map.of(), null, 1L, 1L)));
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenReturn("[]".getBytes());
        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/secured", "GET", Map.of(), Map.of(), null, "cred_test",
            PayloadFormat.JSON, null, null, 60000L);
        fetcher.fetch(request, "sess-1");

        verify(http).fetch(eq("https://api.example.com/secured"), eq("GET"),
            argThat(headers -> "Bearer my-token-123".equals(headers.get("Authorization"))),
            anyMap(), isNull(), anyLong());
    }

    // ───────── BUG-0018: Basic auth must be Base64(username:password) per RFC 7617 ─────────

    @Test
    void basicCredentialInjectedAsBase64() throws Exception {
        when(credService.readSecret("cred_basic")).thenReturn("p@ss");
        when(credRepo.findById("cred_basic")).thenReturn(Optional.of(
            new IngestionCredential("cred_basic", "basic-cred", AuthScheme.BASIC,
                Map.of("username", "alice"), "vault_1", 1L, 1L)));
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenReturn("[]".getBytes());
        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/basic-protected", "GET", Map.of(), Map.of(), null, "cred_basic",
            PayloadFormat.JSON, null, null, 60000L);
        fetcher.fetch(request, "sess-1");

        String expected = "Basic " + java.util.Base64.getEncoder().encodeToString(
            "alice:p@ss".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verify(http).fetch(eq("https://api.example.com/basic-protected"), eq("GET"),
            argThat(headers -> expected.equals(headers.get("Authorization"))),
            anyMap(), isNull(), anyLong());
    }

    @Test
    void basicCredentialFallsBackToSecretAsUserPassWhenNoUsernameSet() throws Exception {
        // Legacy mode: secret already contains "user:pass" pre-concatenated.
        when(credService.readSecret("cred_legacy")).thenReturn("admin:secret123");
        when(credRepo.findById("cred_legacy")).thenReturn(Optional.of(
            new IngestionCredential("cred_legacy", "legacy-basic", AuthScheme.BASIC,
                Map.of(), "vault_1", 1L, 1L)));
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenReturn("[]".getBytes());
        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/x", "GET", Map.of(), Map.of(), null, "cred_legacy",
            PayloadFormat.JSON, null, null, 60000L);
        fetcher.fetch(request, "sess-1");

        String expected = "Basic " + java.util.Base64.getEncoder().encodeToString(
            "admin:secret123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verify(http).fetch(eq("https://api.example.com/x"), eq("GET"),
            argThat(headers -> expected.equals(headers.get("Authorization"))),
            anyMap(), isNull(), anyLong());
    }

    // ───────── pagination with PAGE type ─────────

    @Test
    void pagePaginationFetchesMultiplePages() throws Exception {
        byte[] page1 = "[{\"id\":1},{\"id\":2}]".getBytes();
        byte[] page2 = "[{\"id\":3},{\"id\":4}]".getBytes();
        when(http.fetch(eq("https://api.example.com/paged"), eq("GET"),
            anyMap(), anyMap(), isNull(), anyLong()))
            .thenReturn(page1, page2);

        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        PaginationSpec spec = new PaginationSpec(PaginationType.PAGE,
            Map.of("pageParam", "page"), 2, null);
        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/paged", "GET", Map.of(), Map.of(), null, null,
            PayloadFormat.JSON, spec, null, 60000L);
        var result = fetcher.fetch(request, "sess-1");

        assertThat(result.rowsFetched()).isEqualTo(4);
        assertThat(result.pagesFetched()).isEqualTo(2);
        verify(http, times(2)).fetch(eq("https://api.example.com/paged"),
            eq("GET"), anyMap(), anyMap(), isNull(), anyLong());
    }

    // ───────── BUG-0019: PAGE pagination stops on empty page ─────────

    @Test
    void pagePaginationStopsOnEmptyPage() throws Exception {
        // Pages 1-3 return 2 items each, page 4 returns empty. maxPages=5 so without
        // the fix we'd fetch 5 pages; with the fix we should stop after page 4.
        byte[] page1 = "[{\"id\":1},{\"id\":2}]".getBytes();
        byte[] page2 = "[{\"id\":3},{\"id\":4}]".getBytes();
        byte[] page3 = "[{\"id\":5},{\"id\":6}]".getBytes();
        byte[] pageEmpty = "[]".getBytes();
        when(http.fetch(eq("https://api.example.com/page"), eq("GET"),
            anyMap(), anyMap(), isNull(), anyLong()))
            .thenReturn(page1, page2, page3, pageEmpty);

        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        PaginationSpec spec = new PaginationSpec(PaginationType.PAGE,
            Map.of("pageParam", "page"), 5, null);
        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/page", "GET", Map.of(), Map.of(), null, null,
            PayloadFormat.JSON, spec, null, 60000L);
        var result = fetcher.fetch(request, "sess-1");

        verify(http, times(4)).fetch(eq("https://api.example.com/page"),
            eq("GET"), anyMap(), anyMap(), isNull(), anyLong());
        assertThat(result.rowsFetched()).isEqualTo(6);
    }

    // ───────── BUG-0020: OFFSET pagination stops on empty page ─────────

    @Test
    void offsetPaginationStopsOnEmptyPage() throws Exception {
        byte[] page1 = "[{\"id\":1},{\"id\":2}]".getBytes();
        byte[] page2 = "[{\"id\":3},{\"id\":4}]".getBytes();
        byte[] page3 = "[{\"id\":5},{\"id\":6}]".getBytes();
        byte[] pageEmpty = "[]".getBytes();
        when(http.fetch(eq("https://api.example.com/offset"), eq("GET"),
            anyMap(), anyMap(), isNull(), anyLong()))
            .thenReturn(page1, page2, page3, pageEmpty);

        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        PaginationSpec spec = new PaginationSpec(PaginationType.OFFSET,
            Map.of("offsetParam", "offset", "limit", 2), 5, null);
        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/offset", "GET", Map.of(), Map.of(), null, null,
            PayloadFormat.JSON, spec, null, 60000L);
        var result = fetcher.fetch(request, "sess-1");

        verify(http, times(4)).fetch(eq("https://api.example.com/offset"),
            eq("GET"), anyMap(), anyMap(), isNull(), anyLong());
        assertThat(result.rowsFetched()).isEqualTo(6);
    }

    // ───────── BUG-0021: CURSOR pagination follows top-level `next` key ─────────

    @Test
    void cursorPaginationFollowsTopLevelNextKey() throws Exception {
        // Each response carries top-level `next` — the previous code only found `next`
        // inside nested envelope objects, so it stopped after the first request.
        byte[] pageA = "{\"items\":[{\"id\":1},{\"id\":2}],\"next\":\"B\"}".getBytes();
        byte[] pageB = "{\"items\":[{\"id\":3},{\"id\":4}],\"next\":\"C\"}".getBytes();
        byte[] pageC = "{\"items\":[{\"id\":5}],\"next\":null}".getBytes();
        when(http.fetch(eq("https://api.example.com/cursor"), eq("GET"),
            anyMap(), anyMap(), isNull(), anyLong()))
            .thenReturn(pageA, pageB, pageC);

        when(artifactService.registerExternal(
            anyString(), any(), any(), isNull(), isNull(), any(Path.class),
            anyString(), isNull(), anyMap()))
            .thenReturn(mock(FileArtifact.class));

        PaginationSpec spec = new PaginationSpec(PaginationType.CURSOR,
            Map.of("cursorParam", "cursor"), 5, null);
        var request = new IngestionPayloadFetcher.FetchRequest(
            "https://api.example.com/cursor", "GET", Map.of(), Map.of(), null, null,
            PayloadFormat.JSON, spec, null, 60000L);
        var result = fetcher.fetch(request, "sess-1");

        verify(http, times(3)).fetch(eq("https://api.example.com/cursor"),
            eq("GET"), anyMap(), anyMap(), isNull(), anyLong());
        assertThat(result.rowsFetched()).isEqualTo(5);
    }

    // ───────── http fetch error ─────────

    @Test
    void httpFetchErrorMarksJobFailed() throws Exception {
        when(http.fetch(anyString(), anyString(), anyMap(), anyMap(), any(), anyLong()))
            .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> fetcher.fetch(jsonGetRequest("https://api.example.com/down"), "sess-1"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Connection refused");

        verify(jobRepo).updateStatus(anyString(), eq("failed"),
            contains("Connection refused"), anyLong());
    }
}
