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
import com.datatalk.domain.part.FileUploadPart;
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
    PendingFileUploadEchoRegistry echoRegistry;
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);
    ChannelService svc;

    @BeforeEach
    void setup() {
        sessionRepo = mock(SessionRepository.class);
        busRegistry = mock(SessionBusRegistry.class);
        pending = mock(PendingCallRegistry.class);
        gateway = mock(OpenCodeGateway.class);
        sessionMap = mock(OpenCodeSessionMap.class);
        echoRegistry = new PendingFileUploadEchoRegistry();
        svc = new ChannelService(sessionRepo, busRegistry, pending, clock, gateway, sessionMap,
            mock(AiUserPrefsRepository.class), mock(Translator.class),
            echoRegistry);
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
    void sendMessage_enqueuesFileUploadPartsForLaterEcho_andStillForwardsToOpenCode() {
        // Reproduces BUG-0056: file_upload parts must be stashed locally so the
        // event loop can echo them back to the frontend once OpenCode returns
        // the user message.created event. The wire body to OpenCode stays
        // sanitized (no file_upload type — Zod rejects it), but the registry
        // captures the original FileUploadPart.
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", true, "ses_persisted", 100L, 100L, false)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        TextPart text = new TextPart("p-text", "s-1", "", "hi", null, null, null, Map.of());
        FileUploadPart upload = new FileUploadPart(
            "p-upload", "s-1", "", "file-abc", "photo.png", "image/png", 2048L, Map.of());
        svc.sendMessage("s-1", List.of(text, upload));

        // The registry now holds the original file_upload part for the next user MessageCreated.
        java.util.List<FileUploadPart> drained = echoRegistry.drainNext("s-1");
        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).fileId()).isEqualTo("file-abc");
        assertThat(drained.get(0).filename()).isEqualTo("photo.png");

        // OpenCode still receives the (sanitized) forward — verified separately
        // by inspecting partForWire, but here we just ensure forwarding happened.
        verify(gateway).forwardUserMessage(eq("ses_persisted"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMessage_imageWithDataUri_forwardsAsFilePart_andSkipsEchoRegistry() {
        // New path: image file_upload carrying a base64 data URI is forwarded
        // as a native OpenCode FilePart in the same user message, and is NOT
        // enqueued for local echo (OpenCode itself echoes the FilePart back).
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", true, "ses_persisted", 100L, 100L, false)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        String dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB";
        TextPart text = new TextPart("p-text", "s-1", "", "what is this?", null, null, null, Map.of());
        FileUploadPart image = new FileUploadPart(
            "p-img", "s-1", "", "file-img-1", "screenshot.png", "image/png", 12345L, Map.of(), dataUri);
        svc.sendMessage("s-1", List.of(text, image));

        // echo registry MUST NOT receive the image part
        assertThat(echoRegistry.drainNext("s-1")).isEmpty();

        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(eq("ses_persisted"), bodyCap.capture());
        List<Map<String, Object>> wireParts = (List<Map<String, Object>>) bodyCap.getValue().get("parts");
        assertThat(wireParts).hasSize(2);
        assertThat(wireParts.get(0)).containsEntry("type", "text").containsEntry("text", "what is this?");
        assertThat(wireParts.get(1))
            .containsEntry("type", "file")
            .containsEntry("mime", "image/png")
            .containsEntry("filename", "screenshot.png")
            .containsEntry("url", dataUri);
        // No fileId / sizeBytes / analysis leak into the wire body
        assertThat(wireParts.get(1)).doesNotContainKeys("fileId", "sizeBytes", "analysis");
        // No datatalk_file_read prompt text anywhere
        assertThat(wireParts).noneMatch(p ->
            "text".equals(p.get("type"))
                && p.get("text") != null
                && p.get("text").toString().contains("datatalk_file_read"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMessage_multipleImagesWithDataUri_allBecomeFileParts() {
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", true, "ses_persisted", 100L, 100L, false)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        TextPart text = new TextPart("t", "s-1", "", "compare", null, null, null, Map.of());
        FileUploadPart a = new FileUploadPart("pa", "s-1", "", "fA", "a.png", "image/png", 1L, Map.of(),
            "data:image/png;base64,AAAA");
        FileUploadPart b = new FileUploadPart("pb", "s-1", "", "fB", "b.jpg", "image/jpeg", 2L, Map.of(),
            "data:image/jpeg;base64,BBBB");
        FileUploadPart c = new FileUploadPart("pc", "s-1", "", "fC", "c.webp", "image/webp", 3L, Map.of(),
            "data:image/webp;base64,CCCC");
        svc.sendMessage("s-1", List.of(text, a, b, c));

        assertThat(echoRegistry.drainNext("s-1")).isEmpty();

        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(eq("ses_persisted"), bodyCap.capture());
        List<Map<String, Object>> wireParts = (List<Map<String, Object>>) bodyCap.getValue().get("parts");
        assertThat(wireParts).hasSize(4);
        assertThat(wireParts.get(0)).containsEntry("type", "text");
        assertThat(wireParts.get(1)).containsEntry("type", "file").containsEntry("filename", "a.png");
        assertThat(wireParts.get(2)).containsEntry("type", "file").containsEntry("filename", "b.jpg");
        assertThat(wireParts.get(3)).containsEntry("type", "file").containsEntry("filename", "c.webp");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMessage_mixedImageAndCsv_routesEachToCorrectPath() {
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", true, "ses_persisted", 100L, 100L, false)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        FileUploadPart image = new FileUploadPart("p-img", "s-1", "", "fI", "x.png", "image/png", 1L, Map.of(),
            "data:image/png;base64,XXXX");
        FileUploadPart csv = new FileUploadPart("p-csv", "s-1", "", "fC", "data.csv", "text/csv", 1024L, Map.of());
        svc.sendMessage("s-1", List.of(image, csv));

        // CSV — but NOT the image — must be enqueued for echo
        List<FileUploadPart> drained = echoRegistry.drainNext("s-1");
        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).fileId()).isEqualTo("fC");

        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(eq("ses_persisted"), bodyCap.capture());
        List<Map<String, Object>> wireParts = (List<Map<String, Object>>) bodyCap.getValue().get("parts");
        assertThat(wireParts).hasSize(2);
        assertThat(wireParts.get(0)).containsEntry("type", "file").containsEntry("filename", "x.png");
        // CSV is downgraded to a text part containing the datatalk_file_read hint
        assertThat(wireParts.get(1)).containsEntry("type", "text");
        assertThat(wireParts.get(1).get("text").toString())
            .contains("data.csv")
            .contains("fC")
            .contains("datatalk_file_read");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMessage_imageWithoutDataUri_fallsBackToLegacyTextPath() {
        // Defensive: prefetch failed and url is blank. Image must still reach
        // the AI via the legacy file_read path so the conversation doesn't break.
        when(sessionRepo.findById("s-1")).thenReturn(Optional.of(
            new SessionRecord("s-1", null, "T", true, "ses_persisted", 100L, 100L, false)));
        SessionBus bus = mock(SessionBus.class);
        when(busRegistry.getOrCreate("s-1")).thenReturn(bus);

        FileUploadPart image = new FileUploadPart(
            "p-img", "s-1", "", "fI", "fallback.png", "image/png", 999L, Map.of(), null);
        svc.sendMessage("s-1", List.of(image));

        // Without url, the image MUST be enqueued for echo (legacy behavior)
        assertThat(echoRegistry.drainNext("s-1")).hasSize(1);

        ArgumentCaptor<Map<String, Object>> bodyCap = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forwardUserMessage(eq("ses_persisted"), bodyCap.capture());
        List<Map<String, Object>> wireParts = (List<Map<String, Object>>) bodyCap.getValue().get("parts");
        assertThat(wireParts).hasSize(1);
        assertThat(wireParts.get(0)).containsEntry("type", "text");
        assertThat(wireParts.get(0).get("text").toString()).contains("datatalk_file_read");
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
