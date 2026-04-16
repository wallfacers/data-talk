package com.datatalk.application.opencode;

import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;

import java.util.Map;

/**
 * Minimal view of the OpenCode SSE event stream. Only the subset we actually
 * translate is modelled; everything else lands in {@link Unknown} and is
 * dropped by the translator.
 */
public sealed interface OcEvent {
    record ServerConnected() implements OcEvent {}
    record SessionStatus(String status, Map<String, Object> retryInfo) implements OcEvent {}
    record MessageUpdated(Message message) implements OcEvent {}
    record MessagePartUpdated(Part part) implements OcEvent {}
    record MessagePartDelta(String partId, String field, String delta) implements OcEvent {}
    record MessagePartRemoved(String partId) implements OcEvent {}
    record Unknown(String type, Map<String, Object> payload) implements OcEvent {}
}
