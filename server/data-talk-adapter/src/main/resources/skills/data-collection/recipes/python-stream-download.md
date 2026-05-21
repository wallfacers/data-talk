# Python Stream Download & Batch Write

Download large datasets with `requests.get(stream=True)` and stream rows into
the database via the batch write API. Avoids loading the entire response into
memory.

```python
import os
import json
import requests

BACKEND = os.environ["DT_BACKEND_URL"]
TOKEN = os.environ["DT_SCRIPT_TOKEN"]
CONN_ID = os.environ["DT_CONNECTION_ID"]
DOWNLOAD_URL = os.environ["DT_DOWNLOAD_URL"]
TABLE = "streamed_data"
BATCH_SIZE = 500

session_id = None
batch = []
total_written = 0

def flush_batch(create_table=False):
    global session_id
    if not batch:
        return
    body = {
        "token": TOKEN, "connectionId": CONN_ID,
        "tableName": TABLE, "rows": batch,
        "createTable": create_table,
    }
    if session_id:
        body["sessionId"] = session_id
    resp = requests.post(f"{BACKEND}/api/script-data/batch", json=body, timeout=30)
    resp.raise_for_status()
    result = resp.json()
    session_id = result.get("sessionId")
    return result

# --- JSON Lines (NDJSON) streaming ---
resp = requests.get(DOWNLOAD_URL, stream=True, timeout=120)
resp.raise_for_status()

for line in resp.iter_lines(decode_unicode=True):
    if not line or not line.strip():
        continue
    try:
        row = json.loads(line)
        batch.append(row)
    except json.JSONDecodeError:
        continue

    if len(batch) >= BATCH_SIZE:
        result = flush_batch(create_table=(session_id is None))
        total_written += len(batch)
        print(f"Progress: {total_written} rows written "
              f"(session total: {result['totalRowsInserted']})")
        batch = []

# Flush remaining
if batch:
    result = flush_batch(create_table=(session_id is None))
    total_written += len(batch)
    print(f"Progress: {total_written} rows written "
          f"(session total: {result['totalRowsInserted']})")

# Close session
if session_id:
    requests.post(f"{BACKEND}/api/script-data/batch/close",
                  json={"sessionId": session_id}, timeout=30)
    print(f"Done. {total_written} total rows streamed into '{TABLE}'.")

# For CSV/raw text, use iter_content(chunk_size=8192) instead of iter_lines
```

Key points:
- `stream=True` avoids loading the entire response body into memory
- `iter_lines()` yields one line at a time — ideal for NDJSON
- `iter_content()` yields raw chunks — useful for CSV or binary
- Batch writes keep memory usage bounded regardless of file size
- Progress logging gives visibility into long-running downloads
