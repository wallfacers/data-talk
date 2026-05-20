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
2. **dashboard.html** — self-contained HTML; embeds CSP `<meta>`, ECharts CDN, polling scheduler, and `window.__BEZEL_CONFIG__`. Derived from dashboard.json by compile-rules.md. **Compile it in-context and emit it directly inside the `dashboard-html` fenced block. Never write it to disk — not `/tmp`, not the session directory, not anywhere — and never do a `write`-then-`read` round-trip to "produce" or "verify" it.** Both `dashboard.json` and `dashboard.html` are logical, in-context artifacts; the model never needs them as real files at runtime.

## Delivery contract — MUST follow

This is the only correct delivery path. Any deviation breaks the workbench rendering pipeline.

**CRITICAL — every chat turn that emits a `dashboard` block MUST also emit its paired `dashboard-html` block.** This applies to ALL scenarios: first-time generation, regeneration after user feedback, JSON edits via `ui_patch`, title changes, theme changes, widget adjustments — no exceptions. The frontend `DashboardBlock` pairs the Nth `dashboard` with the Nth `dashboard-html` in document order. If you skip the HTML block, the user clicks "打开到工作台" and sees a dead placeholder. Even if the user only asked for a small JSON change, you MUST recompile and emit the full HTML. There is no such thing as "the HTML is unchanged so I can skip it" — the promote API stores whatever you emit, and the iframe loads whatever was stored.

1. **Output channel**: emit **two** fenced code blocks **in this order** in the chat reply:
   1. ```` ```dashboard ```` — body = the dashboard JSON, schemaVersion 2 (source-of-truth).
   2. ```` ```dashboard-html ```` — body = the self-contained HTML compiled from the JSON per `references/compile-rules.md`. Base it on the matching `assets/templates/NN-<industry>.html` template; replace the placeholder tokens (`__BEZEL_SERVER_ORIGIN__`, `__JSON_HASH__`, `__DASHBOARD_TITLE__`, `__WIDGET_CONTAINERS__`, `__BEZEL_CONFIG_JSON__`, `__POLLING_SCHEDULER_IIFE__`, industry/style CSS variables) with concrete values, then inline the polling scheduler IIFE from `compile-rules.md` Section 5. The host server replaces `__BEZEL_SERVER_ORIGIN__` again on serve, so leaving that placeholder intact is acceptable.

   The two blocks are paired in document order: the Nth `dashboard` block binds to the Nth `dashboard-html` block. Always emit them adjacent (a short prose sentence between them is fine, but no other fenced block in between).
