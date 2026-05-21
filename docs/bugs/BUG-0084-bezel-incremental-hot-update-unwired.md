# BUG-0084: Bezel 增量热更新链路未接通（客户端忽略 changes + Differ 丢 chartSemantics/query）

| Field | Value |
|-------|-------|
| ID | BUG-0084 |
| Status | open |
| Severity | low |
| Priority | P2 |
| Module | bezel-compiler / dashboard |
| Discovered | 2026-05-21 |
| Discoverer | code review（E2E 数据流排查） |
| Source | code-review |
| FixPlanRef | bezel-compiler-redesign |

## Summary

bezel 重设计的目标之一是"<2s 多轮热更新"——服务端 `DashboardDiffer` 算出增量 `changes`，`/update` 返回，客户端经 `widget/update` postMessage 推给 iframe 做增量替换，避免整页重载。该链路当前**未接通**，靠 version 变化触发 iframe 整页重载兜底（正确性 OK，但未达 <2s 增量目标）。

## Root Cause

1. **客户端忽略 `changes`**：`client/src/features/dashboard/dashboard-frame.tsx` 导出的 `sendWidgetUpdate`（发 `widget/update` postMessage）**无任何生产调用方**；`DashboardAdapter.patch` 调 `updateDashboard` 后只用 `result.version`，丢弃 `/update` 响应里的 `changes` / `html`。`dashboard-frame.tsx` 注释"Full reload when dashboardId or version changes"——即靠 version 变化整页重载。
2. **`DashboardDiffer.toWidget` 丢字段**：`DashboardDiffer.toWidget(JsonNode)` 把 `chartSemantics` 和 `query` 都传 `null`（见该文件 ~L110），因此即使客户端接通，增量算出的 `WidgetChange.baseOption` 会按默认 chartType（bar）重编译，丢失真实图表类型/语义。

> 注：`DashboardCompiler.toWidget` 已在 BUG-0083 修复中补齐 `query`/`refresh` 解析，但 `DashboardDiffer` 有**独立的** `toWidget`，未同步。

## Impact

- 多轮迭代目前走整页重载：功能正确（已 E2E 验证：改 KPI→2000 重载反映），但每轮重新拉全量 HTML + 重新 init 所有图表，达不到设计的 <2s postMessage 增量。
- 增量路径（`DashboardDiffer`）当前是 dead path（无客户端消费），其 chartSemantics/query 丢失为休眠缺陷，一旦接通会立刻暴露。

## Fix Plan（未实施）

1. 客户端：`DashboardAdapter`（或 patch 回调）消费 `/update` 的 `changes`，对每个变更 widget 调 `sendWidgetUpdate(iframe, change)`；仅当 `needsFullRebuild` 时才整页重载。需走 `client/DESIGN.md` gate。
2. 服务端：`DashboardDiffer.toWidget` 补齐 `chartSemantics` + `query`（复用 / 抽取 `DashboardCompiler` 的解析逻辑，避免两份 toWidget 漂移）。
3. 加端到端测试覆盖增量路径（postMessage → iframe 图表 setOption(notMerge)）。

## Evidence

- `grep -rn sendWidgetUpdate client/src` → 仅定义 + 测试，无生产调用方。
- `DashboardAdapter.patch` 仅用 `result.version`。
- `DashboardDiffer.toWidget` 第 ~110 行 `null` chartSemantics + `null` query。
