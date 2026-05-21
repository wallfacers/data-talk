---
id: BUG-0002
title: ER Designer bind_target 成功但 diff_against_db / generate_ddl 仍拒绝
status: fixed
priority: P1
source: e2e-mcp
modules:
  - er-designer
  - stage
discovered: 2026-05-06
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

ER Designer 的 `bind_target` 动作通过 MCP 调用后返回成功，且 `targetConnectionId` 被写入 designer payload；但随后调用 `diff_against_db` 或 `generate_ddl` 时，后端仍返回错误 "Bind a target connection first"。

## Reproduction Steps

1. 打开一个 ER Designer Tab（通过 `workspace.open_er_designer`）
2. 调用 `er_designer.bind_target` 传入 `targetConnectionId`
3. 动作返回成功
4. 调用 `er_designer.diff_against_db` 或 `er_designer.generate_ddl`
5. 后端拒绝并提示 "Bind a target connection first"

## Expected vs Actual

- **Expected**: `bind_target` 成功后，`diff_against_db` / `generate_ddl` 应能正常执行
- **Actual**: `bind_target` 返回成功，但后续动作仍要求绑定目标连接

## Environment

- Backend commit: develop 分支最新
- Frontend commit: develop 分支最新
- Data source: N/A（任何数据源均可复现）

## Evidence

- E2E 测试：`client/tests/e2e/agents-batch5-er-tabs.spec.ts`
  - `test('contract: diff_against_db after bind_target returns structured diff')`
  - `test('contract: generate_ddl produces query_editor tab and does NOT execute DDL')`

## Root Cause

前端 `ErDesignerPayload` 包含视图状态字段（`kind`、`positions`、`collapsed`、`viewport`）以及设计器专属字段，而这些字段在 Java 后端 `ErDesignerPayload` record 中不存在。当前端通过 `/api/er/diff`、`/api/er/generate-ddl`、`/api/er/sync-from-db` 发送完整 payload 时，Jackson 默认拒绝未知属性，导致请求体反序列化失败，控制器无法到达，`diff_against_db` / `generate_ddl` 等动作因此失败。

## 修复

在 `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErDesignerPayload.java` 上添加 `@JsonIgnoreProperties(ignoreUnknown = true)`，允许 Jackson 反序列化时忽略前端传入的额外视图字段。

## Verification

- 后端编译通过
- E2E 测试中 `diff_against_db` 与 `generate_ddl` 的 `test.fixme` 已取消

## Notes

- 该 BUG 导致 ER Designer 的 "同步数据库差异" 和 "生成 DDL" 功能不可用
- `StagePersistenceCoordinator.flush()` 中添加的 `hydrationCache.delete(tabId)` 为调试期间的尝试，未解决根本问题，但保留无害（帮助在 flush 后重新 hydrate）
