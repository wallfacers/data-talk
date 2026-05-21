package com.datatalk.domain.er;

import java.util.List;

public record ErDdlPlan(List<ErDdlStatement> statements, List<SkippedOp> skipped) {
    public ErDdlPlan {
        statements = statements == null ? List.of() : List.copyOf(statements);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }
}
