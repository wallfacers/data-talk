---
name: bezel
description: Premium industrial dashboards rendered from JSON. Use this skill whenever the user asks to build a dashboard, monitoring screen, KPI board, big-screen display, operations cockpit, or industry-specific data visualization — even when the word "dashboard" is not explicitly used. Produces a JSON description plus a self-contained HTML artifact with embedded polling, ready to be embedded as an iframe inside DataTalk.
---

# bezel — Premium Industrial Dashboard Skill

bezel turns a JSON dashboard description into a self-contained, premium-quality HTML artifact ready to render inside an iframe. It carries 12 industry-specific design patterns (ecommerce, manufacturing, finance, ...) and bakes refresh policy + polling endpoints into the HTML so the host needs no runtime dependency on bezel.

## When to use

Trigger this skill whenever the user mentions any of:
- dashboards, monitoring screens, KPI boards, ops cockpits, big-screen displays
- "data wall", "TV-wall", "control center", "command center"
- industry-specific data visualization (ecommerce ops, factory monitoring, finance trading floor, healthcare bed map, energy grid, etc.)
- "I need a visualization for X" where X is a multi-metric operational view

Even if the user does not say "dashboard", if the intent matches the above, use this skill.

## What it produces

A **single chat-stream deliverable**: a ```` ```dashboard ```` fenced code block whose body is the dashboard JSON (schemaVersion 2). The frontend `DashboardBlock` recognizes the fence, previews it, and on user confirmation promotes it via `POST /api/dashboards/promote` into a stage tab that renders inside a sandboxed iframe.

Two logical artifacts exist internally, but only the JSON is what you "produce" toward the user:

1. **dashboard.json** — schemaVersion 2; declares `id` (`dash_<8+chars>`), `title`, `theme` (`industry-*`), `renderer: "bezel"`, `layout.engine: "free"`, `widgets[]` with `patternId`, `query.sql + connectionId`, `refresh.intervalMs`, `version`, `createdAt`, `updatedAt`. **This is what you emit in the fenced block.**
2. **dashboard.html** — self-contained HTML; embeds CSP `<meta>`, ECharts CDN, polling scheduler, and `window.__BEZEL_CONFIG__`. Derived from dashboard.json by compile-rules.md. **Server-side / skill-internal compile artifact. Never emit it as a chat deliverable, never write it to disk.**

## Delivery contract — MUST follow

This is the only correct delivery path. Any deviation breaks the workbench rendering pipeline.

1. **Output channel**: emit **two** fenced code blocks **in this order** in the chat reply:
   1. ```` ```dashboard ```` — body = the dashboard JSON, schemaVersion 2 (source-of-truth).
   2. ```` ```dashboard-html ```` — body = the self-contained HTML compiled from the JSON per `references/compile-rules.md`. Base it on the matching `assets/templates/NN-<industry>.html` template; replace the placeholder tokens (`__BEZEL_SERVER_ORIGIN__`, `__JSON_HASH__`, `__DASHBOARD_TITLE__`, `__WIDGET_CONTAINERS__`, `__BEZEL_CONFIG_JSON__`, `__POLLING_SCHEDULER_IIFE__`, industry/style CSS variables) with concrete values, then inline the polling scheduler IIFE from `compile-rules.md` Section 5. The host server replaces `__BEZEL_SERVER_ORIGIN__` again on serve, so leaving that placeholder intact is acceptable.

   The two blocks are paired in document order: the Nth `dashboard` block binds to the Nth `dashboard-html` block. Always emit them adjacent (a short prose sentence between them is fine, but no other fenced block in between).
2. **Forbidden delivery channels**:
   - ❌ `write` tool to materialize HTML or JSON to any filesystem path (including the active session directory). The workbench has **no file-pickup pipeline**; files dropped on disk are invisible to the host.
   - ❌ `bash` redirection (`> file.html`) or any other path that bypasses the chat fenced blocks.
   - ❌ Emitting raw HTML in a generic ```` ```html ```` fence or as inline `<html>` markup — only the `dashboard-html` fence is recognized.
   - ❌ Skipping the `dashboard-html` block — if you only emit the JSON, the stage tab will render the v1 missing placeholder ("v1 dashboard — 在 chat 中说『重新生成视觉』生成新版 HTML"), and the user sees no chart.
