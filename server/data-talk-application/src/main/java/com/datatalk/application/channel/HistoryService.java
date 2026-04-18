package com.datatalk.application.channel;

import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryService {

    private final SessionRepository sessions;
    private final ArtifactRepository artifacts;
    private final OpenCodeGateway gateway;
    private final ObjectMapper om;

    public HistoryService(SessionRepository sessions, ArtifactRepository artifacts,
                          OpenCodeGateway gateway, ObjectMapper om) {
        this.sessions = sessions;
        this.artifacts = artifacts;
        this.gateway = gateway;
        this.om = om;
    }

    public JsonNode getMessages(String sessionId) {
        return sessions.findById(sessionId)
            .map(s -> s.openCodeSid())
            .filter(Strings::isNotBlank)
            .map(ocSid -> gateway.listMessages(ocSid, null))
            .orElseGet(om::createArrayNode);
    }

    public List<ArtifactRecord> getArtifacts(String sessionId) {
        return artifacts.findBySession(sessionId);
    }
}