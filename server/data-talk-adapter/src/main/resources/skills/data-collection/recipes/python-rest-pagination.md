# Python REST API Pagination

Handle paginated REST APIs with three common patterns. Each example writes
data to the database via the DataTalk write API.

```python
import os
import time
import requests

BACKEND = os.environ["DT_BACKEND_URL"]
TOKEN = os.environ["DT_SCRIPT_TOKEN"]
CONN_ID = os.environ["DT_CONNECTION_ID"]
TABLE = "paginated_data"

def write_page(rows, create_table=False):
    resp = requests.post(f"{BACKEND}/api/script-data/write", json={
        "token": TOKEN, "connectionId": CONN_ID,
        "tableName": TABLE, "rows": rows, "createTable": create_table,
    }, timeout=30)
    resp.raise_for_status()
    return resp.json()

# --- Pattern 1: Page Number ---
page, create = 1, True
while True:
    resp = requests.get(f"https://api.example.com/items?page={page}&per_page=100", timeout=30)
    resp.raise_for_status()
    data = resp.json()
    if not data["items"]:
        break
    result = write_page(data["items"], create_table=create)
    create = False
    print(f"Page {page}: {result['rowsInserted']} rows, total {result['totalRowsInserted']}")
    page += 1
    time.sleep(0.5)  # rate limit

# --- Pattern 2: Offset / Limit ---
offset, create = 0, True
while True:
    resp = requests.get(f"https://api.example.com/items?offset={offset}&limit=200", timeout=30)
    resp.raise_for_status()
    rows = resp.json()
    if not rows:
        break
    write_page(rows, create_table=create)
    create = False
    offset += 200
    time.sleep(0.5)

# --- Pattern 3: Cursor / Next ---
url = "https://api.example.com/items?limit=100"
create = True
while url:
    resp = requests.get(url, timeout=30)
    if resp.status_code == 429:  # rate limited
        time.sleep(int(resp.headers.get("Retry-After", 5)))
        continue
    resp.raise_for_status()
    data = resp.json()
    write_page(data["data"], create_table=create)
    create = False
    print(f"Cursor batch: {len(data['data'])} rows written")
    url = data.get("next") or data.get("paging", {}).get("next")
    time.sleep(0.5)

print("Pagination complete.")
```

Key points:
- First batch uses `createTable: true`; subsequent batches omit it
- Always `sleep()` between pages to respect rate limits
- Check for `Retry-After` header on 429 responses
- `raise_for_status()` surfaces HTTP errors early
