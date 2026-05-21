---
id: BUG-0085
title: 重设计前 promote 的存量模板大屏 KPI/表格永不加载数据（旧内联脚本数据形状与接口契约不匹配）
status: wontfix
priority: P2
source: e2e-playwright
modules: [bezel-compiler, dashboard]
discovered: 2026-05-21
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary
bezel 编译器重设计（c149569b）之前 promote 的存量大屏，其存储 HTML 携带的是**旧模板内联轮询脚本**（非外置 `scheduler.js`）。该内联脚本在 `applyHtmlData('kpi', ...)` 里把 `rows[0]` 当**对象**取值（`row.value` / `row.label`），并对 chart 直接 `setOption({dataset:{source:data.rows}})`、对 table 用 `r[c]` keyed 取值；但 widget data 接口返回的是 `{columns:[...], rows:[[...]]}`（列名 + 二维数组）。形状不匹配 → KPI 永远停在占位 `---`、表格/图表数据也无法正确填充。

与 [[BUG-0083]] 区分：BUG-0083 是**当前编译器**路径（外置 scheduler.js）的"无首次取数 + intervalMs 不下传"，已修复并验证；本 BUG 是**遗留路径**（旧内联脚本）的数据形状契约不匹配，根因与代码路径都不同。

## Reproduction Steps
1. 后端含重设计后代码（schemaVersion 3 编译器）。
2. 浏览器打开一个重设计前 promote 的存量大屏：`GET /api/dashboards/dash_g2t35tkk/html`（"电商运营实时监控中心"，15 widget）。
3. 观察 KPI 卡片。

## Expected vs Actual
- **Expected**: KPI 显示真实数值（如总订单数 1248），表格/图表填充数据。
- **Actual**: 所有 KPI 停在 `---`；widget data POST 返回 200 且响应体含数据（`{"columns":["label","value"],"rows":[["总订单数",1248]]}`），但内联脚本因 `rows[0].value === undefined` 不写入 DOM。

## Environment
- Backend commit: 0544893a（重设计后，本地重建 + 重启）
- Frontend commit: develop / 0544893a
- OS / Browser: WSL2 / Chromium (playwright-cli)
- Data source: 大屏内嵌 query（存量大屏，连接随其 JSON）

## Evidence
- DOM 断言：`document.getElementById('kpi_w_total_orders')` 内为 `<div class="kpi-block"><div class="value">---</div><div class="label">总订单数</div></div>`；`rows[0]=["总订单数",1248]`（数组），`row.value` 为 undefined。
- 存量大屏 HTML 不含 `/bezel/scheduler.js` 引用（`grep -c scheduler = 0`），用旧内联 `applyHtmlData`。
- 受影响存量大屏：`dash_g2t35tkk` / `dash_wg3snhnw` / `dash_g4aos0mt` / `dash_po7q38tt` / `dash_x0gjsp5m` / `dash_jeyy4fmn`（maintenance 列表 6 个，均 "电商运营实时监控中心"）。

## Root Cause
重设计把运行时从"模板内联脚本"切到"外置 `scheduler.js` + `WidgetCompiler` 产出（`.kpi-value` + `colIndex(columns,'value')` 列名取数）"。`/html` 接口走 `dashboardService.loadHtml(id)` **直接 serve 存储的 HTML，不按当前编译器重编译**，因此存量大屏永远停留在旧内联脚本，其 `rows[0].value` 对象取数假设与接口的 `columns/rows[][]` 不兼容。

## Fix
**Resolution: wontfix（按设计废弃 v2 + 清理遗留工件，不写代码兼容）。**

产品决策（2026-05-21，用户确认「直接删除即可，老的无需保留」）：bezel 编译器重设计是断代式重写，schemaVersion 2（position 布局）已被 v3（slot+template）取代且无迁移代码；这 6 个 v2 大屏均为开发期测试数据（同名「电商运营实时监控中心」），无真实用户依赖。

执行的清理动作：删除全部 6 个 v2 存量大屏（`dash_g2t35tkk` / `dash_wg3snhnw` / `dash_g4aos0mt` / `dash_po7q38tt` / `dash_x0gjsp5m` / `dash_jeyy4fmn`，均 `DELETE /api/maintenance/dashboards/{id}` → 204）。

未采纳的方案及原因：
- 迁移（v2→v3 position→slot）：为 6 个测试大屏构建布局迁移器不成比例。
- serve 时字符串补丁旧内联脚本：对存储 HTML 做正则改写，hacky 且只为废弃路径续命。
- 未加 `schemaVersion<3` 服务端守卫：v2 promote 已被 v3 schema 校验拒绝，无法再产生新 v2 大屏，删除存量后该场景不可能复现（避免为不会发生的情况加防御代码）。

## Verification
删除后 `GET /api/maintenance/dashboards` 返回空列表，无 v2 大屏可被 `/html` 静默 serve 出坏渲染。新建大屏一律走 v3 编译器（外置 scheduler.js + `columns/rows[][]` 正确取数），渲染正确性已由 [[BUG-0083]] 复验覆盖（KPI=1248 / 表格 / chart 全渲染）。

## Notes
- 本 BUG 在验证 [[BUG-0083]] 时旁路发现：选取 maintenance 已有大屏作为验证目标，才暴露存量大屏走的是遗留内联脚本路径。最终 BUG-0083 改用当前编译器新 promote 的大屏（`dash_eaj4eeze`）验证通过。
- 数据接口本身对 v2 widget 正常（v2 的 `query.sql` 与 v3 同构，新后端仍返回 `{"columns":["label","value"],"rows":[["总订单数",1248]]}`）；偏差纯在 v2 存储 HTML 的旧内联 `applyHtmlData`（`rows[0].value` 对象取数）。
- 若未来出现真实用户的 v2 大屏需保留，应重开新 BUG 并评估 v2→v3 迁移路径（本 BUG 不复用）。
