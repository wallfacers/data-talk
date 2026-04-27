package com.datatalk.domain.stage;

import java.util.Objects;

/**
 * Payload + searchable content for a stage tab, stored separately
 * from metadata for selective loading.
 */
public record StageTabContent(
    String tabId,
    String payloadJson,
    String contentText,
    int contentVersion,
    long updatedAt
) {
    public StageTabContent {
        Objects.requireNonNull(tabId, "tabId must not be null");
        Objects.requireNonNull(payloadJson, "payloadJson must not be null");
        Objects.requireNonNull(contentText, "contentText must not be null");
    }
}
