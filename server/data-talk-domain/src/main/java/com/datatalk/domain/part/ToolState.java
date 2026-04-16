package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "status")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ToolState.Pending.class,   name = "pending"),
    @JsonSubTypes.Type(value = ToolState.Running.class,   name = "running"),
    @JsonSubTypes.Type(value = ToolState.Completed.class, name = "completed"),
    @JsonSubTypes.Type(value = ToolState.Errored.class,   name = "error")
})
public sealed interface ToolState {
    record Pending() implements ToolState {}
    record Running(Long startedAt) implements ToolState {
        public Running() { this(null); }
    }
    record Completed(Map<String, Object> output) implements ToolState {}
    record Errored(String code, String message, Boolean retriable) implements ToolState {}
}
