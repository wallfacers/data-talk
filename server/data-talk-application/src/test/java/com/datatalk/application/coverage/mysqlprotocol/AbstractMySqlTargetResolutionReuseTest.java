package com.datatalk.application.coverage.mysqlprotocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that target-database resolution (USE statement, SHOW DATABASES)
 * works identically across every MySQL-protocol-compatible kind.
 *
 * Concrete subclasses provide:
 * <ul>
 *   <li>{@link #kindUnderTest()} — data-source kind identifier</li>
 *   <li>{@link #dataSource()} — live DataSource to the test container</li>
 *   <li>{@link #testDatabaseName()} — name of the test database</li>
 *   <li>{@link #expectedSystemDatabases()} — system databases expected in SHOW DATABASES</li>
 * </ul>
 */
public abstract class AbstractMySqlTargetResolutionReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();
    protected abstract String testDatabaseName();
    protected abstract Set<String> expectedSystemDatabases();

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
    void useStatementChangesActiveDatabase() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("USE " + testDatabaseName());
        }
        String activeDb;
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT DATABASE()")) {
            assertThat(rs.next()).isTrue();
            activeDb = rs.getString(1);
        }
        assertThat(activeDb)
                .as("kindUnderTest=%s — USE %s should change active database",
                        kindUnderTest(), testDatabaseName())
                .isEqualToIgnoringCase(testDatabaseName());
    }

    @Test
    void showDatabasesIncludesTestDatabaseAndExpectedSystemDatabases() throws SQLException {
        Set<String> databases = new HashSet<>();
        try (ResultSet rs = conn.createStatement().executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                databases.add(rs.getString(1).toLowerCase());
            }
        }
        assertThat(databases)
                .as("kindUnderTest=%s — SHOW DATABASES should include test database", kindUnderTest())
                .contains(testDatabaseName().toLowerCase());

        Set<String> expected = new HashSet<>();
        for (String sysDb : expectedSystemDatabases()) {
            expected.add(sysDb.toLowerCase());
        }
        assertThat(databases)
                .as("kindUnderTest=%s — SHOW DATABASES should include expected system databases %s",
                        kindUnderTest(), expected)
                .containsAll(expected);
    }
}
