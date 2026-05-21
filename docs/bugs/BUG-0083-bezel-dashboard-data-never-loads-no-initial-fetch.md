# BUG-0083: Bezel 大屏数据永不加载（defaultIntervalMs 不下传 + 无首次取数）

| Field | Value |
|-------|-------|
| ID | BUG-0083 |
| Status | fixed |
| Severity | high |
| Module | bezel-compiler |
| Discovered | 2026-05-21 |
| Discoverer | code review（E2E 数据流排查） |
| Source | code-review |
| FixPlanRef | bezel-compiler-redesign |

## Summary

即便修掉 BUG-0082（GET→POST），编译后的大屏数据仍加载不出来，根因有二：

1. **`defaultIntervalMs` 不向下继承**：`WidgetCompiler.buildConfigEntry` 只读 `widget.refresh.intervalMs`，未给"没设 per-widget refresh"的 widget 套用 dashboard 级 `refresh.defaultIntervalMs`。AI 生成的 widget 通常不带 per-widget refresh → 编译出 `intervalMs=0` → `scheduler.startPolling` 直接 return，永不轮询。
2. **没有首次取数**：`scheduler.js` 只在 `setInterval` 里取数，`init()` 不做一次立即拉取。即使 `intervalMs>0`，首屏也要等满一个周期（如 10s）才出数据；`intervalMs=0` 时则永远是骨架。

由于客户端的父级推送路径（`sendWidgetUpdate` / `widget/update` postMessage）当前**未接通**（见下方"关联"），iframe 自轮询是数据的唯一来源，所以上述两点直接导致"大屏只有骨架、无数据"——等同 BUG-0050 在新编译器下复发。

## Root Cause

- `WidgetCompiler.java`：`intervalMs` 仅来自 widget 级，无 dashboard 级 fallback；且对静态件（markdown/divider/...）也照设 endpoint，若无差别轮询会刷 400。
- `scheduler.js`：`init()` → `startPolling()` 仅 `setInterval`，无 immediate fetch。

golden/单元测试只编译 HTML、不在浏览器执行 scheduler，故未捕获（同 BUG-0081/0082 盲区）。

## Fix Applied

- `WidgetCompiler.compile(...)` 新增 `int defaultIntervalMs` 形参（`DashboardCompiler` 从 `dashboard.refresh.defaultIntervalMs` 取，`DashboardDiffer` 从 `newJson` 取）。
- `buildConfigEntry`：计算 `hasData`（widget 是否带 `query.sql`）；仅 `hasData` 时设 `intervalMs`（widget 级覆盖 → 否则继承 `defaultIntervalMs`）；config entry 新增 `hasData` 标志。
- `scheduler.js`：抽出 `fetchWidget(widget)`（POST）；`init()` 对 `hasData` 的 widget 立即取数一次；`startPolling` 以 `hasData` + `intervalMs>0` 为门控，静态件不再轮询。

## 关联 / 未闭环（独立跟踪）

- **BUG-0082**：同族，scheduler GET 轮询 vs POST 接口（已修）。
- **BUG-0084**：客户端 `widget/update` postMessage 增量热更新链路未接通（`sendWidgetUpdate` 无调用方、`DashboardAdapter` 忽略 `changes`），且 `DashboardDiffer.toWidget` 丢 chartSemantics/query。靠整页重载兜底，未达 <2s 目标。已单列跟踪。

## Evidence

- `WidgetCompiler.java` 原 `buildConfigEntry`：`intervalMs` 仅 `widget.refresh().intervalMs()`。
- `scheduler.js` 原 `init()` 无 immediate fetch。
- E2E：promote 的大屏所有 widget config `intervalMs=0`。
