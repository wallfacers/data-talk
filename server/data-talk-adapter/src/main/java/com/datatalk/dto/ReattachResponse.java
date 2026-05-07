package com.datatalk.dto;

import java.util.List;

public record ReattachResponse(List<String> succeeded, List<FailedItem> failed) {
    public record FailedItem(String id, String reason) {}
}
