package com.datatalk.application.semantic;

import com.datatalk.domain.semantic.SemanticModel;
import com.datatalk.domain.semantic.VerifiedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SemanticModelDigester {

    private static final int MAX_CHARS = 2_000;
    private static final int TOP_K_VQ = 10;
    private static final int TOP_N_MEASURES = 15;
    private static final int TOP_M_MAPPINGS = 10;
    private static final String NO_CONNECTION_SENTINEL = "<no semantic model — please bind a connection>";
    private static final String NO_MODEL_SENTINEL = "<no semantic model defined for this connection>";

    private final SemanticModelRepository repository;

    @Autowired
    public SemanticModelDigester(SemanticModelRepository repository) {
        this.repository = repository;
    }

    public SemanticModelDigester() {
        this.repository = null;
    }

    public String digest(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return NO_CONNECTION_SENTINEL;
        }
        if (repository == null) {
            return NO_CONNECTION_SENTINEL;
        }
        List<String> domains = repository.listDomains(connectionId);
        if (domains.isEmpty()) {
            return NO_MODEL_SENTINEL;
        }
        String connectionName = resolveConnectionName(connectionId);
        StringBuilder sb = new StringBuilder("## Semantic Model Snapshot\n\n");
        sb.append("Current connection: ").append(connectionName).append("\n");
        sb.append("Domains loaded: ").append(String.join(", ", domains)).append("\n");

        List<MeasureDigest> allMeasures = new java.util.ArrayList<>();
        Map<String, Map<String, String>> allMappings = new java.util.LinkedHashMap<>();
        List<VQDigest> allVqs = new java.util.ArrayList<>();

        for (String domain : domains) {
            Optional<SemanticModel> modelOpt = repository.loadDomain(connectionId, domain);
            if (modelOpt.isEmpty()) continue;
            SemanticModel m = modelOpt.get();
            for (var measure : m.measures()) {
                allMeasures.add(new MeasureDigest(measure.name(), measure.labelZh(), measure.labelEn()));
            }
            allMappings.putAll(m.literalMappings());
            List<VerifiedQuery> vqs = repository.listVerifiedQueries(connectionId, TOP_K_VQ);
            for (var vq : vqs) {
                allVqs.add(new VQDigest(vq.question(), vq.hitCount()));
            }
        }

        // Render measures (top N)
        int measureLimit = Math.min(allMeasures.size(), TOP_N_MEASURES);
        if (measureLimit > 0) {
            sb.append("\nTop measures:\n");
            for (int i = 0; i < measureLimit; i++) {
                var m = allMeasures.get(i);
                sb.append("- ").append(m.labelZh).append(" / ").append(m.labelEn)
                    .append(" → measures.").append(m.name).append("\n");
            }
        }

        // Render literal mappings (top M)
        int mappingCount = 0;
        int mappingLimit = Math.min(allMappings.size(), TOP_M_MAPPINGS);
        if (mappingLimit > 0) {
            sb.append("\nCommon literal mappings:\n");
            for (var entry : allMappings.entrySet()) {
                if (mappingCount >= mappingLimit) break;
                for (var inner : entry.getValue().entrySet()) {
                    if (mappingCount >= mappingLimit) break;
                    sb.append("- ").append(inner.getKey()).append(" → ").append(inner.getValue())
                        .append(" (dim: ").append(entry.getKey()).append(")\n");
                    mappingCount++;
                }
            }
        }

        // Render top verified queries (top K)
        if (!allVqs.isEmpty()) {
            int vqLimit = Math.min(allVqs.size(), TOP_K_VQ);
            sb.append("\nTop verified queries (use datatalk_verified_query_find for full list):\n");
            for (int i = 0; i < vqLimit; i++) {
                var vq = allVqs.get(i);
                sb.append("- \"").append(truncate(vq.question, 120))
                    .append("\" (hits: ").append(vq.hitCount).append(")\n");
            }
        }

        sb.append("\nUser defaults: limit=100, time_range=\"last_30d\"\n");

        String result = sb.toString();
        if (result.length() > MAX_CHARS) {
            result = applyBudgetCuts(result, sb, allVqs, allMeasures, allMappings, domains, connectionName);
        }
        return result;
    }

    private String applyBudgetCuts(String original, StringBuilder sb,
                                    List<VQDigest> vqs, List<MeasureDigest> measures,
                                    Map<String, Map<String, String>> mappings,
                                    List<String> domains, String connectionName) {
        // First cut: reduce VQ to top 5
        String result = rebuild(connectionName, domains, measures, Math.min(measures.size(), TOP_N_MEASURES),
            mappings, Math.min(mappings.size(), TOP_M_MAPPINGS),
            vqs, 5);
        if (result.length() <= MAX_CHARS) return result;

        // Second cut: reduce measures to top 8
        result = rebuild(connectionName, domains, measures, Math.min(measures.size(), 8),
            mappings, Math.min(mappings.size(), TOP_M_MAPPINGS),
            vqs, 5);
        if (result.length() <= MAX_CHARS) return result;

        // Third cut: reduce literal mappings to top 5
        result = rebuild(connectionName, domains, measures, Math.min(measures.size(), 8),
            mappings, 5, vqs, 5);
        if (result.length() <= MAX_CHARS) return result;

        // Final resort: truncate
        return result.substring(0, MAX_CHARS - 3) + "...";
    }

    private String rebuild(String connectionName, List<String> domains,
                            List<MeasureDigest> measures, int measureLimit,
                            Map<String, Map<String, String>> mappings, int mappingLimit,
                            List<VQDigest> vqs, int vqLimit) {
        StringBuilder sb = new StringBuilder("## Semantic Model Snapshot\n\n");
        sb.append("Current connection: ").append(connectionName).append("\n");
        sb.append("Domains loaded: ").append(String.join(", ", domains)).append("\n");

        if (measureLimit > 0 && !measures.isEmpty()) {
            sb.append("\nTop measures:\n");
            int n = Math.min(measures.size(), measureLimit);
            for (int i = 0; i < n; i++) {
                var m = measures.get(i);
                sb.append("- ").append(m.labelZh).append(" / ").append(m.labelEn)
                    .append(" → measures.").append(m.name).append("\n");
            }
        }

        int mc = 0;
        if (mappingLimit > 0 && !mappings.isEmpty()) {
            sb.append("\nCommon literal mappings:\n");
            for (var entry : mappings.entrySet()) {
                if (mc >= mappingLimit) break;
                for (var inner : entry.getValue().entrySet()) {
                    if (mc >= mappingLimit) break;
                    sb.append("- ").append(inner.getKey()).append(" → ").append(inner.getValue())
                        .append(" (dim: ").append(entry.getKey()).append(")\n");
                    mc++;
                }
            }
        }

        if (vqLimit > 0 && !vqs.isEmpty()) {
            sb.append("\nTop verified queries (use datatalk_verified_query_find for full list):\n");
            int n = Math.min(vqs.size(), vqLimit);
            for (int i = 0; i < n; i++) {
                var vq = vqs.get(i);
                sb.append("- \"").append(truncate(vq.question, 120))
                    .append("\" (hits: ").append(vq.hitCount).append(")\n");
            }
        }

        sb.append("\nUser defaults: limit=100, time_range=\"last_30d\"\n");
        return sb.toString();
    }

    private String resolveConnectionName(String connectionId) {
        return connectionId;
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private record MeasureDigest(String name, String labelZh, String labelEn) {}
    private record VQDigest(String question, int hitCount) {}
}
