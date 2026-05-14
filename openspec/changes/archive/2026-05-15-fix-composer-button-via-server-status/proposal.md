## Why

[[BUG-0046]] 表明：streaming 中按 CTRL+R 刷新，composer 按钮短暂出现停止态又立刻翻回"发送"，与后端实际仍在生成的 OpenCode turn 不一致。已存在的 5 层防御（L1 event-id 去重、L2 500ms 重放窗口、L3/L4 同步 sessionStorage 写、L5 `shouldSkipReplace`）全部基于**前端持久化 + 重放抑制**，缺乏权威源；任意一处写错（如 `sendMessage` `finally { setStreaming(false) }` 在 fetch abort 时同步落盘 false）即污染整条链路。之前 commit 2c3e3de9 的纯前端"streamOpened 守卫"方案虽解决症状但永久卡 `streamingBySession` 为 true，副作用让 `shouldSkipReplace` 阻塞历史加载（commit e172edcb / 155706c8 已 revert）。

OpenCode 服务端本身维护权威 `SessionStatus`（`/home/wallfacers/project/opencode/packages/opencode/src/session/status.ts` 第 9-102 行；HTTP 端点 `GET /session/status` 见 `/home/wallfacers/project/opencode/packages/opencode/src/server/routes/session.ts:74-96`），返回 `Record<sessionID, {type: "idle"|"busy"|"retry"}>`。把这条信息暴露给前端做"刷新后一次性 reconcile"即可彻底解耦"streaming 真值"和"history 加载守卫"。

## What Changes

- **新增**：`OpenCodeHttpClient.getSessionStatuses()` 透传 OpenCode `GET /session/status`。
- **新增**：DataTalk 后端 `GET /api/sessions/{sessionId}/status` 经 `OpenCodeSessionMap.openCodeFor()` 把映射后的 OpenCode 状态归一化为 `{type: "idle"|"busy"|"retry"}`，未映射 / OpenCode 不可达时返回 idle（fail-open）。
- **新增**：前端 `services/channel/session-status.ts` 提供 `fetchSessionStatus(sessionId)`；`useSessionSubscribe` 在订阅 SSE 之前并发拉一次 status，busy / retry 时 `setStreaming(sessionId, true)`。
- **BREAKING（内部 store schema）**：`useChatPartsStore` 的 `persist.partialize/merge` 移除 `streamingBySession`，`setStreaming` 的同步 sessionStorage 写入逻辑同步删除。streaming 状态完全 in-memory，权威源由后端 status 接口提供。
- **保留**：BUG-0038 的 500ms 重放抑制窗口和 L3 `lastEventIdBySession` 同步写均不动 —— 它们是与权威源正交的事件流防御层。

## Capabilities

### New Capabilities

- `composer-streaming-state`: composer 发送/停止按钮的状态机契约，定义 streaming 真值的权威源（服务端 `SessionStatus`）、客户端 reconcile 时机（mount / 订阅前）、与历史加载的优先级关系。

### Modified Capabilities

- 无（现有 spec 与本变更无直接 GIVEN/WHEN/THEN 重叠；`composer-streaming-state` 是首次形式化）。

## Impact

**后端**：
- `data-talk-infrastructure/.../OpenCodeHttpClient.java`：新增 `getSessionStatuses()` 方法。
- `data-talk-infrastructure/.../channel/ChannelController.java` 或同包新增一个轻量 controller：新增 `GET /api/sessions/{sessionId}/status`。
- 测试：`OpenCodeHttpClientTest`（WireMock）、`ChannelStatusControllerTest`。

**前端**：
- `client/src/services/channel/session-status.ts`：新增。
- `client/src/features/session/hooks/use-session-subscribe.ts`：mount 时 fetch status。
- `client/src/stores/chat-parts-store.ts`：移除持久化字段 + 同步写。
- 测试：`use-session-status.test.ts`、`chat-parts-store.test.ts`（更新断言）。

**风险 / Known Issues**：
- [[BUG-0037]]、[[BUG-0038]] 的旧持久化路径被移除。这两个 BUG 的语义由"前端持久化"迁移到"服务端 reconcile"。需要确保 status 查询失败时 fail-open 不退化（保留按钮当前态而不是默认翻回发送态）。
- [[BUG-0044]] / `shouldSkipReplace` 路径不受影响：本方案不再持久化 streaming，rehydrate 时默认空集，shouldSkipReplace 自然回到原语义。

## Design Inputs

- [client/DESIGN.md](../../../client/DESIGN.md) §control-state-matrix：composer 发送/停止按钮属于 `primary-action` 五态控件，本次只修真值来源，**渲染 token 不变**（idle → `composer.send.idle`，streaming → `composer.send.streaming.stop`）。
- `docs/bugs/BUG-0037`、`BUG-0038`、`BUG-0046`：行为契约对齐 expected behavior 段。
