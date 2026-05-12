# Bezel Patterns Catalog v2

## Read Order
1. This file → determine industry and mapped style
2. references/styles/<style>.md → complete visual specification
3. references/industries/<industry>.md → data semantics and color overrides
4. references/compile-rules.md → JSON → HTML assembly
5. references/data-contract.md → JSON schema and polling protocol

## Industry-to-Style Mapping
| # | Industry ID | Chinese Name | Style | Style File |
|---|---|---|---|---|
| 01 | multi-screen | 多屏综合监控 | Orbital | styles/orbital.md |
| 02 | ecommerce | 电商运营实时监控中心 | Mosaic | styles/mosaic.md |
| 03 | manufacturing | 工业制造智能监控中心 | Horizon | styles/horizon.md |
| 04 | saas | SaaS 运营监控中心 | Mosaic | styles/mosaic.md |
| 05 | finance | 财务数据分析中心 | Monument | styles/monument.md |
| 06 | logistics | 物流供应链监控中心 | Horizon | styles/horizon.md |
| 07 | healthcare | 医疗健康大数据中心 | Organic | styles/organic.md |
| 08 | hr | 人力资源分析中心 | Organic | styles/organic.md |
| 09 | energy | 能源环保监控中心 | Terrain | styles/terrain.md |
| 10 | cybersecurity | 网络安全态势感知中心 | Orbital | styles/orbital.md |
| 11 | agriculture | 智慧农业大数据中心 | Terrain | styles/terrain.md |
| 12 | education | 在线教育数据中心 | Organic | styles/organic.md |

## Style Selection Guide (Custom Industries)
Decision tree:
1. Monitoring, surveillance, 360-degree awareness → Orbital
2. KPI-driven, dense metrics, ops cockpit → Mosaic
3. Sequential process, pipeline, flow → Horizon
4. People, care, learning, HR → Organic
5. Money, regulation, compliance, authority → Monument
6. Nature, geography, environment, spatial → Terrain
7. Still unclear? Default to Mosaic.

## Industry Index

| # | Industry ID     | Chinese Name         | Visual Signature                              | Key Widget Patterns                                                     |
|---|-----------------|----------------------|-----------------------------------------------|-------------------------------------------------------------------------|
| 01 | multi-screen   | 多屏综合监控          | Cyan starscape, floating scene grid, radar    | radar, multi-gauge, heatmap, timeline, trend-lines, scene-grid          |
| 02 | ecommerce      | 电商运营实时监控中心    | Red-gold particles, funnel gradient, marquee  | funnel-gradient, pie, heatmap-cohort, heatmap-matrix, bar-h, gauge, map |
| 03 | manufacturing  | 工业制造智能监控中心    | Dark steel, rivets, LED pulses, scan-lines    | stacked-bar, dual-axis, h-bar, stacked-area, spc-line, gauge, topology  |
| 04 | saas           | SaaS 运营监控中心       | Teal-purple glass, floating wave circles      | waterfall, donut, retention-multi-line, api-perf, heatmap, churn-forecast |
| 05 | finance        | 财务数据分析中心         | Gold-blue formal, radial glow, dividers       | bar-line-dual, treemap, tree, pie, stacked-area, waterfall-bar, h-bar, gauge |
| 06 | logistics      | 物流供应链监控中心       | Cyan grid, arrow KPI cards, route map         | pie, h-bar, gauge, effectScatter-map, lines-map, bar-line-dual, bar, scatter |
| 07 | healthcare     | 医疗健康大数据中心       | Green-blue heartbeat line, cross badge        | h-bar, heatmap, gauge, bar, line-area, funnel-horizontal, stacked-bar   |
| 08 | hr             | 人力资源分析中心         | Orange accent, network dot background         | graph-force, mini-pie, line-area, scatter-ninebox, boxplot, heatmap     |
| 09 | energy         | 能源环保监控中心         | Green wave background, leaf icons             | stacked-area, h-bar, line-target, pie, bar-line, heatmap, multi-gauge   |
| 10 | cybersecurity  | 网络安全态势感知中心      | Matrix rain, terminal green, scan-lines       | line-area, rose-pie, h-bar, stacked-bar, heatmap, funnel                |
| 11 | agriculture    | 智慧农业大数据中心       | Sun glow, gold-green gradient, texture        | multi-gauge, line-bar-triple, map, line-area, line-dual, h-bar          |
| 12 | education      | 在线教育数据中心         | Yellow top glow, book texture, pill KPIs      | gauge, h-bar, line-area, heatmap, funnel-horizontal                     |

