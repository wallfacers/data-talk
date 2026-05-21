package com.datatalk.domain.semantic;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public sealed interface PatchOp
    permits PatchOp.AddVerifiedQuery, PatchOp.IncHitCount, PatchOp.AddLiteralMapping {

    String op();
    String by();
    Instant at();

    record AddVerifiedQuery(
        String op,
        VerifiedQuery payload,
        String by,
        Instant at
    ) implements PatchOp {
        public AddVerifiedQuery {
            Objects.requireNonNull(payload, "payload must not be null");
        }
    }

    record IncHitCount(
        String op,
        String vqId,
        int delta,
        String by,
        Instant at
    ) implements PatchOp {
        public IncHitCount {
            Objects.requireNonNull(vqId, "vq_id must not be null");
            if (delta < 1) throw new IllegalArgumentException("delta must be >= 1, got " + delta);
        }
    }

    record AddLiteralMapping(
        String op,
        String dimension,
        Map<String, String> map,
        String by,
        Instant at
    ) implements PatchOp {
        public AddLiteralMapping {
            Objects.requireNonNull(dimension, "dimension must not be null");
            Objects.requireNonNull(map, "map must not be null");
            if (map.isEmpty()) throw new IllegalArgumentException("map must not be empty");
            map = Map.copyOf(map);
        }
    }
}
