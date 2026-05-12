# CSV Bulk Import

Import a CSV file from a URL into a database table.

## Request

```json
{
  "url": "https://data.example.com/export/orders.csv",
  "method": "GET",
  "payloadFormat": "csv",
  "timeoutMs": 120000
}
```

## Notes

- First line is treated as column headers
- RFC-4180 quoting is supported
- Type inference uses voting across sampled rows
- If CSV has no header row, inform user that column names will be auto-generated (col_0, col_1, etc.)
