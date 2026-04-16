package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FilePart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String mime,
    String filename,
    String url,
    String source
) implements Part {}
