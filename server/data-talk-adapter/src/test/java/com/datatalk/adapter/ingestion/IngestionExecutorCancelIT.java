package com.datatalk.adapter.ingestion;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.IngestionConfirmedTokenStore;
import com.datatalk.application.ingestion.IngestionEventPublisher;
import com.datatalk.application.ingestion.IngestionExecutor;
import com.datatalk.application.ingestion.IngestionRunRegistry;
import com.datatalk.application.ingestion.ddl.H2IngestionDdlAdapter;
import com.datatalk.application.ingestion.parser.CsvPayloadParser;
import com.datatalk.application.ingestion.parser.HtmlTablePayloadParser;
import com.datatalk.application.ingestion.parser.JsonPayloadParser;
import com.datatalk.application.ingestion.parser.JsonlPayloadParser;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.ingestion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Integration test for {@link IngestionExecutor} cancellation: a synchronous batch
 * insert against an H2 in-memory database is interrupted via
 * {@link IngestionRunRegistry#requestStop(String)} and must exit promptly with
 * {@link IngestionCancelledException}.
 *
 * <p>Uses H2 directly so the test runs without docker.</p>
 */
class IngestionExecutorCancelIT {

    @TempDir Path tmp;

    IngestionJobRepository jobRepo;
    IngestionConfirmedTokenStore tokenStore;
    ConnectionRepository connRepo;
    ConnectionService connService;
    FileArtifactRepository artifactRepo;
    IngestionEventPublisher publisher;
    IngestionRunRegistry runRegistry;
    ObjectMapper om;
    IngestionExecutor executor;

    String jobId;
    String connId;
    ConnectionRecord connRecord;

    @BeforeEach
    void setUp() {
        jobRepo = mock(IngestionJobRepository.class);
        tokenStore = new IngestionConfirmedTokenStore();
        connRepo = mock(ConnectionRepository.class);
        connService = mock(ConnectionService.class);
        artifactRepo = mock(FileArtifactRepository.class);
        publisher = mock(IngestionEventPublisher.class);
        runRegistry = new IngestionRunRegistry();
        om = new ObjectMapper();

        JsonPayloadParser jsonParser = new JsonPayloadParser(om);
        JsonlPayloadParser jsonlParser = mock(JsonlPayloadParser.class);
        CsvPayloadParser csvParser = mock(CsvPayloadParser.class);
        HtmlTablePayloadParser htmlParser = mock(HtmlTablePayloadParser.class);

        executor = new IngestionExecutor(
            jobRepo, tokenStore, List.of(new H2IngestionDdlAdapter()),
            connRepo, connService, artifactRepo,
            jsonParser, jsonlParser, csvParser, htmlParser,
            publisher, runRegistry, om);

        connId = "conn_h2_cancel";
        // Use a persistent JDBC URL (mem with DB_CLOSE_DELAY=-1) so CREATE TABLE and INSERT share state.
        connRecord = new ConnectionRecord(
            connId, "h2-cancel", "h2",
            "mem", 0,
            "mem:cancel_db;DB_CLOSE_DELAY=-1", "sa", new byte[0],
            null, System.currentTimeMillis(), 30,
            "ok", System.currentTimeMillis(),
            null, 1, true, null, false,
            null, null, null);

        when(connRepo.findById(connId)).thenReturn(Optional.of(connRecord));
        when(connService.decryptPassword(connId)).thenReturn("");
    }

    @Test
    void requestStopInterruptsBatchInsertWithCancelledException() throws Exception {
        // Build a 2000-row JSON payload so the loop is guaranteed to outlive the cancel signal.
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 2000; i++) {
            if (i > 0) json.append(",");
            json.append("{\"id\":").append(i).append(",\"name\":\"row").append(i).append("\"}");
        }
        json.append("]");

        Path payload = tmp.resolve("payload.json");
        Files.writeString(payload, json.toString());

        FileArtifact artifact = new FileArtifact(
            "art_cancel", null, null, null, null, null, "payload.json",
            payload.toAbsolutePath().toString(), Files.size(payload), "application/json",
            null, null, Instant.now(), Instant.now(), null, null, false);
        when(artifactRepo.findById("art_cancel")).thenReturn(Optional.of(artifact));

        IngestionMapping mapping = new IngestionMapping("map_cancel", List.of(
            new MappingColumn("$.id", "id", InferredType.INTEGER_64, false, List.of(), false),
            new MappingColumn("$.name", "name", InferredType.STRING_64, false, List.of(), false)));
        String mappingHash = com.datatalk.application.ingestion.MappingHash.compute(mapping);

        jobId = "ing_cancel_001";
        IngestionJob job = new IngestionJob(
            jobId, "cancel test", "https://example.com/api", "GET",
            Map.of(), Map.of(), null, null, null,
            PayloadFormat.JSON, "art_cancel", "writing", connId, null, "cancel_target",
            mapping, null, null, null, mappingHash,
            "ai", null, null, null,
            System.currentTimeMillis(), System.currentTimeMillis(), null, null);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

        var token = tokenStore.issue(jobId, mappingHash);
        executor.createTable(jobId, connId, null, "cancel_target", mappingHash, token.tokenId(), null);

        // Run ingestion on a separate thread so the test can call requestStop concurrently.
        CompletableFuture<Throwable> future = CompletableFuture.supplyAsync(() -> {
            try {
                executor.ingestPayload(jobId, 10, null);
                return null;
            } catch (Throwable t) {
                return t;
            }
        });

        // Wait until the registry observes the worker is active.
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!runRegistry.isActive(jobId) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(runRegistry.isActive(jobId)).as("worker should register").isTrue();

        // Give the worker a moment to start inserting so we exercise the mid-loop cancel path.
        Thread.sleep(50);
        boolean signalled = runRegistry.requestStop(jobId);
        assertThat(signalled).isTrue();

        Throwable thrown;
        try {
            thrown = future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new AssertionError("ingestPayload did not exit within 5s after requestStop");
        } catch (ExecutionException ee) {
            thrown = ee.getCause();
        }

        // The executor surfaces IngestionCancelledException directly (does not call updateStatus(failed)).
        assertThat(thrown)
            .as("executor should surface IngestionCancelledException without wrapping in a generic failure")
            .isInstanceOf(IngestionCancelledException.class);

        verify(jobRepo, never()).updateStatus(eq(jobId), eq("failed"), anyString(), anyLong());
        verify(jobRepo, never()).updateStatus(eq(jobId), eq("completed"), any(), anyLong());
    }
}
