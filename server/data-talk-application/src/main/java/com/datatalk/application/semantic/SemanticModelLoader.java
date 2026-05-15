package com.datatalk.application.semantic;

import com.datatalk.domain.semantic.PatchOp;
import com.datatalk.domain.semantic.SemanticModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class SemanticModelLoader {

    private final SemanticModelRepository repository;

    @Autowired
    public SemanticModelLoader(SemanticModelRepository repository) {
        this.repository = repository;
    }

    public SemanticModelLoader() {
        this.repository = null;
    }

    public Optional<SemanticModel> loadDomain(String connectionId, String name) {
        if (repository == null) return Optional.empty();
        Optional<SemanticModel> base = repository.loadDomain(connectionId, name);
        if (base.isEmpty()) return Optional.empty();
        List<PatchOp> patches = repository.listPatches(connectionId, name);
        if (patches.isEmpty()) return base;
        return Optional.of(PatchApplier.apply(base.get(), patches));
    }

    /**
     * Compact patches: merge all patches back into the yaml and clear the patches file.
     * Called by HousekeepingScheduler when patch count > 200.
     */
    public void compact(String connectionId, String domain, SemanticModel model) {
        if (repository == null) return;
        repository.compactPatches(connectionId, domain, model);
    }
}
