package com.datatalk.domain.part;

public record FilePart(String filename, String mimeType, String url, long sizeBytes) implements Part {
}
