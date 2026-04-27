package com.datatalk.domain.diagnostics;

public record PoolReport(int active, int idle, int maxSize, String poolName) {}