## Pattern Selection Priority Rules

When the AI needs to select an industry template for a user's data, apply these rules in order:

1. **Business noun weight > industry noun weight**
   - Column names like `revenue`, `GMV`, `churn_rate` take precedence over vague industry mentions.
   - If the user says "show me sales data for my retail store" and the table has `gmv` columns, prefer `02-ecommerce`.

2. **Table/column name signal > natural language signal**
   - Actual schema names (`order_amount`, `patient_id`, `attack_source_ip`) are stronger signals than the user's free-text description.
   - Always inspect `SELECT` columns and `FROM` table names before choosing.

3. **When multiple industries match, prefer multi-screen (01)**
   - If the data could plausibly fit 2+ industries and no clear winner emerges, use `01-multi-screen` as the default hub layout and embed industry-specific charts inside it.

4. **When unclear, ask back with 3 candidates**
   - If after rules 1-3 the best match is still ambiguous, present the top 3 candidate industry IDs with a one-line reason each and ask the user to confirm.

## Cross-Industry Generic Widget Pattern Library

These patterns are available in every industry context. They serve as fallback widgets when no industry-specific pattern is needed.

### `generic.kpi-tile`
- Single metric card with value, label, optional delta arrow.
- HTML-only (no ECharts).
- SQL convention: `label TEXT, value NUMERIC, delta TEXT, trend TEXT('up'|'down')`
- `refresh.intervalMs`: 5000

### `generic.echarts-card`
- ECharts chart wrapped in a standard card with title bar.
- The `option` object is passed directly to `echarts.setOption()`.
- SQL convention: depends on chart type (see industry-specific patterns for column examples).
- `refresh.intervalMs`: 10000

### `generic.table`
- Scrollable data table with sortable columns.
- HTML-only.
- SQL convention: columns map 1:1 from the result set.
- `refresh.intervalMs`: 30000

### `generic.markdown`
- Rich text block rendered from Markdown source.
- HTML-only.
- No SQL; content is generated by the AI or user.
- `refresh.intervalMs`: 0 (static)

### `generic.section-header`
- Section divider with title text and optional icon.
- HTML-only.
- No SQL.
- `refresh.intervalMs`: 0 (static)

### `generic.divider`
- Thin horizontal line divider.
- HTML-only (1px line).
- No SQL.
- `refresh.intervalMs`: 0 (static)

### `generic.image`
- Image placeholder with URL source and optional caption.
- HTML `<img>` in a card.
- No SQL.
- `refresh.intervalMs`: 0 (static)

### `generic.filter-bar`
- Horizontal parameter input bar (dropdowns, date pickers, text inputs).
- HTML-only; emits filter values to parent dashboard.
- SQL convention: filter keys correspond to WHERE clause parameters.
- `refresh.intervalMs`: 0 (event-driven)

## Template File Locations

All HTML templates live under `skills/bezel/assets/templates/`:

```
01-multi-screen-dashboard.html
02-ecommerce.html
03-manufacturing.html
04-saas.html
05-finance.html
06-logistics.html
07-healthcare.html
08-hr.html
09-energy.html
10-cybersecurity.html
11-agriculture.html
12-education.html
```

## How to Use This Catalog

1. Receive the user's SQL result set and query context.
2. Apply **Pattern Selection Priority Rules** to pick an industry ID.
3. Look up the mapped **Style** in the Industry-to-Style Mapping table above.
4. Read `references/styles/<style>.md` for the complete visual specification (layout, cards, charts, background, motion).
5. Read the corresponding `industries/NN-*.md` file for data semantics, KPI list, color overrides, and widget recommendations.
6. Mix industry-specific widgets with `generic.*` widgets as needed.
7. Generate the dashboard HTML using the template as the visual baseline.
