package com.datatalk.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DtEventJsonTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void sessionMetaUpdated_roundTrip() throws Exception {
        DtEvent.SessionMetaUpdated e = new DtEvent.SessionMetaUpdated("s1", "AI \u6807\u9898", false, 2L);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"session.meta.updated\"");
        DtEvent back = om.readValue(json, DtEvent.class);
        assertThat(back).isEqualTo(e);
    }

    @Test
    void sessionIdle_roundTrip() throws Exception {
        DtEvent.SessionIdle e = new DtEvent.SessionIdle("s1");
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"session.idle\"");
        assertThat(om.readValue(json, DtEvent.class)).isEqualTo(e);
    }

    @Test
    void sessionError_roundTrip() throws Exception {
        DtEvent.SessionError e = new DtEvent.SessionError("s1", "boom");
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"session.error\"");
        assertThat(om.readValue(json, DtEvent.class)).isEqualTo(e);
    }

    @Test
    void sessionDiff_roundTrip() throws Exception {
        DtEvent.SessionDiff e = new DtEvent.SessionDiff("s1", Map.of("a", 1));
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"session.diff\"");
        assertThat(om.readValue(json, DtEvent.class)).isEqualTo(e);
    }
}