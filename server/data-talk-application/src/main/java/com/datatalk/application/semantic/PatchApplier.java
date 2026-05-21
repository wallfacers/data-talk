package com.datatalk.application.semantic;

import com.datatalk.domain.semantic.PatchOp;
import com.datatalk.domain.semantic.SemanticModel;
import com.datatalk.domain.semantic.VerifiedQuery;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PatchApplier {

    private PatchApplier() {}

    public static SemanticModel apply(SemanticModel base, List<PatchOp> patches) {
        Map<String, Map<String, String>> literalMappings = new HashMap<>(base.literalMappings());
        Map<String, VerifiedQuery> vqPatches = new LinkedHashMap<>();
        var vqRefs = new LinkedHashMap<String, SemanticModel.VerifiedQueryRef>();

        for (PatchOp p : patches) {
            switch (p) {
                case PatchOp.AddVerifiedQuery avq -> {
                    vqPatches.put(avq.payload().id(), avq.payload());
                    vqRefs.put(avq.payload().id(), new SemanticModel.VerifiedQueryRef(avq.payload().id()));
                }
                case PatchOp.IncHitCount ignored -> {
                    // hit counts are applied to verified_queries.jsonl, not the model
                }
                case PatchOp.AddLiteralMapping alm -> {
                    literalMappings.computeIfAbsent(alm.dimension(), k -> new LinkedHashMap<>())
                        .putAll(alm.map());
                }
            }
        }

        return new SemanticModel(
            base.name(), base.version(), base.description(),
            base.entities(), base.dimensions(), base.measures(), base.metrics(),
            literalMappings,
            base.verifiedQueryRefs().isEmpty() ? List.copyOf(vqRefs.values())
                : mergeRefs(base.verifiedQueryRefs(), vqRefs),
            base.lastModified(), base.authoredBy()
        );
    }

    private static List<SemanticModel.VerifiedQueryRef> mergeRefs(
        List<SemanticModel.VerifiedQueryRef> existing,
        Map<String, SemanticModel.VerifiedQueryRef> patches
    ) {
        Map<String, SemanticModel.VerifiedQueryRef> merged = new LinkedHashMap<>();
        for (var ref : existing) merged.put(ref.id(), ref);
        merged.putAll(patches);
        return List.copyOf(merged.values());
    }
}
