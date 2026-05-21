package com.datatalk.application.coverage.mysqlprotocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that JDBC batch DML (addBatch / executeBatch) works identically
 * across every MySQL-protocol-compatible kind.
 *
 * Concrete subclasses provide:
 * <ul>
 *   <li>{@link #kindUnderTest()} — data-source kind identifier</li>
 *   <li>{@link #dataSource()} — live DataSource to the test container</li>
 * </ul>
 */
public abstract class AbstractMySqlBatchDmlReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();

    private static final String TABLE = "reuse_batch_t";

    private Connection conn;

    @BeforeEach
    void resetTable() throws SQLException {
        conn = dataSource().getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE);
            stmt.execute("CREATE TABLE " + TABLE + " (id INT PRIMARY KEY, val VARCHAR(50))");
        }
    }

    @AfterEach
    void closeConnection() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    void executeBatchInsertsAllRows() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.addBatch("INSERT INTO " + TABLE + " (id, val) VALUES (1, 'a')");
            stmt.addBatch("INSERT INTO " + TABLE + " (id, val) VALUES (2, 'b')");
            stmt.addBatch("INSERT INTO " + TABLE + " (id, val) VALUES (3, 'c')");
            int[] counts = stmt.executeBatch();

            assertThat(counts)
                    .as("kindUnderTest=%s — executeBatch() should return per-row counts", kindUnderTest())
                    .hasSize(3);

            int totalUpdated = 0;
            for (int c : counts) {
                assertThat(c)
                        .as("kindUnderTest=%s — each batch row should report 1 affected row", kindUnderTest())
                        .isGreaterThanOrEqualTo(1);
                totalUpdated += (c > 0 ? c : 0);
            }
            assertThat(totalUpdated)
                    .as("kindUnderTest=%s — total batch-affected rows should be 3", kindUnderTest())
                    .isGreaterThanOrEqualTo(3);
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("kindUnderTest=%s — table should contain exactly 3 rows after batch insert", kindUnderTest())
                    .isEqualTo(3);
        }
    }
}
