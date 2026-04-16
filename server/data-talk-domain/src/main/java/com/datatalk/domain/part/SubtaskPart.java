package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubtaskPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String prompt,
    String description,
    String agent
) implements Part {}
