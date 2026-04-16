package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record TextPart(
        String id,
        @JsonProperty("sessionID") String sessionID,
        @JsonProperty("messageID") String messageID,
        String text,
        Boolean synthetic,
        Boolean ignored,
        Time time,
        Map<String, Object> metadata
) implements Part {

    public record Time(long start, Long end) {}

    /** Convenience constructor for simple text content. */
    public TextPart(String text) {
        this(null, null, null, text, false, false, null, Map.of());
    }
}
