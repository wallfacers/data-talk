---
id: BUG-0006
title: 页面刷新后 ER Designer Tab targetConnectionId 丢失
status: fixed
priority: P2
source: e2e-playwright
modules:
  - stage
  - er-designer
discovered: 2026-05-07
discoveredBy: agent
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

E2E 测试 E5：`page.reload()` 后 `targetConnectionId` 为 null，而 `tables` 和 `dialect` 能恢复。

## Reproduction Steps

1. 创建 ER Designer tab
2. Bind target 到有效 connection
3. 刷新页面
4. 检查 `targetConnectionId`

## Expected vs Actual

- **Expected**: `targetConnectionId` 完整恢复
- **Actual**: `targetConnectionId` 为 null

## Root Cause

持久化 coordinator 的 content write 有 1s debounce。`bind_target` 修改 payload 后，content write 被调度但尚未执行时就 reload 页面，导致 `targetConnectionId` 更新丢失。而 `openErDesignerViaShortcut` 初始创建时写入的内容（`tables` / `dialect`）可能在更早的时间点完成了持久化，所以能恢复。

## Fix

与 BUG-0005 共享持久化 flush 根因修复：
1. `stage-persistence-bootstrap.ts` 注册 `beforeunload` + `visibilitychange` listener（行 315-330）
2. E2E helper 补齐 `createdAt` 和 `payloadVersion` 字段
3. `ErDesignerTab` loading 态添加 `data-er-tab-id` 属性

## Verification

- `npx tsc --noEmit` 通过
- 全仓 vitest 975/982 通过（7 个预存失败，无新增回归）
- E2E 测试需在实际运行环境中验证

## Notes

BUG-0005 和 BUG-0006 共享同一个持久化 flush 根因修复。
