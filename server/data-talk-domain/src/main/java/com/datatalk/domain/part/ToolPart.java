package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ToolPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    @JsonProperty("callID") String callID,
    String tool,
    ToolState state,
    Map<String, Object> metadata
) implements Part {}
