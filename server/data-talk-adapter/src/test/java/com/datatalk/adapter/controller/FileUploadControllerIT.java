package com.datatalk.adapter.controller;

import com.datatalk.application.upload.UploadedFileRepository;
import com.datatalk.domain.upload.UploadedFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=never")
class FileUploadControllerIT {

    private static final String SESSION_ID = "ses-file-upload-controller-it";

    /**
     * Static upload base shared across all test methods in this class; bound into the
     * Spring environment via {@link DynamicPropertySource} so the controller's
     * {@code uploadBase} field is rooted at this temp dir instead of {@code ~/.data-talk/uploads}.
     */
    @TempDir
    static Path uploadBase;

    /**
     * Independent temp dir for the path-traversal scenario. NOT under {@link #uploadBase},
     * so a sentinel file written here lies outside the upload whitelist.
     */
    @TempDir
    static Path outsideBase;

    @DynamicPropertySource
    static void overrideUploadBase(DynamicPropertyRegistry registry) {
        registry.add("datatalk.upload-base", () -> uploadBase.toAbsolutePath().toString());
    }

    @Autowired
    WebApplicationContext ctx;

    @Autowired
    UploadedFileRepository uploadedFileRepo;

    @Autowired
    @Qualifier("datatalkJdbc")
    JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM uploaded_file");
        // V5 migration added FK uploaded_file.session_id -> sessions(id); ensure the parent
        // session row exists before any uploaded_file insert in this IT (idempotent INSERT OR IGNORE).
        long now = System.currentTimeMillis();
        jdbc.update("""
            INSERT OR IGNORE INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid,
                created_at, updated_at, title_locked)
            VALUES(?, NULL, ?, 0, ?, ?, ?, 0)
            """, SESSION_ID, "FileUpload IT", "oc-" + SESSION_ID, now, now);
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void getContent_returns_file_with_correct_headers_when_present() throws Exception {
        String fileId = UUID.randomUUID().toString();
        Path fileDir = uploadBase.resolve(fileId);
        Files.createDirectories(fileDir);
        Path physical = fileDir.resolve("hello.txt");
        byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);
        Files.write(physical, body);

        uploadedFileRepo.insert(new UploadedFile(
            fileId,
            SESSION_ID,
            "hello.txt",
            "text/plain",
            body.length,
            physical.toAbsolutePath().toString(),
            Map.of("type", "TEXT"),
            Instant.now()
        ));

        MvcResult result = mvc.perform(get("/api/files/{fileId}/content", fileId))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain"))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, body.length))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename*=UTF-8''hello.txt"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=300"))
            .andReturn();

        byte[] respBody = result.getResponse().getContentAsByteArray();
        assertThat(respBody).hasSize(body.length).isEqualTo(body);
    }

    @Test
    void getContent_returns_404_when_fileId_not_in_db() throws Exception {
        MvcResult result = mvc.perform(get("/api/files/{fileId}/content", "non-existent-id-xyz"))
            .andExpect(status().isNotFound())
            .andReturn();

        // Body must not leak any filesystem path or upload-base location
        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .doesNotContain(uploadBase.toString())
            .doesNotContain(".data-talk/uploads");
    }

    @Test
    void getContent_returns_404_when_physical_file_missing() throws Exception {
        String fileId = UUID.randomUUID().toString();
        Path fileDir = uploadBase.resolve(fileId);
        Files.createDirectories(fileDir);
        Path physical = fileDir.resolve("ghost.txt");
        // DB says it exists, but we never write the file to disk

        uploadedFileRepo.insert(new UploadedFile(
            fileId,
            SESSION_ID,
            "ghost.txt",
            "text/plain",
            42L,
            physical.toAbsolutePath().toString(),
            Map.of(),
            Instant.now()
        ));

        assertThat(Files.exists(physical)).isFalse();

        mvc.perform(get("/api/files/{fileId}/content", fileId))
            .andExpect(status().isNotFound());
    }

    @Test
    void getContent_returns_404_when_physicalPath_escapes_uploadBase() throws Exception {
        // Sentinel file lives OUTSIDE uploadBase. Even though it exists on disk and the
        // DB row points to it, the whitelist check must reject the request.
        Path sentinel = outsideBase.resolve("secret.txt");
        byte[] secret = "SUPER SECRET DATA — MUST NOT LEAK".getBytes(StandardCharsets.UTF_8);
        Files.write(sentinel, secret);
        assertThat(Files.exists(sentinel)).isTrue();
        assertThat(sentinel.toAbsolutePath().normalize().startsWith(uploadBase)).isFalse();

        String fileId = UUID.randomUUID().toString();
        uploadedFileRepo.insert(new UploadedFile(
            fileId,
            SESSION_ID,
            "secret.txt",
            "text/plain",
            secret.length,
            sentinel.toAbsolutePath().toString(),
            Map.of(),
            Instant.now()
        ));

        MvcResult result = mvc.perform(get("/api/files/{fileId}/content", fileId))
            .andExpect(status().isNotFound())
            .andReturn();

        byte[] respBody = result.getResponse().getContentAsByteArray();
        assertThat(respBody).doesNotContain(secret);
        assertThat(new String(respBody, StandardCharsets.UTF_8))
            .doesNotContain("SUPER SECRET");
    }

    @Test
    void getContent_percent_encodes_chinese_filename_per_rfc5987() throws Exception {
        String fileId = UUID.randomUUID().toString();
        String chineseName = "效果图.png";
        Path fileDir = uploadBase.resolve(fileId);
        Files.createDirectories(fileDir);
        Path physical = fileDir.resolve(chineseName);
        // 1×1 transparent PNG header bytes — enough to count as a real file
        byte[] pngBytes = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        Files.write(physical, pngBytes);

        uploadedFileRepo.insert(new UploadedFile(
            fileId,
            SESSION_ID,
            chineseName,
            "image/png",
            pngBytes.length,
            physical.toAbsolutePath().toString(),
            Map.of(),
            Instant.now()
        ));

        mvc.perform(get("/api/files/{fileId}/content", fileId))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename*=UTF-8''%E6%95%88%E6%9E%9C%E5%9B%BE.png"))
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, pngBytes.length));
    }
}
