package com.datatalk.application.importexport;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.script.ScriptDataWriteService;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.application.sql.CalciteSqlRiskAnalyzer;
import com.datatalk.application.sql.SqlStatementSplitters;
import com.datatalk.domain.event.DtEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataExportServiceTest {

    private DataExportService service;
    private ScriptDataWriteService writeService;
    private SessionBusRegistry sessionBusRegistry;
    private CalciteSqlRiskAnalyzer sqlRiskAnalyzer;
    private ConnectionRepository connRepo;
    private ConnectionService connSvc;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        connRepo = mock(ConnectionRepository.class);
        connSvc = mock(ConnectionService.class);
        writeService = new ScriptDataWriteService(connRepo, connSvc);
        sessionBusRegistry = mock(SessionBusRegistry.class);
        SqlStatementSplitters splitters = (kind, sql) -> List.of(sql);
        sqlRiskAnalyzer = new CalciteSqlRiskAnalyzer(splitters);

        service = new DataExportService(writeService, sessionBusRegistry, sqlRiskAnalyzer);
    }

    private ConnectionRecord h2Record(String id, String dbName) {
        return new ConnectionRecord(id, "Test", "h2", "", 0,
            dbName, "sa", new byte[0], null, 0, 0, null, null,
            null, 1, true, null, false, null, null, null);
    }

    private String setupTestDb(String prefix) throws Exception {
        String dbName = "mem:" + prefix + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String connId = prefix + "_conn";
        when(connRepo.findById(connId)).thenReturn(Optional.of(h2Record(connId, dbName)));
        when(connSvc.decryptPassword(connId)).thenReturn("");

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            c.createStatement().execute("CREATE TABLE TEST_DATA (ID INT, NAME VARCHAR(100), SCORE DOUBLE)");
            c.createStatement().execute("INSERT INTO TEST_DATA VALUES (1, 'Alice', 95.5), (2, 'Bob', 87.3), (3, 'Charlie', 72.1)");
        }
        return connId;
    }

    @Test
    void csvSyncExport() throws Exception {
        String connId = setupTestDb("csv");
        DataExportService.ExportResult result = service.export(
            "session1", connId, "SELECT * FROM TEST_DATA", null,
            "csv", "test-export", null);

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.format()).isEqualTo("csv");
        assertThat(result.exportId()).isNotBlank();
        assertThat(result.downloadUrl()).contains(result.exportId());

        // Verify file content
        Path file = service.resolveExportFile(result.exportId());
        assertThat(file).isNotNull();
        assertThat(Files.exists(file)).isTrue();

        String content = Files.readString(file);
        assertThat(content).contains("ID");
        assertThat(content).contains("NAME");
        assertThat(content).contains("Alice");
        assertThat(content).contains("Bob");
        assertThat(content).contains("Charlie");
    }

    @Test
    void jsonSyncExport() throws Exception {
        String connId = setupTestDb("json");
        DataExportService.ExportResult result = service.export(
            "session1", connId, "SELECT * FROM TEST_DATA", null,
            "json", "test-export", null);

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.format()).isEqualTo("json");

        Path file = service.resolveExportFile(result.exportId());
        assertThat(file).isNotNull();
        String content = Files.readString(file);
        assertThat(content).startsWith("[");
        assertThat(content).contains("\"ID\"");
        assertThat(content).contains("\"Alice\"");
        assertThat(content).endsWith("]");
    }

    @Test
    void sqlInsertSyncExport() throws Exception {
        String connId = setupTestDb("sqlins");
        DataExportService.ExportResult result = service.export(
            "session1", connId, "SELECT * FROM TEST_DATA", "TEST_DATA",
            "sql_insert", "test-export", null);

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.format()).isEqualTo("sql_insert");

        Path file = service.resolveExportFile(result.exportId());
        assertThat(file).isNotNull();
        String content = Files.readString(file);
        assertThat(content).contains("INSERT INTO");
        assertThat(content).contains("TEST_DATA");
        assertThat(content).contains("Alice");
        // Verify single quotes are escaped
        assertThat(content).doesNotContain("Alice''");  // Alice has no quotes to escape
    }

    @Test
    void sqlInsertEscapesSingleQuotes() throws Exception {
        String connId = setupTestDb("sqlq");
        // Insert a row with a single quote
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:sqlq" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "")) {
            // Re-setup for this test
        }

        String dbName = "mem:sqlq" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        when(connRepo.findById("sqlq_conn")).thenReturn(Optional.of(h2Record("sqlq_conn", dbName)));
        when(connSvc.decryptPassword("sqlq_conn")).thenReturn("");

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            c.createStatement().execute("CREATE TABLE QUOTED_DATA (ID INT, NAME VARCHAR(100))");
            c.createStatement().execute("INSERT INTO QUOTED_DATA VALUES (1, 'O''Brien')");
        }

        DataExportService.ExportResult result = service.export(
            "session1", "sqlq_conn", "SELECT * FROM QUOTED_DATA", "QUOTED_DATA",
            "sql_insert", "test-quoted", null);

        Path file = service.resolveExportFile(result.exportId());
        String content = Files.readString(file);
        assertThat(content).contains("O''Brien");
    }

    @Test
    void xlsxSyncExport() throws Exception {
        String connId = setupTestDb("xlsx");
        DataExportService.ExportResult result = service.export(
            "session1", connId, "SELECT * FROM TEST_DATA", null,
            "xlsx", "test-export", null);

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.format()).isEqualTo("xlsx");

        Path file = service.resolveExportFile(result.exportId());
        assertThat(file).isNotNull();
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.size(file)).isGreaterThan(0);
        assertThat(file.toString()).endsWith(".xlsx");
    }

    @Test
    void nonSelectQueryRejected() {
        assertThatThrownBy(() -> service.export(
            "session1", "conn1",
            "INSERT INTO test_table VALUES (1, 'test')", null,
            "csv", "test", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("QUERY_NOT_READ_ONLY");

        assertThatThrownBy(() -> service.export(
            "session1", "conn1",
            "UPDATE test_table SET name='x'", null,
            "csv", "test", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("QUERY_NOT_READ_ONLY");

        assertThatThrownBy(() -> service.export(
            "session1", "conn1",
            "DELETE FROM test_table", null,
            "csv", "test", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("QUERY_NOT_READ_ONLY");
    }

    @Test
    void tableNameExport() throws Exception {
        String connId = setupTestDb("tbl");
        DataExportService.ExportResult result = service.export(
            "session1", connId, null, "TEST_DATA",
            "csv", null, null);

        assertThat(result.status()).isEqualTo("completed");

        Path file = service.resolveExportFile(result.exportId());
        assertThat(file).isNotNull();
        String content = Files.readString(file);
        assertThat(content).contains("Alice");
    }

    @Test
    void neitherSqlNorTableName_throwsError() {
        assertThatThrownBy(() -> service.export(
            "session1", "conn1", null, null, "csv", "test", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Either sql or tableName must be provided");
    }

    @Test
    void rowLimitWarning() throws Exception {
        String dbName = "mem:lim" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String connId = "lim_conn";
        when(connRepo.findById(connId)).thenReturn(Optional.of(h2Record(connId, dbName)));
        when(connSvc.decryptPassword(connId)).thenReturn("");

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            c.createStatement().execute("CREATE TABLE BIG_DATA (ID INT)");
            Statement stmt = c.createStatement();
            for (int i = 0; i < 20; i++) {
                stmt.addBatch("INSERT INTO BIG_DATA VALUES (" + i + ")");
            }
            stmt.executeBatch();
        }

        DataExportService.ExportResult result = service.export(
            "session1", connId, "SELECT * FROM BIG_DATA", null,
            "csv", "limited", 5);

        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().get(0)).contains("limited to 5");
    }

    @Test
    void asyncExport_publishesEvent() throws Exception {
        String dbName = "mem:async" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String connId = "async_conn";
        when(connRepo.findById(connId)).thenReturn(Optional.of(h2Record(connId, dbName)));
        when(connSvc.decryptPassword(connId)).thenReturn("");

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            c.createStatement().execute("CREATE TABLE ASYNC_DATA (ID INT, NAME VARCHAR(100))");
            Statement stmt = c.createStatement();
            for (int i = 0; i < 15_000; i++) {
                stmt.addBatch("INSERT INTO ASYNC_DATA VALUES (" + i + ", 'name" + i + "')");
            }
            stmt.executeBatch();
        }

        SessionBus mockBus = mock(SessionBus.class);
        when(sessionBusRegistry.getOrCreate("session1")).thenReturn(mockBus);

        DataExportService.ExportResult result = service.export(
            "session1", connId, "SELECT * FROM ASYNC_DATA", null,
            "csv", "async-export", 15000);

        assertThat(result.status()).isEqualTo("processing");

        // Wait for async export to complete (up to 10s)
        Path file = null;
        for (int i = 0; i < 100; i++) {
            file = service.resolveExportFile(result.exportId());
            if (file != null && Files.exists(file) && Files.size(file) > 0) break;
            Thread.sleep(100);
        }
        assertThat(file).isNotNull();

        // Allow some time for the virtual thread to publish the event
        Thread.sleep(500);
        verify(mockBus).publish(any(DtEvent.ExportCompleted.class));
    }
}
