package com.datatalk.domain.diagnostics;

import java.util.List;

public record ExplainNode(
    String operation,
    String table,
    ScanType scanType,
    long rows,
    Double cost,
    String extra,
    List<ExplainNode> children
) {}
