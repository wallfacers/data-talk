# bezel Data Contract

> JSON schema v2、polling 协议、`window.__BEZEL_CONFIG__` 和 postMessage 通信契约。

## 1. JSON Schema v2

### 根字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schemaVersion` | `2` | **是** | 固定值 `2`。v1 由 `DashboardArtifactService.migrateV1ToV2` 自动迁移 |
| `id` | `string` | **是** | 格式 `dash_[a-zA-Z0-9_]{4,}` |
| `title` | `string` | **是** | 1–256 字符 |
| `description` | `string` | 否 | 最长 32768 字符 |
| `defaultConnectionId` | `string \| null` | **是\*** | 默认数据源连接 ID。\*技术上 schema 是 `optional`，但 AI 在用户已绑定连接时**必须**填写。 |
| `defaultDatabase` | `string \| null` | **是\*** | 默认 database 名（widget SQL 未限定 database 时使用）。\*只有当用户在会话里**真的没绑定 database** 时才允许为 null，否则 AI 必须填会话当前选中的 database。**这是 AI 的责任，不是后端兜底的责任**——后端虽然有 `X-DataTalk-Session-Id` header 兜底，但只用于客户端注入失效的极端竞态情况，不是默认路径。空缺时 widget 接口会报 `表 X 命中多个候选：a, b。请先明确选择 database/schema`。 |
| `defaultSchema` | `string \| null` | 否 | 默认 schema 名。Postgres / SQL Server 等使用 schema 的方言下，若选中了非默认 schema（非 `public`/`dbo`），AI **必须**填；MySQL/SQLite 等不使用 schema 的方言可省略。 |
| `theme` | `string` | **是** | 格式 `industry-[a-z-]+`，如 `industry-ecommerce` |
| `renderer` | `"bezel"` | **是** | 固定值，标识渲染器 |
| `refresh` | `DashboardRefresh` | 否 | 全局刷新策略（默认 `10000ms / pauseOnHidden: true`） |
| `parameters` | `ParameterDef[]` | **是** | 全局参数定义 |
| `widgets` | `Widget[]` | **是** | Widget 列表 |
| `layout` | `FreeLayout` | **是** | 布局配置 |
| `version` | `integer` | **是** | ≥1，每次 promote 递增 |
| `createdAt` | `integer` | 否 | Unix 时间戳 ms |
| `updatedAt` | `integer` | 否 | Unix 时间戳 ms |

### DashboardRefresh

```ts
{
  defaultIntervalMs: number   // ≥1000, 默认 10000
  pauseOnHidden: boolean      // 默认 true
}
```

### Widget 字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `string` | **是** | 格式 `[a-z]+_w_[a-zA-Z0-9]{4,16}` |
| `type` | `enum` | **是** | `chart` / `kpi` / `table` / `markdown` / `filter` / `section` / `divider` / `image` |
| `patternId` | `string` | **是** | 格式 `[a-z0-9-]+\.[a-z0-9-]+`，如 `ecommerce.funnel-gradient` |
| `position` | `GridPosition` | **是** | `{x, y, w, h, z?}` |
| `parameters` | `ParameterDef[]` | 否 | Widget 级参数 |
| `query` | `WidgetQuery` | 否 | 数据查询 |
| `refresh` | `WidgetRefresh` | 否 | Widget 级刷新策略 |
| `options` | `object` | **是** | ECharts option / KPI 模板等 |

### WidgetQuery

```ts
{
  connectionId?: string | null    // 覆盖 defaultConnectionId
  sql: string                     // 单条 SELECT
  paramRefs: Record<string, string>  // { sqlParamName: parameterDefId }
}
```

### ParameterDef

```ts
{
  id: string            // 格式: (global|local):[a-zA-Z0-9_:]+
  scope: 'global' | 'local'
  ownerWidgetId?: string | null
  name: string
  type: 'date' | 'date_range' | 'string' | 'number' | 'string_list'
  default: unknown
}
```

### FreeLayout

```ts
{
  engine: 'free'
  viewport?: {
    minWidth: number    // ≥640
    aspect: string      // 格式 \d+:\d+, 如 "16:9"
  }
}
```

### WidgetRefresh

```ts
{
  intervalMs?: number     // ≥1000, 覆盖 dashboard default
  strategy?: 'data-only' | 'full-rerender'
}
```

### 完整 JSON 示例（电商 dashboard，5 widgets）

