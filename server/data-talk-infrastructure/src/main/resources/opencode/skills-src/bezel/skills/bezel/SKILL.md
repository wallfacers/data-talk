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

Two artifacts per request:
1. **dashboard.json** — schemaVersion 2; declares title, theme (industry-*), widgets with `patternId`, `query.sql + connectionId`, and `refresh.intervalMs`. Source-of-truth.
2. **dashboard.html** — self-contained; embeds CSP `<meta>`, ECharts CDN, polling scheduler, and `window.__BEZEL_CONFIG__`. Derived from dashboard.json.

## Reference layout

- `references/design-language.md` — color, typography, spacing, motion tokens
- `references/data-contract.md` — JSON schema v2 spec + polling protocol + `window.__BEZEL_CONFIG__` contract
- `references/compile-rules.md` — JSON → HTML assembly algorithm + must-include elements + security constraints
- `references/patterns-catalog.md` — 12 industry index + pattern selection priority
- `references/industries/NN-name.md` — per-industry visual signature, layout skeleton, widget recipes, AI trigger cues
- `assets/templates/NN-name.html` — original visual references (read-only inspiration)
- `scripts/validate.py` — compile-output self-check; run before promoting
- `scripts/preview.py` — local JSON + mock-data → HTML preview for debugging

Read `patterns-catalog.md` first to pick the industry, then the per-industry file, then `compile-rules.md` for assembly, then `data-contract.md` to wire the polling layer.

## Key invariants

1. JSON is source-of-truth; HTML is derived. Never hand-edit HTML.
2. HTML must self-contain — no runtime dependency on bezel after production.
3. Refresh updates ECharts data only (`setOption({ dataset })`); never rebuild DOM.
4. CSP is enforced via `<meta http-equiv="Content-Security-Policy">` inside the HTML, with placeholder `__BEZEL_SERVER_ORIGIN__` that the host server replaces on serve.
5. Every widget SQL must be a single SELECT — no DDL/DML, no admin commands. Server-side `SqlStatementGuard` will reject otherwise.
