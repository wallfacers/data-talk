package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.DiagnosticsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DamengDiagnosticsProviderRegistrationTest {

    @Autowired
    private List<DiagnosticsProvider> providers;

    @Test
    void damengProviderIsRegistered() {
        boolean registered = providers.stream()
            .anyMatch(p -> p.supportedDriverTypes().contains("dameng"));
        assertThat(registered)
            .as("DamengDiagnosticsProvider must be Spring-registered")
            .isTrue();
    }
}