```json
{
  "schemaVersion": 2,
  "id": "dash_ecom_demo01",
  "title": "电商运营实时监控中心",
  "description": "实时 GMV / 转化漏斗 / 热销排行 / 区域热力图",
  "defaultConnectionId": "mysql-prod",
  "theme": "industry-ecommerce",
  "renderer": "bezel",
  "refresh": {
    "defaultIntervalMs": 5000,
    "pauseOnHidden": true
  },
  "parameters": [
    {
      "id": "global:date_range",
      "scope": "global",
      "name": "日期范围",
      "type": "date_range",
      "default": ["2026-05-01", "2026-05-11"]
    }
  ],
  "widgets": [
    {
      "id": "chart_w_gmv01",
      "type": "chart",
      "patternId": "ecommerce.gmv-trend",
      "position": { "x": 0, "y": 0, "w": 8, "h": 4 },
      "query": {
        "sql": "SELECT DATE(order_time) AS dt, SUM(amount) AS gmv FROM orders WHERE order_time BETWEEN ? AND ? GROUP BY dt ORDER BY dt",
        "paramRefs": { "dt_start": "global:date_range[0]", "dt_end": "global:date_range[1]" }
      },
      "refresh": { "intervalMs": 3000 },
      "options": {}
    },
    {
      "id": "kpi_w_total",
      "type": "kpi",
      "patternId": "ecommerce.gmv-kpi",
      "position": { "x": 8, "y": 0, "w": 4, "h": 2 },
      "query": {
        "sql": "SELECT SUM(amount) AS total_gmv, COUNT(*) AS order_count FROM orders WHERE DATE(order_time) = CURRENT_DATE"
      },
      "options": { "unit": "¥", "label": "今日 GMV" }
    },
    {
      "id": "chart_w_funnel",
      "type": "chart",
      "patternId": "ecommerce.funnel-gradient",
      "position": { "x": 0, "y": 4, "w": 4, "h": 4 },
      "query": {
        "sql": "SELECT stage, COUNT(*) AS cnt FROM funnel_events WHERE DATE(ctime) = CURRENT_DATE GROUP BY stage ORDER BY seq"
      },
      "options": {}
    },
    {
      "id": "chart_w_heatmap",
      "type": "chart",
      "patternId": "ecommerce.heatmap",
      "position": { "x": 4, "y": 4, "w": 4, "h": 4 },
      "query": {
        "sql": "SELECT province, category, SUM(amount) AS amount FROM orders GROUP BY province, category"
      },
      "options": {}
    },
    {
      "id": "table_w_top",
      "type": "table",
      "patternId": "generic.table",
      "position": { "x": 8, "y": 2, "w": 4, "h": 6 },
      "query": {
        "sql": "SELECT product_name, SUM(qty) AS qty, SUM(amount) AS revenue FROM order_items GROUP BY product_name ORDER BY revenue DESC LIMIT 20"
      },
      "refresh": { "intervalMs": 10000 },
      "options": {}
    }
  ],
  "layout": {
    "engine": "free",
    "viewport": { "minWidth": 1280, "aspect": "16:9" }
  },
  "version": 1,
  "createdAt": 1747000000000,
  "updatedAt": 1747000000000
}
```

## 2. WidgetQuery 约束

### SQL 安全规则

1. **仅允许单条 SELECT 语句** — 不允许 DDL/DML/DDL/admin commands。服务端 `SqlStatementGuard.assertReadOnlySelect()` 会在执行前拦截。
2. **参数化查询** — 通过 `paramRefs` 将 dashboard 参数映射到 SQL 参数，服务端使用 JDBC `PreparedStatement` 的 `?` 占位符绑定，杜绝 SQL 注入。
3. **列名约定** — 每个 widget pattern 在对应的 `industries/*.md` 中声明它期待的列名和类型。AI 生成 SQL 时必须匹配这些列名，否则 ECharts option 模板中的映射会断裂。

### 参数解析流程

```
1. Host 发送 { type: 'params/update', params: { 'global:date_range': ['2026-05-01', '2026-05-11'] } }
2. Polling 调度器更新 cfg.widgets[].params
3. 发起 POST /api/dashboards/{id}/widgets/{wid}/data，body = { params: w.params }
4. 服务端 resolveParams(): 按 widget.query.paramRefs 反查 dashboard.parameters + widget.parameters 的 defaultValue
5. JDBC PreparedStatement.setString(N, resolvedValue)
6. 执行查询，返回 { columns, rows, executedAt }
```

## 3. Polling 协议

### `window.__BEZEL_CONFIG__` 定义

HTML 内嵌一个 `<script>` 标签：

```html
<script>
window.__BEZEL_CONFIG__ = {
  dashboardId: 'dash_xxx',
  defaultIntervalMs: 10000,
  pauseOnHidden: true,
  widgets: [
    {
      id: 'chart_w_xxx',
      intervalMs: 5000,
      endpoint: '/api/dashboards/dash_xxx/widgets/chart_w_xxx/data',
      params: { dt: '2026-05-11' }
    }
  ]
};
</script>
```

