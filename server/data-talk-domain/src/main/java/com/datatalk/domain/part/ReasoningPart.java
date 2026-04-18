package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ReasoningPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String text,
    Map<String, Object> metadata,
    long timeStart,
    Long timeEnd
) implements Part {

    @Override
    public Part withMessageId(String mid) {
        return new ReasoningPart(id, sessionID, mid, text, metadata, timeStart, timeEnd);
    }
}
