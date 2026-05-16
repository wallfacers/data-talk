# Compile Rules — JSON to HTML Assembly Algorithm

This document defines the complete algorithm for compiling a `dashboard.json` definition into a self-contained HTML file that renders an ECharts-based dashboard inside a Bezel iframe. Every compiled output **must** conform to the skeleton, required elements, and security constraints described below. The `scripts/validate.py` validator enforces these rules at build time.

---

## 1. Overall HTML Skeleton Template

Every compiled dashboard HTML file follows this exact skeleton. Placeholder tokens (wrapped in `__DOUBLE_UNDERSCORES__`) are replaced during assembly.

```html
<!DOCTYPE html>
<html lang="__LANG__">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="Content-Security-Policy"
        content="default-src 'none';
                 script-src https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js 'unsafe-inline';
                 style-src 'unsafe-inline';
                 img-src data: https: blob:;
                 font-src https://cdn.jsdelivr.net/;
                 connect-src __BEZEL_SERVER_ORIGIN__;
                 base-uri 'none';
                 form-action 'none'">
  <meta name="__JSON_HASH__" content="__SHA256_VALUE__">
  <title>__DASHBOARD_TITLE__</title>
  <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
  <style>
/* __INDUSTRY_CSS_VARIABLES__ */
:root {
  /* Injected from references/industries/<industry>.md */
}

/* __LAYOUT_GRID_STYLES__ */
html, body {
  margin: 0;
  padding: 0;
  width: 100%;
  min-height: 100%;
  font-family: __FONT_FAMILY__;
  background: var(--bg-primary, #0f172a);
  color: var(--text-primary, #e2e8f0);
}

/* The dashboard body is a 12-column CSS Grid; every widget is a grid child.
   Each widget's column/row span comes from dashboard.json widget.position
   ({x:0-11, y:>=0, w:1-12, h:>=1}) via inline grid-column / grid-row.
   IMPORTANT: .bezel-widget MUST NOT use `position: absolute` or inline
   `top/left/width/height`. Doing so detaches widgets from the grid and
   causes them to overlap on top of each other. */
body {
  box-sizing: border-box;
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  grid-auto-rows: minmax(64px, auto);
  gap: 12px;
  padding: 12px;
  overflow: auto;
}

.__WIDGET_CLASS__ {
  /* grid-column / grid-row are set per-widget via inline style from widget.position.
     Do NOT add `position: absolute` here — widgets must remain grid items. */
  min-width: 0;
  min-height: 0;
  position: relative; /* allow inner ::before/::after decorations to be absolutely positioned */
}
  </style>
</head>
<body>
<!-- Widget containers — one <div> per widget -->
__WIDGET_CONTAINERS__

<script>
window.__BEZEL_CONFIG__ = __BEZEL_CONFIG_JSON__;

// -- Polling Scheduler IIFE --
__POLLING_SCHEDULER_IIFE__
</script>
</body>
</html>
```

### Token Replacement Map

| Token | Source | Example |
|---|---|---|
| `__LANG__` | `dashboard.json` → `lang` (default `"zh-CN"`) | `zh-CN` |
| `__BEZEL_SERVER_ORIGIN__` | Runtime config (origin of the Bezel server) | `http://localhost:3100` |
| `__SHA256_VALUE__` | `sha256:` + base64 of SHA-256 digest of `JSON.stringify(dashboard.json)` | `sha256:abc123...` |
| `__DASHBOARD_TITLE__` | `dashboard.json` → `title` | `"Sales Overview"` |
| `__INDUSTRY_CSS_VARIABLES__` | `references/industries/<industry>.md` → CSS variables block | See industry files |
| `__STYLE_CSS__` | `references/styles/<style>.md` → full CSS template | Orbital/Mosaic/... CSS |
| `__FONT_FAMILY__` | Industry theme or `dashboard.json` → `theme.fontFamily` | `"Inter, system-ui, sans-serif"` |
| `__WIDGET_CLASS__` | Derived constant: `"bezel-widget"` | — |
| `__WIDGET_CONTAINERS__` | Generated per widget (see Section 4, step 4) | — |
| `__BEZEL_CONFIG_JSON__` | Serialized config object (see Section 4, step 5) | — |
| `__POLLING_SCHEDULER_IIFE__` | Standard IIFE code (see Section 5) | — |

---

