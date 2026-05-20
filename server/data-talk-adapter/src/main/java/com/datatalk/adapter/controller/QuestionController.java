package com.datatalk.adapter.controller;

import com.datatalk.application.opencode.OpenCodeQuestionClient;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.dto.QuestionReplyRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Proxies OpenCode's {@code /question} endpoints. List is scoped to a DataTalk
 * session (OpenCode lists across all sessions, so we filter by the mapped
 * OpenCode sid and re-stamp each request's {@code sessionID} back to the
 * DataTalk sid for frontend consistency). Reply/reject are {@code requestId}-keyed
 * and global — they release the OpenCode-side {@code Deferred}.
 */
@RestController
public class QuestionController {

    private final OpenCodeQuestionClient questions;
    private final OpenCodeSessionMap sessionMap;
    private final ObjectMapper om;

    public QuestionController(OpenCodeQuestionClient questions,
                              OpenCodeSessionMap sessionMap,
                              ObjectMapper om) {
        this.questions = questions;
        this.sessionMap = sessionMap;
        this.om = om;
    }

    @GetMapping("/api/sessions/{sessionId}/questions")
    public JsonNode listForSession(@PathVariable String sessionId) {
        ArrayNode result = om.createArrayNode();
        String ocSid = sessionMap.openCodeFor(sessionId);
        if (ocSid == null) {
            return result;
        }
        JsonNode all = questions.listQuestions();
        if (all == null || !all.isArray()) {
            return result;
        }
        for (JsonNode q : all) {
            if (ocSid.equals(q.path("sessionID").asText(null)) && q instanceof ObjectNode obj) {
                obj.put("sessionID", sessionId);
                result.add(obj);
            }
        }
        return result;
    }

    @PostMapping("/api/questions/{requestId}/reply")
    public boolean reply(@PathVariable String requestId, @RequestBody QuestionReplyRequest body) {
        List<List<String>> answers = body == null || body.answers() == null ? List.of() : body.answers();
        return questions.replyQuestion(requestId, answers);
    }

    @PostMapping("/api/questions/{requestId}/reject")
    public boolean reject(@PathVariable String requestId) {
        return questions.rejectQuestion(requestId);
    }
}
