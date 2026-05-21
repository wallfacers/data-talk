package com.datatalk.application.channel;

import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.persistence.SyntheticSessionMessageRecord;
import com.datatalk.application.persistence.SyntheticSessionMessageRepository;
import com.datatalk.domain.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SyntheticSessionMessageService {

    public static final String BANG_QUERY_USER_KIND = "bang_query_user";

    private final SessionRepository sessions;
    private final SyntheticSessionMessageRepository messages;
    private final ObjectMapper om;

    public SyntheticSessionMessageService(SessionRepository sessions,
                                          SyntheticSessionMessageRepository messages,
                                          ObjectMapper om) {
        this.sessions = sessions;
        this.messages = messages;
        this.om = om;
    }

    @Transactional
    public SyntheticSessionMessageRecord createBangQueryUserMessage(String sessionId, String text, long createdAt) {
        if (Strings.isBlank(text)) {
            throw new IllegalArgumentException("bang query message text must not be blank");
        }
        sessions.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException("unknown session: " + sessionId));

        long effectiveCreatedAt = createdAt > 0 ? createdAt : System.currentTimeMillis();

        SyntheticSessionMessageRecord record = new SyntheticSessionMessageRecord(
            "sqm_" + UUID.randomUUID(),
            sessionId,
            BANG_QUERY_USER_KIND,
            text,
            bangQueryMetadataJson(),
            effectiveCreatedAt
        );
        messages.insert(record);
        sessions.markHasEverSent(sessionId, System.currentTimeMillis());
        return record;
    }

    public List<SyntheticSessionMessageRecord> findBySession(String sessionId) {
        return messages.findBySession(sessionId);
    }

    private String bangQueryMetadataJson() {
        return om.createObjectNode()
            .put("displayKind", BANG_QUERY_USER_KIND)
            .put("queryMode", "direct_sql")
            .toString();
    }
}
