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

## 2. `source` 字段标注（强制）

**规则**：每个 `table` / `chart` / `kpi-strip` block 旁的 `source` 字段必须标注数据来源。

**格式**：`<connection-name> · <table-or-summary-name>`，可选附 ` as of <ISO-date>`。

### ✓ 正面例子

```json
{
  "type": "chart",
  "echartsOption": { ... },
  "caption": "2026 年 4 月 GMV 渠道分布",
  "source": "mysql-prod · sales_summary as of 2026-04-30"
}
```

```json
{
  "type": "kpi-strip",
  "items": [
    { "label": "总 GMV", "value": "¥3.2M", "delta": "+18%" }
  ],
  "source": "mysql-prod · sales_summary as of 2026-04-30"
}
```

### ✗ 反面例子

```json
{
  "type": "table",
  "columns": ["渠道", "GMV"],
  "rows": [["自营", "1.2M"]]
  // 缺 source — 数据来源不明，审计不可追溯
}
```

HTML 渲染时，`source` 字段会出现在 block 下方小字脚注（CSS class `ledger-block-source`），供阅读者快速核对数据出处。

## 3. 大表附录 CSV 两步交互（强制）

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
  "source": "mysql-prod · sku_sales as of 2026-04-30",
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
