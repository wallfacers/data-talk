package com.datatalk.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 查询结果值对象
 */
public record QueryResult(
        List<String> columns,
        List<Map<String, Object>> rows,
        long durationMs,
        List<Integer> columnTypes
) {

    public QueryResult(List<String> columns, List<Map<String, Object>> rows, long durationMs) {
        this(columns, rows, durationMs, Collections.emptyList());
    }

    public QueryResult {
        if (columnTypes == null) columnTypes = Collections.emptyList();
    }

    public int rowCount() {
        return rows.size();
    }
}
