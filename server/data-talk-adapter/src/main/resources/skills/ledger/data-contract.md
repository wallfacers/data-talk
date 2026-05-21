# Ledger Data Contract — AI 取数与 promote 数据契约

本文档定义 AI 在生成报告时**必须**遵守的数据获取与组织规则。违反这些规则会导致 promote 失败或报告内容缺失审计字段。

## 1. 跨连接显式 `connectionId`（强制）

**规则**：每次调用 `datatalk_query_data` 时**必传 `connectionId` 参数**，不依赖 session 默认 connection。任何在 ledger 生成报告流程中省略 `connectionId` 的 query MUST 被 AI 自己识别为错误并修正后重试。

**为什么**：

- 一份报告通常跨多个数据源（如 mysql-prod 取销售、postgres-ods 取库存、redis-metrics 取曝光）
- session 默认 connection 在跨源场景下没有意义
- 数据来源审计要求每条 SQL 都能精确追溯到具体 connection

### ✓ 正确示例

```
datatalk_query_data(
  connectionId: "conn-mysql-prod-7d4a",
  sql: "SELECT channel, SUM(gmv) as gmv FROM sales WHERE date BETWEEN '2026-04-01' AND '2026-04-30' GROUP BY channel"
)
```

### ✗ 错误示例

```
// 省略 connectionId，依赖 session 默认 — 拒绝接受这种模式
datatalk_query_data(
  sql: "SELECT channel, SUM(gmv) FROM sales GROUP BY channel"
)
```

发现自己在生成的 tool call 里省略了 `connectionId`，**必须撤回并重新生成带 connectionId 的版本**。

## 2. 大表附录 CSV 两步交互（强制）

**规则**：单个 `table` block 的 `rows` 数组**不得超过 200 行**。超过时**必须**使用两步交互：

1. **第一步**：调用 `datatalk_export_data(format='csv', sql=<完整 SQL>, connectionId=...)` 导出完整数据，得到返回的 `{ fileArtifactId }`
2. **第二步**：把该 `fileArtifactId` 写入 table block 的 `appendixCsvRef` 字段；inline `rows` 只保留前 N 行（N ≤ 200，建议 50-100 行做预览）

### ✓ 正面例子（完整）

```
// Step 1: 先 export 完整数据
result = datatalk_export_data(
  connectionId: "conn-mysql-prod-7d4a",
  format: "csv",
  sql: "SELECT * FROM sku_sales WHERE month='2026-04' ORDER BY gmv DESC"
)
// result = { fileArtifactId: "fa-csv-9e3d4..." }

// Step 2: 把 fileArtifactId 写入 table block，inline 只放前 50 行预览
{
  "type": "table",
  "columns": ["sku_id", "name", "gmv", "qty"],
  "rows": [/* 前 50 行 */],
  "caption": "Top 50 SKU 销售明细（完整 800 行见附录）",
  "appendixCsvRef": "fa-csv-9e3d4..."
}

// Step 3: 一次性 promote 整份 report.json（含 appendixCsvRef 引用）
datatalk_promote_report(report: {...}, workspaceId: "ws-1")
```

### ✗ 反面例子

```json
// 直接 inline 800 行数据 — 会被服务端拒绝（REPORT_TABLE_OVERSIZE_NO_APPENDIX）
{
  "type": "table",
  "columns": ["sku_id", "name", "gmv", "qty"],
  "rows": [
    /* 800 rows... */
  ]
  // 缺 appendixCsvRef
}
```

## 3. 渲染必填字段（promote 会硬校验，错填即拒绝）

**规则**：以下字段名 / 形状写错会让 block 渲染成**空白**（不报错、静默丢失），因此 promote 时会被**直接拒绝**并返回对应 `errorCode` + `recoveryHint`。这些是最容易写错的点，**生成前逐条核对字段名**：

| block | 必填字段（正确写法） | 常见错误（会被拒） | errorCode |
|---|---|---|---|
| `narrative` | `markdown`（非空字符串） | 写成 `content` / `text` | `REPORT_NARRATIVE_INVALID` |
| `executive-summary` | `bullets`（非空 string[]） | 写成 `blocks` | `REPORT_EXECSUMMARY_INVALID` |
| `kpi-strip` | `items`（非空数组，每项 `label`+`value`） | items 为空 / 字段名错 | `REPORT_KPISTRIP_INVALID` |
| `risk-list` | `items`（数组，每项 `severity`∈{critical,high,medium,low}+`description`） | 写成 `risks` + `level` | `REPORT_RISKLIST_INVALID` |
| `chart` | `echartsOption`（对象，含 inline 数据） | 缺 `echartsOption` | `REPORT_CHART_INVALID` |
| `table` | `columns`: `string[]`、`rows`: `string[][]` | 写成对象数组（`[{...}]`） | `REPORT_TABLE_SHAPE_INVALID` |

**`chart.id` 例外**：可以不写，服务端会自动补全唯一 id（`ch-auto-N`）。写了则保留。

### ✗ 反面例子（每条都会被拒）