## 2. Required Elements Checklist (validator enforced)

The `scripts/validate.py` script checks every compiled HTML file for the presence and correctness of these elements. A file that is missing any item **fails validation**.

### 2.1 CSP Meta Tag

```html
<meta http-equiv="Content-Security-Policy" content="...__BEZEL_SERVER_ORIGIN__...">
```

**Rules:**
- `frame-ancestors` is omitted because browsers ignore it in `<meta>` tags; the iframe is already sandboxed by the host page.
- `connect-src` must contain `__BEZEL_SERVER_ORIGIN__` (the only allowed fetch target).
- `script-src` must **not** contain `unsafe-eval`.
- `default-src 'none'` is the baseline; everything is explicitly allowlisted.
- `img-src` allows `data:`, `https:`, and `blob:` for chart images and ECharts glyphs.

### 2.2 JSON Hash Meta Tag

```html
<meta name="__JSON_HASH__" content="sha256:...">
```

**Rules:**
- The `content` attribute must start with `sha256:`.
- The value after `sha256:` is the base64-encoded SHA-256 digest of `JSON.stringify(dashboard.json)` (the original, pre-compilation JSON).
- This hash is used by the host page to verify iframe content integrity and detect staleness.

### 2.3 ECharts CDN Script

```html
<script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
```

**Rules:**
- Only this exact URL is allowed. No other `<script src="...">` tags are permitted.
- Version pinning to `5.5.0` is mandatory. The validator rejects any other version string.

### 2.4 Bezel Config Script

```html
<script>window.__BEZEL_CONFIG__ = { ... };</script>
```

**Rules:**
- Must be a single `<script>` block (inline, no src).
- The config object must contain at minimum:
  ```json
  {
    "dashboardId": "<string>",
    "defaultIntervalMs": <number>,
    "pauseOnHidden": <boolean>,
    "widgets": [
      {
        "id": "<string>",
        "type": "chart" | "kpi" | "table" | "markdown" | "filter" | "section" | "divider" | "image",
        "patternId": "<string>",
        "endpoint": "<URL string>",
        "intervalMs": <number>,
        "params": {},
        "paramRefs": [],
        "baseOption": <object | null>
      }
    ]
  }
  ```
- `widgets` array length must match the number of widget container `<div>` elements in the body.
- For every widget in `widgets`:
  - `type === 'chart'` → `baseOption` MUST be a non-null object containing at minimum `series` OR (`xAxis` AND `yAxis`) (validator code `E_CHART_MISSING_BASE_OPTION`).
  - `type !== 'chart'` → `baseOption` MUST be `null` (validator code `E_NONCHART_HAS_BASE_OPTION`).
  - `type` MUST be present (validator code `E_CONFIG_TYPE_MISSING`).

### 2.5 Polling Scheduler IIFE

The standard polling scheduler IIFE (see Section 5) must be present as an immediately-invoked function expression within a `<script>` block. The validator checks for the signature `(function() {` at the start and `})();` at the end of the relevant code region.

### 2.6 Type-Aware Widget Initialization Segments

For every widget `w` in the config, the polling scheduler MUST follow type-aware initialization:

- **`w.type === 'chart'`** → `echarts.init(el)` SHALL be called once, followed by `ch.setOption(w.baseOption)` for first-paint, where `el = document.getElementById(w.id)`. The chart instance is stored in the scheduler's local `charts[w.id]` registry for use by subsequent polling.
- **`w.type !== 'chart'`** (`kpi` / `table` / `markdown` / `filter` / `section` / `divider` / `image`) → `echarts.init(el)` SHALL NOT be called. The widget container's DOM has already been rendered during compile-time HTML assembly (Section 4 Step 4). Polling refreshes go through `applyHtmlData(w.type, el, data.rows)`.

The validator ensures:

- For every widget `w`, a `<div id="<w.id>" class="bezel-widget">` element exists in the body.
- The polling scheduler references every widget ID.
- For every widget where `w.type !== 'chart'`, its container `<div>` SHALL carry attribute `data-bezel-render-kind="<w.type>"` (validator code `E_HTML_KIND_ATTR_MISSING`).
- The compiled scheduler script SHALL contain a runtime branch `if (w.type === 'chart')` (or semantically equivalent guard) before any `echarts.init(` call (validator code `E_NONCHART_HAS_ECHARTS_INIT`). Calling `echarts.init` unconditionally on every widget is **forbidden** and was the root cause of BUG-0055.

