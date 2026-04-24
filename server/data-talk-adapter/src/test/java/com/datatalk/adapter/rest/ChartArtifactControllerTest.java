package com.datatalk.adapter.rest;

import com.datatalk.application.chart.ChartArtifactService;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChartArtifactController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChartArtifactControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ChartArtifactService chartArtifactService;
    @MockBean SessionRepository sessionRepository;
    @MockBean Translator translator;

    @Test
    void createChartArtifactAcceptsFullBodyAndDelegatesWithNullCallId() throws Exception {
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(existingSession("s1")));
        when(chartArtifactService.createChartArtifact(any()))
            .thenReturn(new ChartArtifactService.Result("art_1", 3));

        mvc.perform(post("/api/sessions/s1/artifacts/chart")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "echartsOption": {
                        "title": {"text": "Revenue"},
                        "series": [{"type": "bar", "data": [1, 2, 3]}]
                      },
                      "sourceArtifactId": "art_src",
                      "originMessageId": "msg_9",
                      "originPartId": "part_4"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artifactId").value("art_1"))
            .andExpect(jsonPath("$.version").value(3));

        ArgumentCaptor<ChartArtifactService.Request> requestCaptor =
            ArgumentCaptor.forClass(ChartArtifactService.Request.class);
        verify(chartArtifactService).createChartArtifact(requestCaptor.capture());

        ChartArtifactService.Request delegated = requestCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(delegated.sessionId()).isEqualTo("s1");
        org.assertj.core.api.Assertions.assertThat(delegated.sourceArtifactId()).isEqualTo("art_src");
        org.assertj.core.api.Assertions.assertThat(delegated.supersedesArtifactId()).isNull();
        org.assertj.core.api.Assertions.assertThat(delegated.originMessageId()).isEqualTo("msg_9");
        org.assertj.core.api.Assertions.assertThat(delegated.originPartId()).isEqualTo("part_4");
        org.assertj.core.api.Assertions.assertThat(delegated.callId()).isNull();
        org.assertj.core.api.Assertions.assertThat(delegated.echartsOption())
            .containsEntry("title", Map.of("text", "Revenue"))
            .containsKey("series");
    }

    @Test
    void createChartArtifactAllowsNullSourceArtifactId() throws Exception {
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(existingSession("s1")));
        when(chartArtifactService.createChartArtifact(any()))
            .thenReturn(new ChartArtifactService.Result("art_2", 1));

        mvc.perform(post("/api/sessions/s1/artifacts/chart")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "echartsOption": {
                        "series": [{"type": "line", "data": [3, 2, 1]}]
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artifactId").value("art_2"))
            .andExpect(jsonPath("$.version").value(1));

        ArgumentCaptor<ChartArtifactService.Request> requestCaptor =
            ArgumentCaptor.forClass(ChartArtifactService.Request.class);
        verify(chartArtifactService).createChartArtifact(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().sourceArtifactId()).isNull();
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().callId()).isNull();
    }

    @Test
    void missingEchartsOptionReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/sessions/s1/artifacts/chart")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        verify(chartArtifactService, never()).createChartArtifact(any());
    }

    @Test
    void oversizedEchartsOptionReturnsPayloadTooLarge() throws Exception {
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(existingSession("s1")));
        String oversized = "x".repeat(300_000);

        mvc.perform(post("/api/sessions/s1/artifacts/chart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "echartsOption", Map.of("tooltip", Map.of("formatter", oversized))
                ))))
            .andExpect(status().isPayloadTooLarge());

        verify(chartArtifactService, never()).createChartArtifact(any());
    }

    @Test
    void unknownSessionReturnsNotFound() throws Exception {
        when(sessionRepository.findById("missing")).thenReturn(Optional.empty());

        mvc.perform(post("/api/sessions/missing/artifacts/chart")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "echartsOption": {
                        "series": [{"type": "pie", "data": [1, 2]}]
                      }
                    }
                    """))
            .andExpect(status().isNotFound());

        verify(chartArtifactService, never()).createChartArtifact(any());
    }

    private static SessionRecord existingSession(String id) {
        return new SessionRecord(id, null, "Title", true, null, 1L, 1L, false);
    }
}
