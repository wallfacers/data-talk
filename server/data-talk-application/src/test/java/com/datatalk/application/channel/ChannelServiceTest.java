package com.datatalk.application.channel;

import com.datatalk.application.ai.AiUserPrefsRepository;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelServiceTest {

    SessionRepository sessionRepo;
    SessionBusRegistry busRegistry;
    PendingCallRegistry pending;
    OpenCodeGateway gateway;
    OpenCodeSessionMap sessionMap;
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);
    ChannelService svc;

    @BeforeEach
    void setup() {
        sessionRepo = mock(SessionRepository.class);
        busRegistry = mock(SessionBusRegistry.class);
        pending = mock(PendingCallRegistry.class);
        gateway = mock(OpenCodeGateway.class);
        sessionMap = mock(OpenCodeSessionMap.class);
        svc = new ChannelService(sessionRepo, busRegistry, pending, clock, gateway, sessionMap,
            mock(AiUserPrefsRepository.class), mock(Translator.class));
    }

    @Test
    void sendMessageForwardsToOpenCodeAndEmitsBusyStatusOnly() {
        // SessionRecord: id, title, systemPrompt, has_ever_sent, openCodeSid, createdAt, updatedAt, isArchived
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", false, null, 100L, 100L, false)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);
        when(gateway.createOpenCodeSession()).thenReturn("ses_fresh");

        TextPart p = new TextPart("p1", "s-1", "m-ignored", "hi", null, null, null, Map.of());
        svc.sendMessage("s-1", List.of(p));

        // 不再发 MessageCreated/MessagePartCreated，只发 SessionStatus("busy")
        ArgumentCaptor<DtEvent> captor = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus, atLeastOnce()).publish(captor.capture());
        assertThat(captor.getAllValues())
            .noneMatch(e -> e instanceof DtEvent.MessageCreated)
            .noneMatch(e -> e instanceof DtEvent.MessagePartCreated)
            .anyMatch(e -> e instanceof DtEvent.SessionStatus s && "busy".equals(s.status()));
        // 仍 forward 到 OpenCode
        verify(gateway).forwardUserMessage(anyString(), anyMap());
        // 仍 markHasEverSent
        verify(sessionRepo).markHasEverSent(eq("s-1"), anyLong());
    }

    @Test
    void actionResultCompletesPendingFuture() {
        when(pending.complete("c-1", Map.of("ok", true))).thenReturn(true);
        boolean ok = svc.completeActionResult("c-1", true, Map.of("ok", true), null);
        assertThat(ok).isTrue();
    }

    @Test
    void sendMessage_reuses_persisted_opencode_sid_without_creating_new() {
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", true, "ses_persisted", 100L, 100L, false)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        TextPart p = new TextPart("p1","s-1","m-ignored","hi",null,null,null, Map.of());
        svc.sendMessage("s-1", List.of(p));

        verify(gateway, never()).createOpenCodeSession();
        verify(sessionRepo, never()).updateOpenCodeSid(any(), any(), anyLong());
        verify(gateway).forwardUserMessage(eq("ses_persisted"), any());
        verify(sessionMap).bind("s-1", "ses_persisted");
    }

    @Test
    void sendMessage_creates_opencode_session_and_persists_on_first_send() {
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", false, null, 100L, 100L, false)));
        when(sessionMap.openCodeFor("s-1")).thenReturn(null);
        when(gateway.createOpenCodeSession()).thenReturn("ses_fresh");
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        TextPart p = new TextPart("p1","s-1","m-ignored","hi",null,null,null, Map.of());
        svc.sendMessage("s-1", List.of(p));

        verify(gateway).createOpenCodeSession();
        verify(sessionRepo).updateOpenCodeSid(eq("s-1"), eq("ses_fresh"), anyLong());
        verify(sessionMap).bind("s-1", "ses_fresh");
        verify(gateway).forwardUserMessage(eq("ses_fresh"), any());
    }

    @Test
    void abort_forwards_to_persisted_opencode_session_and_emits_idle() {
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", true, "ses_abort", 100L, 100L, false)));
        when(gateway.abortOpenCodeSession("ses_abort")).thenReturn(true);
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        boolean aborted = svc.abort("s-1");

        assertThat(aborted).isTrue();
        verify(gateway).abortOpenCodeSession("ses_abort");
        ArgumentCaptor<DtEvent> captor = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus, atLeastOnce()).publish(captor.capture());
        assertThat(captor.getAllValues())
            .anyMatch(e -> e instanceof DtEvent.SessionStatus s && "idle".equals(s.status()));
    }

    @Test
    void abort_falls_back_to_session_map_when_persisted_open_code_sid_missing() {
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", true, null, 100L, 100L, false)));
        when(sessionMap.openCodeFor("s-1")).thenReturn("ses_from_map");
        when(gateway.abortOpenCodeSession("ses_from_map")).thenReturn(false);
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        boolean aborted = svc.abort("s-1");

        assertThat(aborted).isFalse();
        verify(gateway).abortOpenCodeSession("ses_from_map");
    }
}