3. **JSON skeleton — minimum required fields** (the frontend `dashboardSchema` will reject anything missing these):
   ```json
   {
     "schemaVersion": 2,
     "id": "dash_<8+ alphanumerics, no hyphens>",
     "title": "...",
     "theme": "industry-<kebab-case>",
     "renderer": "bezel",
     "layout": { "engine": "free" },
     "parameters": [],
     "widgets": [
       {
         "id": "<lowercase>_w_<4-16 alphanumerics>",
         "type": "chart" | "kpi" | "table" | "markdown" | "filter" | "section" | "divider" | "image",
         "patternId": "<kebab>.<kebab>",
         "position": { "x": 0, "y": 0, "w": 12, "h": 8 },
         "query": { "connectionId": "<id|null>", "sql": "SELECT ...", "paramRefs": {} },
         "refresh": { "intervalMs": 10000 },
         "options": { /* pattern-specific */ }
       }
     ],
     "version": 1,
     "createdAt": <epoch-ms>,
     "updatedAt": <epoch-ms>
   }
   ```
4. **What happens after you emit the blocks**:
   - Frontend `decorateDashboardBlocks` detects `language-dashboard` and mounts `DashboardBlock`; if a `language-dashboard-html` block follows, the HTML is base64-attached to the same mount as `data-dashboard-html-b64` and removed from the visible chat stream.
   - `DashboardBlock` parses + Zod-validates the JSON and shows a preview card with widget icons.
   - User clicks **"在工作台打开"** → `promoteDashboard(dashboard, html)` opens a `dashboard` stage tab and `POST /api/dashboards/promote` persists **both** the JSON and the HTML.
   - `DashboardTab` → `DashboardIframeShell` → `GET /api/dashboards/{id}/html` returns the stored HTML, which is loaded into a sandboxed iframe via `srcDoc`. The polling scheduler inside the HTML calls `POST /api/dashboards/{id}/widgets/{wid}/data` per widget refresh interval. If the HTML block was missing the iframe falls back to the v1 missing placeholder — that is the user-visible signal that you skipped the `dashboard-html` block.
5. **Verification before responding**: the dashboard JSON you emit must round-trip through `dashboardSchema` (see `client/src/features/dashboard/schema.ts`). If you cannot satisfy the regex constraints (`id`, `widget.id`, `widget.patternId`, `theme`), fix the JSON — never relax delivery by falling back to `write`.

## Reference layout

Read in this order:
1. `references/patterns-catalog.md` → look up industry + mapped style
2. `references/styles/<style>.md` → complete visual spec (layout, cards, charts, background, motion)
3. `references/industries/<industry>.md` → data semantics, KPI list, color overrides, widget recommendations
4. `references/design-language.md` → base tokens (spacing, typography, naming)
5. `references/compile-rules.md` → JSON → HTML assembly algorithm
6. `references/data-contract.md` → JSON schema v2 + polling protocol + `window.__BEZEL_CONFIG__` contract
7. `assets/templates/NN-name.html` → original visual references (read-only inspiration)
8. `scripts/validate.py` → compile-output self-check; run before promoting
9. `scripts/preview.py` → local JSON + mock-data → HTML preview for debugging

## Key invariants

1. **Delivery is a fenced `dashboard` block in chat — never a file write.** The OpenCode `write` tool, `bash` shell redirection, or any other filesystem materialization of the HTML/JSON breaks the workbench pipeline. See "Delivery contract" above.
2. JSON is source-of-truth; HTML is derived. Never hand-edit HTML.
3. HTML must self-contain — no runtime dependency on bezel after production.
4. Refresh updates ECharts data only (`setOption({ dataset })`); never rebuild DOM.
5. CSP is enforced via `<meta http-equiv="Content-Security-Policy">` inside the HTML, with placeholder `__BEZEL_SERVER_ORIGIN__` that the host server replaces on serve.
6. Every widget SQL must be a single SELECT — no DDL/DML, no admin commands. Server-side `SqlStatementGuard` will reject otherwise.
