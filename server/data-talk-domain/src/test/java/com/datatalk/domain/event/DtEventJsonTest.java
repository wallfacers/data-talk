package com.datatalk.domain.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DtEventJsonTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void dtEventDoesNotUseCentralJsonSubTypesRegistry() {
        assertThat(DtEvent.class.getAnnotation(JsonSubTypes.class)).isNull();
    }

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

    @Test
    void questionEvents_roundTrip() throws Exception {
        // Guards the @JsonTypeName runtime trap: missing annotation would throw at resolver init.
        com.fasterxml.jackson.databind.JsonNode questions = om.readTree(
            "[{\"question\":\"Continue?\",\"header\":\"Confirm\","
            + "\"options\":[{\"label\":\"Yes\",\"description\":\"go\"}],\"multiple\":false,\"custom\":true}]");
        DtEvent[] events = {
            new DtEvent.QuestionAsked("s1", "qst_1", questions, "msg_9", "call_9"),
            new DtEvent.QuestionReplied("s1", "qst_1"),
            new DtEvent.QuestionRejected("s1", "qst_1")
        };
        for (DtEvent event : events) {
            String json = om.writeValueAsString(event);
            assertThat(json).contains("\"type\":\"" + event.typeName() + "\"");
            assertThat(om.readValue(json, DtEvent.class)).isEqualTo(event);
        }
    }

    @Test
    void fileArtifactEvents_roundTrip() throws Exception {
        DtEvent[] events = {
            new DtEvent.FileArtifactDetected("fa1", "s1", "report.md", "report", "temporary", 42L),
            new DtEvent.FileArtifactArchiveRequested("fa1", "s1", "report", "Report", "Summary"),
            new DtEvent.FileArtifactArchived("fa1", "s1", "conn1", "report.md", "/tmp/report.md"),
            new DtEvent.FileArtifactDiscarded("fa1", "user_action"),
            new DtEvent.LegacyMigrated(3)
        };

        for (DtEvent event : events) {
            String json = om.writeValueAsString(event);
            assertThat(json).contains("\"type\":\"" + event.typeName() + "\"");
            assertThat(om.readValue(json, DtEvent.class)).isEqualTo(event);
        }
    }
}
