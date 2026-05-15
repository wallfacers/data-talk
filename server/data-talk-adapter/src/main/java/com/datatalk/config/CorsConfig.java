package com.datatalk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        // Default config for the desktop/web UI hitting /api/** with credentials.
        CorsConfiguration uiConfig = new CorsConfiguration();
        uiConfig.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://192.168.1.3:*",
                "http://172.17.220.222:*"
        ));
        uiConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        uiConfig.setAllowedHeaders(List.of("*"));
        uiConfig.setAllowCredentials(true);

        // Bezel dashboard widget data endpoint is fetched by sandboxed iframes
        // (sandbox="allow-scripts" → Origin: null). Browsers forbid combining
        // `Access-Control-Allow-Origin: null` with credentials, so this config is
        // credential-less and only allows the null origin. Registered with higher
        // precedence than uiConfig so it wins for matching paths.
        CorsConfiguration iframeConfig = new CorsConfiguration();
        iframeConfig.setAllowedOrigins(List.of("null"));
        iframeConfig.setAllowedMethods(List.of("POST", "OPTIONS"));
        iframeConfig.setAllowedHeaders(List.of("*"));
        iframeConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Register the iframe rule FIRST — UrlBasedCorsConfigurationSource matches
        // in insertion order and the first hit wins.
        source.registerCorsConfiguration("/api/dashboards/*/widgets/*/data", iframeConfig);
        source.registerCorsConfiguration("/api/**", uiConfig);
        return new CorsFilter(source);
    }
}