### 2.7 Pattern Initialization Invariant

`compile-rules.md` and `data-contract.md` together encode the following two-step ECharts contract for chart widgets:

1. **Pattern initialization** (compile-time + iframe load-time): `ch.setOption(w.baseOption)` runs ONCE per chart widget when the iframe loads, before any polling fetch. `w.baseOption` is the verbatim copy of `dashboard.json` `widget.options` (containing `series`, `xAxis`, `yAxis`, `encode`, etc.), injected by `DashboardArtifactService` into `BezelWidgetConfig.baseOption`. **There is no separate "pattern initialization" step performed by the scheduler — the scheduler IS where it happens.** Without this step, ECharts has no structural information and cannot render any data the polling scheduler later supplies.
2. **Data application** (each poll tick): `ch.setOption({ dataset: { source: rows } }, { lazyUpdate: true })` updates ONLY the dataset. The series / axes / encode definitions are inherited from the first-paint `baseOption`. Never re-emit structural fields here — that would defeat lazyUpdate and cause flicker.

### 2.8 Visibility Change Listener

```js
document.addEventListener('visibilitychange', ...);
```

The compiled HTML must include a `visibilitychange` event listener that pauses/resumes polling when the tab is hidden, unless `pauseOnHidden` is explicitly `false` in the config.

---

## 3. Security Constraints

These constraints are enforced by the validator and must be respected during assembly. Violations cause the build to fail.

### 3.1 No Inline Event Handlers

**Forbidden patterns:**
```html
<div onclick="...">
<body onload="...">
<img onerror="...">
```

All event binding must use `addEventListener` in `<script>` blocks. The validator rejects any HTML attribute starting with `on`.

### 3.2 Script Source Whitelist

The only permitted external `<script src="...">` URL is:

```
https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js
```

Any other `src` attribute value causes validation failure. Inline `<script>` blocks (without `src`) are allowed for config and the polling scheduler.

### 3.3 CSP Directives — Required and Forbidden

**Required directives:**

| Directive | Required Value |
|---|---|
| `default-src` | `'none'` |
| `script-src` | Must include the ECharts CDN URL and `'unsafe-inline'`; must **not** include `'unsafe-eval'` |
| `style-src` | `'unsafe-inline'` (for CSS variables and layout) |
| `img-src` | `data:` `https:` `blob:` |
| `font-src` | `https://cdn.jsdelivr.net/` |
| `connect-src` | `__BEZEL_SERVER_ORIGIN__` (replaced at assembly time) |
| `base-uri` | `'none'` |
| `form-action` | `'none'` |

**Forbidden patterns in CSP:**
- `unsafe-eval` anywhere in `script-src`.
- Wildcard `*` as a standalone source (e.g., `connect-src *` is forbidden).
- `data:` or `blob:` in `script-src`.
- `http:` (non-TLS) sources for scripts or connections (CDN uses HTTPS).

### 3.4 Allowed `img-src` Wildcards

The only place wildcards are permitted:
- `img-src data: https: blob:` — allows chart rendering and ECharts internal image handling.

### 3.5 No `document.write` or `eval`

The compiled output must not contain calls to `document.write`, `eval`, `new Function`, or `setTimeout`/`setInterval` with string arguments (only function references are allowed).

### 3.6 PostMessage Origin

All `parent.postMessage(...)` calls in the polling scheduler use `'*'` as the target origin. This is acceptable because:
- The iframe runs inside the Bezel host page.
- Messages are informational only (status, errors, ready signal).
- The host page must validate incoming messages by checking `ev.data.type` and `ev.data.jsonHash`.

---

## 4. JSON to HTML Assembly Algorithm (step-by-step pseudocode)

This is the authoritative assembly procedure. Implementations must follow these steps in order.

