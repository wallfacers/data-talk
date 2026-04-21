package com.datatalk.adapter.dto;

import java.util.List;

public record SqlExecuteResultItem(
    String resultId,
    String kind,
    String title,
    int statementIndex,
    String statementText,
    List<String> columns,
    List<List<Object>> rows,
    int rowCount,
    long executionMs,
    boolean truncated,
    Integer affectedRows,
    String errorMessage
) {}
