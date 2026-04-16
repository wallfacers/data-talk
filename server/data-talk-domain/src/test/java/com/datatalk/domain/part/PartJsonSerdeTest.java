package com.datatalk.domain.part;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartJsonSerdeTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Test
    void textPart_roundTrip() throws Exception {
        var part = new TextPart("Hello, world!");
        String json = mapper.writeValueAsString(part);
        Part deserialized = mapper.readValue(json, Part.class);
        assertThat(deserialized).isInstanceOf(TextPart.class);
        assertThat(((TextPart) deserialized).content()).isEqualTo("Hello, world!");
    }

    @Test
    void toolPart_pending_roundTrip() throws Exception {
        var part = new ToolPart(
                "calculator",
                "{\"a\": 1, \"b\": 2}",
                ToolState.pending()
        );
        String json = mapper.writeValueAsString(part);
        Part deserialized = mapper.readValue(json, Part.class);
        assertThat(deserialized).isInstanceOf(ToolPart.class);
        ToolPart tp = (ToolPart) deserialized;
        assertThat(tp.toolName()).isEqualTo("calculator");
        assertThat(tp.toolInput()).isEqualTo("{\"a\": 1, \"b\": 2}");
        assertThat(tp.toolState()).isInstanceOf(ToolState.Pending.class);
    }

    @Test
    void toolPart_completed_roundTrip() throws Exception {
        var part = new ToolPart(
                "calculator",
                "{\"a\": 1, \"b\": 2}",
                ToolState.completed("3")
        );
        String json = mapper.writeValueAsString(part);
        Part deserialized = mapper.readValue(json, Part.class);
        assertThat(deserialized).isInstanceOf(ToolPart.class);
        ToolPart tp = (ToolPart) deserialized;
        assertThat(tp.toolState()).isInstanceOf(ToolState.Completed.class);
        assertThat(((ToolState.Completed) tp.toolState()).toolOutput()).isEqualTo("3");
    }

    @Test
    void reasoningPart_roundTrip() throws Exception {
        var part = new ReasoningPart("Let me think about this...");
        String json = mapper.writeValueAsString(part);
        Part deserialized = mapper.readValue(json, Part.class);
        assertThat(deserialized).isInstanceOf(ReasoningPart.class);
        assertThat(((ReasoningPart) deserialized).reasoning()).isEqualTo("Let me think about this...");
    }

    @Test
    void stepStartAndFinish_roundTrip() throws Exception {
        var start = new StepStartPart("search", "Searching the database");
        var finish = new StepFinishPart("search", "success");

        String startJson = mapper.writeValueAsString(start);
        String finishJson = mapper.writeValueAsString(finish);

        Part deserializedStart = mapper.readValue(startJson, Part.class);
        Part deserializedFinish = mapper.readValue(finishJson, Part.class);

        assertThat(deserializedStart).isInstanceOf(StepStartPart.class);
        assertThat(((StepStartPart) deserializedStart).stepName()).isEqualTo("search");
        assertThat(((StepStartPart) deserializedStart).description()).isEqualTo("Searching the database");

        assertThat(deserializedFinish).isInstanceOf(StepFinishPart.class);
        assertThat(((StepFinishPart) deserializedFinish).stepName()).isEqualTo("search");
        assertThat(((StepFinishPart) deserializedFinish).status()).isEqualTo("success");
    }
}
