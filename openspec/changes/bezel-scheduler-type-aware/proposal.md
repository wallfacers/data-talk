## Why

bezel skill 的三份契约文档(`patterns-catalog.md` / `data-contract.md` / `compile-rules.md`)对"polling scheduler 如何初始化 widget"的描述互相矛盾,导致 AI 每次生成大屏都会复现两个运行时 bug:

1. **KPI / table / markdown 等 HTML-only widget 被无差别 `echarts.init()`** — `patterns-catalog.md:75-79` 说 `generic.kpi-tile` 是 HTML-only,但 `compile-rules.md:175` 硬性要求 "**For every widget** w in the config, the polling scheduler **must** call `echarts.init(el)`",`data-contract.md:228-240` 的调度器算法同样无差别 init。12 份 template 里的 `bindWidget` 实现完全照搬。AI 学到的是"对每个 widget 都 init",运行时一旦 widget 列表混入 HTML-only 类型就出错或污染容器。
2. **chart 缺少首屏 base option** — `SKILL.md:121` / `compile-rules.md:464` 写"chart option 在 pattern initialization 时设置一次,scheduler 只更新 dataset",但调度器算法里**没有 pattern initialization 步骤**,直接 `init → fetch → setOption({dataset})`。ECharts 收到只有 `dataset` 没有 `series` / `xAxis` 的 option,根本不知道怎么渲染数据,空图表。

更糟的是,`BezelWidgetConfig` 的 schema(`data-contract.md:275-287`)只有 `id / intervalMs / endpoint / params` 四个字段,**连 `type` 和 `options` 都没有**。即便 AI 知道要按 type 分流,运行时也拿不到这两个字段做判断。这是 spec 层的系统性缺陷,不是单次会话的 typo 修复能解决的。

## What Changes

- **BREAKING(契约)**: `BezelWidgetConfig` 新增两个必填字段:
  - `type: 'chart' | 'kpi' | 'table' | 'markdown' | 'filter' | 'section' | 'divider' | 'image'`
  - `baseOption: object | null` —— chart 类型 SHALL 非空(用于首屏 `setOption`),非 chart 类型 SHALL 为 `null`
- **BREAKING(算法)**: `data-contract.md` 的 polling scheduler 算法重写为按 `widget.type` 分流:
  - `type === 'chart'` → `echarts.init(el) → ch.setOption(w.baseOption) → schedule()`
  - 其他类型 → 走 HTML 渲染分支,**不调用 `echarts.init`**;若 `intervalMs > 0` 也走 `schedule()`,刷新时通过 `applyHtmlData(el, data.rows)` 而非 `setOption`
- `compile-rules.md` 删除 "For every widget … must call echarts.init" 这条硬性规则,改成 "for every widget where type === 'chart'"
- `compile-rules.md` 增加 "Pattern initialization" 章节,明确编译期 `DashboardArtifactService` 须把每个 chart widget 的 `widget.options` 拷贝到 `BezelWidgetConfig.baseOption`(作为运行时首屏 option)
- `patterns-catalog.md` 给每个 generic widget 标注 `renderKind: 'chart' | 'html'`,与新 `type` 字段对齐
- 12 份 `assets/templates/*.html` 的 `bindWidget` 实现按新算法重写,作为 AI 的 few-shot 正例
- `scripts/validate.py` 增加校验:`type === 'chart'` 必有 `baseOption`,且 `baseOption` 至少含 `series` 或 `xAxis`/`yAxis` 其一;非 chart 类型 `baseOption` 必为 `null`
- 后端 `DashboardArtifactService` 在 JSON → HTML 编译时,把 `widget.type` 和 `widget.options` 注入到 `__BEZEL_CONFIG__.widgets[].type` / `baseOption`

## Capabilities

### New Capabilities
- `bezel-scheduler-contract`: bezel iframe 内 polling scheduler 的运行时契约 —— `BezelWidgetConfig` schema、初始化算法、首屏 option 装配、错误兜底、HTML-only widget 刷新路径

### Modified Capabilities
(无 —— `bezel-scheduler-contract` 之前从未作为 canonical spec 落地;现有规则散落在 skill 内的 reference markdown 中)

## Impact

- **bezel skill**: `references/patterns-catalog.md` / `references/data-contract.md` / `references/compile-rules.md` 三份契约文档同步重写;12 份 `assets/templates/*.html` 重写 `bindWidget`;`scripts/validate.py` 增加 type/baseOption 校验
- **后端 `DashboardArtifactService`**: JSON → HTML 编译路径增加 `widget.type` / `widget.options` → `__BEZEL_CONFIG__.widgets[].type` / `baseOption` 注入
- **运行时 iframe**: 历史 dashboard JSON 在 schema migration 时,后端默认按 `widget.type` 推导;`options` 字段直接拷为 `baseOption`(chart),非 chart 时强制 `null`
- **前端 host 页**: `iframe-protocol.ts` 无需改动 —— postMessage 协议未变
- **关联 BUG**: BUG-0050(大屏 JSON 模式 widget 仅渲染骨架)/ BUG-0051(iframe 长时间白屏)同源,本 change 一并收敛根因。新登记 BUG-0055 跟踪本次缺陷
- **数据源类型兼容性**: N/A —— 本 change 不触达 JDBC / DDL / SQL 解析路径,仅修改 bezel 编译期与运行时算法
- **客户端 DESIGN.md**: N/A —— bezel 是 iframe sandbox,不消费 `client/DESIGN.md` 设计 token
