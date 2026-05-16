package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextPart.class, name = "text"),
        @JsonSubTypes.Type(value = ReasoningPart.class, name = "reasoning"),
        @JsonSubTypes.Type(value = ToolPart.class, name = "tool"),
        @JsonSubTypes.Type(value = FilePart.class, name = "file"),
        @JsonSubTypes.Type(value = StepStartPart.class, name = "step_start"),
        @JsonSubTypes.Type(value = StepFinishPart.class, name = "step_finish"),
        @JsonSubTypes.Type(value = SubtaskPart.class, name = "subtask"),
        @JsonSubTypes.Type(value = FileUploadPart.class, name = "file_upload")
})
public sealed interface Part
        permits TextPart, ReasoningPart, ToolPart, FilePart,
                StepStartPart, StepFinishPart, SubtaskPart, FileUploadPart {

    String id();
    String sessionID();

    /** Returns a copy of this part with the given messageID. */
    Part withMessageId(String mid);
}
