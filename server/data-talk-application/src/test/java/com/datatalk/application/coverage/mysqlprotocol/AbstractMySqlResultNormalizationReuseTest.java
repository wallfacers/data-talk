package com.datatalk.application.coverage.mysqlprotocol;

import com.datatalk.application.sql.JdbcResultValueNormalizer;
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
 * Verifies that {@link JdbcResultValueNormalizer#normalize(Object)} handles
 * MySQL-protocol-specific column types (JSON, DECIMAL, BIT, ENUM, SET, YEAR)
 * without error across every MySQL-protocol-compatible kind.
 *
 * Concrete subclasses provide:
 * <ul>
 *   <li>{@link #kindUnderTest()} — data-source kind identifier</li>
 *   <li>{@link #dataSource()} — live DataSource to the test container</li>
 * </ul>
 */
public abstract class AbstractMySqlResultNormalizationReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();

    private static final String TABLE = "reuse_norm_t";

    private Connection conn;

    @BeforeEach
    void resetTable() throws SQLException {
        conn = dataSource().getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE);
            stmt.execute("CREATE TABLE " + TABLE + " ("
                    + "  id INT PRIMARY KEY"
                    + ", json_col JSON"
                    + ", decimal_col DECIMAL(15,2)"
                    + ", bit_col BIT(8)"
                    + ", enum_col ENUM('small','medium','large')"
                    + ", set_col SET('a','b','c')"
                    + ", year_col YEAR"
                    + ")");
            stmt.execute("INSERT INTO " + TABLE + " VALUES ("
                    + "1, "
                    + "'{\"key\": \"value\"}', "
                    + "99999999999.99, "
                    + "b'10101010', "
                    + "'medium', "
                    + "'a,b', "
                    + "2024"
                    + ")");
        }
    }

    @AfterEach
    void closeConnection() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    void allTypesNormalizeWithoutError() throws SQLException {
        String[] columns = {"json_col", "decimal_col", "bit_col", "enum_col", "set_col", "year_col"};

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + TABLE + " WHERE id = 1")) {
            assertThat(rs.next())
                    .as("kindUnderTest=%s — should have exactly 1 row in %s", kindUnderTest(), TABLE)
                    .isTrue();

            for (String col : columns) {
                Object raw = rs.getObject(col);
                Object normalized = JdbcResultValueNormalizer.normalize(raw);
                assertThat(normalized)
                        .as("kindUnderTest=%s — normalize(%s) should not return null for column '%s'",
                                kindUnderTest(),
                                raw != null ? raw.getClass().getSimpleName() : "null", col)
                        .isNotNull();
            }
        }
    }
}
