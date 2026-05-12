package com.datatalk.application.ingestion;

import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class IngestionUrlValidator {

    private final IngestionConfig cfg;

    public IngestionUrlValidator(IngestionConfig cfg) {
        this.cfg = cfg;
    }

    public void validate(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("malformed URL: " + url);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new IllegalArgumentException("disallowed URL scheme: " + scheme);
        }

        if (cfg.isSsrfDenyEnabled()) {
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("URL has no host");
            }
            String lower = host.toLowerCase();
            for (String denied : cfg.getHostDeny()) {
                if (lower.equals(denied.toLowerCase())) {
                    throw new IllegalArgumentException("host denied by SSRF rule: " + host);
                }
            }
        }
    }
}
