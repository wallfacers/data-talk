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

**初版（已被结构性方案取代）**：曾用 `producedBy` 区分工具产物 vs REST 自提升，仅工具产物才触发塌缩。该方案随后被下面的"单一展示面"重构整体替换。

**最终方案 —— 统一为单一展示面（用户决策：保留围栏块）**：从根上消除两条聊天内图表渲染路径，塌缩逻辑随之删除，BUG-0089 不再可能发生：

1. `artifact-created.tsx`：`datatalk_render_chart` 工具卡片**不再内联渲染图表画布**，退化为"紧凑行 + 在工作台查看(眼睛)"。chart 与 table 产物表现一致。
2. `chart-block.tsx`：`ChartBlock` 成为聊天内**唯一**图表画布——画布恒显，删除 `dedupedByToolCard` / `REST_PRODUCED_BY` / "已在上方图表产物中展示" 塌缩分支。`findMatchedArtifact` 简化为按 origin 匹配，仅用于把提升按钮翻为"已在工作台"并在点击时聚焦已开 tab（避免重复提升）。
3. 回退初版的 `producedBy` 贯通（`event-reducer.ts` / `use-channel.ts` / `use-session-history.ts`），因塌缩删除后该字段不再被消费。
4. Prompt：`skill:charts-and-dashboards` + `AGENTS.md` 改为"聊天内图表唯一走围栏块；`datatalk_render_chart` 不内联出图,仅用于持久化工件"。

## Verification

- `chart-block.test.tsx`：single-surface case —— 存在同 origin chart 工件时画布仍渲染、无塌缩提示、按钮显示"已在工作台"且点击不触发 `promoteChartToStage`。
- `artifact-created.test.tsx`：重写为断言**不**渲染 chart 画布、仅紧凑行 + 眼睛。
- 后端 `AgentsTemplateContractTest.chartsAndDashboardsSkillDeclaresSingleInChatChartSurface` 校验新文案（17/17 通过）。
- 前端 chart-block / artifact-created / markdown / use-channel 套件全过；`tsc --noEmit` 零错误。

## Notes

- 与 [BUG-0064](BUG-0064-chart-artifact-rendered-twice-when-llm-also-embeds-echarts-block.md) 同模块同函数，`regression: true`。两者根因同源——"两条聊天内渲染路径并存"。最终通过让 `ArtifactCreated` 不出图、`ChartBlock` 独占展示，从结构上消除双路径（也顺带消除空围栏外的另一类困惑），而非继续靠运行时去重。
- 后端契约未改 chart 工件本身；仅 `AgentsTemplateContractTest` 一处契约文案随 SKILL.md 更新。
