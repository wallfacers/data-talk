package com.datatalk.application.opencode;

import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class OpenCodeEventTranslatorTest {

    private SessionTitleSyncer syncer;
    private OpenCodeEventTranslator tr;

    @BeforeEach
    void setUp() {
        syncer = Mockito.mock(SessionTitleSyncer.class);
        tr = new OpenCodeEventTranslator(syncer);
    }

    @Test
    void firstPartUpdatedBecomesPartCreated() {
        TextPart p = new TextPart("p1","s1","m1","hi",null,null,null, Map.of());
        List<DtEvent> out = tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        assertThat(out).hasSize(1);
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartCreated.class);
    }

    @Test
    void subsequentPartUpdatedStaysUpdated() {
        TextPart p = new TextPart("p1","s1","m1","hi",null,null,null, Map.of());
        tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        List<DtEvent> out = tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartUpdated.class);
    }

    @Test
    void deltaIsPassedThrough() {
        List<DtEvent> out = tr.translate("s1",
            new OcEvent.MessagePartDelta("p1", "text", "hello"));
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartDelta.class);
    }

    @Test
    void serverConnectedIsSwallowed() {
        List<DtEvent> out = tr.translate("s1", new OcEvent.ServerConnected());
        assertThat(out).isEmpty();
    }

    @Test
    void sessionStatusIsPassedThrough() {
        List<DtEvent> out = tr.translate("s1",
            new OcEvent.SessionStatus("busy", Map.of()));
        assertThat(out.get(0)).isInstanceOf(DtEvent.SessionStatus.class);
    }

    @Test
    void unknownEventIsDropped() {
        List<DtEvent> out = tr.translate("s1", new OcEvent.Unknown("weird", Map.of()));
        assertThat(out).isEmpty();
    }

    @Test
    void partIsolatedBySessionId() {
        TextPart p = new TextPart("p1","s-a","m1","hi",null,null,null, Map.of());
        tr.translate("s-a", new OcEvent.MessagePartUpdated(p));
        TextPart q = new TextPart("p1","s-b","m1","hi",null,null,null, Map.of());
        List<DtEvent> out = tr.translate("s-b", new OcEvent.MessagePartUpdated(q));
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartCreated.class);
    }

    @Test
    void forgetClearsSeenSet() {
        TextPart p = new TextPart("p1","s1","m1","hi",null,null,null, Map.of());
        tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        tr.forget("s1");
        List<DtEvent> out = tr.translate("s1", new OcEvent.MessagePartUpdated(p));
        assertThat(out.get(0)).isInstanceOf(DtEvent.MessagePartCreated.class);
    }

    @Test
    void sessionUpdatedTriggersSyncerAndEmitsMetaUpdated() {
        var info = new SessionInfo("oc-1", "AI 标题", 2L);
        when(syncer.apply("oc-1", "AI 标题")).thenReturn(true);
        List<DtEvent> out = tr.translate("dt-1", new OcEvent.SessionUpdated(info));
        assertThat(out).singleElement().isInstanceOf(DtEvent.SessionMetaUpdated.class);
    }

    @Test
    void sessionUpdatedSkippedWhenSyncerReturnsFalse() {
        var info = new SessionInfo("oc-1", "AI 标题", 2L);
        when(syncer.apply("oc-1", "AI 标题")).thenReturn(false);
        List<DtEvent> out = tr.translate("dt-1", new OcEvent.SessionUpdated(info));
        assertThat(out).isEmpty();
    }

    @Test
    void sessionIdleDoesNotTriggerSyncer() {
        tr.translate("dt-1", new OcEvent.SessionIdle(new SessionInfo("oc-1", null, 1L)));
        verifyNoInteractions(syncer);
    }

    @Test
    void sessionErrorIsTranslated() {
        List<DtEvent> out = tr.translate("dt-1",
            new OcEvent.SessionError(new SessionInfo("oc-1", null, 1L), "boom"));
        assertThat(out).singleElement().isInstanceOf(DtEvent.SessionError.class);
        assertThat(((DtEvent.SessionError) out.get(0)).error()).isEqualTo("boom");
    }

    @Test
    void sessionCreatedIsTranslated() {
        var info = new SessionInfo("oc-1", "T", 1L);
        List<DtEvent> out = tr.translate("dt-1", new OcEvent.SessionCreated(info));
        assertThat(out).singleElement().isInstanceOf(DtEvent.SessionCreated.class);
    }

    @Test
    void sessionDeletedIsTranslated() {
        var info = new SessionInfo("oc-1", null, 1L);
        List<DtEvent> out = tr.translate("dt-1", new OcEvent.SessionDeleted(info));
        assertThat(out).singleElement().isInstanceOf(DtEvent.SessionDeleted.class);
    }

    @Test
    void sessionCompactedIsTranslated() {
        var info = new SessionInfo("oc-1", null, 1L);
        List<DtEvent> out = tr.translate("dt-1", new OcEvent.SessionCompacted(info));
        assertThat(out).singleElement().isInstanceOf(DtEvent.SessionCompacted.class);
    }

    @Test
    void sessionDiffIsTranslated() {
        var info = new SessionInfo("oc-1", null, 1L);
        Map<String, Object> payload = Map.of("changes", List.of());
        List<DtEvent> out = tr.translate("dt-1", new OcEvent.SessionDiff(info, payload));
        assertThat(out).singleElement().isInstanceOf(DtEvent.SessionDiff.class);
        assertThat(((DtEvent.SessionDiff) out.get(0)).payload()).isEqualTo(payload);
    }
}
