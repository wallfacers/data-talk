package com.datatalk.domain.part;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PartJsonSerdeTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void roundTripsTextPart() throws Exception {
        Part in = new TextPart("p1", "s1", "m1", "hello", false, false, null, Map.of());
        String json = om.writeValueAsString(in);
        assertThat(json).contains("\"type\":\"text\"").contains("\"text\":\"hello\"");
        Part back = om.readValue(json, Part.class);
        assertThat(back).isEqualTo(in);
    }

    @Test
    void roundTripsToolPartWithPendingState() throws Exception {
        Part in = new ToolPart("p2", "s1", "m1", "call-1", "demo.echo",
            new ToolState.Pending(), Map.of());
        String json = om.writeValueAsString(in);
        assertThat(json).contains("\"type\":\"tool\"").contains("\"tool\":\"demo.echo\"");
        Part back = om.readValue(json, Part.class);
        assertThat(back).isEqualTo(in);
    }

    @Test
    void roundTripsToolPartWithCompletedState() throws Exception {
        Part in = new ToolPart("p3", "s1", "m1", "call-2", "demo.echo",
            new ToolState.Completed(Map.of("reversed", "olleh")), Map.of());
        String json = om.writeValueAsString(in);
        assertThat(json).contains("\"status\":\"completed\"");
        Part back = om.readValue(json, Part.class);
        assertThat(back).isEqualTo(in);
    }

    @Test
    void roundTripsReasoningPart() throws Exception {
        Part in = new ReasoningPart("p4", "s1", "m1", "thinking about it", Map.of(), 100L, 200L);
        String json = om.writeValueAsString(in);
        assertThat(json).contains("\"type\":\"reasoning\"");
        Part back = om.readValue(json, Part.class);
        assertThat(back).isEqualTo(in);
    }

    @Test
    void roundTripsStepDividers() throws Exception {
        Part start = new StepStartPart("p5", "s1", "m1", null);
        Part finish = new StepFinishPart("p6", "s1", "m1", "ok", null, 0.0005,
            new StepFinishPart.Tokens(10, 20, 0, 0, 0));
        Part backStart = om.readValue(om.writeValueAsString(start), Part.class);
        Part backFinish = om.readValue(om.writeValueAsString(finish), Part.class);
        assertThat(backStart).isEqualTo(start);
        assertThat(backFinish).isEqualTo(finish);
    }
}
