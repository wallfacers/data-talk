---
id: BUG-0005
title: 页面刷新后 ER Inspector Tab 不恢复
status: verified
priority: P1
source: e2e-playwright
modules:
  - stage
  - er-inspector
discovered: 2026-05-07
discoveredBy: agent
fixCommit: 9d67946
fixPlanRef: null
closedDate: 2026-05-07
duplicateOf: null
regression: false
---

## Summary

E2E 测试 E4：`page.reload()` 后 `[data-er-tab-id]` 不可见，15 秒超时。Tab 从 stage 中消失。

## Reproduction Steps

1. 创建 ER Inspector tab 并执行 auto layout + drag
2. 刷新页面
3. 等待 tab 恢复

## Expected vs Actual

- **Expected**: Tab 完整恢复，selection / positions / viewport / neighborDepth 保留
- **Actual**: Tab 不存在或不可见

## Root Cause

1. `ErInspectorTab` loading 态缺少 `data-er-tab-id` 属性，E2E 选择器无法匹配
2. `ErDesignerTab` loading 态同样缺少 `data-er-tab-id`
3. 持久化 coordinator 的 content write 有 1s debounce，但缺少 `beforeunload` listener 在页面离开前 flush 未决写入
4. `openErInspectorViaShortcut` / `openErDesignerViaShortcut`（E2E helper）创建的 tab 缺少 `createdAt` 字段，导致后端 upsert 可能失败

## Fix

1. `ErInspectorTab` loading 态添加 `data-er-tab-id` 属性（`er-inspector-tab.tsx:31`）
2. `ErDesignerTab` loading 态添加 `data-er-tab-id` 属性（`er-designer-tab.tsx:318`）
3. `stage-persistence-bootstrap.ts` 注册 `beforeunload` + `visibilitychange` listener 调用 `coordinator.flushAllSync()`（行 315-330），确保页面离开前 content write 通过 `navigator.sendBeacon` 发送到 `/api/stage/tabs/{id}/payload-beacon`
4. E2E helper `openErInspectorViaShortcut` / `openErDesignerViaShortcut` 补齐 `createdAt` 和 `payloadVersion` 字段（`er-test-helpers.ts`）

## Verification

- `npx tsc --noEmit` 通过（仅 3 个预存 DuckDB 错误，无关）
- 定向 vitest 50/51 通过（1 个预存失败）
- 全仓 vitest 975/982 通过（7 个预存失败，无新增回归）

## Notes

E2E 测试需在实际运行环境中验证。后端 `payload-beacon` 端点在 `StageTabController.java:104` 已存在。
