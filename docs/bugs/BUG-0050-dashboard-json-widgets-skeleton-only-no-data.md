---
id: BUG-0050
title: 大屏 JSON 模式 widget 仅渲染骨架，未调接口取数 + 文本乱码
status: open
priority: P1
source: manual-report
modules: [dashboard, stage]
discovered: 2026-05-15
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

bezel skill 产物中的 dashboard JSON 在 stage 渲染时，widget 区只显示骨架（标题占位 / 加载 spinner / 空网格），没有触发 widget data endpoint 拉取实际数据，ECharts 也没有渲染实际图表。同时显示出的中文文本（标题、占位提示等）伴随乱码。

注意：与 [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md) 区别——本 BUG 描述的是 dashboard **JSON 模式渲染链路**（widget 通过 `GET /api/dashboards/{id}/widgets/{wid}/data` 取数 + 客户端 ECharts 渲染），不是 iframe srcDoc 直接渲染的 HTML 模式。

## Reproduction Steps

1. chat 端要求 AI 生成 dashboard JSON（仅 JSON，不附 HTML，或后端选择 JSON 模式渲染）
2. promote 后 stage 打开 dashboard tab
3. 观察 widget 区域：每个 widget 卡片只显示骨架边框 / 加载态，无数据
4. 打开 DevTools Network 面板：没有看到对 `/api/dashboards/{id}/widgets/{wid}/data` 的请求（或请求发出但响应未被消费）
5. widget 标题中的中文字符显示乱码

## Expected vs Actual

- **Expected**:
  - 每个 widget 挂载后自动调用 `GET /api/dashboards/{id}/widgets/{wid}/data?...` 拿到数据
  - 数据回写后 ECharts / KPI / markdown 等 renderer 完成渲染
  - 中文标题、轴 label、tooltip 等正常显示
- **Actual**:
  - widget 仅渲染骨架结构（卡片框 + 占位文本），无 ECharts canvas、无 KPI 数字、无真实内容
  - 中文文本显示乱码

## Environment

- Backend commit: 3f67eb44 (develop)
- Frontend commit: develop
- OS / Browser: WSL2 Linux / Chromium

## Evidence

待补充：
- widget 骨架态截图
- DevTools Network 面板截图（确认有 / 无 widget data 请求）
- 后端日志（确认 widget data endpoint 是否被调用 + 响应 status）
- widget 标题 i18n key 与 JSON title 字段的乱码对比

## Root Cause

未定位。可疑路径：

1. **widget data endpoint 调用链断裂**：bezel JSON 模式下 widget 渲染器可能没有挂接 widget data 取数（v1 → v2 升级遗漏？）
2. **paramRefs 未解析**：widget JSON 的 `paramRefs: {}` 空对象 + dashboard params 缺省导致 data endpoint 拒绝服务（参考 BUG-0012 已修但场景不同）
3. **连接 / database 未透传**：与 BUG-0012 同源——widget data 调用未携带 dashboard 级 default connection / database
4. **Renderer 路由错误**：JSON 中 `theme: 'industry-default'` + `renderer: 'bezel'` 等字段在前端没有对应实现，回退到通用 skeleton renderer 上
5. **中文乱码**：dashboard JSON `title` 字段中的中文字面量在 OpenCode SSE 传输或前后端往返过程中破坏（可能与 [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md) 同源，但作用于不同字段）

## Fix

TBD

## Verification

TBD

## Notes

- 与 [BUG-0049](BUG-0049-bezel-dashboard-html-chinese-garbled.md) 区分：BUG-0049 是 HTML iframe 渲染链路（已能渲染真图，仅中文乱码）；本 BUG 是 JSON widget 渲染链路（连图都渲染不出 + 中文乱码）
- bezel skill 当前实际链路以 [BUG-0048](BUG-0048-dashboard-promote-v1-misses-html.md) 修复后的双 fenced block（JSON + HTML）为主，HTML 模式已能正常加载；JSON 模式因本 BUG 暂不可用
- 优先级 P1，因为 JSON 模式是 dashboard skill 的官方设计契约之一，HTML 模式只是 v1 兜底；JSON 模式失效意味着大屏长期依赖 AI 端 HTML 编译（≥7 分钟），无法走 server 编译加速路径
