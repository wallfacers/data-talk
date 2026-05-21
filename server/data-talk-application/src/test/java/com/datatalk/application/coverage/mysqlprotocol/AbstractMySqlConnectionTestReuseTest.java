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
 * Verifies basic JDBC connectivity (isValid, SELECT 1) works identically
 * across every MySQL-protocol-compatible kind.
 *
 * Concrete subclasses provide:
 * <ul>
 *   <li>{@link #kindUnderTest()} — data-source kind identifier</li>
 *   <li>{@link #dataSource()} — live DataSource to the test container</li>
 * </ul>
 */
public abstract class AbstractMySqlConnectionTestReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();

    private Connection conn;

    @BeforeEach
    void openConnection() throws SQLException {
        conn = dataSource().getConnection();
    }

    @AfterEach
    void closeConnection() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    void connectionIsValid() throws SQLException {
        assertThat(conn.isValid(2))
                .as("kindUnderTest=%s — Connection.isValid(2) should return true", kindUnderTest())
                .isTrue();
    }

    @Test
    void selectOneReturnsOne() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next())
                    .as("kindUnderTest=%s — SELECT 1 should return at least one row", kindUnderTest())
                    .isTrue();
            assertThat(rs.getInt(1))
                    .as("kindUnderTest=%s — SELECT 1 should return value 1", kindUnderTest())
                    .isEqualTo(1);
        }
    }
}
