package com.datatalk.adapter.ingestion;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.IngestionConfirmedTokenStore;
import com.datatalk.application.ingestion.IngestionEventPublisher;
import com.datatalk.application.ingestion.IngestionExecutor;
import com.datatalk.application.ingestion.ddl.MysqlIngestionDdlAdapter;
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
import org.testcontainers.containers.MySQLContainer;
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
 * Integration test for IngestionExecutor against MySQL 8.0.
 * Requires Docker. Run with: {@code mvn verify -Dit.test=IngestionExecutorMysqlIT}
 */
@Disabled("Requires Docker — run with -Dit.test=IngestionExecutorMysqlIT")
@Testcontainers
class IngestionExecutorMysqlIT {

    @Container
    static MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("ingest_it")
        .withUsername("root")
        .withPassword("root");

    @TempDir Path tmp;

    IngestionJobRepository jobRepo;
    IngestionConfirmedTokenStore tokenStore;
    ConnectionRepository connRepo;
    ConnectionService connService;
    FileArtifactRepository artifactRepo;
    IngestionEventPublisher publisher;
    ObjectMapper om;
    IngestionExecutor executor;

    String jobId;
    String connId;
    ConnectionRecord connRecord;

    @BeforeEach
    void setUp() throws Exception {
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
            jobRepo, tokenStore, List.of(new MysqlIngestionDdlAdapter()),
            connRepo, connService, artifactRepo,
            jsonParser, jsonlParser, csvParser, htmlParser,
            publisher,
            new com.datatalk.application.ingestion.IngestionRunRegistry(),
            om
        );

        jobId = "ing_it_test_001";
        connId = "conn_mysql_it";

        connRecord = new ConnectionRecord(
            connId, "mysql-it", "mysql",
            MYSQL.getHost(), MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT),
            "ingest_it", "root", new byte[0],
            null, System.currentTimeMillis(), 30,
            "ok", System.currentTimeMillis(),
            null, 1, true, null, false,
            null, null, null
        );

        when(connRepo.findById(connId)).thenReturn(Optional.of(connRecord));
        when(connService.decryptPassword(connId)).thenReturn(MYSQL.getPassword());
    }

    @Test
    void createTableThenIngestThreeJsonRows() throws Exception {
        // Write payload file
        Path payload = tmp.resolve("payload.json");
        Files.writeString(payload, """
            [{"id":1,"name":"alice"},{"id":2,"name":"bob"},{"id":3,"name":"carol"}]
            """);

        // Create artifact pointing to payload
        FileArtifact artifact = new FileArtifact(
            "art_001", null, null, null,
            null, null, "payload.json", payload.toAbsolutePath().toString(),
            Files.size(payload), "application/json", null, null,
            Instant.now(), Instant.now(), null, null, false
        );
        when(artifactRepo.findById("art_001")).thenReturn(Optional.of(artifact));

        // Create mapping with 2 columns
        IngestionMapping mapping = new IngestionMapping("map_001", List.of(
            new MappingColumn("$.id", "id", InferredType.INTEGER_64, false, List.of(), false),
            new MappingColumn("$.name", "name", InferredType.STRING_64, false, List.of(), false)
        ));
        String mappingHash = com.datatalk.application.ingestion.MappingHash.compute(mapping);

        IngestionJob job = new IngestionJob(
            jobId, "mysql basic test", "https://example.com/api", "GET", Map.of(), Map.of(), null, null, null,
            PayloadFormat.JSON, "art_001", "writing", connId, null, "ingest_test_table",
            mapping, null, null, null, mappingHash,
            "ai", null, null, null,
            System.currentTimeMillis(), System.currentTimeMillis(), null, null
        );
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

        // Issue token
        var token = tokenStore.issue(jobId, mappingHash);

        // Execute CREATE TABLE
        var result = executor.createTable(jobId, connId, null, "ingest_test_table", mappingHash, token.tokenId(), null);
        assertThat(result.targetTable()).isEqualTo("ingest_test_table");

        // Execute INSERT
        var ingestResult = executor.ingestPayload(jobId, 100, null);
        assertThat(ingestResult.rowsInserted()).isEqualTo(3);

        // Verify against MySQL directly
        try (var conn = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM ingest_test_table")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }

        try (var conn = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT name FROM ingest_test_table WHERE id=2")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("bob");
        }
    }

    @Test
    void typeRoundTrip_booleanIntegerDecimalTimestamp() throws Exception {
        Path payload = tmp.resolve("types.json");
        Files.writeString(payload, """
            [{"flag":true,"count":42,"price":19.99,"label":"test"},{"flag":false,"count":0,"price":0.01,"label":"edge"}]
            """);

        FileArtifact artifact = new FileArtifact(
            "art_types", null, null, null, null, null, "types.json",
            payload.toAbsolutePath().toString(), Files.size(payload), "application/json",
            null, null, Instant.now(), Instant.now(), null, null, false
        );
        when(artifactRepo.findById("art_types")).thenReturn(Optional.of(artifact));

        IngestionMapping mapping = new IngestionMapping("map_types", List.of(
            new MappingColumn("$.flag", "flag", InferredType.BOOLEAN, false, List.of(), false),
            new MappingColumn("$.count", "count", InferredType.INTEGER_64, false, List.of(), false),
            new MappingColumn("$.price", "price", InferredType.DECIMAL, false, List.of(), false),
            new MappingColumn("$.label", "label", InferredType.STRING_64, false, List.of(), false)
        ));
        String mappingHash = com.datatalk.application.ingestion.MappingHash.compute(mapping);

        String typesJobId = "ing_types_001";
        IngestionJob job = new IngestionJob(
            typesJobId, "mysql type round-trip", "https://example.com/types", "GET", Map.of(), Map.of(), null, null, null,
            PayloadFormat.JSON, "art_types", "writing", connId, null, "type_test",
            mapping, null, null, null, mappingHash,
            "ai", null, null, null,
            System.currentTimeMillis(), System.currentTimeMillis(), null, null
        );
        when(jobRepo.findById(typesJobId)).thenReturn(Optional.of(job));

        var token = tokenStore.issue(typesJobId, mappingHash);
        executor.createTable(typesJobId, connId, null, "type_test", mappingHash, token.tokenId(), null);
        executor.ingestPayload(typesJobId, 100, null);

        try (var conn = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT * FROM type_test ORDER BY count")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("flag")).isFalse();
            assertThat(rs.getLong("count")).isEqualTo(0);
            assertThat(rs.getBigDecimal("price")).isEqualByComparingTo("0.01");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("flag")).isTrue();
            assertThat(rs.getLong("count")).isEqualTo(42);
        }
    }
}
