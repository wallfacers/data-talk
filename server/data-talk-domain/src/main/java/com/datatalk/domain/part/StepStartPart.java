package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StepStartPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String snapshot
) implements Part {}
