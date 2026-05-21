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
 * Verifies that JDBC {@link java.sql.DatabaseMetaData} queries required by
 * DataTalk work identically across every MySQL-protocol-compatible kind.
 *
 * Concrete subclasses provide:
 * <ul>
 *   <li>{@link #kindUnderTest()} — data-source kind identifier</li>
 *   <li>{@link #dataSource()} — live DataSource to the test container</li>
 *   <li>{@link #testDatabaseName()} — name of the database/catalog to verify</li>
 * </ul>
 */
public abstract class AbstractMySqlMetadataReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();
    protected abstract String testDatabaseName();

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
    void getCatalogsReturnsAtLeastTestDatabase() throws SQLException {
        var meta = conn.getMetaData();
        Set<String> catalogs = new HashSet<>();
        try (ResultSet rs = meta.getCatalogs()) {
            while (rs.next()) {
                catalogs.add(rs.getString("TABLE_CAT"));
            }
        }
        assertThat(catalogs)
                .as("kindUnderTest=%s — getCatalogs() should include test database '%s'",
                        kindUnderTest(), testDatabaseName())
                .contains(testDatabaseName());
    }

    @Test
    void getTablesReturnsTestTable() throws SQLException {
        String tableName = "reuse_meta_t";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tableName);
            stmt.execute("CREATE TABLE " + tableName + " (id INT PRIMARY KEY, name VARCHAR(50))");
        }

        var meta = conn.getMetaData();
        boolean found = false;
        try (ResultSet rs = meta.getTables(testDatabaseName(), null, tableName, new String[]{"TABLE"})) {
            while (rs.next()) {
                if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    found = true;
                }
            }
        }
        assertThat(found)
                .as("kindUnderTest=%s — getTables() should report created table '%s'",
                        kindUnderTest(), tableName)
                .isTrue();
    }

    @Test
    void informationSchemaKeyColumnUsageIsQueryable() throws SQLException {
        String parentTable = "reuse_fk_parent";
        String childTable = "reuse_fk_child";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + childTable);
            stmt.execute("DROP TABLE IF EXISTS " + parentTable);
            stmt.execute("CREATE TABLE " + parentTable + " (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE " + childTable
                    + " (id INT PRIMARY KEY, parent_id INT,"
                    + " FOREIGN KEY (parent_id) REFERENCES " + parentTable + "(id))");
        }

        boolean found = false;
        try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME"
                        + " FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE"
                        + " WHERE TABLE_SCHEMA = '" + testDatabaseName() + "'"
                        + " AND REFERENCED_TABLE_NAME IS NOT NULL")) {
            while (rs.next()) {
                if (childTable.equalsIgnoreCase(rs.getString("TABLE_NAME"))
                        && "parent_id".equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    found = true;
                }
            }
        }
        assertThat(found)
                .as("kindUnderTest=%s — INFORMATION_SCHEMA.KEY_COLUMN_USAGE should expose FK relationship",
                        kindUnderTest())
                .isTrue();
    }
}
