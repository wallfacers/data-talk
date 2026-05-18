package com.datatalk.application.importexport;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.script.ScriptDataWriteService;
import com.datatalk.application.upload.UploadedFileRepository;
import com.datatalk.domain.upload.UploadedFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataImportServiceTest {

    private DataImportService service;
    private UploadedFileRepository fileRepo;
    private ConnectionRepository connRepo;
    private ConnectionService connSvc;
    private ScriptDataWriteService writeService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileRepo = mock(UploadedFileRepository.class);
        connRepo = mock(ConnectionRepository.class);
        connSvc = mock(ConnectionService.class);
        writeService = new ScriptDataWriteService(connRepo, connSvc);
        objectMapper = new ObjectMapper();
        service = new DataImportService(fileRepo, writeService, objectMapper);
    }

    private ConnectionRecord h2Record(String id, String dbName) {
        return new ConnectionRecord(id, "Test", "h2", "", 0,
            dbName, "sa", new byte[0], null, 0, 0, null, null,
            null, 1, true, null, false, null, null, null);
    }

    private void mockConnection(String connId, String dbName) {
        when(connRepo.findById(connId)).thenReturn(Optional.of(h2Record(connId, dbName)));
        when(connSvc.decryptPassword(connId)).thenReturn("");
    }

    private UploadedFile mockFile(String fileId, String filename, Path physicalPath) throws Exception {
        UploadedFile file = new UploadedFile(fileId, "session1", filename,
            "application/octet-stream", Files.size(physicalPath),
            physicalPath.toAbsolutePath().toString(), Map.of(), Instant.now());
        when(fileRepo.findById(fileId)).thenReturn(Optional.of(file));
        return file;
    }

    // ── CSV tests ─────────────────────────────────────────────────────

    @Test
    void importCsv_endToEnd() throws Exception {
        String dbName = "mem:csv1" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockConnection("c1", dbName);

        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, "id,name,age\n1,Alice,30\n2,Bob,25\n3,Charlie,35\n");
        mockFile("f1", "test.csv", csvFile);

        var result = service.importFromFile("f1", "c1", "USERS", true, null, null);

        assertThat(result.rowsImported()).isEqualTo(3);
        assertThat(result.tableName()).isEqualTo("USERS");
        assertThat(result.columns()).hasSize(3);
        assertThat(result.sampleRows()).hasSize(3);
        assertThat(result.importId()).isNotBlank();

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM \"USERS\"");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }
    }

    @Test
    void importCsv_withColumnMappings() throws Exception {
        String dbName = "mem:csv2" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockConnection("c1", dbName);

        Path csvFile = tempDir.resolve("map.csv");
        Files.writeString(csvFile, "id,name\n1,Alice\n2,Bob\n");
        mockFile("f2", "map.csv", csvFile);

        Map<String, String> mappings = Map.of("id", "user_id", "name", "user_name");
        var result = service.importFromFile("f2", "c1", "MAPPED", true, mappings, null);

        assertThat(result.rowsImported()).isEqualTo(2);
        assertThat(result.columns().stream().map(c -> c.name()).toList())
            .containsExactly("user_id", "user_name");

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            ResultSet rs = c.createStatement().executeQuery("SELECT * FROM \"MAPPED\"");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("user_id")).isEqualTo(1);
            assertThat(rs.getString("user_name")).isEqualTo("Alice");
        }
    }

    @Test
    void importCsv_quotedFields() throws Exception {
        String dbName = "mem:csv3" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockConnection("c1", dbName);

        Path csvFile = tempDir.resolve("quoted.csv");
        Files.writeString(csvFile, "id,desc\n1,\"hello, world\"\n2,\"he said \"\"hi\"\"\"\n");
        mockFile("f3", "quoted.csv", csvFile);

        var result = service.importFromFile("f3", "c1", "QUOTED", true, null, null);

        assertThat(result.rowsImported()).isEqualTo(2);

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            ResultSet rs = c.createStatement().executeQuery("SELECT * FROM \"QUOTED\" ORDER BY \"id\"");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("desc")).isEqualTo("hello, world");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("desc")).isEqualTo("he said \"hi\"");
        }
    }

    // ── JSON tests ────────────────────────────────────────────────────

    @Test
    void importJson_endToEnd() throws Exception {
        String dbName = "mem:json1" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockConnection("c1", dbName);

        Path jsonFile = tempDir.resolve("test.json");
        Files.writeString(jsonFile, "[{\"id\":1,\"name\":\"Alice\",\"score\":95.5},{\"id\":2,\"name\":\"Bob\",\"score\":87.3}]");
        mockFile("j1", "test.json", jsonFile);

        var result = service.importFromFile("j1", "c1", "SCORES", true, null, null);

        assertThat(result.rowsImported()).isEqualTo(2);
        assertThat(result.columns()).hasSize(3);
        assertThat(result.sampleRows()).hasSize(2);

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM \"SCORES\"");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    // ── cross-DB copy tests ───────────────────────────────────────────

    @Test
    void importFromQuery_crossDbCopy() throws Exception {
        String srcDb = "mem:src" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String tgtDb = "mem:tgt" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockConnection("src", srcDb);
        mockConnection("tgt", tgtDb);

        // Setup source data
        try (Connection src = DriverManager.getConnection("jdbc:h2:" + srcDb, "sa", "")) {
            src.createStatement().execute("CREATE TABLE SOURCE (ID INT, NAME VARCHAR(100))");
            src.createStatement().execute("INSERT INTO SOURCE VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Charlie')");
        }

        var result = service.importFromQuery("src", "SELECT * FROM SOURCE", "tgt", "TARGET", true);

        assertThat(result.rowsImported()).isEqualTo(3);
        assertThat(result.tableName()).isEqualTo("TARGET");
        assertThat(result.columns()).hasSize(2);

        try (Connection tgt = DriverManager.getConnection("jdbc:h2:" + tgtDb, "sa", "")) {
            ResultSet rs = tgt.createStatement().executeQuery("SELECT COUNT(*) FROM TARGET");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }
    }

    // ── error handling tests ──────────────────────────────────────────

    @Test
    void importFromFile_fileNotFound_throws() {
        when(fileRepo.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importFromFile("nonexistent", "c1", "T", true, null, null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("FILE_NOT_FOUND");
    }

    @Test
    void importFromFile_unsupportedFileType_throws() throws Exception {
        String dbName = "mem:err1" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockConnection("c1", dbName);

        Path imgFile = tempDir.resolve("photo.png");
        Files.writeString(imgFile, "not a real png");
        mockFile("img1", "photo.png", imgFile);

        assertThatThrownBy(() -> service.importFromFile("img1", "c1", "T", true, null, null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("UNSUPPORTED_FILE_TYPE");
    }

    // ── empty file tests ──────────────────────────────────────────────

    @Test
    void importCsv_emptyFile() throws Exception {
        String dbName = "mem:empty1" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockConnection("c1", dbName);

        Path csvFile = tempDir.resolve("empty.csv");
        Files.writeString(csvFile, "");
        mockFile("e1", "empty.csv", csvFile);

        var result = service.importFromFile("e1", "c1", "EMPTY", true, null, null);

        assertThat(result.rowsImported()).isEqualTo(0);
        assertThat(result.columns()).isEmpty();
    }

    @Test
    void importCsv_headerOnly() throws Exception {
        String dbName = "mem:hdr1" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockConnection("c1", dbName);

        Path csvFile = tempDir.resolve("header.csv");
        Files.writeString(csvFile, "id,name,age\n");
        mockFile("h1", "header.csv", csvFile);

        var result = service.importFromFile("h1", "c1", "HEADER_ONLY", true, null, null);

        assertThat(result.rowsImported()).isEqualTo(0);
        assertThat(result.columns()).hasSize(3);
    }
}
