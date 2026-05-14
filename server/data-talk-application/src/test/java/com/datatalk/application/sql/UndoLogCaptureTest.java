package com.datatalk.application.sql;

import com.datatalk.application.persistence.UndoLogRepository;
import com.datatalk.domain.undo.UndoOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UndoLogCaptureTest {

    private final UndoLogRepository repo = mock(UndoLogRepository.class);
    private final UndoLogCapture capture = new UndoLogCapture(repo);

    private Connection h2;

    @BeforeEach
    void setUp() throws SQLException {
        h2 = DriverManager.getConnection("jdbc:h2:mem:undotest;DB_CLOSE_DELAY=-1");
        try (Statement stmt = h2.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users_with_pk (id INT PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("CREATE TABLE IF NOT EXISTS users_no_pk (id INT, name VARCHAR(100))");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement stmt = h2.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS users_with_pk");
            stmt.execute("DROP TABLE IF EXISTS users_no_pk");
        }
        h2.close();
    }

    @Test
    void selectStatement_returnsSkipped() {
        UndoOutcome outcome = capture.capture(h2, "SELECT * FROM users_with_pk", "s1", "c1", "db", null);
        assertThat(outcome).isInstanceOf(UndoOutcome.Skipped.class);
        assertThat(((UndoOutcome.Skipped) outcome).statementType()).isEqualTo("non_dml");
    }

    @Test
    void unparseableSql_returnsNotUndoable() {
        UndoOutcome outcome = capture.capture(h2, "INVALID SQL {{{", "s1", "c1", "db", null);
        assertThat(outcome).isInstanceOf(UndoOutcome.NotUndoable.class);
        assertThat(((UndoOutcome.NotUndoable) outcome).reason()).isEqualTo("parse_failed");
    }

    @Test
    void tableWithoutPrimaryKey_returnsNotUndoableAndInsertsLog() {
        UndoOutcome outcome = capture.capture(h2, "INSERT INTO users_no_pk (id, name) VALUES (1, 'Alice')", "s1", "c1", "db", null);
        assertThat(outcome).isInstanceOf(UndoOutcome.NotUndoable.class);
        assertThat(((UndoOutcome.NotUndoable) outcome).reason()).isEqualTo("no_primary_key");
        verify(repo).insert(any());
    }

    @Test
    void insertWithPrimaryKey_returnsCaptured() {
        UndoOutcome outcome = capture.capture(h2, "INSERT INTO users_with_pk (id, name) VALUES (1, 'Alice')", "s1", "c1", "db", null);
        assertThat(outcome).isInstanceOf(UndoOutcome.Captured.class);
        UndoOutcome.Captured captured = (UndoOutcome.Captured) outcome;
        assertThat(captured.capture().undoable()).isTrue();
        assertThat(captured.capture().operation()).isEqualTo("INSERT");
        assertThat(captured.capture().beforeState()).isNull();
        verify(repo).insert(any());
    }

    @Test
    void updateWithPrimaryKey_returnsCapturedWithBeforeState() throws SQLException {
        try (Statement stmt = h2.createStatement()) {
            stmt.execute("INSERT INTO users_with_pk (id, name) VALUES (1, 'Alice')");
        }
        UndoOutcome outcome = capture.capture(h2, "UPDATE users_with_pk SET name = 'Bob' WHERE id = 1", "s1", "c1", "db", null);
        assertThat(outcome).isInstanceOf(UndoOutcome.Captured.class);
        UndoOutcome.Captured captured = (UndoOutcome.Captured) outcome;
        assertThat(captured.capture().undoable()).isTrue();
        assertThat(captured.capture().affectedRows()).isEqualTo(1);
        assertThat(captured.capture().inverseSql()).contains("UPDATE");
        assertThat(captured.capture().inverseSql()).contains("'Alice'");
        verify(repo).insert(any());
    }

    @Test
    void deleteAffectingMoreThan100Rows_returnsNotUndoable() throws SQLException {
        try (Statement stmt = h2.createStatement()) {
            for (int i = 0; i < 150; i++) {
                stmt.execute("INSERT INTO users_with_pk (id, name) VALUES (" + i + ", 'user" + i + "')");
            }
        }
        UndoOutcome outcome = capture.capture(h2, "DELETE FROM users_with_pk WHERE id < 150", "s1", "c1", "db", null);
        assertThat(outcome).isInstanceOf(UndoOutcome.NotUndoable.class);
        assertThat(((UndoOutcome.NotUndoable) outcome).reason()).startsWith("too_many_rows");
    }
}
