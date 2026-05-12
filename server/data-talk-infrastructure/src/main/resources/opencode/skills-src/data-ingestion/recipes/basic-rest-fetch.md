# Basic REST JSON Fetch

Fetch paginated JSON data from a REST API and ingest into a SQL table.

## Steps

1. Call `datatalk_http_request`:
```json
{
  "url": "https://api.example.com/v1/orders",
  "method": "GET",
  "headers": { "Accept": "application/json" },
  "payloadFormat": "json",
  "pagination": {
    "type": "page",
    "params": { "pageParam": "page", "sizeParam": "size", "pageSize": 100, "startPage": 1 },
    "maxPages": 50,
    "terminationHint": { "type": "empty_array", "jsonPath": "$.data" }
  },
  "timeoutMs": 60000
}
```

2. Call `datatalk_infer_ingestion_schema` with the returned `jobId`

3. Present mapping to user for confirmation

4. Call `datatalk_create_ingestion_table` then `datatalk_ingest_payload`
