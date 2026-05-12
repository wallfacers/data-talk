package com.datatalk.application.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "datatalk.ingestion")
public class IngestionConfig {

    private long fetchTimeoutMs = 60_000L;
    private long payloadMaxBytes = 500L * 1024 * 1024;

    // Conditional deny list — toggled off by ssrfDenyEnabled=false in e2e/dev
    // when the test harness needs to reach the loopback mock server.
    private List<String> hostDeny = List.of("localhost", "127.0.0.1");

    // Always-enforced deny list — cloud metadata endpoints etc., never disabled
    // regardless of ssrfDenyEnabled. Disabling these would defeat SSRF defense.
    private List<String> hostDenyAlways = List.of(
        "169.254.169.254",
        "metadata.google.internal",
        "metadata.azure.com"
    );
    private boolean ssrfDenyEnabled = true;

    public long getFetchTimeoutMs() { return fetchTimeoutMs; }
    public void setFetchTimeoutMs(long v) { this.fetchTimeoutMs = v; }

    public long getPayloadMaxBytes() { return payloadMaxBytes; }
    public void setPayloadMaxBytes(long v) { this.payloadMaxBytes = v; }

    public List<String> getHostDeny() { return hostDeny; }
    public void setHostDeny(List<String> v) { this.hostDeny = v; }

    public List<String> getHostDenyAlways() { return hostDenyAlways; }
    public void setHostDenyAlways(List<String> v) { this.hostDenyAlways = v; }

    public boolean isSsrfDenyEnabled() { return ssrfDenyEnabled; }
    public void setSsrfDenyEnabled(boolean v) { this.ssrfDenyEnabled = v; }
}
