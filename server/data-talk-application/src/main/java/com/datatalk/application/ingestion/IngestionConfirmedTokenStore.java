package com.datatalk.application.ingestion;

import com.datatalk.domain.ingestion.IngestionTokenInvalidException;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IngestionConfirmedTokenStore {

    public record IssuedToken(String tokenId, long expiresAt) {}

    private record Entry(String jobId, String mappingHash, long expiresAt, boolean consumed) {}

    private static final long TTL_MS = 5 * 60_000L;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private ClockProvider clockProvider;

    public IngestionConfirmedTokenStore() {
        this.clockProvider = System::currentTimeMillis;
    }

    IngestionConfirmedTokenStore(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
    }

    void setClockProvider(ClockProvider cp) {
        this.clockProvider = cp;
    }

    @FunctionalInterface
    interface ClockProvider {
        long millis();
    }

    public IssuedToken issue(String jobId, String mappingHash) {
        String tokenId = "ict_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long exp = clockProvider.millis() + TTL_MS;
        store.put(tokenId, new Entry(jobId, mappingHash, exp, false));
        return new IssuedToken(tokenId, exp);
    }

    public void consume(String tokenId, String jobId, String mappingHash) {
        Entry e = store.get(tokenId);
        if (e == null) throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.NOT_FOUND);
        if (e.consumed()) throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.ALREADY_CONSUMED);
        if (clockProvider.millis() > e.expiresAt()) {
            store.remove(tokenId);
            throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.EXPIRED);
        }
        if (!e.jobId().equals(jobId)) throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.JOB_MISMATCH);
        if (!e.mappingHash().equals(mappingHash)) throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.MAPPING_HASH_MISMATCH);
        store.put(tokenId, new Entry(e.jobId(), e.mappingHash(), e.expiresAt(), true));
    }
}
