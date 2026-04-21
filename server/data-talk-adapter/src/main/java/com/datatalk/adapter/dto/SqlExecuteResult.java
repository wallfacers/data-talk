package com.datatalk.adapter.dto;

import com.datatalk.dto.ResolvedDataContextDto;

import java.util.List;

public record SqlExecuteResult(
    ResolvedDataContextDto resolvedContext,
    String contextNotice,
    List<SqlExecuteResultItem> results
) {}
