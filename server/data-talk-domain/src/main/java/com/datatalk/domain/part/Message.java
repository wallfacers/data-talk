package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

public record Message(
    String id,
    String sessionId,
    Role role,
    List<Part> parts,
    long createdAt,
    String providerID,
    String modelID
) {
    public enum Role {
        USER, ASSISTANT, SYSTEM;

        @JsonValue
        public String toJsonValue() { return name().toLowerCase(); }

        @JsonCreator
        public static Role from(String s) { return valueOf(s.toUpperCase()); }
    }
}
