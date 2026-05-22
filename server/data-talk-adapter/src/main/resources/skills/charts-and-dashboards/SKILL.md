---
name: charts-and-dashboards
description: Use when the user asks for a chart, KPI, trend, report, or multi-widget dashboard. Triggers on phrases like 图表/趋势图/可视化/报表/仪表盘/大屏/多图组合 and chart/visualization/dashboard/KPI/multi-widget/ECharts/grid layout. Covers the inline `chart` fenced code block (default single-chart path), `chart:<artifactId>` for SQL-derived charts, and dashboard schema v1 plus incremental `ui_patch` edits for persistent multi-widget layouts.
---

# Charts and Dashboards Skill

## When to use

- "画一个柱状图展示月度销售额" / "render an ECharts line chart for this query result"
- "把这个 SQL 结果保存成图表工件" → `datatalk_render_chart`
- "做一个销售总览的仪表盘，包含趋势图和 KPI 卡片" / "build a multi-widget dashboard"
- "在现有仪表盘上加一个新图表 / 改标题" → incremental `ui_patch`

## When NOT to use

- The user only wants the raw query result table → use `datatalk_execute_sql` and return rows directly; no chart needed.
- The user asks for premium industrial dashboards / KPI 大屏 / heavy bezel visuals → prefer the `bezel` skill, which is a separate path.
- The user wants ad-hoc text/markdown without any visualization → reply in chat; do not invoke chart or dashboard tools.

## Charts

