# Python CSV Parsing & Batch Write

Read CSV files with `csv.DictReader`, infer types, and stream rows into the
database via the batch write API.

```python
import os
import csv
import requests

BACKEND = os.environ["DT_BACKEND_URL"]
TOKEN = os.environ["DT_SCRIPT_TOKEN"]
CONN_ID = os.environ["DT_CONNECTION_ID"]
CSV_PATH = os.environ.get("DT_CSV_PATH", "data.csv")
TABLE = "csv_data"
BATCH_SIZE = 1000

# --- Streaming batch write ---
session_id = None
batch = []

def flush_batch(create_table=False):
    global session_id
    body = {
        "token": TOKEN, "connectionId": CONN_ID,
        "tableName": TABLE, "rows": batch,
        "createTable": create_table,
    }
    if session_id:
        body["sessionId"] = session_id
    resp = requests.post(f"{BACKEND}/api/script-data/batch", json=body, timeout=30)
    result = resp.json()
    session_id = result.get("sessionId")
    return result

with open(CSV_PATH, newline="", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    row_count = 0
    for row in reader:
        batch.append(row)
        row_count += 1
        if len(batch) >= BATCH_SIZE:
            result = flush_batch(create_table=(session_id is None))
            print(f"Written {row_count} rows, {result['totalRowsInserted']} total")
            batch = []

    # Flush remaining
    if batch:
        result = flush_batch(create_table=(session_id is None))
        row_count += len(batch)
        print(f"Written {row_count} rows, {result['totalRowsInserted']} total")

# Close batch session
if session_id:
    requests.post(f"{BACKEND}/api/script-data/batch/close",
                  json={"sessionId": session_id}, timeout=30)
    print(f"Batch session closed. {row_count} total rows imported.")
```

Key points:
- `csv.DictReader` uses the first row as column names
- First batch includes `createTable: true` to auto-create the table
- Type inference is heuristic — the write API will coerce values
- Always close the batch session with `batch/close`
- Session auto-expires after 5 minutes of inactivity
