package com.datatalk.adapter.dto;

import com.datatalk.dto.ResolvedDataContextDto;

import java.util.List;

public record SqlExecuteResult(
    List<String> columns,
    List<List<Object>> rows,
    int rowCount,
    long executionMs,
    boolean truncated,
    ResolvedDataContextDto resolvedContext,
    String contextNotice
) {}
