package com.datatalk.adapter.actions;

import com.datatalk.application.upload.UploadedFileRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.upload.UploadedFile;
import org.springframework.stereotype.Component;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.file_read",
    executor = Executor.SERVER,
    description = "action.file_read.description",
    riskLevel = { RiskLevel.L1 },
    category = { Category.MISC }
)
public class FileReadActionHandler implements ActionHandler<Map, Map> {

    private static final int MAX_LIMIT = 4096;

    private final UploadedFileRepository uploadedFileRepo;

    public FileReadActionHandler(UploadedFileRepository uploadedFileRepo) {
        this.uploadedFileRepo = uploadedFileRepo;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("fileId"),
            "properties", Map.of(
                "fileId", Map.of("type", "string"),
                "offset", Map.of("type", "integer", "minimum", 0, "default", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "default", MAX_LIMIT)
            ));
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "fileId", Map.of("type", "string"),
                "offset", Map.of("type", "integer"),
                "content", Map.of("type", "string"),
                "bytesRead", Map.of("type", "integer")
            ));
    }

    @Override
    public List<OntologyEffect> sideEffects() {
        return List.of(OntologyEffect.NONE);
    }

    @Override
    public Class<Map> inputType() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> {
            String fileId = (String) input.get("fileId");
            if (fileId == null || fileId.isBlank()) {
                return errorResult("fileId is required");
            }

            UploadedFile uploaded = uploadedFileRepo.findById(fileId).orElse(null);
            if (uploaded == null) {
                return errorResult("File not found");
            }

            int offset = intVal(input.get("offset"), 0);
            int limit = Math.min(intVal(input.get("limit"), MAX_LIMIT), MAX_LIMIT);
            offset = Math.max(0, offset);

            Path path = Path.of(uploaded.physicalPath());
            if (!Files.exists(path)) {
                return errorResult("Physical file not found on disk");
            }

            // Binary read path for image files — return base64 data URI, ignore offset/limit
            String mimeType = uploaded.mimeType();
            if (mimeType != null && mimeType.startsWith("image/")) {
                try {
                    byte[] allBytes = Files.readAllBytes(path);
                    String encoded = Base64.getEncoder().encodeToString(allBytes);
                    String dataUri = "data:" + mimeType + ";base64," + encoded;
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("fileId", fileId);
                    out.put("offset", 0);
                    out.put("content", dataUri);
                    out.put("bytesRead", allBytes.length);
                    return out;
                } catch (Exception e) {
                    return errorResult("Failed to read image file: " + e.getMessage());
                }
            }

            // Text read path for non-image files
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                long fileLen = raf.length();
                int from = (int) Math.min(offset, fileLen);
                int toRead = (int) Math.min(limit, fileLen - from);
                if (toRead <= 0) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("fileId", fileId);
                    out.put("offset", from);
                    out.put("content", "");
                    out.put("bytesRead", 0);
                    return out;
                }
                raf.seek(from);
                byte[] buf = new byte[toRead];
                int bytesRead = raf.read(buf);

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("fileId", fileId);
                out.put("offset", from);
                out.put("content", new String(buf, 0, bytesRead));
                out.put("bytesRead", bytesRead);
                return out;
            } catch (Exception e) {
                return errorResult("Failed to read file: " + e.getMessage());
            }
        });
    }

    private static Map<String, Object> errorResult(String message) {
        return Map.of("error", message);
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