```
INPUT:  dashboard.json
        references/industries/<industry>.md
        __BEZEL_SERVER_ORIGIN__ (runtime config)
OUTPUT: compiled HTML file

──────────────────────────────────────────────────────────────────────

STEP 1 — Parse and Resolve
  Read dashboard.json.
  Extract: title, lang, industry, theme, layout, widgets[].
  Resolve layout.engine (currently only 'free' is supported).
  Collect the set of patternIds from widgets[].

STEP 2 — Load Industry Reference + Style Reference
  Read references/industries/<dashboard.json.industry>.md.
  Extract:
    - Color overrides (--bezel-accent-primary, --bezel-accent-secondary, --bezel-bg-app)
    - KPI list and widget recommendations
    - AI trigger keywords
  Resolve style name from patterns-catalog.md mapping table.
  Read references/styles/<style>.md.
  Extract:
    - Layout skeleton CSS
    - Card, KPI, title component CSS
    - Chart configuration templates
    - Background implementation HTML+CSS
    - Motion tokens
  If the industry file does not exist, FAIL with error:
    "Industry reference not found: <industry>"
  If the style file does not exist, FAIL with error:
    "Style reference not found: <style>"

STEP 3 — Assemble <head>
  Create the <head> element with:
    a. <meta charset="UTF-8">
    b. <meta name="viewport" ...>
    c. CSP <meta http-equiv="Content-Security-Policy">
       - Replace __BEZEL_SERVER_ORIGIN__ with the actual origin
       - Ensure all required CSP directives are present (see Section 3.3)
    d. <meta name="__JSON_HASH__" content="__PLACEHOLDER__">
       (actual hash computed in Step 7)
    e. <title> from dashboard.json.title
    f. ECharts CDN <script src="...">
    g. <style> block:
       - Layer 1: Base tokens from design-language.md (spacing, typography)
       - Layer 2: Style tokens from style file (card, motion, layout)
       - Layer 3: Industry color overrides (accent colors, bg)
       - Component CSS from style file (card, KPI, title, background)
       (Layers are sequential :root blocks — later declarations win via cascade)
       - Layout styles (html/body reset, .bezel-widget class)

STEP 4 — Assemble <body> Widget Containers
  For each widget w in dashboard.json.widgets[]:
    a. Resolve w.patternId to its HTML fragment template
       (pattern templates are defined in pattern reference files)
    b. Compute CSS Grid placement from w.position {x, y, w, h}.
       w.position is in 12-column grid units:
         x ∈ [0, 11]   (column start, 0-indexed)
         y ∈ [0, +∞)   (row start, 0-indexed)
         w ∈ [1, 12]   (column span)
         h ∈ [1, +∞)   (row span)
       Map to CSS Grid (which is 1-indexed):
         grid-column-start = w.position.x + 1
         grid-column-end   = w.position.x + 1 + w.position.w     // i.e. span w
         grid-row-start    = w.position.y + 1
         grid-row-end      = w.position.y + 1 + w.position.h     // i.e. span h
       FORBIDDEN: emitting `position: absolute`, `top`, `left`, or inline `width`/`height`
       on a `.bezel-widget` element. Doing so detaches the widget from the 12-column grid
       and causes overlap. Always express placement via grid-column / grid-row.
    c. Generate:
       if w.type === 'chart':
         <div id="<w.id>"
              class="bezel-widget"
              style="grid-column: <w.position.x + 1> / span <w.position.w>;
                     grid-row: <w.position.y + 1> / span <w.position.h>;">
           <!-- chart container is empty; ECharts will render canvas at runtime -->
         </div>
       else:
         <div id="<w.id>"
              class="bezel-widget"
              data-bezel-render-kind="<w.type>"
              style="grid-column: <w.position.x + 1> / span <w.position.w>;
                     grid-row: <w.position.y + 1> / span <w.position.h>;">
           <!-- patternId-derived HTML fragment (KPI markup, table skeleton, etc.) -->
         </div>
       The `data-bezel-render-kind` attribute is REQUIRED for every non-chart widget — it
       lets the runtime `applyHtmlData(kind, el, rows)` select the right DOM update strategy
       without re-parsing widget metadata.
    d. Append to body.

STEP 5 — Assemble <script> window.__BEZEL_CONFIG__
  Build the config object:
    {
      dashboardId:   dashboard.json.id,
      defaultIntervalMs: dashboard.json.defaultIntervalMs || 10000,
      pauseOnHidden: dashboard.json.pauseOnHidden !== false,
      widgets: dashboard.json.widgets.map(w => ({
        id:         w.id,
        type:       w.type,             // REQUIRED — drives scheduler init branching
        patternId:  w.patternId,
        endpoint:   w.endpoint,         // resolved URL for data fetch
        intervalMs: w.intervalMs ?? (dashboard.json.defaultIntervalMs || 10000),
        params:     w.params || {},
        paramRefs:  w.paramRefs || [],
        baseOption: (w.type === 'chart') ? (w.options || {}) : null
                    // chart: verbatim copy of widget.options for first-paint setOption
                    // non-chart: MUST be null (validator enforced)
      }))
    }
  Serialize as JSON and embed:
    <script>window.__BEZEL_CONFIG__ = <serialized JSON>;</script>

  NOTES on baseOption integrity:
    - The baseOption MUST be JSON-serializable. Function-valued formatters
      (e.g. `formatter: function(p){...}`) will be silently dropped by
      JSON.stringify. Use ECharts 5+ string templates instead
      (e.g. `formatter: '{b}: {c}'`).
    - DashboardArtifactService MAY wrap baseOption parse failures with a
      placeholder `{ title: { text: 'widget options invalid', textStyle: { color: '#f87171' } } }`
      so a single broken widget does not crash the whole iframe. Other
      widgets in the same dashboard SHALL still initialize normally.

STEP 6 — Assemble Polling Scheduler
  Append the standard polling scheduler IIFE (see Section 5)
  as an inline <script> block immediately after the config script.

STEP 7 — Compute and Inject JSON Hash
  Compute: jsonHash = "sha256:" + base64(SHA-256(JSON.stringify(dashboard.json)))
  Note: Use the canonical JSON serialization of the original dashboard.json,
        NOT the config object from Step 5.
  Replace the __JSON_HASH__ meta placeholder:
    <meta name="__JSON_HASH__" content="<jsonHash>">

STEP 8 — Validate Output
  Run: python3 scripts/validate.py <output-file>
  If validation fails:
    - Read error messages
    - Fix the assembly code (not the output manually)
    - Re-run assembly from Step 1
  If validation passes:
    - Output is ready for deployment
    - Return the file path

──────────────────────────────────────────────────────────────────────

POST-CONDITIONS:
  - Output HTML contains exactly one CSP meta with correct directives
  - Output HTML contains exactly one __JSON_HASH__ meta with valid sha256
  - Output HTML contains exactly one external script (ECharts CDN)
  - Output HTML contains exactly one inline script with __BEZEL_CONFIG__
  - Output HTML contains exactly one inline script with polling scheduler IIFE
  - Number of .bezel-widget divs == number of widgets in config
  - No inline on* event handler attributes exist
  - No forbidden CSP patterns exist
```