### Polling 调度器行为

```
1. 读取 window.__BEZEL_CONFIG__
2. 对每个 widget:
   a. document.getElementById(widget.id) → ECharts.init(el)
   b. 启动 setInterval / setTimeout 循环:
      - 暂停中 → 跳过本轮，等下一轮
      - 未暂停 → fetch(widget.endpoint, POST, { params: widget.params })
      - 成功 → ch.setOption({ dataset: { source: data.rows } }, { lazyUpdate: true })
      - 失败 → 保留上一帧数据，postMessage({ type: 'error', ... }) 给 host
3. document.visibilitychange → 切换 paused 状态
4. window message listener → 接收 host 的 params/update / refresh/pause / refresh/resume
5. 初始化完成后 postMessage({ type: 'ready', jsonHash }) 给 host
```

## 4. 错误处理

### Widget 级错误（polling 失败）

- **策略**: 保留上一帧 ECharts 数据，不 clear 不 dispose
- **视觉反馈**: Widget 右上角叠加一个橙色 `⚠` 图标，hover 显示 tooltip 含：
  - HTTP 状态码（如 `503 Service Unavailable`）
  - 最后成功时间
- **恢复**: 下一次 poll 成功后自动移除错误图标

### 全页面错误（HTML 启动失败）

- **触发场景**: ECharts CDN 加载失败、`__BEZEL_CONFIG__` 缺失、JS 执行异常
- **上报**: iframe 内 `window.onerror` → `parent.postMessage({ type: 'error', widgetId: '__root__', message: ... }, '*')`
- **Host 处理**: `iframe-shell.tsx` 的 `onError` 回调收到后展示全页面错误状态

## 5. `window.__BEZEL_CONFIG__` 完整 Schema

```ts
interface BezelConfig {
  /** Dashboard ID，与 API endpoint 路径一致 */
  dashboardId: string

  /** 全局默认轮询间隔 ms，widget 未指定时使用 */
  defaultIntervalMs: number

  /** 页面不可见时是否暂停轮询 */
  pauseOnHidden: boolean

  /** Widget 轮询配置列表 */
  widgets: BezelWidgetConfig[]
}

interface BezelWidgetConfig {
  /** Widget DOM id，同时是 ECharts 容器 id */
  id: string

  /** 轮询间隔 ms，覆盖 defaultIntervalMs */
  intervalMs: number

  /** 数据 API endpoint，格式 /api/dashboards/{id}/widgets/{wid}/data */
  endpoint: string

  /** 附加参数，随 POST body 发送 */
  params: Record<string, unknown>
}
```

## 6. postMessage 协议

Host（data-talk 前端）与 iframe（bezel HTML）通过 `window.postMessage` 双向通信。

### Host → Iframe

```ts
type HostToIframe =
  | { type: 'params/update'; params: Record<string, unknown> }
  | { type: 'refresh/pause' }
  | { type: 'refresh/resume' }
```

| 类型 | 触发场景 | 行为 |
|---|---|---|
| `params/update` | 用户在 dashboard chrome 中修改参数 | 合并 `cfg.widgets[].params` |
| `refresh/pause` | 用户点击暂停按钮 / 切到其他 tab | `paused = true` |
| `refresh/resume` | 用户恢复 / 切回 tab | `paused = false` |

### Iframe → Host

```ts
type IframeToHost =
  | { type: 'ready'; jsonHash: string | null }
  | { type: 'error'; widgetId: string; message: string }
  | { type: 'metric'; name: string; value: number }
```

| 类型 | 触发场景 | Host 行为 |
|---|---|---|
| `ready` | HTML 初始化完成，ECharts 就绪 | `iframe-shell.tsx` 将 status 从 `loading` 切为 `ready`；记录 `jsonHash` 用于 diff |
| `error` | Widget polling 失败 / 全页面异常 | `iframe-shell.tsx` 调用 `onError({ widgetId, message })`，chrome 展示错误提示 |
| `metric` | 每次 poll 成功（可选） | 可用于调试 / 性能监控，生产可忽略 |

### 消息校验

Host 侧使用 `iframe-protocol.ts` 的 `isIframeToHost()` 类型守卫过滤非法消息：

```ts
export function isIframeToHost(x: unknown): x is IframeToHost {
  if (!x || typeof x !== 'object') return false
  const t = (x as { type?: unknown }).type
  return t === 'ready' || t === 'error' || t === 'metric'
}
```

iframe 侧同理，只处理已知的 `HostToIframe` type 值。
