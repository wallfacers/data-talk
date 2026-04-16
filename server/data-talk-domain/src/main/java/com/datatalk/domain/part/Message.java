package com.datatalk.domain.part;

import java.util.List;

public record Message(
    String id,
    String sessionId,
    Role role,
    List<Part> parts,
    long createdAt
) {
    public enum Role { USER, ASSISTANT, SYSTEM }
}
