package com.datatalk.domain.event;

/**
 * Error information carried by error events.
 */
public record ErrorInfo(
        String code,
        String message,
        boolean retriable,
        java.util.Map<String, Object> details
) {
}
