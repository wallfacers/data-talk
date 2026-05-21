package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActionInvocationRepository {

    private final JdbcTemplate jdbc;

    public ActionInvocationRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void start(String callId, String sessionId, String actionId, String inputJson, long now) {
        jdbc.update("""
            INSERT INTO action_invocations(call_id, session_id, action_id, status, input_json, started_at)
            VALUES(?, ?, ?, 'running', ?, ?)
            """, callId, sessionId, actionId, inputJson, now);
    }

    public void complete(String callId, String outputJson, long now) {
        jdbc.update("""
            UPDATE action_invocations
            SET status = 'completed', output_json = ?, ended_at = ?
            WHERE call_id = ?
            """, outputJson, now, callId);
    }

    public void fail(String callId, String errorJson, long now) {
        jdbc.update("""
            UPDATE action_invocations
            SET status = 'error', error_json = ?, ended_at = ?
            WHERE call_id = ?
            """, errorJson, now, callId);
    }

    public void cancel(String callId, long now) {
        jdbc.update("""
            UPDATE action_invocations SET status = 'cancelled', ended_at = ? WHERE call_id = ?
            """, now, callId);
    }
}
