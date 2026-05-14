package com.datatalk.domain.undo;

import com.datatalk.domain.action.RiskLevel;

public record UndoRequest(
    String undoLogId,
    boolean confirmed,
    RiskLevel riskAck
) {}
