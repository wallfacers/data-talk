## ADDED Requirements

### Requirement: per-connection SSE 广播通道

系统 SHALL 提供 `GET /api/connections/{connectionId}/op-log/stream` 端点，返回 `text/event-stream` 响应。该通道 SHALL 在 undo_log 记录创建或状态变更时推送事件。

#### Scenario: 建立 SSE 连接

- **GIVEN** connection `conn-1` 存在
- **WHEN** 客户端发起 `GET /api/connections/conn-1/op-log/stream`
- **THEN** 返回 HTTP 200，Content-Type 为 `text/event-stream`
- **AND** 连接 SHALL 保持长存活（无超时）

#### Scenario: 连接断开后自动清理

- **GIVEN** SSE 连接已建立
- **WHEN** 客户端断开连接
- **THEN** 系统 SHALL 在 30 秒 grace period 后销毁该 connection 的 ConnectionOpLogBus（若无其他 subscriber）

### Requirement: undo_log 创建事件推送

当 undo_log 记录创建时，系统 SHALL 推送 `undo_log.created` 事件。

#### Scenario: DML 执行后推送创建事件

- **GIVEN** 用户在 connection `conn-1` 上执行 INSERT 成功
- **AND** undo_log 记录已创建
- **WHEN** ConnectionOpLogBus 收到 UndoLogCreatedEvent
- **THEN** 所有订阅 `conn-1` 的 SSE 连接 SHALL 收到：
  ```
  event: undo_log.created
  data: {"id":"log-1","operation":"INSERT","tableName":"orders","affectedRows":3,"createdAt":1715865250000}
  ```

### Requirement: undo_log 状态变更事件推送

当 undo_log 记录状态变更时，系统 SHALL 推送 `undo_log.status_changed` 事件。

#### Scenario: undo 执行后推送状态变更

- **GIVEN** undo_log `log-1` 状态从 `active` 变为 `undone`
- **WHEN** UndoExecuteService 完成回滚
- **THEN** 所有订阅该 connection 的 SSE 连接 SHALL 收到：
  ```
  event: undo_log.status_changed
  data: {"id":"log-1","status":"undone","undoneAt":1715865300000}
  ```

#### Scenario: 过期清理后推送状态变更

- **GIVEN** UndoLogCleanupScheduler 将 `log-2` 标记为 expired
- **WHEN** 批量过期完成
- **THEN** 所有订阅该 connection 的 SSE 连接 SHALL 收到：
  ```
  event: undo_log.status_changed
  data: {"id":"log-2","status":"expired"}
  ```

### Requirement: SSE 心跳保活

SSE 连接 SHALL 每 30 秒发送心跳帧（`:\n\n` SSE comment），保持连接活跃并检测断开的客户端。

#### Scenario: 心跳发送

- **GIVEN** SSE 连接已建立且空闲
- **WHEN** 距离上一帧发送已过 30 秒
- **THEN** 系统 SHALL 发送 `:\n\n` 心跳帧

### Requirement: ConnectionOpLogBus 生命周期管理

ConnectionOpLogBusRegistry SHALL 管理 per-connection 的 ConnectionOpLogBus 实例。首个 subscriber 连接时创建 bus，最后一个 subscriber 断开后 30 秒 grace period 销毁。grace period 内新 subscriber 连接 SHALL 取消销毁并复用现有 bus。

#### Scenario: 首个 subscriber 触发创建

- **GIVEN** connection `conn-1` 无活跃 ConnectionOpLogBus
- **WHEN** 第一个 SSE 请求到达
- **THEN** 创建新的 ConnectionOpLogBus 实例

#### Scenario: grace period 内重连复用

- **GIVEN** 最后一个 subscriber 断开，进入 30 秒 grace period
- **WHEN** 新 subscriber 在 grace period 内连接
- **THEN** 取消销毁，复用现有 bus 实例

#### Scenario: grace period 过期销毁

- **GIVEN** 最后一个 subscriber 断开，进入 30 秒 grace period
- **WHEN** 30 秒内无新 subscriber 连接
- **THEN** 销毁 bus 实例，释放资源
