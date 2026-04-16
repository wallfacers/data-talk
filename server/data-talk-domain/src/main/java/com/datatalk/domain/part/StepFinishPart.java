package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StepFinishPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String reason,
    String snapshot,
    double cost,
    Tokens tokens
) implements Part {

    public record Tokens(long input, long output, long reasoning, long cacheRead, long cacheWrite) {}
}
