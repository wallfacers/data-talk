package com.datatalk.application.channel;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Centralized id generator. Tests replace it with a deterministic stub. */
@Component
public class IdGenerator {
    private static final String ARTIFACT_PREFIX = "art-";
    private static final String QUERY_HANDLE_PREFIX = "qr-";

    public String next() {
        return UUID.randomUUID().toString();
    }

    public String nextArtifactId() {
        return ARTIFACT_PREFIX + next();
    }

    public String nextQueryHandleId() {
        return QUERY_HANDLE_PREFIX + next();
    }
}
