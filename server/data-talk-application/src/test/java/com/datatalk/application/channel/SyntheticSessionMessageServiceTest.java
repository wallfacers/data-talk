package com.datatalk.application.channel;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.persistence.SyntheticSessionMessageRecord;
import com.datatalk.application.persistence.SyntheticSessionMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyntheticSessionMessageServiceTest {

    private final SessionRepository sessions = mock(SessionRepository.class);
    private final SyntheticSessionMessageRepository messages = mock(SyntheticSessionMessageRepository.class);
    private final SyntheticSessionMessageService service =
        new SyntheticSessionMessageService(sessions, messages, new ObjectMapper());

    @Test
    void createBangQueryUserMessagePersistsSyntheticMetadataAndMarksSessionSent() {
        long clientCreatedAt = 32503680000000L;
        when(sessions.findById("sess-1")).thenReturn(Optional.of(
            new SessionRecord("sess-1", "conn-1", "t", false, null, 1L, 1L, false)));

        SyntheticSessionMessageRecord record =
            service.createBangQueryUserMessage("sess-1", "!select 1", clientCreatedAt);

        assertThat(record.sessionId()).isEqualTo("sess-1");
        assertThat(record.kind()).isEqualTo(SyntheticSessionMessageService.BANG_QUERY_USER_KIND);
        assertThat(record.text()).isEqualTo("!select 1");
        assertThat(record.metadataJson()).contains("\"displayKind\":\"bang_query_user\"");
        assertThat(record.metadataJson()).contains("\"queryMode\":\"direct_sql\"");
        assertThat(record.createdAt()).isEqualTo(clientCreatedAt);
        verify(messages).insert(argThat(saved ->
            saved.id().startsWith("sqm_")
                && saved.sessionId().equals("sess-1")
                && saved.kind().equals(SyntheticSessionMessageService.BANG_QUERY_USER_KIND)
                && saved.text().equals("!select 1")
                && saved.createdAt() == clientCreatedAt
        ));
        verify(sessions).markHasEverSent(eq("sess-1"), longThat(updatedAt -> updatedAt > 0 && updatedAt < clientCreatedAt));
    }

    @Test
    void createBangQueryUserMessageRejectsBlankText() {
        assertThatThrownBy(() -> service.createBangQueryUserMessage("sess-1", "   ", 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("bang query message text must not be blank");
    }
}
