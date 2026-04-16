package com.datatalk.application.channel;

import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.domain.part.Message;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryService {

    private final MessageRepository messages;
    private final ArtifactRepository artifacts;

    public HistoryService(MessageRepository messages, ArtifactRepository artifacts) {
        this.messages = messages;
        this.artifacts = artifacts;
    }

    public List<Message> getMessages(String sessionId) {
        return messages.findBySession(sessionId);
    }

    public List<ArtifactRecord> getArtifacts(String sessionId) {
        return artifacts.findBySession(sessionId);
    }
}
