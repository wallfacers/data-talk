package com.datatalk.adapter.controller;

import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import com.datatalk.application.i18n.Translator;
import com.datatalk.application.report.LedgerSkillResolver;
import com.datatalk.application.report.ReportSystemStatus;
import com.datatalk.config.CorsConfig;
import com.datatalk.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code /api/reports/_assets/**} static assets are served with
 * correct content types. CORS headers are handled by {@link CorsFilter}
 * (see {@code CorsConfig}).
 *
 * <p>Closes BUG-0073.
 */
@WebMvcTest({ReportController.class, CorsConfig.class})
@AutoConfigureMockMvc(addFilters = true)
class ReportControllerCorsTest {

    @Autowired MockMvc mvc;

    @MockBean ReportRepository reportRepo;
    @MockBean ReportSystemStatus systemStatus;
    @MockBean LedgerSkillResolver ledgerSkill;
    @MockBean SessionWorkdirRoot workdirRoot;
    // No @MockBean ObjectMapper — Spring's auto-configured ObjectMapper is also used
    // by MVC's RouterFunctionMapping; mocking it breaks the test context boot.
    @MockBean Translator translator;     // GlobalExceptionHandler @ControllerAdvice dependency

    @TempDir Path skillRoot;

    @BeforeEach
    void setupSkillRoot() throws IOException {
        Path assets = skillRoot.resolve("assets");
        Path fontsDir = assets.resolve("fonts");
        Path stylesDir = assets.resolve("styles");
        Files.createDirectories(fontsDir);
        Files.createDirectories(stylesDir);
        Files.writeString(stylesDir.resolve("ledger.css"), "/* css */", StandardCharsets.UTF_8);
        Files.write(fontsDir.resolve("NotoSerifSC-Regular.otf"), new byte[] {0x00, 0x01, 0x02, 0x03});
        when(ledgerSkill.skillRoot()).thenReturn(skillRoot);
    }

    @Test
    void font_response_carries_cors_headers_for_null_origin() throws Exception {
        mvc.perform(get("/api/reports/_assets/fonts/NotoSerifSC-Regular.otf")
                        .header("Origin", "null"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "null"))
                .andExpect(header().string("Content-Type", "font/otf"));
    }

    @Test
    void css_response_carries_cors_headers_for_null_origin() throws Exception {
        mvc.perform(get("/api/reports/_assets/styles/ledger.css")
                        .header("Origin", "null"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "null"))
                .andExpect(header().string("Content-Type", "text/css"));
    }
}
