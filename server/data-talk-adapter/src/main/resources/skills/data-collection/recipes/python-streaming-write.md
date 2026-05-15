# Python Streaming Batch Write

For large datasets, use streaming batch writes to avoid memory issues.

```python
import os
import requests
import json

BACKEND = os.environ["DT_BACKEND_URL"]
TOKEN = os.environ["DT_SCRIPT_TOKEN"]
CONN_ID = os.environ["DT_CONNECTION_ID"]
BATCH_SIZE = 500

session_id = None

def write_batch(rows, create_table=False):
    global session_id
    body = {
        "token": TOKEN,
        "connectionId": CONN_ID,
        "tableName": "large_dataset",
        "rows": rows,
        "createTable": create_table,
    }
    if session_id:
        body["sessionId"] = session_id

    resp = requests.post(f"{BACKEND}/api/script-data/batch", json=body)
    result = resp.json()
    if not session_id:
        session_id = result["sessionId"]
    return result

# Stream data in batches
batch = []
for i, item in enumerate(fetch_data_stream()):  # Your data source
    batch.append(item)
    if len(batch) >= BATCH_SIZE:
        result = write_batch(batch, create_table=(i < BATCH_SIZE))
        print(f"Batch {i // BATCH_SIZE}: {result['totalRowsInserted']} total rows")
        batch = []

# Flush remaining
if batch:
    result = write_batch(batch)
    print(f"Final batch: {result['totalRowsInserted']} total rows")

# Close session
if session_id:
    close_resp = requests.post(f"{BACKEND}/api/script-data/batch/close", json={"sessionId": session_id})
    print(f"Session closed: {close_resp.json()}")
```

Key points:
- First batch includes `createTable: true`
- Subsequent batches reference `sessionId`
- Session auto-expires after 5 minutes of inactivity
- Always call `batch/close` when done
