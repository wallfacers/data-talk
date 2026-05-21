# 可靠性实践

## SSE 流可靠性

### 断线重连

- 每个 `DtEvent` 通过 `NumberedEvent` 携带自增 `event_id`
- 客户端断线后通过 `Last-Event-ID` 头发起重连
- 服务端从 events 表中回放缺失事件（增量补齐）
- 若事件已超出缓冲窗口，通过 `artifact.snapshot` 快照重建完整状态

### SessionBus 合并窗口

- 16ms flush 周期内的多个事件合并为单次 SSE 推送
- 减少网络往返，避免客户端渲染抖动
- 合并策略：同一 Part 的多次 delta 合并为一次 update

## Action 执行可靠性

### PendingCallRegistry 超时看门狗

- 每个 Action 调用有 `timeoutMs`（默认 30s）
- `PendingCallRegistry` 维护待处理调用，超时自动返回错误
- 超时后清理资源，不留脏状态

### 中止 (Abort) 语义

- 任意中间步骤可中止
- `ActionCancel` 事件传播给 Handler
- Handler 的 `CompletionStage` 被取消
- 不留脏状态（部分写入需回滚）

## 数据持久化可靠性

### SQLite

- WAL 模式（Write-Ahead Logging）支持并发读
- 所有写操作在单线程内序列化
- Flyway 管理 Schema 迁移，避免手动 DDL

### 密码安全

- 数据库连接密码使用 AES-GCM 加密存储（`SecretVault`）
- 密钥不落地明文（通过环境变量或系统凭据管理器提供）

## 故障模式清单

| 故障 | 影响 | 缓解措施 |
|------|------|---------|
| OpenCode 服务不可达 | AI 功能不可用 | 超时 + 错误事件推送给前端，非 AI 功能不受影响 |
| 用户数据库断连 | SQL 执行失败 | 连接池健康检查 + 明确错误提示 |
| SSE 流中断 | 前端状态滞后 | Last-Event-ID 重连 + 快照回补 |
| Action 超时 | 单个操作失败 | PendingCallRegistry 超时清理 + 错误回传 |
| SQLite 写冲突 | 元数据写入排队 | 单写线程 + WAL 模式 |
