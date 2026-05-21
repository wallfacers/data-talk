package com.datatalk.domain.undo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record UndoCapture(
    boolean undoable,
    String undoLogId,
    List<Map<String, Object>> beforeState,
    String inverseSql,
    String tableName,
    String operation,
    int affectedRows,
    Set<String> pkColumns
) {}
