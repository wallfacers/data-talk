package com.datatalk.domain.part;

public record ToolPart(String toolName, String toolInput, ToolState toolState) implements Part {
}
