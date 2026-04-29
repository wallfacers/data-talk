package com.datatalk.application.er;

import com.datatalk.domain.er.Dialect;
import com.datatalk.domain.er.ErDdlStatement;
import com.datatalk.domain.er.ErSchemaDiff;
import com.datatalk.domain.er.SkippedOp;

public interface ErDdlGenerator {
    Dialect dialect();

    GenerateResult generate(ErSchemaDiff diff);

    sealed interface GenerateResult permits GenerateResult.Generated, GenerateResult.Skipped {
        record Generated(ErDdlStatement statement) implements GenerateResult {}
        record Skipped(SkippedOp skipped) implements GenerateResult {}
    }
}
