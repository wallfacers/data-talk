---
id: BUG-0089
title: 点击"打开到工作台"后 ChartBlock 误塌缩为"已在上方图表产物中展示"，实际上方无图表产物卡片
status: fixed
priority: P2
source: manual-report
modules: [chat, markdown, chart]
discovered: 2026-05-23
discoveredBy: human
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: true
---

## Summary

对一个独立的 markdown ```` ```chart ```` 代码块（未走 `datatalk_render_chart`）点击"打开到工作台"后，该 `ChartBlock` 立即塌缩为"已在上方图表产物中展示"提示，但聊天里**上方根本没有**任何图表产物卡片——图表凭空消失。这是 [BUG-0064](BUG-0064-chart-artifact-rendered-twice-when-llm-also-embeds-echarts-block.md) 去重修复引入的回归。

## Reproduction Steps

1. 让 AI 直接输出一段 ```` ```chart ```` 代码块（不调用 `datatalk_render_chart`，即上方不会出现 `ArtifactCreated` 工具卡片）。
2. 在该图表卡片工具栏点击"打开到工作台"（外链 / 提升按钮）。
3. 观察该 chat 气泡。

## Expected vs Actual

- **Expected**: 图表继续显示；提升按钮翻为"已在工作台"，再次点击聚焦已打开的工作台 tab。
- **Actual**: `ChartBlock` 塌缩为"已在上方图表产物中展示"文本提示，图表消失；而"上方"并无任何图表产物卡片，提示是假的。

## Environment

- Backend commit: develop @ 965d79a3 起点
- Frontend commit: develop @ 965d79a3 起点
- OS / Browser: WSL2 Ubuntu / Tauri dev
- Data source: N/A（纯前端渲染逻辑）

## Evidence

- 用户口述复现：点击分享/打开到工作台按钮后变成"已在上方图表产物中展示"，实际上方图表产物不存在。
- 控制台错误片段：N/A（无报错，纯渲染逻辑误判）

## Root Cause

BUG-0064 的去重 `findMatchedArtifact`（`client/src/features/chat/components/markdown/chart-block.tsx`）仅按 `originMessageId + originPartId` 匹配 chart 工件，无法区分工件的两种来源：

1. `datatalk_render_chart` 工具产物 — 后端 `ChartArtifactService` 写入 `producedBy = callId`，对应聊天里一张 `ArtifactCreated` 卡片渲染在上方。此时塌缩为提示是正确的。
2. 手动"打开到工作台" — `promoteChartToStage` 走 REST `/api/sessions/{id}/artifacts/chart`，后端 `callId == null` → `producedBy = "rest:chart"`，且把**当前 ChartBlock 自己的** `originMessageId/originPartId` 写进新工件。聊天里**没有**对应的 `ArtifactCreated` 卡片。

提升完成后，新建的 REST 工件经 `ontology.updated` 进入 ontology store，`findMatchedArtifact` 用同一 origin 匹配到它，误判"上方已有产物卡片"，于是隐藏画布、显示 hint。但上方空无一物。

次因：前端 ingest 链路（`use-channel.ts` 的 `ontology.updated` 分支、`use-session-history.ts` 的快照映射）此前未把后端已下发的 `producedBy` 提升到工件顶层字段，导致前端无从区分来源。

## Fix

1. **贯通 `producedBy`**：
   - `event-reducer.ts` `Artifact` 类型新增 `producedBy?: string`。
   - `use-channel.ts` `ontology.updated` → `upsertArtifact` 映射 `producedBy: d.patch?.producedBy`（后端 `buildPatch` 已下发）。
   - `use-session-history.ts` 快照 `ArtifactDto` + 映射补 `producedBy`（后端 `ArtifactDto` 已含）。
2. **去重区分来源**（`chart-block.tsx`）：
   - 新增常量 `REST_PRODUCED_BY = 'rest:chart'`（对齐后端 `ChartArtifactService.REST_PRODUCED_BY`）。
   - `findMatchedArtifact` 优先返回工具产物（非 rest）；rest 产物仅作降级匹配。
   - 新增 `dedupedByToolCard = !!matched && matched.producedBy !== REST_PRODUCED_BY`。
   - 折叠分支与提升按钮隐藏改用 `dedupedByToolCard`；按钮文案 / `setActive` 仍用 `matched`，使 REST 自提升保留图表并把按钮翻为"已在工作台"。

## Verification

- `chart-block.test.tsx` 新增 BUG-0089 case：ontology store 内存在 `producedBy='rest:chart'` 且同 origin 的 chart 工件时，`ChartBlock` **不**塌缩、画布保留、按钮显示"已在工作台"。
- 回归 BUG-0064 case：`producedBy='call_render_chart_01'`（工具产物）时仍正确塌缩为 `data-dedup-skipped` 提示。
- `chart-block.test.tsx`（13）+ `use-channel.test.ts`（33）全过；`tsc --noEmit` 零错误。

## Notes

- 与 [BUG-0064](BUG-0064-chart-artifact-rendered-twice-when-llm-also-embeds-echarts-block.md) 同模块同函数，`regression: true`。BUG-0064 的去重假设"任何同 origin 的 chart 工件都对应上方一张卡片"在手动提升场景下不成立。
- 后端契约未改动；`producedBy` 字段后端早已下发，仅前端 ingest 漏接。
