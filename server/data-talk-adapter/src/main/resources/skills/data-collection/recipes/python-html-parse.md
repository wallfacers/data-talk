# Python HTML Parsing with BeautifulSoup

Parse HTML pages and extract structured data. Install dependencies first:

```bash
pip install requests beautifulsoup4
```

```python
import os
import requests
from bs4 import BeautifulSoup

BACKEND = os.environ["DT_BACKEND_URL"]
TOKEN = os.environ["DT_SCRIPT_TOKEN"]
CONN_ID = os.environ["DT_CONNECTION_ID"]

def write_rows(rows, table, create_table=False):
    resp = requests.post(f"{BACKEND}/api/script-data/write", json={
        "token": TOKEN, "connectionId": CONN_ID,
        "tableName": table, "rows": rows, "createTable": create_table,
    }, timeout=30)
    resp.raise_for_status()
    return resp.json()

page = requests.get("https://example.com/data-page", timeout=30)
page.raise_for_status()
soup = BeautifulSoup(page.text, "html.parser")

# --- Extract HTML tables ---
table = soup.find("table", class_="data-table")
headers = [th.get_text(strip=True) for th in table.find_all("th")]
rows = []
for tr in table.find_all("tr")[1:]:  # skip header row
    cells = [td.get_text(strip=True) for td in tr.find_all("td")]
    if len(cells) == len(headers):
        rows.append(dict(zip(headers, cells)))
if rows:
    write_rows(rows, "scraped_table", create_table=True)

# --- Extract lists with CSS selectors ---
items = soup.select("ul.product-list > li.product-item")
product_rows = []
for item in items:
    name_el = item.select_one(".product-name")
    price_el = item.select_one(".product-price")
    product_rows.append({
        "name": name_el.get_text(strip=True) if name_el else "",
        "price": price_el.get_text(strip=True) if price_el else "",
    })
if product_rows:
    write_rows(product_rows, "scraped_products", create_table=True)

# --- Extract all links ---
for link in soup.find_all("a", href=True):
    href = link["href"]
    text = link.get_text(strip=True)
    print(f"{text}: {href}")

print("HTML parsing complete.")
```

Key points:
- Use `soup.select()` with CSS selectors for precise targeting
- `get_text(strip=True)` removes extra whitespace
- Always set `timeout` on HTTP calls
- Call `raise_for_status()` to catch HTTP errors
