package com.datatalk.valueobject;

import java.util.List;
import java.util.Map;

/**
 * 查询结果值对象
 */
public record QueryResult(
        List<String> columns,
        List<Map<String, Object>> rows,
        long durationMs
) {

    public int rowCount() {
        return rows.size();
    }
}
