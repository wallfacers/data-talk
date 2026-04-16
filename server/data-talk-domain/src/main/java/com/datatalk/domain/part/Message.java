package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record Message(
        String id,
        @JsonProperty("sessionID") String sessionId,
        @JsonProperty("messageID") String messageId,
        Role role,
        List<Part> parts,
        Instant createdAt
) {

    public enum Role {
        USER, ASSISTANT, SYSTEM
    }
}
