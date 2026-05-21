package com.datatalk.infra.opencode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(OpenCodeMcpProperties.class)
public class OpenCodeConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeConfig.class);

    @Bean
    public OpenCodeHttpClient openCodeHttpClient(
        @Value("${datatalk.opencode.base-url:http://localhost:4096}") String baseUrl,
        ObjectMapper om
    ) {
        return new OpenCodeHttpClient(baseUrl, om);
    }

    @Bean
    public ApplicationRunner legacyOpenCodePropertyWarning(Environment environment) {
        return args -> {
            warnIfPresent(environment, "datatalk.opencode.plugin-callback-base");
            warnIfPresent(environment, "datatalk.opencode.shared-secret");
        };
    }

    private static void warnIfPresent(Environment environment, String key) {
        if (environment.containsProperty(key)) {
            log.warn("{} is deprecated and ignored; use datatalk.mcp.* bootstrap instead", key);
        }
    }
}
