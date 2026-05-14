# Tasks — fix-composer-button-via-server-status

## 1. 后端：OpenCode 状态查询

- [x] 1.1 `OpenCodeHttpClient.getSessionStatuses()` 新增方法，调用 OpenCode `GET /session/status`，返回 `JsonNode`（`Map<openCodeSid, {type}>` 形态，idle 不在 map 中）。
- [x] 1.2 在 `OpenCodeHttpClient` 已有 `WebClient` 之上对该方法设置 2s timeout，timeout / 5xx / 连接异常时返回空 `ObjectNode`。
- [x] 1.3 新增 `ChannelStatusController`（或在 `ChannelController` 同包内新增 endpoint）暴露 `GET /api/sessions/{sessionId}/status`，调用 `OpenCodeSessionMap.openCodeFor(sessionId)`，未映射 → `{type: "idle"}`；已映射时从 `getSessionStatuses()` 结果中查表，未命中 → `{type: "idle"}`，命中 → 返回 type 原值（保留 retry）。
- [x] 1.4 `mvn install -pl data-talk-infrastructure -am -DskipTests` 安装后端模块。

## 2. 前端：移除持久化 + 接入 status 查询

- [x] 2.1 `client/src/services/channel/session-status.ts` 新增 `fetchSessionStatus(sessionId)` 函数，返回 `{type: "idle" | "busy" | "retry"}`；HTTP 失败时 fail-open 返回 `{type: "idle"}`。
- [x] 2.2 `client/src/features/session/hooks/use-session-subscribe.ts` 在 `subscribe()` 之前 await `fetchSessionStatus`，busy / retry 时 `setStreaming(true)`；使用 `cancelled` 标志避免组件已卸载时仍写 store。
- [x] 2.3 `client/src/stores/chat-parts-store.ts`：
  - 从 `partialize` 删除 `streamingBySession`
  - 从 `merge` 删除 streamingBySession 处理逻辑
  - 从 `setStreaming` 删除同步 sessionStorage 写代码块及相关注释
- [x] 2.4 `npx tsc --noEmit` 确认无类型错误。

## 3. 测试

- [x] 3.1 后端 `OpenCodeHttpClientStatusTest`（WireMock）：busy 透传 / idle 不在 map / OpenCode 5xx fail-open 三个用例。
- [x] 3.2 后端 `ChannelStatusControllerTest`：mapped+busy / unmapped → idle / OpenCode 异常 → idle 三个用例。
- [x] 3.3 前端 `client/src/services/channel/session-status.test.ts`：成功 busy / 网络错误 fail-open 两个用例（实际写了 7 个）。
- [x] 3.4 前端更新 `client/src/stores/chat-parts-store.test.ts`：断言 partialize 不含 streamingBySession；setStreaming 不写 sessionStorage；reload 后 streamingBySession 不复原。
- [x] 3.5 前端 `client/src/services/channel/use-channel.test.ts`：原有 BUG-0037/0038 测试断言依然通过（in-memory state 路径不变，500ms 重放抑制窗口保留）。
- [x] 3.6 `cd server && mvn -pl data-talk-infrastructure test` 包含两套新测试 — 23 个测试全绿。
- [x] 3.7 `npx vitest run src/services/channel src/stores src/features/session` — 189 测试全绿。

## 4. 收尾

- [x] 4.1 更新 `docs/bugs/BUG-0046-*.md` 状态为 `fixed`，回填 `fixCommit`（提交后回填实际 SHA）。
- [x] 4.2 更新 `docs/bugs/index.md`：把 BUG-0046 行从 Open BUGs 移到 In Progress（status=fixed）。
- [ ] 4.3 `git status` 检查后提交单一 commit。
- [ ] 4.4 `/opsx:archive fix-composer-button-via-server-status`。
