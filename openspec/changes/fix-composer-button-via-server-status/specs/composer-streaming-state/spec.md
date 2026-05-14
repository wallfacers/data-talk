## ADDED Requirements

### Requirement: composer 发送按钮形态权威源

composer 发送/停止按钮的形态 SHALL 以 OpenCode 服务端 `SessionStatus` 作为唯一权威源。客户端 `useChatPartsStore.streamingBySession` MUST NOT 持久化（不进入 sessionStorage / localStorage），任何跨页面生命周期的状态一致性都通过服务端 reconcile 实现。

#### Scenario: streaming 中 CTRL+R 刷新（首次）后按钮维持停止态

- **GIVEN** 用户在 session `S` 中已发送一条消息，OpenCode 正在处理（OpenCode `SessionStatus.list()` 中 `S → {type: "busy"}`）
- **WHEN** 用户按 CTRL+R 刷新页面
- **AND** 页面重新挂载，并 `useSessionSubscribe(S)` 触发
- **THEN** 客户端先调用 `GET /api/sessions/S/status` 获取 `{type: "busy"}`
- **AND** `setStreaming(S, true)` 写入 in-memory streamingBySession
- **AND** prompt-composer 渲染时 isStreaming=true，按钮显示 Loader2 转圈停止态

#### Scenario: session idle 时刷新按钮显示发送态

- **GIVEN** session `S` 已完成上一轮 turn（OpenCode `SessionStatus.list()` 不含 `S`）
- **WHEN** 用户刷新页面
- **THEN** `GET /api/sessions/S/status` 返回 `{type: "idle"}`
- **AND** 客户端不调用 `setStreaming`，streamingBySession 保持空集
- **AND** prompt-composer 按钮显示 ArrowUp 发送态

#### Scenario: OpenCode 不可达时 fail-open

- **GIVEN** OpenCode 进程不可达（连接被拒 / 5xx）
- **WHEN** 客户端 `fetchSessionStatus(S)` 触发
- **THEN** DataTalk 后端 `GET /api/sessions/S/status` 返回 200 `{type: "idle"}`
- **AND** 客户端按钮显示发送态（与 BUG-0046 修复前现状等价的回归路径）
- **AND** 不抛错、不阻塞后续 SSE 订阅启动

### Requirement: streaming 状态与历史加载守卫解耦

`useSessionHistory.shouldSkipReplace` MUST 仅在内存态 streamingBySession 非空时阻塞 `replaceSession`。客户端 SHALL NOT 在 mount / rehydrate 阶段从外部存储恢复 streaming 标志，确保历史加载逻辑在任何刷新路径上都不被卡死。

#### Scenario: 刷新后历史消息正常加载

- **GIVEN** session `S` 有 N 条历史消息
- **WHEN** 用户刷新（无论 OpenCode 当前是 busy 还是 idle）
- **AND** `useSessionHistory(S)` 完成 fetch
- **THEN** mount 期间 streamingBySession 为空集（默认状态，无持久化恢复）
- **AND** `shouldSkipReplace` 返回 false
- **AND** `replaceSession(S, list)` 把 N 条历史写入 store
- **AND** 用户可见全部 N 条消息

#### Scenario: streaming 中 reconcile 完成后新事件正常累加

- **GIVEN** session `S` 刷新后 fetchStatus 返回 busy，streamingBySession=`{S}`
- **WHEN** SSE 订阅启动并接收新 `message.part.updated` 事件
- **THEN** 事件按 L1 event-id 去重正常通过
- **AND** `upsertPart` 把新 part 写入 partsBySession
- **AND** 不会因为 `shouldSkipReplace` 在 mount 阶段返回 true 而丢失（因为 mount 阶段 streamingBySession 还没被 reconcile，shouldSkipReplace 当时为 false，历史已完整 replace）

### Requirement: 后端 status 接口

DataTalk 后端 SHALL 暴露 `GET /api/sessions/{sessionId}/status` 接口，返回 JSON `{type: "idle" | "busy" | "retry"}`，状态来源为 OpenCode `/session/status`，经 `OpenCodeSessionMap.openCodeFor(sessionId)` 进行 ID 转换。

#### Scenario: busy session 返回 busy

- **GIVEN** DataTalk session `S` 经 `OpenCodeSessionMap` 映射到 OpenCode session `O`
- **AND** OpenCode `/session/status` 返回 `{"O": {"type": "busy"}}`
- **WHEN** 客户端发起 `GET /api/sessions/S/status`
- **THEN** 响应 200 `{"type": "busy"}`

#### Scenario: 未映射 session 返回 idle

- **GIVEN** DataTalk session `S` 尚未发送任何消息，`OpenCodeSessionMap.openCodeFor(S)` 返回 null
- **WHEN** 客户端发起 `GET /api/sessions/S/status`
- **THEN** 响应 200 `{"type": "idle"}`

#### Scenario: OpenCode 错误时 fail-open

- **GIVEN** OpenCode `/session/status` 抛出连接错误 / 5xx / timeout
- **WHEN** 客户端发起 `GET /api/sessions/S/status`
- **THEN** 响应 200 `{"type": "idle"}`
- **AND** 后端记录 warn 日志，不向上游传播错误
