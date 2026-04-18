package com.datatalk.application.channel;

import com.datatalk.application.ai.AiUserPrefsRepository;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.application.session.PendingCallRegistry;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.TextPart;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelServiceTest {

    SessionRepository sessionRepo;
    MessageRepository msgRepo;
    SessionBusRegistry busRegistry;
    PendingCallRegistry pending;
    IdGenerator ids;
    OpenCodeGateway gateway;
    OpenCodeSessionMap sessionMap;
    ObjectMapper om = new ObjectMapper();
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);
    ChannelService svc;

    @BeforeEach
    void setup() {
        sessionRepo = mock(SessionRepository.class);
        msgRepo = mock(MessageRepository.class);
        busRegistry = mock(SessionBusRegistry.class);
        pending = mock(PendingCallRegistry.class);
        ids = mock(IdGenerator.class);
        gateway = mock(OpenCodeGateway.class);
        sessionMap = mock(OpenCodeSessionMap.class);
        svc = new ChannelService(sessionRepo, msgRepo, busRegistry, pending, ids, clock, gateway, sessionMap, om,
            mock(AiUserPrefsRepository.class));
    }

    @Test
    void sendMessagePersistsAndPublishes() {
        when(ids.next()).thenReturn("m-new");
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", false, null, 100L, 100L, false)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        TextPart p = new TextPart("p1","s-1","m-new","hi",null,null,null, Map.of());
        svc.sendMessage("s-1", List.of(p));

        verify(msgRepo).save(any(Message.class));
        verify(sessionRepo).markHasEverSent(eq("s-1"), anyLong());
        ArgumentCaptor<DtEvent> evt = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus, org.mockito.Mockito.atLeast(2)).publish(evt.capture());
        // at minimum: message.created + message.part.created + session.status:busy
        assertThat(evt.getAllValues()).anyMatch(e -> e instanceof DtEvent.MessageCreated);
        assertThat(evt.getAllValues()).anyMatch(e -> e instanceof DtEvent.MessagePartCreated);
    }

    @Test
    void actionResultCompletesPendingFuture() {
        when(pending.complete("c-1", Map.of("ok", true))).thenReturn(true);
        boolean ok = svc.completeActionResult("c-1", true, Map.of("ok", true), null);
        assertThat(ok).isTrue();
    }

    @Test
    void sendMessage_reuses_persisted_opencode_sid_without_creating_new() {
        when(ids.next()).thenReturn("m-new");
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", true, "ses_persisted", 100L, 100L, false)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        TextPart p = new TextPart("p1","s-1","m-new","hi",null,null,null, Map.of());
        svc.sendMessage("s-1", List.of(p));

        verify(gateway, org.mockito.Mockito.never()).createOpenCodeSession();
        verify(sessionRepo, org.mockito.Mockito.never()).updateOpenCodeSid(any(), any(), anyLong());
        verify(gateway).forwardUserMessage(eq("ses_persisted"), any());
        verify(sessionMap).bind("s-1", "ses_persisted");
    }

    @Test
    void sendMessage_creates_opencode_session_and_persists_on_first_send() {
        when(ids.next()).thenReturn("m-new");
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", false, null, 100L, 100L, false)));
        when(sessionMap.openCodeFor("s-1")).thenReturn(null);
        when(gateway.createOpenCodeSession()).thenReturn("ses_fresh");
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        TextPart p = new TextPart("p1","s-1","m-new","hi",null,null,null, Map.of());
        svc.sendMessage("s-1", List.of(p));

        verify(gateway).createOpenCodeSession();
        verify(sessionRepo).updateOpenCodeSid(eq("s-1"), eq("ses_fresh"), anyLong());
        verify(sessionMap).bind("s-1", "ses_fresh");
        verify(gateway).forwardUserMessage(eq("ses_fresh"), any());
    }
}
