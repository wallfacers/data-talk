---
name: bezel
description: Premium industrial dashboards from v3 JSON. Use for dashboards, KPI boards, monitoring screens, ops cockpits, big-screen displays, or any multi-metric data visualization. The backend Bezel compiler turns JSON into HTML deterministically — no AI-generated HTML.
---

# bezel — v3 JSON Dashboard Skill

Emit **only** a `` ```dashboard `` `` fenced code block containing v3 JSON. The server-side Bezel compiler (`DashboardCompiler`) compiles it into a self-contained HTML dashboard. **Never emit HTML directly.**

## Delivery

1. Output a single `` ```dashboard `` `` fence with v3 JSON (`schemaVersion: 3`).
2. **Forbidden**: `` ```dashboard-html `` `` fence — HTML is now server-compiled, not AI-generated.
3. **Forbidden**: `write` tool, `bash` redirection, or any filesystem materialization.

## Widget ID Validation

Every `widget.id` must match: `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`

The suffix after `_w_` must be 4–32 characters. Short abbreviations are forbidden.

| Example | Valid | Reason |
|---|---|---|
| `kpi_w_orders01` | ✓ | suffix `orders01` = 8 chars |
| `kpi_w_total_gmv` | ✓ | suffix `total_gmv` = 9 chars |
| `chart_w_funnel01` | ✓ | suffix `funnel01` = 8 chars |
| `kpi_w_gmv` | ✗ | suffix `gmv` = 3 chars (< 4 minimum) |
| `kpi_w_a` | ✗ | suffix `a` = 1 char (< 4 minimum) |
| `kpi_w_cpu` | ✗ | suffix `cpu` = 3 chars — use `cpu_usage` instead |

## v3 Schema Essentials

- `schemaVersion: 3`, `renderer: "bezel"`, `layout.engine: "free"`, `layout.template: "<name>"`
- `widget.slot` is required — match a slot from the template definition
- `widget.title` is required (1–64 chars)
- `widget.patternId` — pick from the pattern catalog reference
- `widget.chartSemantics` — optional chart rendering hints
- Data-context fields (`defaultConnectionId`, `defaultDatabase`, `defaultSchema`) are mandatory when known

## References

- `references/patterns-catalog.md` — generated from `pattern-catalog.yaml`; industry-to-style mapping, widget pattern library
- `references/layout-templates.md` — generated; 6 layout templates with named slots and capacities
- `references/data-contract.md` — v3 JSON schema field reference, polling protocol, `__BEZEL_CONFIG__` contract
- `references/industries/` — per-industry data semantics, KPI lists, color overrides
- `references/styles/` — per-style visual specifications (cards, motion, backgrounds)
