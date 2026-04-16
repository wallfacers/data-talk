package com.datatalk.application.opencode;

import com.datatalk.application.opencode.OcEvent;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.TextPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeEventTranslatorTest {

    private final OpenCodeEventTranslator tr = new OpenCodeEventTranslator();

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
}
