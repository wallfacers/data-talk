package com.datatalk.domain.dashboard;

public record DashboardRefresh(int defaultIntervalMs, boolean pauseOnHidden) {

    public DashboardRefresh {
        if (defaultIntervalMs < 1000) {
            throw new IllegalArgumentException("defaultIntervalMs must be >= 1000, got " + defaultIntervalMs);
        }
    }

    public static DashboardRefresh defaults() {
        return new DashboardRefresh(30_000, true);
    }
}
