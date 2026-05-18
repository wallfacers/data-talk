# Python Authentication Patterns

Three common auth patterns for external APIs. **Never hardcode credentials** —
always read them from environment variables.

```python
import os
import requests
from requests.auth import HTTPBasicAuth

BACKEND = os.environ["DT_BACKEND_URL"]
TOKEN = os.environ["DT_SCRIPT_TOKEN"]
CONN_ID = os.environ["DT_CONNECTION_ID"]

# --- Pattern 1: Bearer Token ---
BEARER_TOKEN = os.environ["API_BEARER_TOKEN"]
resp = requests.get(
    "https://api.example.com/v2/data",
    headers={"Authorization": f"Bearer {BEARER_TOKEN}"},
    timeout=30,
)
resp.raise_for_status()
write_resp = requests.post(f"{BACKEND}/api/script-data/write", json={
    "token": TOKEN, "connectionId": CONN_ID,
    "tableName": "bearer_data", "rows": resp.json(),
    "createTable": True,
}, timeout=30)
print(f"Bearer auth result: {write_resp.json()}")

# --- Pattern 2: API Key (header or query param) ---
API_KEY = os.environ["API_KEY"]

# Header variant
resp = requests.get(
    "https://api.example.com/data",
    headers={"X-API-Key": API_KEY},
    timeout=30,
)

# Query param variant
resp = requests.get(
    "https://api.example.com/data",
    params={"api_key": API_KEY},
    timeout=30,
)

resp.raise_for_status()
write_resp = requests.post(f"{BACKEND}/api/script-data/write", json={
    "token": TOKEN, "connectionId": CONN_ID,
    "tableName": "apikey_data", "rows": resp.json(),
    "createTable": True,
}, timeout=30)
print(f"API key auth result: {write_resp.json()}")

# --- Pattern 3: HTTP Basic Auth ---
BASIC_USER = os.environ["API_USERNAME"]
BASIC_PASS = os.environ["API_PASSWORD"]

resp = requests.get(
    "https://api.example.com/secure/data",
    auth=HTTPBasicAuth(BASIC_USER, BASIC_PASS),
    timeout=30,
)
resp.raise_for_status()
write_resp = requests.post(f"{BACKEND}/api/script-data/write", json={
    "token": TOKEN, "connectionId": CONN_ID,
    "tableName": "basic_auth_data", "rows": resp.json(),
    "createTable": True,
}, timeout=30)
print(f"Basic auth result: {write_resp.json()}")
```

Key points:
- **Always use `os.environ`** — never write secrets in source code
- `requests.auth.HTTPBasicAuth` is the standard approach for Basic Auth
- For Bearer tokens, prefer reading from an env var, not a config file
- Use `.env` files for local development (never commit them)
