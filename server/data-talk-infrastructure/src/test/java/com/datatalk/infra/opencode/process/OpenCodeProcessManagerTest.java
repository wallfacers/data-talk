package com.datatalk.infra.opencode.process;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeProcessManagerTest {

    @Test
    void proxyEnvironmentIsInheritedByDefault() {
        OpenCodeServeProperties props = new OpenCodeServeProperties();
        Map<String, String> environment = proxyEnvironment();

        OpenCodeProcessManager.applyProxyEnvironmentPolicy(environment, props);

        assertThat(environment)
            .containsEntry("HTTP_PROXY", "http://localhost:7897")
            .containsEntry("HTTPS_PROXY", "http://localhost:7897")
            .containsEntry("http_proxy", "http://localhost:7897")
            .containsEntry("https_proxy", "http://localhost:7897")
            .containsEntry("PROXY_PORT", "7897");
        assertLoopbackNoProxyEntries(environment, "NO_PROXY");
        assertLoopbackNoProxyEntries(environment, "no_proxy");
    }

    @Test
    void stripProxyEnvironmentRemovesOnlyHttpProxyVariablesWhenEnabled() {
        OpenCodeServeProperties props = new OpenCodeServeProperties();
        props.setStripProxyEnv(true);
        Map<String, String> environment = proxyEnvironment();

        OpenCodeProcessManager.applyProxyEnvironmentPolicy(environment, props);

        assertThat(environment)
            .doesNotContainKeys("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy")
            .containsEntry("PROXY_PORT", "7897");
        assertLoopbackNoProxyEntries(environment, "NO_PROXY");
        assertLoopbackNoProxyEntries(environment, "no_proxy");
    }

    @Test
    void blankProxyEnvironmentValuesAreDroppedByDefault() {
        OpenCodeServeProperties props = new OpenCodeServeProperties();
        Map<String, String> environment = proxyEnvironment();
        environment.put("HTTP_PROXY", "");
        environment.put("HTTPS_PROXY", " ");

        OpenCodeProcessManager.applyProxyEnvironmentPolicy(environment, props);

        assertThat(environment)
            .doesNotContainKeys("HTTP_PROXY", "HTTPS_PROXY")
            .containsEntry("http_proxy", "http://localhost:7897")
            .containsEntry("https_proxy", "http://localhost:7897");
        assertLoopbackNoProxyEntries(environment, "NO_PROXY");
    }

    @Test
    void proxyEnvironmentAddsExactLoopbackBypassEntriesByDefault() {
        OpenCodeServeProperties props = new OpenCodeServeProperties();
        Map<String, String> environment = proxyEnvironment();
        environment.put("NO_PROXY", "172.31.*,127.*,localhost,<local>");
        environment.put("no_proxy", "172.31.*,127.*,localhost,<local>");

        OpenCodeProcessManager.applyProxyEnvironmentPolicy(environment, props);

        assertThat(environment)
            .containsEntry("HTTP_PROXY", "http://localhost:7897")
            .containsEntry("HTTPS_PROXY", "http://localhost:7897");
        assertLoopbackNoProxyEntries(environment, "NO_PROXY");
        assertLoopbackNoProxyEntries(environment, "no_proxy");
    }

    private static Map<String, String> proxyEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("HTTP_PROXY", "http://localhost:7897");
        environment.put("HTTPS_PROXY", "http://localhost:7897");
        environment.put("http_proxy", "http://localhost:7897");
        environment.put("https_proxy", "http://localhost:7897");
        environment.put("NO_PROXY", "localhost,127.0.0.1");
        environment.put("no_proxy", "localhost,127.0.0.1");
        environment.put("PROXY_PORT", "7897");
        return environment;
    }

    private static void assertLoopbackNoProxyEntries(Map<String, String> environment, String name) {
        List<String> entries = Arrays.stream(environment.get(name).split(","))
            .map(String::trim)
            .toList();
        assertThat(entries).contains("localhost", "127.0.0.1", "::1");
    }
}
