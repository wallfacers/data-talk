# Paginated Fetch (cursor-based)

For APIs using cursor-based pagination.

## Request

```json
{
  "url": "https://api.example.com/v1/records",
  "method": "GET",
  "headers": { "Authorization": "Bearer {{token}}" },
  "payloadFormat": "json",
  "pagination": {
    "type": "cursor",
    "params": { "cursorParam": "cursor", "pageSizeParam": "limit", "pageSize": 200 },
    "maxPages": 100,
    "terminationHint": { "type": "json_path_count_zero", "jsonPath": "$.data" }
  },
  "credentialId": "cred_abc123"
}
```

## Notes

- Cursor pagination reads `next_cursor` from each response page
- `terminationHint` checks if the data array is empty
- Always set `maxPages` as safety limit
