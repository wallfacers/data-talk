package com.datatalk.application.diagnostics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("datatalk.diagnostics")
public record DiagnosticsThresholdProperties(
    Lock lock,
    Pool pool,
    Space space
) {
    public DiagnosticsThresholdProperties {
        lock = lock != null ? lock : new Lock(5_000L);
        pool = pool != null ? pool : new Pool(0.80, 0.95);
        space = space != null ? space : new Space(0.30, 104_857_600L);
    }

    public record Lock(long longWaitMs) {}
    public record Pool(double warnRatio, double criticalRatio) {}
    public record Space(double reclaimRatio, long minDataSizeBytes) {}
}
