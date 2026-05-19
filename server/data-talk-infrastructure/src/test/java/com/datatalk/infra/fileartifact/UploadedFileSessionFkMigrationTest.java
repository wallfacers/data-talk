package com.datatalk.infra.fileartifact;

import com.datatalk.infra.persistence.SqlScriptSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the V5__uploaded_file_session_fk.sql migration:
 * FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL.
 */
class UploadedFileSessionFkMigrationTest {

    /**
     * Apply the initial schema (V1) and then the V5 FK migration.
     */
    private void applyMigrations(Statement s) throws Exception {
        // V1: creates sessions and uploaded_file (without FK)
        String v1 = new String(
                new ClassPathResource("db/migration/V1__init.sql").getInputStream().readAllBytes());
        for (String stmt : SqlScriptSplitter.split(v1)) {
            s.executeUpdate(stmt);
        }

        // V5: rebuilds uploaded_file with FK ON DELETE SET NULL
        String v5 = new String(
                new ClassPathResource("db/migration/V5__uploaded_file_session_fk.sql")
                        .getInputStream().readAllBytes());
        for (String stmt : SqlScriptSplitter.split(v5)) {
            s.executeUpdate(stmt);
        }
    }

    @Test
    void validSessionIdIsAllowed(@TempDir Path tmp) throws Exception {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            // SQLite needs this pragma for FK enforcement
            s.executeUpdate("PRAGMA foreign_keys = ON");
            applyMigrations(s);

            // Insert a session, then an uploaded_file referencing it
            s.executeUpdate(
                    "INSERT INTO sessions (id, title, created_at, updated_at) " +
                    "VALUES ('sess-1', 'Test Session', 1000, 1000)");
            s.executeUpdate(
                    "INSERT INTO uploaded_file (id, session_id, filename, mime_type, size_bytes, physical_path, created_at) " +
                    "VALUES ('upl-1', 'sess-1', 'data.csv', 'text/csv', 1024, '/tmp/data.csv', 1700000000000)");

            // Verify the row exists
            try (ResultSet rs = s.executeQuery(
                    "SELECT session_id FROM uploaded_file WHERE id='upl-1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("sess-1");
            }
        }
    }

    @Test
    void invalidSessionIdIsRejected(@TempDir Path tmp) throws Exception {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys = ON");
            applyMigrations(s);

            // No session row — FK should reject
            assertThatThrownBy(() ->
                    s.executeUpdate(
                            "INSERT INTO uploaded_file (id, session_id, filename, mime_type, size_bytes, physical_path, created_at) " +
                            "VALUES ('upl-bad', 'sess-nonexistent', 'bad.csv', 'text/csv', 100, '/tmp/bad.csv', 1700000000000)")
            ).isInstanceOf(java.sql.SQLException.class)
             .hasMessageContaining("FOREIGN KEY");
        }
    }

    @Test
    void nullSessionIdIsAllowed(@TempDir Path tmp) throws Exception {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys = ON");
            applyMigrations(s);

            // NULL session_id should be allowed (no FK check for NULL)
            s.executeUpdate(
                    "INSERT INTO uploaded_file (id, session_id, filename, mime_type, size_bytes, physical_path, created_at) " +
                    "VALUES ('upl-null', NULL, 'orphan.csv', 'text/csv', 100, '/tmp/orphan.csv', 1700000000000)");

            try (ResultSet rs = s.executeQuery(
                    "SELECT session_id FROM uploaded_file WHERE id='upl-null'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isNull();
            }
        }
    }

    @Test
    void deletingSessionSetsNullOnUploadedFile(@TempDir Path tmp) throws Exception {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys = ON");
            applyMigrations(s);

            // Insert session and uploaded_file
            s.executeUpdate(
                    "INSERT INTO sessions (id, title, created_at, updated_at) " +
                    "VALUES ('sess-1', 'Test Session', 1000, 1000)");
            s.executeUpdate(
                    "INSERT INTO uploaded_file (id, session_id, filename, mime_type, size_bytes, physical_path, created_at) " +
                    "VALUES ('upl-1', 'sess-1', 'data.csv', 'text/csv', 1024, '/tmp/data.csv', 1700000000000)");

            // Delete the session
            s.executeUpdate("DELETE FROM sessions WHERE id='sess-1'");

            // Verify session_id is now NULL (ON DELETE SET NULL)
            try (ResultSet rs = s.executeQuery(
                    "SELECT session_id FROM uploaded_file WHERE id='upl-1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isNull();
            }

            // Verify the uploaded_file row still exists (not cascade-deleted)
            try (ResultSet rs = s.executeQuery(
                    "SELECT id, filename FROM uploaded_file WHERE id='upl-1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("id")).isEqualTo("upl-1");
                assertThat(rs.getString("filename")).isEqualTo("data.csv");
            }
        }
    }

    @Test
    void migrationOrphanCleanupDeletesRowsWithInvalidSessionId(@TempDir Path tmp) throws Exception {
        String url = "jdbc:sqlite:" + tmp.resolve("dt.db");

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            // Apply V1 (no FK), insert a row with non-existent session_id
            String v1 = new String(
                    new ClassPathResource("db/migration/V1__init.sql").getInputStream().readAllBytes());
            for (String stmt : SqlScriptSplitter.split(v1)) {
                s.executeUpdate(stmt);
            }

            // Insert orphan row directly before V5
            s.executeUpdate(
                    "INSERT INTO uploaded_file (id, session_id, filename, mime_type, size_bytes, physical_path, created_at) " +
                    "VALUES ('orphan-1', 'nonexistent-session', 'orphan.csv', 'text/csv', 50, '/tmp/orphan.csv', 1700000000000)");

            // Apply V5 migration
            String v5 = new String(
                    new ClassPathResource("db/migration/V5__uploaded_file_session_fk.sql")
                            .getInputStream().readAllBytes());
            for (String stmt : SqlScriptSplitter.split(v5)) {
                s.executeUpdate(stmt);
            }

            // Verify the orphan row was cleaned up by the DELETE step in V5
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM uploaded_file WHERE id='orphan-1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }
    }
}
