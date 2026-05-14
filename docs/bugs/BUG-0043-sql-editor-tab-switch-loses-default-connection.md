---
id: BUG-0043
title: Chat Run SQL 打开多个 SQL 编辑器，切换 tab 导致默认 connection 丢失
status: open
priority: P1
source: manual-report
modules: [stage, query-editor, chat]
discovered: 2026-05-14
discoveredBy: human
testRunId: null
fixCommit: null
fixPlanRef: openspec/changes/query-editor-connection-default-fallback/
duplicateOf: null
regression: false
---

## Summary

在 chat 中先后两次点击 "执行 SQL" 按钮（chat 上 SQL 代码块的 Run SQL 或 `execute_sql` tool renderer 的 "Open in SQL Workbench"），系统打开两个独立 SQL 编辑器 tab。两个 editor 的 connection 选择器都默认显示 `tide`（用户未手动改动）。在两个 SQL 编辑器 tab 之间切换后，连接选择器丢失了 `tide`（显示空 / 默认选项被清掉）。

## Reproduction Steps

1. 一个绑定 TiDB Connection 的会话中，AI 在 chat 输出含 SQL 代码块的回复
2. 点击代码块上的 "Run SQL" / "Open in SQL Workbench" 按钮 → 打开 SQL 编辑器 tab A，toolbar connection 选择器自动显示 `tide`
3. 不操作 tab A，回到 chat（或保持 stage 打开），再点同一个/另一个代码块的 Run SQL → 打开 SQL 编辑器 tab B，toolbar connection 选择器也自动显示 `tide`
4. 在 stage 顶部 tab bar 上点击切换：tab A ↔ tab B
5. 观察切换后的 tab 的 connection 选择器

## Expected vs Actual

- **Expected**: 切换 tab 后两个 editor 各自的 connection 选择器仍稳定显示 `tide`（两次都是从 chat session 上下文/code-block metadata 派生，应该被 persist 在各 editor 自己的 contextOverride 中）
- **Actual**: 切换后看到的 editor connection 选择器为空 / "请选择"，原本的 `tide` 选择丢失；用户必须手动重新选择

## Environment

- Frontend commit: develop / (在分支 develop，本次 query-editor-connection-default-fallback change apply 后)
- Backend: 无需重启
- Browser: 待复现确认（用户手动报告）
- Data source: TiDB（kind=tidb）

## Evidence

待 playwright 复现补充（assets/BUG-0043/）。代码层面的初步定位：

- 入口：`client/src/features/stage/utils/open-direct-sql-query-editor-tab.ts` 或 `client/src/features/chat/components/tools/renderers/execute-sql.tsx`
- 两者均 `openMode: 'always_new'`，每次点击都开新 editor，传入 `connectionId`（chat 上下文派生）
- 因为传 `connectionId`，`resolveQueryEditorOpenContext` 进入 explicit-connectionId 分支 → `useSessionContext=false`, `contextOverride={connectionId:'tide', database, schema}`
- `buildQueryEditorPayload` 把 `contextOverride` 写入 payload
- 切 tab 时旧 tab unmount 新 tab mount，每个 tab 的 `payload.contextOverride` 都已 persist 在 `StageTab.payload` 里
- `sql-workbench-tab.tsx:275-281` `runtimeContextOverride = tabState.override || payload.contextOverride`
- 期望：切换后 payload.contextOverride 仍是 `{connectionId:'tide',...}`，显示 `tide`

可能 root cause 待复现确认：
1. tab 切换时某个 useEffect 触发 `setQueryEditorContext`，把 contextOverride 清掉（例如 handleConnectionChange line 604-611 被误触发）
2. `sql-workbench-store` 的 `override` runtime 字段被 reset
3. payload 持久化不一致，切回时读到旧的 / 没 contextOverride 的版本
4. 与 BUG-0042 同源——某条非 explicit 路径让 useSessionContext 变 true，从而 contextOverride 被 buildQueryEditorPayload 设为 null

## Root Cause

TBD（需 playwright 复现 + 状态快照）

## Fix

TBD

## Verification

修复后通过以下场景验证：
- chat 中两次点 Run SQL → 打开两个 SQL editor，切换 tab 多次，两个 editor 的 connection 选择器稳定显示 `tide`
- 重新加载页面后两个 editor 状态恢复，connection 显示仍是 `tide`

## Notes

- 关联 change: `openspec/changes/query-editor-connection-default-fallback/`
- 该 change 的 walkthrough Section 8.8 测试 "Chat 里 AI 回复一段 SQL → 点击代码块的 Run SQL → 编辑器打开，database 显示 analytics"，没覆盖 "两次点 Run SQL + tab 切换" 的组合场景，缺测试覆盖
- 可能与 BUG-0042 同源，修复方案要一并考虑
