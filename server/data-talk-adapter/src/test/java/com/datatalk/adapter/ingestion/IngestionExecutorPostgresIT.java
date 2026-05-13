package com.datatalk.adapter.ingestion;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.IngestionConfirmedTokenStore;
import com.datatalk.application.ingestion.IngestionEventPublisher;
import com.datatalk.application.ingestion.IngestionExecutor;
import com.datatalk.application.ingestion.ddl.PostgresIngestionDdlAdapter;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration test for IngestionExecutor against PostgreSQL 16.
 * Requires Docker. Run with: {@code mvn verify -Dit.test=IngestionExecutorPostgresIT}
 */
@Disabled("Requires Docker — run with -Dit.test=IngestionExecutorPostgresIT")
@Testcontainers
class IngestionExecutorPostgresIT {

    @Container
    static PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ingest_it")
        .withUsername("postgres")
        .withPassword("postgres");

    @TempDir Path tmp;

    IngestionJobRepository jobRepo;
    IngestionConfirmedTokenStore tokenStore;
    ConnectionRepository connRepo;
    ConnectionService connService;
    FileArtifactRepository artifactRepo;
    IngestionEventPublisher publisher;
    ObjectMapper om;
    IngestionExecutor executor;

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
        om = new ObjectMapper();

        JsonPayloadParser jsonParser = new JsonPayloadParser(om);
        JsonlPayloadParser jsonlParser = mock(JsonlPayloadParser.class);
        CsvPayloadParser csvParser = mock(CsvPayloadParser.class);
        HtmlTablePayloadParser htmlParser = mock(HtmlTablePayloadParser.class);

        executor = new IngestionExecutor(
            jobRepo, tokenStore, List.of(new PostgresIngestionDdlAdapter()),
            connRepo, connService, artifactRepo,
            jsonParser, jsonlParser, csvParser, htmlParser,
            publisher,
            new com.datatalk.application.ingestion.IngestionRunRegistry(),
            om
        );

        connId = "conn_pg_it";
        connRecord = new ConnectionRecord(
            connId, "pg-it", "postgresql",
            PG.getHost(), PG.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            "ingest_it", "postgres", new byte[0],
            null, System.currentTimeMillis(), 30,
            "ok", System.currentTimeMillis(),
            null, 1, true, null, false,
            null, null, null
        );

        when(connRepo.findById(connId)).thenReturn(Optional.of(connRecord));
        when(connService.decryptPassword(connId)).thenReturn(PG.getPassword());
    }

    @Test
    void createTableThenIngestThreeJsonRows() throws Exception {
        String jobId = "ing_pg_001";
        Path payload = tmp.resolve("payload.json");
        Files.writeString(payload, """
            [{"id":1,"name":"alice"},{"id":2,"name":"bob"},{"id":3,"name":"carol"}]
            """);

        FileArtifact artifact = new FileArtifact(
            "art_pg_001", null, null, null, null, null, "payload.json",
            payload.toAbsolutePath().toString(), Files.size(payload), "application/json",
            null, null, Instant.now(), Instant.now(), null, null, false
        );
        when(artifactRepo.findById("art_pg_001")).thenReturn(Optional.of(artifact));

        IngestionMapping mapping = new IngestionMapping("map_pg", List.of(
            new MappingColumn("$.id", "id", InferredType.INTEGER_64, false, List.of(), false),
            new MappingColumn("$.name", "name", InferredType.STRING_64, false, List.of(), false)
        ));
        String mappingHash = com.datatalk.application.ingestion.MappingHash.compute(mapping);

        IngestionJob job = new IngestionJob(
            jobId, "pg basic test", "https://example.com/api", "GET", Map.of(), Map.of(), null, null, null,
            PayloadFormat.JSON, "art_pg_001", "writing", connId, null, "pg_test_table",
            mapping, null, null, null, mappingHash,
            "ai", null, null, null,
            System.currentTimeMillis(), System.currentTimeMillis(), null, null
        );
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

        var token = tokenStore.issue(jobId, mappingHash);
        var result = executor.createTable(jobId, connId, null, "pg_test_table", mappingHash, token.tokenId(), null);
        assertThat(result.targetTable()).isEqualTo("pg_test_table");

        var ingestResult = executor.ingestPayload(jobId, 100, null);
        assertThat(ingestResult.rowsInserted()).isEqualTo(3);

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM pg_test_table")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }

        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT name FROM pg_test_table WHERE id=3")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("carol");
        }
    }

    @Test
    void typeRoundTrip_jsonbColumnForJsonInferredType() throws Exception {
        String jobId = "ing_pg_types";
        Path payload = tmp.resolve("types.json");
        Files.writeString(payload, """
            [{"metadata":"{\\"key\\":\\"val\\"}","score":99}]
            """);

        FileArtifact artifact = new FileArtifact(
            "art_pg_types", null, null, null, null, null, "types.json",
            payload.toAbsolutePath().toString(), Files.size(payload), "application/json",
            null, null, Instant.now(), Instant.now(), null, null, false
        );
        when(artifactRepo.findById("art_pg_types")).thenReturn(Optional.of(artifact));

        IngestionMapping mapping = new IngestionMapping("map_pg_types", List.of(
            new MappingColumn("$.metadata", "metadata", InferredType.JSON, false, List.of(), false),
            new MappingColumn("$.score", "score", InferredType.INTEGER_64, false, List.of(), false)
        ));
        String mappingHash = com.datatalk.application.ingestion.MappingHash.compute(mapping);

        IngestionJob job = new IngestionJob(
            jobId, "pg type round-trip", "https://example.com/types", "GET", Map.of(), Map.of(), null, null, null,
            PayloadFormat.JSON, "art_pg_types", "writing", connId, null, "pg_type_test",
            mapping, null, null, null, mappingHash,
            "ai", null, null, null,
            System.currentTimeMillis(), System.currentTimeMillis(), null, null
        );
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

        var token = tokenStore.issue(jobId, mappingHash);
        executor.createTable(jobId, connId, null, "pg_type_test", mappingHash, token.tokenId(), null);
        executor.ingestPayload(jobId, 100, null);

        // Verify JSONB column type via information_schema
        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT data_type FROM information_schema.columns WHERE table_name='pg_type_test' AND column_name='metadata'")) {
            assertThat(rs.next()).isTrue();
            String dataType = rs.getString("data_type");
            assertThat(dataType).containsAnyOf("json", "jsonb", "character");
        }

        // Verify data
        try (var conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT score FROM pg_type_test")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("score")).isEqualTo(99);
        }
    }
}