2. **Forbidden delivery channels**:
   - ❌ `write` tool to materialize HTML or JSON to any filesystem path (including the active session directory). The workbench has **no file-pickup pipeline**; files dropped on disk are invisible to the host.
   - ❌ `bash` redirection (`> file.html`) or any other path that bypasses the chat fenced blocks.
   - ❌ Emitting raw HTML in a generic ```` ```html ```` fence or as inline `<html>` markup — only the `dashboard-html` fence is recognized.
   - ❌ Skipping the `dashboard-html` block — if you only emit the JSON, the stage tab will render the v1 missing placeholder ("v1 dashboard — 在 chat 中说「重新生成视觉」生成新版 HTML"), and the user sees no chart. **This is the #1 cause of user-reported dashboard bugs. Never skip the HTML block, even in follow-up turns.**
3. **JSON skeleton — minimum required fields** (the frontend `dashboardSchema` will reject anything missing these):
   ```json
   {
     "schemaVersion": 2,
     "id": "dash_<8+ alphanumerics, no hyphens>",
     "title": "...",
     "theme": "industry-<kebab-case>",
     "renderer": "bezel",
     "defaultConnectionId": "<the connection the chat session is currently using>",
     "defaultDatabase":     "<the database the chat session is currently using>",
     "defaultSchema":       "<the schema, if the dialect requires one; otherwise null>",
     "layout": { "engine": "free" },
     "parameters": [],
     "widgets": [
       {
         "id": "<lowercase>+_w_<4-32 alphanumerics-or-underscores>",  // regex: ^[a-z]+_w_[a-zA-Z0-9_]{4,32}$
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

   **Widget id examples** — the suffix after `_w_` must be ≥4 characters; pure short abbreviations are forbidden:
   - ✓ `kpi_w_orders01`
   - ✓ `kpi_w_total_gmv`
   - ✓ `chart_w_funnel01`
   - ✗ `kpi_w_gmv` (suffix is only 3 chars < 4 — Zod will reject)
   - ✗ `kpi_w_a` (suffix is only 1 char < 4 — Zod will reject)

   **Data-context fields are MANDATORY when you know them — do NOT outsource the decision to the server.**
   - `defaultConnectionId` — read from the chat session's data context (the user's currently selected connection).
   - `defaultDatabase` — read from the chat session's data context. **Required** whenever:
     - the connection exposes more than one database (MySQL/Postgres usually do), OR
     - the connection record has no fixed `databaseName` configured.
     If you skip it, widget SQL like `SELECT ... FROM users` will reach `TableContextAutoResolver` with no scope hint and return HTTP 400 `表 X 命中多个候选：a, b。请先明确选择 database/schema` — the user sees blank widgets.
   - `defaultSchema` — required for Postgres/SQL Server dialects when the schema isn't `public`/`dbo`. Omit (`null`) only if the dialect doesn't use schemas (MySQL/SQLite/etc.).
   - You *may* set `defaultConnectionId` only if the user truly hasn't bound a database, but **never relax `defaultDatabase` when a real database is selected.** The server has a `X-DataTalk-Session-Id`-driven backstop that fills blanks from the session's data context, but it is a *safety net for race conditions*, not a substitute for explicit AI emission. Relying on it hides genuine ambiguity and produces dashboards that silently bind to whatever database the user last switched to.
4. **What happens after you emit the blocks**:
   - Frontend `decorateDashboardBlocks` detects `language-dashboard` and mounts `DashboardBlock`; if a `language-dashboard-html` block follows, the HTML is base64-attached to the same mount as `data-dashboard-html-b64` and removed from the visible chat stream.
   - `DashboardBlock` parses + Zod-validates the JSON and shows a preview card with widget icons.
   - User clicks **"在工作台打开"** → `promoteDashboard(dashboard, html)` opens a `dashboard` stage tab and `POST /api/dashboards/promote` persists **both** the JSON and the HTML.
   - `DashboardTab` → `DashboardIframeShell` → `GET /api/dashboards/{id}/html` returns the stored HTML, which is loaded into a sandboxed iframe via `srcDoc`. The polling scheduler inside the HTML calls `POST /api/dashboards/{id}/widgets/{wid}/data` per widget refresh interval. If the HTML block was missing the iframe falls back to the v1 missing placeholder — that is the user-visible signal that you skipped the `dashboard-html` block.
5. **Verification before responding**: the dashboard JSON you emit must round-trip through `dashboardSchema` (see `client/src/features/dashboard/schema.ts`). If you cannot satisfy the regex constraints (`id`, `widget.id`, `widget.patternId`, `theme`), fix the JSON — never relax delivery by falling back to `write`.

6. **Pre-emit checklist for the data-context block**:
   1. Did you read the current chat session's selected `connectionId` / `database` / `schema`?
   2. Is `defaultConnectionId` set in the JSON?
   3. Is `defaultDatabase` set in the JSON? **If the user hasn't selected one yet, STOP and ask them which database to bind the dashboard to** — do not guess and do not leave it blank.
   4. Is `defaultSchema` set (or explicitly `null` for dialects without schemas)?
   5. Does every `widget.query.sql` use unqualified table names that resolve under the chosen `database` + `schema`? If you mix unqualified and `db.table.column` references, document why.
   6. For each `widget.id`, run it through `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$` — the suffix after `_w_` MUST be ≥4 characters. Do not use short English abbreviations (e.g. `gmv`, `cpu`, `qps`) as suffixes; use informative forms like `gmv01` or `total_gmv` instead.

7. **Pre-emit checklist for the HTML block** — run this AFTER the JSON is finalized and BEFORE sending the reply:
   1. Did you compile a fresh `dashboard-html` block from the final JSON using `references/compile-rules.md`?
   2. Is the `dashboard-html` block placed immediately after the `dashboard` block (adjacent, no other fenced blocks in between)?
   3. Did you self-check the HTML **in-context** against `references/compile-rules.md` (required skeleton, placeholder substitution, CSP meta, polling scheduler IIFE, CSS-Grid layout)? **Do NOT write the HTML to a file and shell out to `scripts/validate.py` at runtime.** `validate.py` / `preview.py` are author-time / local-development tools for editing this skill, not steps in the chat-generation flow — invoking them at runtime forces an illegal disk write (the original cause of files escaping to `/tmp`). If you ever run them while *authoring the skill locally*, write the scratch file under the **active session subdirectory inside the worktree** (per AGENTS.md), never `/tmp` and never the parent cwd.
   4. **If ANY of the above is "no", STOP and fix before sending.** A reply with only a `dashboard` block and no `dashboard-html` block is a broken reply.

## Reference layout

Read in this order:
1. `references/patterns-catalog.md` → look up industry + mapped style
2. `references/styles/<style>.md` → complete visual spec (layout, cards, charts, background, motion)
3. `references/industries/<industry>.md` → data semantics, KPI list, color overrides, widget recommendations
4. `references/design-language.md` → base tokens (spacing, typography, naming)
5. `references/compile-rules.md` → JSON → HTML assembly algorithm
6. `references/data-contract.md` → JSON schema v2 + polling protocol + `window.__BEZEL_CONFIG__` contract
7. `assets/templates/NN-name.html` → original visual references (read-only inspiration)
8. `scripts/validate.py` → **author-time / local-dev only** compile-output self-check. **NOT a runtime step** — at chat-generation time, self-check the HTML in-context against `compile-rules.md` instead of writing a file and shelling out to this script.
9. `scripts/preview.py` → **author-time / local-dev only** JSON + mock-data → HTML browser preview for debugging this skill. **Never invoke at runtime.**

## Key invariants

1. **Delivery is a fenced `dashboard` block in chat — never a file write.** The OpenCode `write` tool, `bash` shell redirection, or any other filesystem materialization of the HTML/JSON breaks the workbench pipeline. This includes "scratch" or "verification" writes: do NOT write `dashboard.json` / `dashboard.html` to `/tmp` (or anywhere) and read them back. Compile both in-context and emit them inline. See "Delivery contract" above.
2. JSON is source-of-truth; HTML is derived. Never hand-edit HTML.
3. HTML must self-contain — no runtime dependency on bezel after production.
4. **Refresh path is type-aware**:
   - `widget.type === 'chart'` → 编译期把 `widget.options` 拷为 `BezelWidgetConfig.baseOption`;iframe 初始化时 `echarts.init(el) → ch.setOption(baseOption)`;轮询只 `ch.setOption({ dataset: { source: rows } }, { lazyUpdate: true })`,never rebuild DOM
   - `widget.type !== 'chart'`(`kpi` / `table` / `markdown` / `filter` / `section` / `divider` / `image`)→ **从不调用 `echarts.init`**;DOM 在编译期渲染好;若 `intervalMs > 0`,轮询通过 `applyHtmlData(widget.type, el, rows)` 重写 `textContent` / `<tbody>` 等,never call ECharts API。详见 `references/data-contract.md` §3 调度器算法
5. CSP is enforced via `<meta http-equiv="Content-Security-Policy">` inside the HTML, with placeholder `__BEZEL_SERVER_ORIGIN__` that the host server replaces on serve.
6. Every widget SQL must be a single SELECT — no DDL/DML, no admin commands. Server-side `SqlStatementGuard` will reject otherwise.
7. **Widget layout is CSS Grid, 12 columns — never `position: absolute`.** The compiled HTML `<body>` is a CSS Grid container (`display: grid; grid-template-columns: repeat(12, minmax(0, 1fr))`). Every `.bezel-widget` element is a grid child placed via inline `grid-column: <position.x + 1> / span <position.w>; grid-row: <position.y + 1> / span <position.h>` derived from the JSON's `widget.position = {x, y, w, h}`. **Forbidden:** applying `position: absolute` (or any `top`/`left`/`width`/`height`) to `.bezel-widget`. That detaches widgets from the grid, causes them to stack on top of each other in the top-left corner, and the user sees only one or two visible widgets out of N. This was a real defect — guard against regenerating it. Decorative pseudo-elements *inside* a widget (`.card::after`, etc.) may still use `position: absolute` relative to the widget itself.
