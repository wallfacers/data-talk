package com.datatalk.application.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "datatalk.ingestion")
public class IngestionConfig {

    private long fetchTimeoutMs = 60_000L;
    private long payloadMaxBytes = 500L * 1024 * 1024;
    private List<String> hostDeny = List.of(
        "localhost", "127.0.0.1", "169.254.169.254",
        "metadata.google.internal", "metadata.azure.com"
    );
    private boolean ssrfDenyEnabled = true;

    public long getFetchTimeoutMs() { return fetchTimeoutMs; }
    public void setFetchTimeoutMs(long v) { this.fetchTimeoutMs = v; }

    public long getPayloadMaxBytes() { return payloadMaxBytes; }
    public void setPayloadMaxBytes(long v) { this.payloadMaxBytes = v; }

    public List<String> getHostDeny() { return hostDeny; }
    public void setHostDeny(List<String> v) { this.hostDeny = v; }

    public boolean isSsrfDenyEnabled() { return ssrfDenyEnabled; }
    public void setSsrfDenyEnabled(boolean v) { this.ssrfDenyEnabled = v; }
}
