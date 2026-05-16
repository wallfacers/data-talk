---
id: BUG-0055
title: bezel polling scheduler 对所有 widget 无差别 echarts.init,且 chart 缺首屏 base option
status: open
priority: P1
source: agent-generated-dashboard
modules: [bezel, dashboard]
discovered: 2026-05-17
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: openspec/changes/bezel-scheduler-type-aware/
duplicateOf: null
regression: false
---

## Summary

bezel skill 内 polling scheduler 的运行时算法和 skill 契约文档互相打架,导致 AI 生成的大屏存在两个稳定可复现的运行时缺陷:

1. **HTML-only widget(`generic.kpi-tile` / `generic.table` / `generic.markdown` 等)被错误地 `echarts.init()`**。运行时 ECharts 把一个本应纯 HTML 渲染的 DOM 容器初始化为 canvas 容器,KPI 数字消失或被空白 canvas 覆盖。
2. **chart 类型 widget 缺少首屏 base option**。调度器算法只在轮询时调用 `ch.setOption({ dataset: { source: rows } }, { lazyUpdate: true })`,而 ECharts 缺 `series` / `xAxis` / `yAxis` 信息根本不知道怎么渲染数据,出现"chart 容器存在但空白"现象。

## Reproduction Steps

1. 让项目内的 AI 生成一个包含 KPI 和 chart 混合的大屏(任意行业 prompt 都能复现)
2. 大屏 iframe 加载后观察:
   - KPI 卡片显示异常(数字不显示 / 容器被 canvas 覆盖)
   - chart 容器存在但首屏空白,要等到第一次 poll 才可能(且只是可能)显示数据
3. 打开 DevTools console,会看到 ECharts 对 HTML 容器 init 时的报错或 warning

## Expected vs Actual

- **Expected**:
  - 非 chart widget 走 HTML 渲染路径,不被 `echarts.init`
  - chart widget 编译期/初始化时立刻设置 base option(`xAxis` / `yAxis` / `series` 模板),首屏即可见空 series 框架;轮询只更新 `dataset`
- **Actual**:
  - 调度器算法对所有 widget 无差别调用 `echarts.init(el)`
  - 调度器只在轮询时 `setOption({ dataset })`,没人负责设 base option

## Root Cause

**这是 spec 层的系统性矛盾,不是单文件的 typo**。三份契约文档互相打架:

1. `server/data-talk-adapter/src/main/resources/skills/bezel/references/patterns-catalog.md:75-79` 明文规定 `generic.kpi-tile` 是 `HTML-only (no ECharts)`。
2. `server/data-talk-adapter/src/main/resources/skills/bezel/references/compile-rules.md:175` 硬性写 "For every widget w in the config, the polling scheduler **must** call `echarts.init(el)`",`validate.py` 还会 enforce。
3. `server/data-talk-adapter/src/main/resources/skills/bezel/references/data-contract.md:228-240` 的调度器算法对每个 widget 无差别 init。
4. 12 份 `assets/templates/*.html` 里的 `bindWidget` 实现完全照搬契约(`charts[w.id] = echarts.init(el);` 不分流),给 AI 提供了错误的 few-shot。

并且 `BezelWidgetConfig` schema(`data-contract.md:275-287`)只有 `id / intervalMs / endpoint / params` 四个字段,**缺 `type` 和 `baseOption`** —— 即便 AI 想让调度器按 type 分流也拿不到 type 信息;即便想首屏 setOption 也没地方传 base option。

`SKILL.md:121` 和 `compile-rules.md:464` 隐含假设 "chart option 在 pattern initialization 时一次设好",但**调度器算法里根本没有 "pattern initialization" 这一步**,从 `init → schedule → fetch → setOption({dataset})` 一步到位,中间没有任何环节负责设 base option。

## Fix

走 OpenSpec change `bezel-scheduler-type-aware`(见 `openspec/changes/bezel-scheduler-type-aware/`):

1. `BezelWidgetConfig` 增加 `type` 和 `baseOption` 两个必填字段,`__BEZEL_CONFIG__` 注入时由后端 `DashboardArtifactService` 写入
2. 调度器算法按 `widget.type` 分流:chart 走 `init → setOption(baseOption) → schedule`,其他类型走 `applyHtmlData`,不 init ECharts
3. `compile-rules.md` 删除 "every widget must echarts.init" 硬规则,改成 "every widget where type === 'chart' …"
4. `scripts/validate.py` 增加 schema 校验:`type === 'chart'` 必有 `baseOption`,反之必为 null
5. 12 份 template 同步重写 `bindWidget`,作为 AI 的正确 few-shot
6. v1 → v2 migration 自动推导历史 JSON 的 `type` 字段

详细任务列表见 `openspec/changes/bezel-scheduler-type-aware/tasks.md`。

## Verification

待 change 实施完成后:

- AI 生成 KPI + chart 混合 dashboard,KPI 容器无 canvas、chart 首屏即可见
- E2E `client/tests/e2e/dashboard-bezel-v2.spec.ts` 新增的混合 fixture 全绿
- `cd server && mvn clean verify` 全绿
- 关联 BUG-0050 / BUG-0051 复现步骤不再触发

## Notes

- **疑似与本 BUG 同源的已"修"BUG**:
  - BUG-0050 "大屏 JSON 模式 widget 仅渲染骨架,未调接口取数" —— 表象就是 chart 空白
  - BUG-0051 "Dashboard tab iframe 加载长时间白屏" —— 表象之一是 chart 首屏没渲染
  - 这两个 BUG 当时被打了局部补丁(loader / CDN 兜底),没动调度器算法,根因仍在。本 change 落地后 SHALL 回归验证,若复现步骤不再触发则在三份 BUG 文档的 Notes 互相交叉引用
- AI 在某次会话里报告 "已修复调度器逻辑:KPI 走 HTML 渲染路径,chart 先初始化基础 option 再注入 dataset" 仅是当次 iframe 内的临时补丁,**不会保留到下一次大屏生成** —— 因为 skill 契约和 12 份 template 没改,AI 下次重新看文档时仍会按错的算法生成。这种"修了但下次会回来"的现象正是本 BUG 必须从 spec 层根治的原因
