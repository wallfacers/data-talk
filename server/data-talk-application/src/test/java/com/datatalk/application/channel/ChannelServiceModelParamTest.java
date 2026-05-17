package com.datatalk.application.channel;

import com.datatalk.application.ai.AiUserPrefsRepository;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.domain.part.TextPart;
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
    private SessionBusRegistry buses;
    private PendingCallRegistry pending;
    private Clock clock;
    private OpenCodeGateway gateway;
    private OpenCodeSessionMap sessionMap;
    private AiUserPrefsRepository userPrefs;
    private ChannelService svc;

    @BeforeEach
    void setUp() {
        sessions = mock(SessionRepository.class);
        buses = mock(SessionBusRegistry.class);
        pending = mock(PendingCallRegistry.class);
        clock = Clock.systemUTC();
        gateway = mock(OpenCodeGateway.class);
        sessionMap = mock(OpenCodeSessionMap.class);
        userPrefs = mock(AiUserPrefsRepository.class);

        svc = new ChannelService(sessions, buses, pending, clock, gateway, sessionMap, userPrefs, mock(Translator.class),
            new PendingFileUploadEchoRegistry());
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
        assertThat(cap.getValue())
            .containsEntry("model", Map.of("providerID", "openai", "modelID", "gpt-5"));
    }

    @Test
    void sendMessage_model_without_slash_uses_modelID_only() {
        when(sessions.findById("s1")).thenReturn(Optional.of(
            new SessionRecord("s1", "test", "T", false, null, 100L, 100L, false)));
        when(sessionMap.openCodeFor("s1")).thenReturn("oc-1");
        when(userPrefs.getCurrentModel()).thenReturn("gpt-5");
        SessionBus bus = mock(SessionBus.class);
        when(buses.getOrCreate("s1")).thenReturn(bus);

        TextPart p = new TextPart("p1", "s1", "m1", "hi", null, null, null, Map.of());
        svc.sendMessage("s1", List.of(p));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(eq("oc-1"), cap.capture());
        assertThat(cap.getValue())
            .containsEntry("model", Map.of("providerID", "", "modelID", "gpt-5"));
    }

    @Test
    void sendMessage_forwards_only_type_and_text_for_text_part() {
        // OpenCode 1.4.7's POST /session/:id/message applies strict Zod validation:
        // any extra field (id, synthetic, ignored, time, sessionID, messageID, metadata)
        // that doesn't match the schema causes the whole request to be rejected. The
        // open-db-studio Rust reference client only sends {type, text} and lets
        // OpenCode generate the rest server-side.
        when(sessions.findById("s1")).thenReturn(Optional.of(
            new SessionRecord("s1", "test", "T", false, null, 100L, 100L, false)));
        when(sessionMap.openCodeFor("s1")).thenReturn("oc-1");
        when(userPrefs.getCurrentModel()).thenReturn(null);
        SessionBus bus = mock(SessionBus.class);
        when(buses.getOrCreate("s1")).thenReturn(bus);

        TextPart p = new TextPart("abc-123", "s1", null, "hi", null, null, null, Map.of());
        svc.sendMessage("s1", List.of(p));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(eq("oc-1"), cap.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) cap.getValue().get("parts");
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0))
            .isEqualTo(Map.of("type", "text", "text", "hi"));
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
