package com.datatalk.dto;

import java.util.List;
import java.util.Map;

/**
 * 查询响应 DTO
 */
public record QueryResponseDto(
        List<String> columns,
        List<Map<String, Object>> rows,
        long durationMs,
        int rowCount
) {
}
