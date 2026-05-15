package com.datatalk.application.semantic;

import com.datatalk.domain.semantic.VerifiedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class VerifiedQueryRouter {

    private static final Map<String, String> SYNONYM_MAP = Map.ofEntries(
        Map.entry("GMV", "销售额"),
        Map.entry("revenue", "销售额"),
        Map.entry("客单价", "AOV"),
        Map.entry("average order value", "AOV"),
        Map.entry("复购率", "repurchase_rate"),
        Map.entry("留存率", "retention_rate"),
        Map.entry("churn", "流失率"),
        Map.entry("MRR", "月经常性收入"),
        Map.entry("ARR", "年经常性收入"),
        Map.entry("DAU", "日活跃用户"),
        Map.entry("MAU", "月活跃用户"),
        Map.entry("conversion", "转化率")
    );

    private final SemanticModelRepository repository;

    @Autowired
    public VerifiedQueryRouter(SemanticModelRepository repository) {
        this.repository = repository;
    }

    public VerifiedQueryRouter() {
        this.repository = null;
    }

    /** L0: exact string match */
    public Optional<VerifiedQuery> findExact(String connectionId, String question) {
        if (repository == null || connectionId == null || question == null) return Optional.empty();
        List<VerifiedQuery> vqs = repository.listVerifiedQueries(connectionId, 100);
        return vqs.stream()
            .filter(vq -> !vq.stale())
            .filter(vq -> vq.question().equals(question))
            .findFirst();
    }

    /** L1: normalized match (trim, case-insensitive, full-width, synonym substitution) */
    public Optional<VerifiedQuery> findNormalized(String connectionId, String question) {
        if (repository == null || connectionId == null || question == null) return Optional.empty();
        String normalized = normalize(question);
        List<VerifiedQuery> vqs = repository.listVerifiedQueries(connectionId, 200);
        return vqs.stream()
            .filter(vq -> !vq.stale())
            .filter(vq -> normalize(vq.question()).equals(normalized))
            .findFirst();
    }

    /** Top-K VQs sorted by hitCount descending, excluding stale entries */
    public List<VerifiedQuery> topKByHits(String connectionId, int k) {
        if (repository == null || connectionId == null) return List.of();
        return repository.listVerifiedQueries(connectionId, 500).stream()
            .filter(vq -> !vq.stale())
            .sorted(Comparator.comparingInt(VerifiedQuery::hitCount).reversed())
            .limit(k)
            .toList();
    }

    static String normalize(String s) {
        if (s == null) return "";
        String result = s.trim().toLowerCase();
        // Full-width ASCII to half-width
        result = result.replace('　', ' ')
            .replace('！', '!')
            .replace('，', ',')
            .replace('：', ':')
            .replace('；', ';')
            .replace('（', '(')
            .replace('）', ')');
        // NFKD normalization to decompose full-width characters, then strip combining marks
        result = Normalizer.normalize(result, Normalizer.Form.NFKD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Synonym substitution
        for (var entry : SYNONYM_MAP.entrySet()) {
            result = result.replace(entry.getKey().toLowerCase(), entry.getValue().toLowerCase());
        }
        return result.replaceAll("\\s+", " ").trim();
    }
}
