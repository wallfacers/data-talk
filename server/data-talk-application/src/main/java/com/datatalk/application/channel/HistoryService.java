package com.datatalk.application.channel;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.persistence.SyntheticSessionMessageRecord;
import com.datatalk.application.persistence.SyntheticSessionMessageRepository;
import com.datatalk.application.persistence.UserMessageAttachmentRecord;
import com.datatalk.application.persistence.UserMessageAttachmentRepository;
import com.datatalk.domain.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    private final SessionRepository sessions;
    private final ArtifactRepository artifacts;
    private final SyntheticSessionMessageRepository syntheticMessages;
    private final UserMessageAttachmentRepository userAttachments;
    private final OpenCodeGateway gateway;
    private final ObjectMapper om;

    public HistoryService(SessionRepository sessions, ArtifactRepository artifacts,
                          SyntheticSessionMessageRepository syntheticMessages,
                          UserMessageAttachmentRepository userAttachments,
                          OpenCodeGateway gateway, ObjectMapper om) {
        this.sessions = sessions;
        this.artifacts = artifacts;
        this.syntheticMessages = syntheticMessages;
        this.userAttachments = userAttachments;
        this.gateway = gateway;
        this.om = om;
    }

    public JsonNode getMessages(String sessionId) {
        List<MessageEnvelope> envelopes = new ArrayList<>();
        envelopes.addAll(syntheticMessages.findBySession(sessionId).stream()
            .map(this::toSyntheticEnvelope)
            .toList());

        Map<String, List<UserMessageAttachmentRecord>> attachmentsByMessage = userAttachments
            .findBySession(sessionId).stream()
            .collect(Collectors.groupingBy(UserMessageAttachmentRecord::messageId));

        sessions.findById(sessionId)
            .map(s -> s.openCodeSid())
            .filter(Strings::isNotBlank)
            .map(ocSid -> gateway.listMessages(ocSid, null))
            .filter(JsonNode::isArray)
            .ifPresent(node -> envelopes.addAll(toOpenCodeEnvelopes(node, attachmentsByMessage)));

        envelopes.sort(Comparator.comparingLong(MessageEnvelope::createdAt)
            .thenComparingInt(MessageEnvelope::sourceWeight)
            .thenComparing(MessageEnvelope::id));

        ArrayNode merged = om.createArrayNode();
        envelopes.forEach(envelope -> merged.add(envelope.node()));
        return merged;
    }

    public List<ArtifactRecord> getArtifacts(String sessionId) {
        return artifacts.findBySession(sessionId);
    }

    private List<MessageEnvelope> toOpenCodeEnvelopes(JsonNode node,
                                                      Map<String, List<UserMessageAttachmentRecord>> attachmentsByMessage) {
        List<MessageEnvelope> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isObject()) {
                result.add(toOpenCodeEnvelope(item, attachmentsByMessage));
            }
        }
        return result;
    }

    private MessageEnvelope toOpenCodeEnvelope(JsonNode item,
                                               Map<String, List<UserMessageAttachmentRecord>> attachmentsByMessage) {
        JsonNode info = item.path("info");
        String id = info.path("id").asText("");
        String role = info.path("role").asText("other");
        long createdAt = info.path("time").path("created").asLong(0L);
        JsonNode copy = item.deepCopy();
        if ("user".equalsIgnoreCase(role) && !id.isEmpty()) {
            List<UserMessageAttachmentRecord> stored = attachmentsByMessage.get(id);
            if (stored != null && !stored.isEmpty() && copy instanceof ObjectNode obj) {
                JsonNode partsNode = obj.path("parts");
                ArrayNode parts = partsNode instanceof ArrayNode arr ? arr : obj.putArray("parts");
                for (UserMessageAttachmentRecord rec : stored) {
                    try {
                        parts.add(om.readTree(rec.partJson()));
                    } catch (Exception ignore) {
                        // skip malformed row but keep the rest of the message intact
                    }
                }
            }
        }
        return new MessageEnvelope(createdAt, sourceWeight(role, false), id, copy);
    }

    private MessageEnvelope toSyntheticEnvelope(SyntheticSessionMessageRecord record) {
        ObjectNode node = om.createObjectNode();
        ObjectNode info = node.putObject("info");
        info.put("id", record.id());
        info.put("role", "user");
        info.put("sessionID", record.sessionId());
        info.putObject("time").put("created", record.createdAt());

        ObjectNode part = node.putArray("parts").addObject();
        part.put("type", "text");
        part.put("id", "prt_" + record.id());
        part.put("sessionID", record.sessionId());
        part.put("messageID", record.id());
        part.put("text", record.text());
        part.set("metadata", parseJson(record.metadataJson()));

        return new MessageEnvelope(record.createdAt(), sourceWeight("user", true), record.id(), node);
    }

    private JsonNode parseJson(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse synthetic metadata json", e);
        }
    }

    private int sourceWeight(String role, boolean syntheticUser) {
        if (syntheticUser) {
            return 0;
        }
        String normalized = role == null ? "other" : role.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "user" -> 1;
            case "assistant" -> 2;
            default -> 3;
        };
    }

    private record MessageEnvelope(long createdAt, int sourceWeight, String id, JsonNode node) {}
}
