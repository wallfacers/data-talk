package com.datatalk.application.channel;

import com.datatalk.application.ai.AiUserPrefsRepository;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.domain.part.Part;
import com.datatalk.domain.part.TextPart;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChannelServiceModelParamTest {

    private SessionRepository sessions;
    private MessageRepository messages;
    private SessionBusRegistry buses;
    private PendingCallRegistry pending;
    private IdGenerator ids;
    private Clock clock;
    private OpenCodeGateway gateway;
    private OpenCodeSessionMap sessionMap;
    private ObjectMapper om;
    private AiUserPrefsRepository userPrefs;
    private ChannelService svc;

    @BeforeEach
    void setUp() {
        sessions = mock(SessionRepository.class);
        messages = mock(MessageRepository.class);
        buses = mock(SessionBusRegistry.class);
        pending = mock(PendingCallRegistry.class);
        ids = mock(IdGenerator.class);
        clock = Clock.systemUTC();
        gateway = mock(OpenCodeGateway.class);
        sessionMap = mock(OpenCodeSessionMap.class);
        om = new ObjectMapper();
        userPrefs = mock(AiUserPrefsRepository.class);

        svc = new ChannelService(sessions, messages, buses, pending, ids, clock, gateway, sessionMap, om, userPrefs);
    }

    @Test
    void sendMessage_forwards_current_model_when_set() {
        when(sessions.findById("s1")).thenReturn(Optional.of(
            new SessionRecord("s1", "test", "T", false, null, 100L, 100L, false)));
        when(sessionMap.openCodeFor("s1")).thenReturn("oc-1");
        when(userPrefs.getCurrentModel()).thenReturn("openai/gpt-5");
        SessionBus bus = mock(SessionBus.class);
        when(buses.getOrCreate("s1")).thenReturn(bus);

        TextPart p = new TextPart("p1", "s1", "m1", "hi", null, null, null, Map.of());
        svc.sendMessage("s1", List.of(p));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(eq("oc-1"), cap.capture());
        assertThat(cap.getValue()).containsEntry("model", "openai/gpt-5");
    }

    @Test
    void sendMessage_omits_model_when_null() {
        when(sessions.findById("s1")).thenReturn(Optional.of(
            new SessionRecord("s1", "test", "T", false, null, 100L, 100L, false)));
        when(sessionMap.openCodeFor("s1")).thenReturn("oc-1");
        when(userPrefs.getCurrentModel()).thenReturn(null);
        SessionBus bus = mock(SessionBus.class);
        when(buses.getOrCreate("s1")).thenReturn(bus);

        TextPart p = new TextPart("p1", "s1", "m1", "hi", null, null, null, Map.of());
        svc.sendMessage("s1", List.of(p));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(eq("oc-1"), cap.capture());
        assertThat(cap.getValue()).doesNotContainKey("model");
    }

    @Test
    void sendMessage_omits_model_when_blank() {
        when(sessions.findById("s1")).thenReturn(Optional.of(
            new SessionRecord("s1", "test", "T", false, null, 100L, 100L, false)));
        when(sessionMap.openCodeFor("s1")).thenReturn("oc-1");
        when(userPrefs.getCurrentModel()).thenReturn("   ");
        SessionBus bus = mock(SessionBus.class);
        when(buses.getOrCreate("s1")).thenReturn(bus);

        TextPart p = new TextPart("p1", "s1", "m1", "hi", null, null, null, Map.of());
        svc.sendMessage("s1", List.of(p));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(eq("oc-1"), cap.capture());
        assertThat(cap.getValue()).doesNotContainKey("model");
    }
}
