package com.datatalk.application.script;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScriptDataWriteServiceTest {

    private ScriptDataWriteService service;
    private ConnectionRepository connRepo;
    private ConnectionService connSvc;

    @BeforeEach
    void setUp() {
        connRepo = mock(ConnectionRepository.class);
        connSvc = mock(ConnectionService.class);
        service = new ScriptDataWriteService(connRepo, connSvc);
    }

    private ConnectionRecord h2Record(String id, String dbName) {
        return new ConnectionRecord(id, "Test", "h2", "", 0,
            dbName, "sa", new byte[0], null, 0, 0, null, null,
            null, 1, true, null, false, null, null, null);
    }

    private void mockTarget(String connId, String dbName) {
        when(connRepo.findById(connId)).thenReturn(Optional.of(h2Record(connId, dbName)));
        when(connSvc.decryptPassword(connId)).thenReturn("");
    }

    // ── write (List<Map>) tests ───────────────────────────────────────

    @Test
    void write_createsTableAndInserts() throws Exception {
        String dbName = "mem:wt1" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockTarget("c1", dbName);

        // First test: verify write with createTable=true works on fresh database
        List<Map<String, Object>> rows = List.of(
            Map.of("id", 1, "name", "Alice"),
            Map.of("id", 2, "name", "Bob")
        );

        var result = service.write("c1", "USERS", rows, true);

        assertThat(result.rowsInserted()).isEqualTo(2);
        assertThat(result.tableName()).isEqualTo("USERS");

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM \"USERS\"");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void write_withColumnTypes_usesOverriddenTypes() throws Exception {
        String dbName = "mem:wt2" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockTarget("c1", dbName);

        List<Map<String, Object>> rows = List.of(
            Map.of("id", 1, "price", "9.99")
        );

        Map<String, String> columnTypes = Map.of("price", "DECIMAL(10,2)");
        var result = service.write("c1", "PRODUCTS", rows, true, columnTypes);

        assertThat(result.rowsInserted()).isEqualTo(1);

        try (Connection c = DriverManager.getConnection("jdbc:h2:" + dbName, "sa", "")) {
            ResultSet rs = c.createStatement().executeQuery(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'PRODUCTS' AND COLUMN_NAME = 'price'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("NUMERIC"); // H2 maps DECIMAL to NUMERIC
        }
    }

    @Test
    void write_emptyRows_returnsZero() {
        String dbName = "mem:wt3" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockTarget("c1", dbName);

        var result = service.write("c1", "EMPTY", List.of(), true);
        assertThat(result.rowsInserted()).isEqualTo(0);
    }

    @Test
    void write_unknownConnection_throws() {
        when(connRepo.findById("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.write("bad", "T", List.of(Map.of("a", 1)), true))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Unknown connection");
    }

    // ── writeStream (ResultSet cursor) tests ───────────────────────────

    @Test
    void writeStream_cursorStreaming() throws Exception {
        String srcDb = "mem:ws1s" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String tgtDb = "mem:ws1t" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockTarget("tgt", tgtDb);

        try (Connection src = DriverManager.getConnection("jdbc:h2:" + srcDb, "sa", "")) {
            src.createStatement().execute("CREATE TABLE SOURCE (ID INT, NAME VARCHAR(100))");
            src.createStatement().execute("INSERT INTO SOURCE VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Charlie')");

            try (ResultSet rs = src.createStatement().executeQuery("SELECT * FROM SOURCE")) {
                var result = service.writeStream("tgt", "TARGET_TABLE", rs, true, null);

                assertThat(result.rowsInserted()).isEqualTo(3);
                assertThat(result.tableName()).isEqualTo("TARGET_TABLE");
                assertThat(result.columns()).hasSize(2);
                assertThat(result.columns().get(0).name()).isEqualTo("ID");
                assertThat(result.columns().get(0).ddlType()).isEqualTo("BIGINT");
                assertThat(result.columns().get(1).name()).isEqualTo("NAME");
                assertThat(result.columns().get(1).ddlType()).isEqualTo("VARCHAR(255)");
            }
        }

        try (Connection tgt = DriverManager.getConnection("jdbc:h2:" + tgtDb, "sa", "")) {
            ResultSet rs = tgt.createStatement().executeQuery("SELECT COUNT(*) FROM TARGET_TABLE");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }
    }

    @Test
    void writeStream_columnTypesOverride() throws Exception {
        String srcDb = "mem:ws2s" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String tgtDb = "mem:ws2t" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockTarget("tgt", tgtDb);

        Map<String, String> overrides = Map.of("ID", "INTEGER", "NAME", "VARCHAR(50)");

        try (Connection src = DriverManager.getConnection("jdbc:h2:" + srcDb, "sa", "")) {
            src.createStatement().execute("CREATE TABLE SOURCE (ID INT, NAME VARCHAR(100))");
            src.createStatement().execute("INSERT INTO SOURCE VALUES (1, 'Test')");

            try (ResultSet rs = src.createStatement().executeQuery("SELECT * FROM SOURCE")) {
                var result = service.writeStream("tgt", "OVERRIDDEN", rs, true, overrides);

                assertThat(result.columns().get(0).ddlType()).isEqualTo("INTEGER");
                assertThat(result.columns().get(1).ddlType()).isEqualTo("VARCHAR(50)");
            }
        }
    }

    @Test
    void writeStream_emptyResultSet() throws Exception {
        String srcDb = "mem:ws3s" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String tgtDb = "mem:ws3t" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockTarget("tgt", tgtDb);

        try (Connection src = DriverManager.getConnection("jdbc:h2:" + srcDb, "sa", "")) {
            src.createStatement().execute("CREATE TABLE EMPTY (ID INT)");

            try (ResultSet rs = src.createStatement().executeQuery("SELECT * FROM EMPTY")) {
                var result = service.writeStream("tgt", "EMPTY_TABLE", rs, true, null);

                assertThat(result.rowsInserted()).isEqualTo(0);
                assertThat(result.columns()).hasSize(1);
                assertThat(result.columns().get(0).name()).isEqualTo("ID");
            }
        }

        // Table should exist even with 0 rows
        try (Connection tgt = DriverManager.getConnection("jdbc:h2:" + tgtDb, "sa", "")) {
            ResultSet rs = tgt.createStatement().executeQuery("SELECT COUNT(*) FROM EMPTY_TABLE");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(0);
        }
    }

    @Test
    void writeStream_createTableFalse_existingTable() throws Exception {
        String srcDb = "mem:ws4s" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String tgtDb = "mem:ws4t" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        mockTarget("tgt", tgtDb);

        // Pre-create target table (uppercase to match H2 unquoted convention)
        try (Connection tgt = DriverManager.getConnection("jdbc:h2:" + tgtDb, "sa", "")) {
            tgt.createStatement().execute("CREATE TABLE EXISTING (ID INT, NAME VARCHAR(100))");
        }

        try (Connection src = DriverManager.getConnection("jdbc:h2:" + srcDb, "sa", "")) {
            src.createStatement().execute("CREATE TABLE SOURCE (ID INT, NAME VARCHAR(100))");
            src.createStatement().execute("INSERT INTO SOURCE VALUES (10, 'Test')");

            try (ResultSet rs = src.createStatement().executeQuery("SELECT * FROM SOURCE")) {
                var result = service.writeStream("tgt", "EXISTING", rs, false, null);
                assertThat(result.rowsInserted()).isEqualTo(1);
            }
        }
    }
}
