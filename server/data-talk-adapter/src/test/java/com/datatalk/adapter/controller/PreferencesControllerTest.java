package com.datatalk.adapter.controller;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.preference.UserPreferencesService;
import com.datatalk.domain.preference.UserPreferences;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PreferencesController.class)
@AutoConfigureMockMvc(addFilters = false)
class PreferencesControllerTest {

    @Autowired MockMvc mvc;

    @MockBean UserPreferencesService service;
    @MockBean Translator translator;

    @Test
    void getPreferences_returnsDefault() throws Exception {
        when(service.getPreferences()).thenReturn(UserPreferences.DEFAULT);

        mvc.perform(get("/api/preferences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("UTC"))
            .andExpect(jsonPath("$.dateFormat").value("yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    void getPreferences_returnsCustom() throws Exception {
        when(service.getPreferences()).thenReturn(
            new UserPreferences("default", ZoneId.of("Asia/Shanghai"), "yyyy年MM月dd日 HH:mm:ss", 1000L));

        mvc.perform(get("/api/preferences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Asia/Shanghai"))
            .andExpect(jsonPath("$.dateFormat").value("yyyy年MM月dd日 HH:mm:ss"));
    }

    @Test
    void putPreferences_updatesTimezone() throws Exception {
        when(service.updateTimeZone("Asia/Tokyo")).thenReturn(
            new UserPreferences("default", ZoneId.of("Asia/Tokyo"), "yyyy-MM-dd HH:mm:ss", 1000L));

        mvc.perform(put("/api/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Asia/Tokyo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"))
            .andExpect(jsonPath("$.dateFormat").value("yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    void putPreferences_updatesDateFormat() throws Exception {
        when(service.updateDateFormat("MM/dd/yyyy HH:mm:ss")).thenReturn(
            new UserPreferences("default", ZoneId.of("UTC"), "MM/dd/yyyy HH:mm:ss", 1000L));

        mvc.perform(put("/api/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dateFormat\":\"MM/dd/yyyy HH:mm:ss\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("UTC"))
            .andExpect(jsonPath("$.dateFormat").value("MM/dd/yyyy HH:mm:ss"));
    }

    @Test
    void putPreferences_invalidTimezone_returns400() throws Exception {
        mvc.perform(put("/api/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Not/A_Real_Zone\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid timezone: Not/A_Real_Zone"));
    }

    @Test
    void putPreferences_emptyBody_returns200NoChanges() throws Exception {
        when(service.getPreferences()).thenReturn(UserPreferences.DEFAULT);

        mvc.perform(put("/api/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("UTC"))
            .andExpect(jsonPath("$.dateFormat").value("yyyy-MM-dd HH:mm:ss"));
    }
}