---

## 5. Polling Scheduler — Standard IIFE

This is the canonical polling scheduler implementation. Copy-paste this exactly into the compiled output. Do not modify function signatures, variable names, or message types without updating the host-page integration code accordingly.

```js
(function() {
  const cfg = window.__BEZEL_CONFIG__;
  if (!cfg) return;
  const charts = {};
  const timers = {};
  let paused = false;

  function bindWidget(w) {
    const el = document.getElementById(w.id);
    if (!el) return;
    // Type-aware initialization: only chart widgets get echarts.init.
    // KPI/table/markdown/etc. are HTML-rendered at compile time; calling
    // echarts.init on them corrupts the container (see BUG-0055).
    if (w.type === 'chart') {
      const ch = echarts.init(el);
      // First-paint base option: series/xAxis/yAxis/encode come from
      // dashboard.json widget.options, copied into w.baseOption by the
      // backend compiler. Without this step ECharts has no structural
      // info and cannot render any data the scheduler later supplies.
      if (w.baseOption) ch.setOption(w.baseOption);
      charts[w.id] = ch;
    }
    if (w.intervalMs && w.intervalMs > 0) schedule(w);
  }

  function schedule(w) {
    const interval = w.intervalMs || cfg.defaultIntervalMs || 10000;
    timers[w.id] = setTimeout(async function tick() {
      if (paused) { timers[w.id] = setTimeout(tick, interval); return; }
      try {
        const res = await fetch(w.endpoint, {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ params: w.params || {} })
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const data = await res.json();
        applyWidgetData(w, data);
      } catch (e) {
        parent.postMessage({ type: 'error', widgetId: w.id, message: String(e) }, '*');
      }
      timers[w.id] = setTimeout(tick, interval);
    }, interval);
  }

  function applyWidgetData(w, data) {
    if (w.type === 'chart') {
      const ch = charts[w.id];
      if (!ch) return;
      // Only dataset — series/xAxis/yAxis inherited from baseOption.
      ch.setOption({ dataset: { source: data.rows } }, { lazyUpdate: true });
      return;
    }
    const el = document.getElementById(w.id);
    if (!el) return;
    applyHtmlData(w.type, el, data.rows || []);
  }

  // HTML-widget refresh strategies. Each kind rewrites only the data-bearing
  // sub-elements that the compile-time HTML already laid out.
  function applyHtmlData(kind, el, rows) {
    if (kind === 'kpi') {
      const row = rows[0] || {};
      const set = (sel, v) => {
        const t = el.querySelector(sel);
        if (t && v !== undefined && v !== null) t.textContent = String(v);
      };
      set('.value', row.value);
      set('.label', row.label);
      set('.delta', row.delta);
      const trendEl = el.querySelector('.trend');
      if (trendEl && row.trend) {
        trendEl.classList.remove('up', 'down');
        trendEl.classList.add(row.trend === 'down' ? 'down' : 'up');
      }
      return;
    }
    if (kind === 'table') {
      const tbody = el.querySelector('tbody');
      if (!tbody) return;
      const headerCells = el.querySelectorAll('thead th');
      const cols = Array.from(headerCells).map(th => th.dataset.column || th.textContent.trim());
      const html = rows.map(r => {
        const tds = cols.map(c => `<td>${escapeHtml(String(r[c] ?? ''))}</td>`).join('');
        return `<tr>${tds}</tr>`;
      }).join('');
      tbody.innerHTML = html;
      return;
    }
    // markdown / section / divider / image / filter:
    // static or event-driven; intervalMs SHOULD already be 0,
    // so polling SHOULD NOT reach this branch. No-op as defensive fallback.
  }

  function escapeHtml(s) {
    return s.replace(/[&<>"']/g, c => (
      { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
  }

  if (cfg.pauseOnHidden !== false) {
    document.addEventListener('visibilitychange', () => { paused = document.hidden; });
  }

  window.addEventListener('message', (ev) => {
    const m = ev.data;
    if (!m || typeof m !== 'object') return;
    if (m.type === 'refresh/pause') paused = true;
    if (m.type === 'refresh/resume') paused = false;
    if (m.type === 'params/update') {
      cfg.widgets.forEach(w => w.params = { ...(w.params||{}), ...m.params });
    }
  });

  cfg.widgets.forEach(bindWidget);
  parent.postMessage({ type: 'ready', jsonHash: document.querySelector('meta[name=__JSON_HASH__]')?.content }, '*');
})();
```

