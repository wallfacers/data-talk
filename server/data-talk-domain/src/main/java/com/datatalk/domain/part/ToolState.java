package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "state"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ToolState.Pending.class, name = "pending"),
        @JsonSubTypes.Type(value = ToolState.Running.class, name = "running"),
        @JsonSubTypes.Type(value = ToolState.Completed.class, name = "completed"),
        @JsonSubTypes.Type(value = ToolState.Errored.class, name = "errored")
})
public sealed interface ToolState
        permits ToolState.Pending, ToolState.Running, ToolState.Completed, ToolState.Errored {

    static Pending pending() {
        return new Pending();
    }

    static Running running() {
        return new Running();
    }

    static Completed completed(String toolOutput) {
        return new Completed(toolOutput);
    }

    static Errored errored(String errorMessage) {
        return new Errored(errorMessage);
    }

    record Pending() implements ToolState {}

    record Running() implements ToolState {}

    record Completed(String toolOutput) implements ToolState {}

    record Errored(String errorMessage) implements ToolState {}
}
