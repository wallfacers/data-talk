package com.datatalk.adapter.controller;

import com.datatalk.application.opencode.OpenCodeQuestionClient;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.dto.QuestionReplyRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionControllerTest {

    private OpenCodeQuestionClient questions;
    private OpenCodeSessionMap sessionMap;
    private ObjectMapper om;
    private QuestionController controller;

    @BeforeEach
    void setUp() {
        questions = mock(OpenCodeQuestionClient.class);
        sessionMap = new OpenCodeSessionMap();
        om = new ObjectMapper();
        controller = new QuestionController(questions, sessionMap, om);
    }

    @Test
    void listFiltersByMappedSessionAndReStampsToDataTalkId() throws Exception {
        sessionMap.bind("dt-1", "oc-1");
        JsonNode all = om.readTree("""
            [
              {"id":"qst_1","sessionID":"oc-1","questions":[{"header":"A"}]},
              {"id":"qst_2","sessionID":"oc-other","questions":[{"header":"B"}]}
            ]
            """);
        when(questions.listQuestions()).thenReturn(all);

        JsonNode out = controller.listForSession("dt-1");

        assertThat(out.isArray()).isTrue();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).path("id").asText()).isEqualTo("qst_1");
        // sessionID re-stamped from oc-1 to the DataTalk id for frontend consistency
        assertThat(out.get(0).path("sessionID").asText()).isEqualTo("dt-1");
    }

    @Test
    void listReturnsEmptyWhenSessionNotMapped() {
        JsonNode out = controller.listForSession("unknown");
        assertThat(out.isArray()).isTrue();
        assertThat(out).isEmpty();
    }

    @Test
    void replyForwardsAnswersAsListOfLists() {
        when(questions.replyQuestion(org.mockito.ArgumentMatchers.eq("qst_1"),
            org.mockito.ArgumentMatchers.anyList())).thenReturn(true);

        boolean ok = controller.reply("qst_1",
            new QuestionReplyRequest(List.of(List.of("Yes"), List.of("A", "B"))));

        assertThat(ok).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(questions).replyQuestion(org.mockito.ArgumentMatchers.eq("qst_1"), captor.capture());
        assertThat(captor.getValue()).containsExactly(List.of("Yes"), List.of("A", "B"));
    }

    @Test
    void rejectForwardsToClient() {
        when(questions.rejectQuestion("qst_2")).thenReturn(true);
        assertThat(controller.reject("qst_2")).isTrue();
        verify(questions).rejectQuestion("qst_2");
    }
}
