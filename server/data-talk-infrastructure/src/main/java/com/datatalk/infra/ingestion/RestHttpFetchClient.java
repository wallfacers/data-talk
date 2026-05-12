package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.HttpFetchClient;
import com.datatalk.domain.ingestion.IngestionAuthFailedException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class RestHttpFetchClient implements HttpFetchClient {

    private final RestClient restClient;

    public RestHttpFetchClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public byte[] fetch(String url, String method, Map<String, String> headers,
                        Map<String, String> queryParams, String body, long timeoutMs) throws Exception {
        UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(url);
        if (queryParams != null) queryParams.forEach(ub::queryParam);
        String finalUrl = ub.build(true).toUriString();

        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) headers.forEach(httpHeaders::set);

        var requestEntity = body != null && !body.isEmpty()
            ? new HttpEntity<>(body, httpHeaders)
            : new HttpEntity<>(httpHeaders);

        ResponseEntity<byte[]> response = restClient
            .method(HttpMethod.valueOf(method.toUpperCase()))
            .uri(finalUrl)
            .headers(h -> h.addAll(httpHeaders))
            .body(body != null ? body : "")
            .retrieve()
            // BUG-0024: surface 401 as a typed auth failure so the handler can map it to
            // INGESTION_AUTH_FAILED instead of the generic INGESTION_FETCH_FAILED bucket.
            .onStatus(status -> status.value() == 401, (req, resp) -> {
                throw new IngestionAuthFailedException(
                    "Upstream returned 401 Unauthorized — credential likely invalid or expired");
            })
            .toEntity(byte[].class);

        return response.getBody() != null ? response.getBody() : new byte[0];
    }
}
