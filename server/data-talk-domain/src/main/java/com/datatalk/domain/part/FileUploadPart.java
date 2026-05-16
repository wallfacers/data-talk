package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record FileUploadPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String fileId,
    String filename,
    String mimeType,
    long sizeBytes,
    Map<String, Object> analysis
) implements Part {

    @Override
    public Part withMessageId(String mid) {
        return new FileUploadPart(id, sessionID, mid, fileId, filename, mimeType, sizeBytes, analysis);
    }
}
