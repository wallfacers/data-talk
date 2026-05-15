package com.datatalk.domain.undo;

public record BatchUndoResult(
    String id,
    String status,
    int affectedRows,
    String errorMessage
) {}