```json
{ "type": "narrative", "content": "..." }                         // 应为 markdown
{ "type": "executive-summary", "blocks": ["..."] }                // 应为 bullets
{ "type": "risk-list", "risks": [{"level":"high","desc":"..."}] } // 应为 items + severity + description
{ "type": "table", "columns": [{"name":"渠道"}], "rows": [{"渠道":"自营"}] } // columns/rows 应为 string[] / string[][]
```

## 4. 数据冻结时刻（重要语义）

**规则**：promote 是"冻结快照"动作。一旦 promote，报告中的数据不再变化——即使源数据库下一秒更新，报告里的数字也保持不变。

**含义**：

- 每个 `chart` / `table` / `kpi-strip` 的数据**必须**在 promote 时已经全部取齐
- 不允许 `chart` block 只放 `sql` 引用而不放 `echartsOption.dataset`——HTML 渲染时不会去数据库取数
- "重新生成"是新建一份报告（新 version），不是在原报告上更新数据

## 5. `report.json` 顶层字段

```json
{
  "schemaVersion": 1,
  "kind": "report",
  "meta": {
    "title": "2026 年 4 月销售月报",
    "subtitle": "全渠道 GMV 与品类表现",
    "author": "数据分析团队",
    "generatedAt": "2026-05-19T10:00:00+08:00",
    "templateId": "ledger.monthly-business-review.v1",
    "templateVersion": "v1",
    "userPrompt": "做一份 2026 年 4 月销售月报"
  },
  "theme": {
    "accent": "#1f4e79"
  },
  "sections": [
    { "type": "cover", ... },
    { "type": "executive-summary", ... },
    { "type": "toc" },
    { "type": "chapter", "heading": "业务总览", "blocks": [...] },
    ...
  ],
  "appendix": [
    { "type": "appendix", "subType": "sql-listing", ... }
  ]
}
```

- `schemaVersion` 当前固定 `1`。未来 schema 升级会引入 `2`，旧 client 不再兼容
- `kind` 必须等于 `"report"`，等于 `"dashboard"` 或其它会被拒绝
- `meta.userPrompt` 选填但**强烈推荐**带上：用户原始诉求，服务端入库到 `user_prompt` 字段供"重新生成"链路复用
- `theme.accent` 单一品牌强调色（hex），默认 `#1f4e79`（经典深蓝）。详见 `design-language.md`

## 6. `datatalk_promote_report` 错误返回结构

校验失败或 promote 异常时，服务端返回带 `error` 字段的对象（**含 `error` 字段即视为失败**，不要从 HTTP 状态码判断）。结构：

```json
{
  "error": "validation failed: meta.title is required; sections array must be non-empty",
  "errorCode": "REPORT_META_MISSING",
  "errorCodes": ["REPORT_META_MISSING", "REPORT_SECTIONS_MISSING"],
  "violations": [
    { "code": "REPORT_META_MISSING", "path": "meta.title", "message": "meta.title is required" },
    { "code": "REPORT_SECTIONS_MISSING", "path": "sections", "message": "sections array must be non-empty" }
  ],
  "recoveryHints": {
    "REPORT_META_MISSING": "把 title / templateId 等必填字段放进顶层 meta 对象",
    "REPORT_SECTIONS_MISSING": "顶层用非空的 sections 数组而不是 blocks"
  }
}
```

字段含义：

| 字段 | 类型 | 说明 |
|---|---|---|
| `error` | string | 全部 violation 的 message 拼接，便于人读 |
| `errorCode` | string | 第一个 violation 的 code，**保留向后兼容**（仍按单错误码消费的客户端用此字段） |
| `errorCodes` | string[] | 本次响应中**全部**违规 code 的列表，可能重复（如多个字段触发同一 code） |
| `violations` | array | 每条 violation：`code`（稳定字符串）、`path`（JSON 路径如 `meta.title` / `sections[3].blocks[1]`）、`message`（人读说明） |
| `recoveryHints` | object | 仅对本次响应出现的 code 给出修复提示文案（中文） |

**AI 处理流程**：

1. 看到 `error` 字段就视为失败；不要按 HTTP 200 / 200 OK 判定为成功。
2. 一次性遍历 `violations[]`，按 `path` 精确定位 report.json 节点；按 `recoveryHints[code]` 提示**一轮内**修齐所有违规后重试。
3. 连续失败 2 次后 MUST 停止并把 violations 列表告诉用户（见 `SKILL.md` 兜底段落）。
4. 校验失败的 retry MUST NOT 带 `groupId`。仅"重新生成"语义才带。

常见 code 与典型修复方向（完整 code 集合见 `ReportSchemaValidator.RECOVERY_HINTS`）：

- `REPORT_META_MISSING` → 把 title / templateId 等必填字段放进顶层 `meta` 对象
- `REPORT_SECTIONS_MISSING` → 顶层用非空 `sections` 数组而不是 `blocks`
- `REPORT_BLOCK_TYPE_UNKNOWN` → block 的 `type` 必须是 ALLOWED_BLOCK_TYPES 集合内的值
- `REPORT_TABLE_OVERSIZE_NO_APPENDIX` → table 超过 200 行：先 `datatalk_export_data` 拿 `fileArtifactId`，写到 `appendixCsvRef`，inline `rows` 截断到前 N 行
