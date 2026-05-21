package com.datatalk.application.er;

import com.datatalk.domain.er.Dialect;
import com.datatalk.domain.er.ErDdlPlan;
import com.datatalk.domain.er.ErDdlStatement;
import com.datatalk.domain.er.ErDesignerPayload;
import com.datatalk.domain.er.ErErrors;
import com.datatalk.domain.er.ErSchemaDiff;
import com.datatalk.domain.er.SkippedOp;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ErDdlGeneratorService {

    private final SchemaDiffPort diffService;
    private final Map<Dialect, ErDdlGenerator> generators;

    public ErDdlGeneratorService(SchemaDiffPort diffService, List<ErDdlGenerator> generators) {
        this.diffService = diffService;
        this.generators = new EnumMap<>(Dialect.class);
        for (ErDdlGenerator generator : generators) this.generators.put(generator.dialect(), generator);
    }

    public ErDdlPlan generate(ErDesignerPayload payload, String connectionId, boolean includeDrops) {
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("generate_ddl requires bind_target first (connectionId is null).");
        }

        Dialect dialect = payload.resolveDialect();
        ErDdlGenerator generator = generators.get(dialect);
        if (generator == null) {
            throw new ErErrors.DialectUnsupportedException(dialect.name().toLowerCase(Locale.ROOT));
        }

        List<ErDdlStatement> statements = new ArrayList<>();
        List<SkippedOp> skipped = new ArrayList<>();
        for (ErSchemaDiff diff : diffService.diff(payload, connectionId)) {
            ErDdlGenerator.GenerateResult result = generator.generate(diff);
            if (result instanceof ErDdlGenerator.GenerateResult.Generated generated) {
                statements.add(generated.statement());
            } else if (result instanceof ErDdlGenerator.GenerateResult.Skipped skip) {
                skipped.add(skip.skipped());
            }
        }
        return new ErDdlPlan(statements, skipped);
    }

    public List<ErSchemaDiff> diff(ErDesignerPayload payload, String connectionId) {
        return diffService.diff(payload, connectionId);
    }

    public interface SchemaDiffPort {
        List<ErSchemaDiff> diff(ErDesignerPayload payload, String connectionId);
    }
}
