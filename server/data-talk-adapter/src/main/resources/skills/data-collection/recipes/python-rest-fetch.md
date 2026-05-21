# Python REST API Fetch + Write

Use `requests` to fetch JSON data from a REST API and write to the database.

```python
import os
import json
import requests

BACKEND = os.environ["DT_BACKEND_URL"]
TOKEN = os.environ["DT_SCRIPT_TOKEN"]
CONN_ID = os.environ["DT_CONNECTION_ID"]

# Fetch data from external API
resp = requests.get("https://api.example.com/data", timeout=30)
resp.raise_for_status()
rows = resp.json()

# Auto-create table and write data
write_resp = requests.post(f"{BACKEND}/api/script-data/write", json={
    "token": TOKEN,
    "connectionId": CONN_ID,
    "tableName": "fetched_data",
    "rows": rows[:1000],  # Batch limit
    "createTable": True,
})
print(f"Written: {write_resp.json()}")
```

Key points:
- `createTable: true` auto-infers column types from first batch
- Token is valid for 10 minutes
- Use `rows[:1000]` to batch large datasets
