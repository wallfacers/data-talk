package com.datatalk.dto;

import java.util.List;

/** Reply body for {@code POST /api/questions/{requestId}/reply}: one selected-label array per sub-question, in order. */
public record QuestionReplyRequest(List<List<String>> answers) {}
