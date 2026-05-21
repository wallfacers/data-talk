package com.datatalk.dto;

import java.util.List;

public record SessionDeleteBlockedDto(
    String error,
    String sessionId,
    List<SessionCandidateDto> candidates
) {
    public static SessionDeleteBlockedDto of(String sessionId, List<SessionCandidateDto> candidates) {
        return new SessionDeleteBlockedDto("session_has_archive_candidates", sessionId, candidates);
    }
}