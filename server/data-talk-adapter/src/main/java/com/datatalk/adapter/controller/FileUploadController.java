package com.datatalk.adapter.controller;

import com.datatalk.application.upload.FileAnalysisResult;
import com.datatalk.application.upload.FileAnalysisService;
import com.datatalk.application.upload.UploadedFileRepository;
import com.datatalk.domain.upload.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);
    private static final long MAX_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB

    private final FileAnalysisService analysisService;
    private final UploadedFileRepository uploadedFileRepo;
    private final Clock clock;
    private final Path uploadBase;

    public FileUploadController(FileAnalysisService analysisService,
                                UploadedFileRepository uploadedFileRepo,
                                Clock clock,
                                @Value("${datatalk.upload-base:}") String uploadBasePath) {
        this.analysisService = analysisService;
        this.uploadedFileRepo = uploadedFileRepo;
        this.clock = clock;
        String resolved = (uploadBasePath == null || uploadBasePath.isBlank())
            ? Path.of(System.getProperty("user.home"), ".data-talk", "uploads").toString()
            : uploadBasePath;
        this.uploadBase = Path.of(resolved).toAbsolutePath().normalize();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("sessionId") String sessionId) throws IOException {
        // 1. Validate file not empty
        if (file.isEmpty()) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "File is empty");
        }

        // 2. Detect MIME type
        String originalFilename = file.getOriginalFilename();
        Path tempTarget = Files.createTempFile("upload-", ".tmp");
        try {
            file.transferTo(tempTarget.toFile());
        } catch (IOException e) {
            Files.deleteIfExists(tempTarget);
            throw e;
        }

        String mimeType = analysisService.detectMime(tempTarget, originalFilename);
        if (mimeType == null) {
            Files.deleteIfExists(tempTarget);
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported file type");
        }

        // 3. Validate size
        if (file.getSize() > MAX_SIZE_BYTES) {
            Files.deleteIfExists(tempTarget);
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "File exceeds 50 MB limit");
        }

        // 4. Generate fileId and store to permanent location
        String fileId = UUID.randomUUID().toString();
        Path fileDir = uploadBase.resolve(fileId);
        Files.createDirectories(fileDir);
        Path permanentPath = fileDir.resolve(originalFilename);
        try {
            Files.move(tempTarget, permanentPath);
        } catch (IOException e) {
            // Move may fail mid-write (cross-device, IO error, client abort). Clean up
            // both the temp file and any partial permanent write, plus the now-empty
            // fileDir, to avoid orphan dirs under ~/.data-talk/uploads/.
            // See openspec/changes/optimize-file-upload-image-and-latency/design.md D8.
            Files.deleteIfExists(tempTarget);
            Files.deleteIfExists(permanentPath);
            Files.deleteIfExists(fileDir);
            throw e;
        }

        // 5. Analyze file
        // NOTE: analysis failure does NOT delete permanentPath. The file metadata is
        // still about to be persisted into uploaded_file, and the AI can degrade to
        // calling datatalk_file_read with the UNKNOWN type to consume raw bytes.
        // Removing the file here would strand a DB row pointing at a missing path —
        // a strictly worse failure mode. See design.md D8 / task 3.2.
        FileAnalysisResult analysis;
        try {
            analysis = analysisService.analyze(permanentPath, mimeType, originalFilename);
        } catch (Exception e) {
            log.warn("[file-upload] analysis failed for {}: {}", originalFilename, e.toString());
            analysis = new FileAnalysisResult("UNKNOWN", false, null, Map.of());
        }

        // 6. Create domain record and persist
        UploadedFile uploaded = new UploadedFile(
            fileId,
            sessionId,
            originalFilename,
            mimeType,
            file.getSize(),
            permanentPath.toAbsolutePath().toString(),
            analysisToMap(analysis),
            clock.instant()
        );
        uploadedFileRepo.insert(uploaded);

        // 7. Build response
        Map<String, Object> analysisJson = new LinkedHashMap<>();
        analysisJson.put("type", analysis.type());
        analysisJson.put("fullContent", analysis.fullContent());
        if (analysis.content() != null) {
            analysisJson.put("content", analysis.content());
        }
        if (analysis.summary() != null) {
            analysisJson.put("summary", analysis.summary());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileId", fileId);
        response.put("filename", originalFilename);
        response.put("mimeType", mimeType);
        response.put("sizeBytes", file.getSize());
        response.put("analysis", analysisJson);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> getContent(@PathVariable String fileId) {
        UploadedFile uf = uploadedFileRepo.findById(fileId).orElse(null);
        if (uf == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Path filePath = Path.of(uf.physicalPath()).toAbsolutePath().normalize();
        if (!filePath.startsWith(uploadBase) || !Files.exists(filePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Resource resource = new FileSystemResource(filePath);
        String encodedName = URLEncoder.encode(uf.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(uf.mimeType()))
            .contentLength(uf.sizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
            .body(resource);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    private static Map<String, Object> analysisToMap(FileAnalysisResult a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", a.type());
        map.put("fullContent", a.fullContent());
        if (a.content() != null) map.put("content", a.content());
        if (a.summary() != null) map.put("summary", a.summary());
        return map;
    }
}
