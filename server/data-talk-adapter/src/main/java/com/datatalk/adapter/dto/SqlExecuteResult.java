package com.datatalk.adapter.dto;

import java.util.List;

public record SqlExecuteResult(
    List<String> columns,
    List<List<Object>> rows,
    int rowCount,
    long executionMs,
    boolean truncated
) {}
