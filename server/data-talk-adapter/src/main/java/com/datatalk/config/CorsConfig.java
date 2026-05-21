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
                "http://172.17.220.222:*",
                // Packaged Tauri webview origins (scheme/host differ per platform).
                "tauri://localhost",
                "http://tauri.localhost",
                "https://tauri.localhost"
        ));
        uiConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        uiConfig.setAllowedHeaders(List.of("*"));
        uiConfig.setAllowCredentials(true);

        // Sandboxed iframes (sandbox="allow-scripts" → Origin: null) need CORS
        // clearance. Browsers forbid combining `Access-Control-Allow-Origin: null`
        // with credentials, so this config is credential-less and only allows the
        // null origin. Registered with higher precedence than uiConfig so it wins
        // for matching paths.
        CorsConfiguration iframeConfig = new CorsConfiguration();
        iframeConfig.setAllowedOrigins(List.of("null"));
        iframeConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        iframeConfig.setAllowedHeaders(List.of("*"));
        iframeConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Register the iframe rules FIRST — UrlBasedCorsConfigurationSource matches
        // in insertion order and the first hit wins.
        source.registerCorsConfiguration("/api/dashboards/*/widgets/*/data", iframeConfig);
        source.registerCorsConfiguration("/api/reports/_assets/**", iframeConfig);
        source.registerCorsConfiguration("/api/**", uiConfig);
        return new CorsFilter(source);
    }
}
