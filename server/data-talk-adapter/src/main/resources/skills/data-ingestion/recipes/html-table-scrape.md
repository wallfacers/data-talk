# HTML Table Scrape

Extract data from an HTML `<table>` element on a web page.

## Request

```json
{
  "url": "https://www.example.com/data-table",
  "method": "GET",
  "payloadFormat": "html",
  "timeoutMs": 30000
}
```

## Notes

- Uses jsoup parser to extract the first `<table>` element
- `<thead>` or first `<tr>` is used as column headers
- JavaScript-rendered tables are NOT supported (Day-1 limitation)
- For complex pages, user should export to CSV first
