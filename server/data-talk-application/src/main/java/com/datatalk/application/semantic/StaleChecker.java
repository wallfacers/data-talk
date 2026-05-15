package com.datatalk.application.semantic;

import com.datatalk.domain.semantic.SemanticModel;
import com.datatalk.domain.semantic.VerifiedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StaleChecker {

    private static final Logger log = LoggerFactory.getLogger(StaleChecker.class);

    private final SemanticModelRepository repository;

    @Autowired
    public StaleChecker(SemanticModelRepository repository) {
        this.repository = repository;
    }

    public StaleChecker() {
        this.repository = null;
    }

    /** Run on startup and periodically to mark stale verified queries. */
    public void checkAll(String connectionId) {
        if (repository == null || connectionId == null) return;
        List<String> domains = repository.listDomains(connectionId);
        for (String domain : domains) {
            var modelOpt = loadModel(connectionId, domain);
            if (modelOpt.isEmpty()) continue;
            SemanticModel model = modelOpt.get();
            List<VerifiedQuery> vqs = repository.listVerifiedQueries(connectionId, 500);
            for (var vq : vqs) {
                if (vq.stale()) continue;
                boolean tablesExist = verifyTableReferences(model, vq);
                if (!tablesExist) {
                    repository.markStale(connectionId, vq.id());
                    log.info("Marked VQ {} as stale (connection={}, domain={})", vq.id(), connectionId, domain);
                }
            }
        }
    }

    private boolean verifyTableReferences(SemanticModel model, VerifiedQuery vq) {
        if (model.entities().isEmpty()) return true;
        for (var entity : model.entities()) {
            String table = entity.physical().table();
            if (vq.sql().toLowerCase().contains(table.toLowerCase())) {
                return true;
            }
        }
        return true;
    }

    private java.util.Optional<SemanticModel> loadModel(String connectionId, String domain) {
        try {
            return repository.loadDomain(connectionId, domain);
        } catch (Exception e) {
            log.warn("Failed to load model {}/{} for stale check: {}", connectionId, domain, e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
