package com.datatalk.adapter.dto;

import com.datatalk.dto.ResolvedDataContextDto;

import java.util.List;

public record SqlExecuteResult(
    String status,
    ResolvedDataContextDto resolvedContext,
    String contextNotice,
    List<SqlExecuteResultItem> results,
    SqlConfirmationPayload confirmation,
    SqlConfirmationInvalid invalidConfirmation
) {
    public static SqlExecuteResult executed(ResolvedDataContextDto ctx, String notice, List<SqlExecuteResultItem> results) {
        return new SqlExecuteResult("executed", ctx, notice, results, null, null);
    }

    public static SqlExecuteResult requiresConfirmation(ResolvedDataContextDto ctx, String notice, SqlConfirmationPayload confirmation) {
        return new SqlExecuteResult("requires_confirmation", ctx, notice, List.of(), confirmation, null);
    }

    public static SqlExecuteResult confirmationInvalid(ResolvedDataContextDto ctx, String notice, SqlConfirmationInvalid invalid) {
        return new SqlExecuteResult("confirmation_invalid", ctx, notice, List.of(), null, invalid);
    }

    public record SqlConfirmationInvalid(String reason, String ackedRisk, String currentRisk, String message) {}
}