- **Default path — inline fenced code block**: emit a fenced code block with language tag `chart`, whose body is the **complete ECharts option JSON, with literal data values inlined into `series`/`xAxis` etc.** This is the cheapest path and is preferred for one-off charts inside the conversation stream.
- **Linking to a SQL artifact**: when the chart is derived from a prior `datatalk_execute_sql` artifact, you may open the fence as `chart:<artifactId>` (e.g. the opening fence becomes ```` ```chart:art-abc123 ````). The `:<artifactId>` suffix is **provenance metadata only** — it is forwarded as `sourceArtifactId` when the user promotes the chart to the workbench. **It does NOT feed data into the chart and does NOT auto-render the rowset.** You still MUST write the full ECharts option (including the actual data points read from the SQL result) into the fence body, exactly as in the default path. The suffix is an annotation on top of a complete chart, never a replacement for it.
- **Saved chart artifact via `datatalk_render_chart`**: call this tool **only** when a persistent chart artifact is required (sharing, embedding into a dashboard widget that needs an artifact reference, or supersede chains). The tool card **no longer renders the chart inline in chat** — it is a compact reference row with an "在工作台查看" (eye) affordance. If the user should also *see* the chart in the conversation, emit a fenced block (below) in addition. See `[[artifacts-output]]` for `supersedes` chain rules.

### The fenced body is NEVER empty

The frontend `ChartBlock` parses the fence body with `JSON.parse`. An empty body — e.g. ```` ```chart:art-abc123 ```` immediately followed by the closing fence — produces `JSON.parse('')` → `Unexpected end of JSON input`, and the chart renders as an error card. The `:<artifactId>` suffix never substitutes for the JSON body. Whenever you open a `chart` / `chart:<artifactId>` fence, the body MUST be a complete, parseable ECharts option object. If you intend to render purely from a saved artifact instead, call `datatalk_render_chart` and emit NO fenced block at all — do not leave an empty fence behind.

### Single in-chat surface — charts render only in the fenced block

In chat, a chart renders in exactly one place: the markdown ```` ```chart ```` / ```` ```echarts ```` fenced block (`ChartBlock`). `datatalk_render_chart` no longer paints a chart in chat — its tool card is a compact reference with an eye ("在工作台查看") only. There is no longer a double-render hazard, but the rules below keep output clean:

- To **show** a chart in the conversation, ALWAYS emit a ```` ```chart ```` / ```` ```echarts ```` fenced block with the full ECharts option in the body. This is the single display surface.
- Call `datatalk_render_chart` **only** when a persistent artifact is required (sharing, supersede chains, or a dashboard widget that needs an artifact reference). It will not display the chart inline; pair it with a fenced block if the user should also see it.
- ```` ```echarts ```` is an alias of ```` ```chart ```` and is bound by the same rules.
- `ChartBlock` exposes promote-to-workbench / expand / copy. The chart becomes a persistent artifact on demand via its "打开到工作台" button — so for most charts you never need `datatalk_render_chart` at all.

## Dashboards

A dashboard is a persistent multi-widget layout backed by server-side JSON storage. Use dashboards when the user asks for a composed view with multiple charts, KPI tiles, tables, or markdown annotations arranged in a grid.

### When to use dashboards vs single charts

- Single chart → use the inline ```` ```chart ```` fenced block (the single in-chat display surface); `datatalk_render_chart` only when a saved artifact is needed (it does not render inline).
- Multi-widget composed view (2+ visuals, filters, or text tiles) → use a dashboard.

### Creating a dashboard

Use `datatalk_ui_exec` with `object=dashboard`, `action=create`, and `params.dashboardJson` containing the full dashboard JSON conforming to the dashboard schema v1. See `[[ui-contract]]` for the generic `datatalk_ui_exec` envelope semantics.

```
ui_exec(object=dashboard, action=create, {
  dashboardJson: {
    schemaVersion: 1,
    id: "dash_placeholder",
    title: "Sales Overview",
    parameters: [],
    widgets: [
      {
        id: "chart_w_sales01",
        type: "chart",
        position: { x: 0, y: 0, w: 6, h: 8 },
        options: { title: "Monthly Revenue", echartsOption: {...} }
      },
      {
        id: "md_w_notes01",
        type: "markdown",
        position: { x: 6, y: 0, w: 6, h: 8 },
        options: { source: "## Notes\nQ2 trend explanation..." }
      }
    ],
    layout: { engine: "grid", cols: 12, rowHeight: 32, gap: 8 },
    version: 1
  }
})
```

The server replaces the client-supplied `id` with a fresh `dash_xxxxxxxx`, sets `version: 1`, and persists the document.

### Incremental updates via ui_patch

After the initial create, use `datatalk_ui_patch` with `object=dashboard` to apply incremental JSON-Patch-style changes. See `[[ui-contract]]` for the generic `datatalk_ui_patch` envelope.

- **Add a widget**: `{ op: "add", path: "/widgets/-", value: {...} }`
- **Remove a widget**: `{ op: "remove", path: "/widgets[id=chart_w_sales01]" }`
- **Replace a widget field**: `{ op: "replace", path: "/widgets[id=chart_w_sales01]/options/title", value: "New Title" }`
- **Change a top-level field**: `{ op: "replace", path: "/title", value: "New Dashboard Title" }`

Always include the latest version when sending patches. On stale versions the server returns a 409 — recovery follows the standard flow in `[[concurrency-contract]]`.

### P1 widget types

P1 supports exactly two widget `type` values:

- `chart` — body lives in `options.echartsOption` (same JSON shape as the inline fenced block).
- `markdown` — body lives in `options.source` (CommonMark string).

Other types (`kpi`, `table`, `filter`, `section`, `divider`, `image`) are reserved for future phases and MUST NOT be emitted in P1 dashboards.

### Path addressing

- **Array matchKey**: `/widgets[id=<widgetId>]/<field>` selects a widget element by its `id` property and then navigates into the named field.
- **Append**: `/widgets/-` appends a new element to the `widgets` array (JSON-Patch append convention).
- **Plain**: `/title`, `/layout/cols`, etc. address top-level fields and nested object fields directly.

### Error handling

| Code | HTTP | Meaning |
|------|------|---------|
| `version_conflict` | 409 | Stale version. Same semantics as `[[concurrency-contract]]` — re-read the dashboard and retry the patch against the new version. |
| `patch_rejected` | 422 | Invalid patch op or path. Fix the ops and retry. |
| `validation_error` | 422 | Resulting dashboard does not pass schema or cross-widget validation. Inspect server error detail, repair the payload, and retry. |
| `payload_too_large` | 413 | Dashboard JSON exceeds 256 KB. Reduce widget count or payload size before retrying. |
| `not_found` | 404 | Dashboard id does not exist. Do not retry; ask the user or re-list dashboards. |

### Relationship to single chart

A dashboard can contain `chart` widgets whose `options.echartsOption` is the same JSON you would otherwise emit inline. When a user's request shifts from a single chart to a multi-widget dashboard, create the dashboard and embed the chart as one widget. Do **not** call `datatalk_render_chart` separately for charts that are embedded in a dashboard — the widget itself is the persistent representation.

## Recommended workflow

1. Decide the shape: single chart (inline fenced block) vs multi-widget (dashboard).
2. For a single chart, prefer the inline ```` ```chart ```` block with the full ECharts option in the body; add the `:<artifactId>` suffix (still with the full body) only to record SQL provenance, and reach for `datatalk_render_chart` only when a saved artifact is required. Never emit an empty fence body.
3. For a multi-widget view, draft the full dashboard JSON (schemaVersion 1, P1 widget types only) and create it via `datatalk_ui_exec` with `object=dashboard`, `action=create`, `params.dashboardJson`.
4. For follow-up tweaks, use `datatalk_ui_patch` with the freshest version; on `version_conflict` re-read and retry per `[[concurrency-contract]]`.
5. Keep widget ids stable and human-readable so subsequent `matchKey` patches stay legible.
