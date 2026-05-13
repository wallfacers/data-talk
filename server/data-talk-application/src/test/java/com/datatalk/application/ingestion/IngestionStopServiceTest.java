package com.datatalk.application.ingestion;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.ddl.IngestionDdlAdapter;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.ingestion.IngestionJob;
import com.datatalk.domain.ingestion.PayloadFormat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionStopServiceTest {

    IngestionJobRepository jobRepo;
    IngestionRunRegistry registry;
    FileArtifactRepository artifactRepo;
    ConnectionRepository connRepo;
    ConnectionService connService;
    List<IngestionDdlAdapter> adapters;
    IngestionStopService service;

    @BeforeEach
    void setUp() {
        jobRepo = mock(IngestionJobRepository.class);
        registry = new IngestionRunRegistry();
        artifactRepo = mock(FileArtifactRepository.class);
        connRepo = mock(ConnectionRepository.class);
        connService = mock(ConnectionService.class);
        adapters = List.of();
        service = new IngestionStopService(jobRepo, registry, artifactRepo, connRepo, connService, adapters);
    }

    private IngestionJob job(String id, String status, String connId, String table, String artifactId) {
        return new IngestionJob(id, "name", "https://x", "GET", Map.of(), Map.of(),
            null, null, null, PayloadFormat.JSON, artifactId,
            status, connId, null, table, null,
            0, 0, 0L, null,
            "ai", null, null, null,
            1000L, 1000L, null, null);
    }

    @Test
    void notFoundReturnsNotFound() {
        when(jobRepo.findById("missing")).thenReturn(Optional.empty());
        assertThat(service.stop("missing", false)).isEqualTo(IngestionStopService.StopOutcome.NOT_FOUND);
        verify(jobRepo, never()).updateStatus(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void completedReturnsAlreadyTerminal() {
        when(jobRepo.findById("done")).thenReturn(Optional.of(job("done", "completed", null, null, null)));
        assertThat(service.stop("done", false)).isEqualTo(IngestionStopService.StopOutcome.ALREADY_TERMINAL);
        verify(jobRepo, never()).updateStatus(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void failedReturnsAlreadyTerminal() {
        when(jobRepo.findById("f")).thenReturn(Optional.of(job("f", "failed", null, null, null)));
        assertThat(service.stop("f", false)).isEqualTo(IngestionStopService.StopOutcome.ALREADY_TERMINAL);
    }

    @Test
    void cancelledReturnsAlreadyTerminal() {
        when(jobRepo.findById("c")).thenReturn(Optional.of(job("c", "cancelled", null, null, null)));
        assertThat(service.stop("c", false)).isEqualTo(IngestionStopService.StopOutcome.ALREADY_TERMINAL);
    }

    @Test
    void pendingNoWorkerNoForceReturnsAppliedAndFlipsToCancelled() {
        when(jobRepo.findById("p")).thenReturn(Optional.of(job("p", "pending", null, null, null)));
        var outcome = service.stop("p", false);
        assertThat(outcome).isEqualTo(IngestionStopService.StopOutcome.APPLIED);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(jobRepo).updateStatus(eq("p"), eq("cancelled"), reason.capture(), anyLong());
        assertThat(reason.getValue()).startsWith("stopped by user");
    }

    @Test
    void fetchingWithActiveWorkerReturnsSignalledAndFinalizes() throws Exception {
        when(jobRepo.findById("act")).thenReturn(Optional.of(job("act", "fetching", null, null, null)));

        var ready = new java.util.concurrent.CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try (var entry = registry.register("act")) {
                ready.countDown();
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ignored) {}
            }
        }, "test-worker");
        worker.start();
        ready.await();

        var outcome = service.stop("act", false);
        assertThat(outcome).isEqualTo(IngestionStopService.StopOutcome.SIGNALLED);

        worker.join(2_000);
        verify(jobRepo).updateStatus(eq("act"), eq("cancelled"), contains("active worker signalled"), anyLong());
    }

    @Test
    void noActiveWorkerWithForceReturnsApplied() {
        when(jobRepo.findById("zombie")).thenReturn(Optional.of(job("zombie", "writing", null, null, null)));
        // Writing status, but no connection so DROP TABLE skipped path is taken
        var outcome = service.stop("zombie", true);
        assertThat(outcome).isEqualTo(IngestionStopService.StopOutcome.APPLIED);
        verify(jobRepo).updateStatus(eq("zombie"), eq("cancelled"), contains("force"), anyLong());
    }

    @Test
    void payloadArtifactDeletionInvoked() {
        when(jobRepo.findById("pa")).thenReturn(Optional.of(job("pa", "pending", null, null, "art_1")));
        FileArtifact artifact = new FileArtifact(
            "art_1", null, null, null, null, null, "payload.json",
            "/tmp/datatalk/non-existent-payload.json", 0L, "application/json",
            null, null, Instant.now(), Instant.now(), null, null, false);
        when(artifactRepo.findById("art_1")).thenReturn(Optional.of(artifact));

        service.stop("pa", false);
        verify(artifactRepo).deleteById("art_1");
    }

    @Test
    void mappingStatusSkipsDropTable() {
        // status = mapping is NOT in STATUSES_WITH_TABLE — DROP path skipped even though
        // connectionId and targetTable are present
        when(jobRepo.findById("m")).thenReturn(Optional.of(job("m", "mapping", "conn_1", "t1", null)));

        service.stop("m", false);
        verify(connRepo, never()).findById(anyString());
        verify(jobRepo).updateStatus(eq("m"), eq("cancelled"), anyString(), anyLong());
    }
}
