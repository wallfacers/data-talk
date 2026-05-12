package com.datatalk.application.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class IngestionUrlValidatorTest {

    IngestionConfig cfg = new IngestionConfig();
    IngestionUrlValidator v = new IngestionUrlValidator(cfg);

    @Test
    void acceptsPublicHttps() {
        v.validate("https://api.example.com/x");
    }

    @Test
    void acceptsPublicHttp() {
        v.validate("http://api.example.com/x");
    }

    @Test
    void rejectsFileScheme() {
        assertThatThrownBy(() -> v.validate("file:///etc/passwd"))
            .hasMessageContaining("scheme");
    }

    @Test
    void rejectsFtpScheme() {
        assertThatThrownBy(() -> v.validate("ftp://x"))
            .hasMessageContaining("scheme");
    }

    @Test
    void rejectsDenyListedHost() {
        assertThatThrownBy(() -> v.validate("http://localhost:8080/admin"))
            .hasMessageContaining("denied");
    }

    @Test
    void rejectsAwsMetadata() {
        assertThatThrownBy(() -> v.validate("http://169.254.169.254/latest/meta-data"))
            .hasMessageContaining("denied");
    }

    @Test
    void rejectsGcpMetadata() {
        assertThatThrownBy(() -> v.validate("http://metadata.google.internal/"))
            .hasMessageContaining("denied");
    }

    @Test
    void rejectsAwsMetadataEvenWhenSsrfDenyDisabled() {
        // BUG-0014 regression guard — host-deny-always must hold even when
        // the conditional SSRF deny list is turned off (e.g., e2e profile).
        cfg.setSsrfDenyEnabled(false);
        assertThatThrownBy(() -> v.validate("http://169.254.169.254/latest/meta-data/"))
            .hasMessageContaining("denied");
    }

    @Test
    void allowsLoopbackWhenSsrfDenyDisabled() {
        // E2E mock server lives on 127.0.0.1 — disabling ssrf-deny-enabled must
        // make that reachable without dropping the always-enforced list.
        cfg.setSsrfDenyEnabled(false);
        v.validate("http://127.0.0.1:8080/some/endpoint");
        v.validate("http://localhost:8080/some/endpoint");
    }
}
