package com.datatalk.application.script;

import com.datatalk.domain.script.ScriptLanguage;
import com.datatalk.domain.script.ScriptRun;
import com.datatalk.domain.script.ScriptStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ScriptRunRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<ScriptRun> MAPPER = (rs, i) -> new ScriptRun(
        rs.getString("id"),
        rs.getString("script_content"),
        ScriptLanguage.fromDb(rs.getString("language")),
        ScriptStatus.fromDb(rs.getString("status")),
        rs.getObject("exit_code") != null ? rs.getInt("exit_code") : null,
        rs.getString("stdout_text"),
        rs.getString("connection_id"),
        rs.getString("target_table"),
        rs.getInt("rows_written"),
        rs.getString("name"),
        rs.getString("created_by_kind"),
        rs.getString("created_by_session_id"),
        rs.getString("error_message"),
        rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
        rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null,
        rs.getObject("duration_ms") != null ? rs.getLong("duration_ms") : null
    );

    public ScriptRunRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ScriptRun run) {
        jdbc.update("""
            INSERT INTO script_run (id, script_content, language, status, exit_code, stdout_text,
                connection_id, target_table, rows_written, name, created_by_kind,
                created_by_session_id, error_message, started_at, finished_at, duration_ms)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            run.id(), run.scriptContent(), run.language().dbValue(), run.status().dbValue(),
            run.exitCode(), run.stdoutText(), run.connectionId(), run.targetTable(),
            run.rowsWritten(), run.name(), run.createdByKind(), run.createdBySessionId(),
            run.errorMessage(),
            run.startedAt() != null ? java.sql.Timestamp.from(run.startedAt()) : null,
            run.finishedAt() != null ? java.sql.Timestamp.from(run.finishedAt()) : null,
            run.durationMs()
        );
    }

    public Optional<ScriptRun> findById(String id) {
        return jdbc.query("SELECT * FROM script_run WHERE id = ?", MAPPER, id)
            .stream().findFirst();
    }

    public List<ScriptRun> list(String connectionId, int limit) {
        if (connectionId != null) {
            return jdbc.query("SELECT * FROM script_run WHERE connection_id = ? ORDER BY started_at DESC LIMIT ?",
                MAPPER, connectionId, limit);
        }
        return jdbc.query("SELECT * FROM script_run ORDER BY started_at DESC LIMIT ?", MAPPER, limit);
    }

    public void updateStatus(String id, ScriptStatus status, Integer exitCode, String errorMessage,
                              String stdoutText, int rowsWritten, Instant finishedAt, Long durationMs) {
        jdbc.update("""
            UPDATE script_run SET status = ?, exit_code = ?, error_message = ?,
                stdout_text = ?, rows_written = ?, finished_at = ?, duration_ms = ?
            WHERE id = ?
            """,
            status.dbValue(), exitCode, errorMessage, stdoutText, rowsWritten,
            finishedAt != null ? java.sql.Timestamp.from(finishedAt) : null,
            durationMs, id
        );
    }
}
