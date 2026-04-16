package com.datatalk.infra.opencode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenCodeConfig {

    @Bean
    public OpenCodeHttpClient openCodeHttpClient(
        @Value("${datatalk.opencode.base-url:http://localhost:4096}") String baseUrl,
        ObjectMapper om
    ) {
        return new OpenCodeHttpClient(baseUrl, om);
    }

    @Bean
    public OpenCodeProperties openCodeProperties(
        @Value("${datatalk.opencode.plugin-callback-base:http://localhost:8080}") String callbackBase,
        @Value("${datatalk.opencode.shared-secret:}") String sharedSecret
    ) {
        return new OpenCodeProperties(callbackBase, sharedSecret);
    }

    public record OpenCodeProperties(String callbackBase, String sharedSecret) {}
}
