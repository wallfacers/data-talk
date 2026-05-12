package com.datatalk.application.ingestion;

import java.util.Map;

/**
 * Abstraction over HTTP fetch operations. Infrastructure module provides the
 * implementation (e.g. JDK HttpClient). Application layer depends on this
 * interface only.
 */
public interface HttpFetchClient {

    /**
     * Execute an HTTP request and return the raw response body as bytes.
     *
     * @param url        target URL (already validated by {@link IngestionUrlValidator})
     * @param method     HTTP method (GET, POST, PUT, etc.)
     * @param headers    request headers (may be empty, never null)
     * @param queryParams query parameters (may be empty, never null)
     * @param body       request body (may be null for GET requests)
     * @param timeoutMs  per-request timeout in milliseconds
     * @return response body bytes
     * @throws Exception on network / timeout / non-2xx errors
     */
    byte[] fetch(String url, String method, Map<String, String> headers,
                 Map<String, String> queryParams, String body, long timeoutMs) throws Exception;
}
