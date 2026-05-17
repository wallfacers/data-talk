package com.datatalk.application.persistence;

public record UserMessageAttachmentRecord(
    String id,
    String sessionId,
    String messageId,
    int position,
    String partJson,
    long createdAt
) {}
