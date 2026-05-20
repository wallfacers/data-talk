package com.datatalk.application.opencode;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Port for proxying OpenCode's {@code /question} endpoints. The OpenCode
 * {@code question} tool blocks on a {@code Deferred} living in the OpenCode
 * process; only hitting these endpoints can release a pending turn.
 */
public interface OpenCodeQuestionClient {

    /**
     * {@code GET /question} — all pending question requests across <em>all</em>
     * sessions (OpenCode has no server-side session filter). Per-session
     * filtering is the caller's responsibility.
     */
    JsonNode listQuestions();

    /**
     * {@code POST /question/{requestId}/reply} with body {@code {answers: string[][]}}.
     * {@code answers} has one label array per sub-question, in order.
     */
    boolean replyQuestion(String requestId, List<List<String>> answers);

    /** {@code POST /question/{requestId}/reject} — no body. */
    boolean rejectQuestion(String requestId);
}