### IIFE Behavior Reference

| Feature | Behavior |
|---|---|
| **Widget binding** | On load, iterates `cfg.widgets` and calls `bindWidget(w)` for each. **Type-aware**: only `w.type === 'chart'` triggers `echarts.init` + `setOption(w.baseOption)`. Non-chart widgets skip ECharts entirely and rely on the compile-time HTML inside their container. |
| **Pattern initialization (chart only)** | Immediately after `echarts.init(el)`, the scheduler calls `ch.setOption(w.baseOption)` so the chart has its series/axes/encode definitions before the first poll arrives. `baseOption` is the verbatim copy of `dashboard.json` `widget.options`. Without this step the chart canvas exists but is blank, even if polling later supplies data. |
| **Polling** | Each widget where `w.intervalMs > 0` gets its own `setTimeout` chain. First fetch fires after `intervalMs`. Subsequent fetches fire `intervalMs` after the previous fetch completes (not on a fixed cadence). HTML widgets MAY participate in polling (e.g. KPI tile refreshing its number every 5s); chart widgets always do. |
| **Pause on hidden** | When `cfg.pauseOnHidden !== false`, listens for `visibilitychange` and sets `paused = true` when the document is hidden. Timers still fire but skip the fetch and reschedule. |
| **Error reporting** | On fetch failure, posts `{ type: 'error', widgetId, message }` to `parent`. Does **not** stop the timer chain — next tick will retry. |
| **Host message protocol** | Accepts three message types from the host page: `refresh/pause`, `refresh/resume`, `params/update`. The `params/update` message merges new params into every widget's param map. |
| **Ready signal** | After all widgets are bound, posts `{ type: 'ready', jsonHash }` to `parent`. The host page uses this to confirm the iframe has loaded and to cross-check the JSON hash. |
| **Data application (chart)** | `applyWidgetData` with `w.type === 'chart'` calls `ch.setOption({ dataset: { source: data.rows } }, { lazyUpdate: true })`. Series/axes/encode are inherited from the pattern-initialization `baseOption` and MUST NOT be re-emitted here. |
| **Data application (HTML)** | `applyWidgetData` with `w.type !== 'chart'` dispatches to `applyHtmlData(kind, el, rows)`. KPI rewrites `.value`/`.label`/`.delta`/`.trend` element text; table rewrites `<tbody>` rows in column order from `<thead th data-column>`; markdown/section/divider/image/filter are static no-ops. **Never calls any ECharts API on HTML widgets.** |

