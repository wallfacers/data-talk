# Node.js Fetch + HTML Scrape

Use `fetch` (Node 18+) to fetch and process HTML or JSON data.

```javascript
const BACKEND = process.env.DT_BACKEND_URL;
const TOKEN = process.env.DT_SCRIPT_TOKEN;
const CONN_ID = process.env.DT_CONNECTION_ID;

async function main() {
  // Fetch JSON data
  const resp = await fetch('https://api.example.com/data');
  const data = await resp.json();

  // Transform if needed
  const rows = data.map(item => ({
    name: item.name,
    value: Number(item.value),
    created_at: new Date().toISOString(),
  }));

  // Write to database
  const writeResp = await fetch(`${BACKEND}/api/script-data/write`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      token: TOKEN,
      connectionId: CONN_ID,
      tableName: 'scraped_data',
      rows: rows,
      createTable: true,
    }),
  });
  console.log('Written:', await writeResp.json());
}

main().catch(console.error);
```
