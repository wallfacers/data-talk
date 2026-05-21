package com.datatalk.application.diagnostics;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class DiagnosticsProviderRegistry {

    private final List<DiagnosticsProvider> providers;

    public DiagnosticsProviderRegistry(List<DiagnosticsProvider> providers) {
        this.providers = providers;
    }

    public Optional<DiagnosticsProvider> find(String driverType) {
        if (driverType == null) return Optional.empty();
        String normalized = driverType.toLowerCase();
        return providers.stream()
            .filter(p -> p.supportedDriverTypes().contains(normalized))
            .findFirst();
    }
}
