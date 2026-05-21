# bezel Data Contract — v3 Schema Field Reference

> Authoritative JSON schema: `dashboard-schema.json` (server classpath).
> This document is the human-readable field reference.

## 1. Root Fields

| Field | Type | Required | Constraint |
|---|---|---|---|
| `schemaVersion` | `3` | yes | const `3` |
| `id` | `string` | yes | `^dash_[a-zA-Z0-9_]{4,}$` |
| `title` | `string` | yes | 1–256 chars |
| `description` | `string` | no | max 32768 chars |
| `defaultConnectionId` | `string \| null` | no | set when session has a connection |
| `defaultDatabase` | `string \| null` | no | set when session has a database |
| `defaultSchema` | `string \| null` | no | set for Postgres/SQL Server when schema differs from default |
| `theme` | `string` | yes | `^industry-[a-z-]+$` |
| `renderer` | `"bezel"` | yes | const |
| `refresh` | `DashboardRefresh` | no | global refresh policy |
| `parameters` | `ParameterDef[]` | yes | global parameter definitions |
| `widgets` | `Widget[]` | yes | widget list |
| `layout` | `LayoutV3` | yes | layout configuration |
| `version` | `integer` | yes | >= 1, increments on promote |
| `createdAt` | `integer` | no | epoch ms — system-assigned, do NOT emit |
| `updatedAt` | `integer` | no | epoch ms — system-assigned, do NOT emit |

## 2. DashboardRefresh

| Field | Type | Default |
|---|---|---|
| `defaultIntervalMs` | `integer` (>= 1000) | `10000` |
| `pauseOnHidden` | `boolean` | `true` |

## 3. ParameterDef

| Field | Type | Required | Constraint |
|---|---|---|---|
| `id` | `string` | yes | `^(global\|local):[a-zA-Z0-9_:]+$` |
| `scope` | `enum` | yes | `global` / `local` |
| `ownerWidgetId` | `string \| null` | no | set for local params |
| `name` | `string` | yes | display name |
| `type` | `enum` | yes | `date` / `date_range` / `string` / `number` / `string_list` |
| `default` | `any` | no | default value |

## 4. LayoutV3

| Field | Type | Required | Constraint |
|---|---|---|---|
| `engine` | `"free"` | yes | const |
| `template` | `enum` | yes | `single-focus` / `two-column-left-heavy` / `two-column-right-heavy` / `three-column-kpi-center` / `top-kpi-bottom-charts` / `grid-equal` |
| `viewport.minWidth` | `integer` | no | >= 1 |
| `viewport.aspect` | `string` | no | e.g. `"16:9"` |

## 5. Widget

| Field | Type | Required | Constraint |
|---|---|---|---|
| `id` | `string` | yes | `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$` |
| `type` | `enum` | yes | `chart` / `kpi` / `table` / `markdown` / `filter` / `section` / `divider` / `image` |
| `slot` | `string` | yes | >= 1 char, must match a template slot |
| `title` | `string` | yes | 1–64 chars |
| `patternId` | `string` | yes | `^[a-z0-9-]+\.[a-z0-9-]+$` |
| `chartSemantics` | `ChartSemantics` | no | chart rendering hints |
| `parameters` | `ParameterDef[]` | no | widget-level params |
| `query` | `WidgetQuery` | no | data query |
| `refresh` | `WidgetRefresh` | no | widget-level refresh override |
| `options` | `object` | yes | pattern-specific options |

## 6. ChartSemantics

| Field | Type | Values |
|---|---|---|
| `chartType` | `enum` | `bar` / `line` / `area` / `pie` / `funnel` / `scatter` / `radar` / `map` |
| `colorScheme` | `enum` | `warm` / `cool` / `monochrome` / `brand` |
| `stacked` | `boolean` | — |
| `showLegend` | `boolean` | — |
| `showTooltip` | `boolean` | — |
| `showAreaFill` | `boolean` | — |
| `labelPosition` | `enum` | `inside` / `outside` / `none` |
| `gridGap` | `enum` | `compact` / `normal` / `spacious` |
| `rawEchartsOption` | `object` | escape hatch for advanced ECharts config |

## 7. WidgetQuery

| Field | Type | Required | Constraint |
|---|---|---|---|
| `connectionId` | `string \| null` | no | overrides defaultConnectionId |
| `database` | `string \| null` | no | overrides defaultDatabase |
| `schema` | `string \| null` | no | overrides defaultSchema |
| `sql` | `string` | yes | single SELECT only |
| `paramRefs` | `object` | no | `{ sqlParam: parameterDefId }` — omit when the SQL has no params; defaults to `{}` |

## 8. WidgetRefresh

| Field | Type | Constraint |
|---|---|---|
| `intervalMs` | `integer` | >= 1000 |
| `strategy` | `enum` | `DATA_ONLY` / `FULL_RERENDER` |

## 9. Polling Protocol

### `window.__BEZEL_CONFIG__`

Embedded in compiled HTML by the server-side Bezel compiler:

```ts
interface BezelConfig {
  dashboardId: string
  defaultIntervalMs: number
  pauseOnHidden: boolean
  widgets: BezelWidgetConfig[]
}

interface BezelWidgetConfig {
  id: string
  type: 'chart' | 'kpi' | 'table' | 'markdown' | 'filter' | 'section' | 'divider' | 'image'
  intervalMs: number
  endpoint: string
  params: Record<string, unknown>
  baseOption: object | null  // chart: non-null ECharts option; non-chart: null
}
```

### Scheduler behavior

- `type === 'chart'`: `echarts.init(el)` + `setOption(baseOption)` first-paint; polling does `setOption({ dataset: { source: rows } })`
- `type !== 'chart'`: DOM rendered at compile time; polling uses `applyHtmlData(kind, el, rows)`
- `visibilitychange` pauses/resumes polling when `pauseOnHidden: true`

### postMessage protocol

Host -> Iframe: `params/update`, `refresh/pause`, `refresh/resume`
Iframe -> Host: `ready` (jsonHash), `error` (widgetId, message), `metric` (name, value)

## 10. Widget ID Validation

Pattern: `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`

Examples:
- `kpi_w_orders01` -- valid
- `chart_w_funnel01` -- valid
- `kpi_w_gmv` -- INVALID (suffix 3 chars < 4)
- `kpi_w_a` -- INVALID (suffix 1 char < 4)