---

## Appendix A: Validation Failure Codes

The `scripts/validate.py` validator emits these error codes. Assembly code should handle them programmatically.

| Code | Meaning | Fix |
|---|---|---|
| `E_CSP_MISSING` | CSP meta tag not found | Ensure Step 3c is executed |
| `E_CSP_UNSAFE_EVAL` | CSP contains `unsafe-eval` in script-src | Remove from CSP template |
| `E_CSP_FRAME_ANCESTORS` | CSP contains `frame-ancestors` directive (not supported in meta) | Remove from CSP template |
| `E_CSP_CONNECT_SRC` | CSP missing `connect-src` or contains wildcard | Set connect-src to server origin |
| `E_HASH_MISSING` | `__JSON_HASH__` meta tag not found | Ensure Step 7 is executed |
| `E_HASH_INVALID` | Hash value does not start with `sha256:` or fails base64 decode | Re-compute hash in Step 7 |
| `E_SCRIPT_EXTERNAL` | External script src is not the whitelisted ECharts CDN | Remove or replace the script tag |
| `E_INLINE_HANDLER` | HTML element has an inline `on*` event attribute | Replace with addEventListener |
| `E_CONFIG_MISSING` | `window.__BEZEL_CONFIG__` script block not found | Ensure Step 5 is executed |
| `E_CONFIG_MALFORMED` | Config JSON is not valid or missing required fields | Check JSON serialization |
| `E_WIDGET_COUNT` | Number of `.bezel-widget` divs does not match config.widgets length | Sync Step 4 and Step 5 |
| `E_WIDGET_ID` | A widget ID in config does not have a matching `<div>` element | Ensure IDs match between Step 4 and Step 5 |
| `E_WIDGET_ABSOLUTE_POSITION` | A `.bezel-widget` rule contains `position: absolute`, or a `.bezel-widget` element's inline style contains `position`/`top`/`left`/`width`/`height` instead of `grid-column`/`grid-row` | Switch to 12-column CSS Grid placement per Section 1 layout styles and Section 4 Step 4 |
| `E_IIFE_MISSING` | Polling scheduler IIFE not found | Ensure Step 6 is executed |
| `E_VISIBILITY_MISSING` | `visibilitychange` listener not found (and pauseOnHidden is not false) | Ensure IIFE includes the visibility listener |
| `E_EVAL_USAGE` | Code contains `eval(`, `new Function(`, or `document.write(` | Remove forbidden calls |
| `E_SETTIMEOUT_STRING` | `setTimeout` or `setInterval` called with a string argument | Use function references only |
| `E_CONFIG_TYPE_MISSING` | A `BezelWidgetConfig` entry is missing the required `type` field | Update Step 5 to copy `w.type` from `dashboard.json` |
| `E_CHART_MISSING_BASE_OPTION` | A widget with `type === 'chart'` has `baseOption: null` or `baseOption` is missing `series` and (`xAxis`+`yAxis`) | Ensure `widget.options` is a complete ECharts option before compile |
| `E_NONCHART_HAS_BASE_OPTION` | A widget with `type !== 'chart'` has a non-null `baseOption` | Step 5 MUST force `baseOption = null` for non-chart widgets |
| `E_HTML_KIND_ATTR_MISSING` | A non-chart widget `<div>` is missing the `data-bezel-render-kind="<type>"` attribute | Update Step 4 to emit the attribute for non-chart widgets |
| `E_NONCHART_HAS_ECHARTS_INIT` | The compiled scheduler IIFE calls `echarts.init` unconditionally on every widget (no `w.type === 'chart'` guard before init) | Use the Section 5 standard IIFE verbatim; do not strip the type guard |
