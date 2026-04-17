# OpenCode 协议参考

DataTalk 后端通过 HTTP 与 OpenCode 服务端通信。本文档记录关键集成点。

## 会话生命周期

1. **创建会话**：`POST /session` → 返回 `{ id: string }`
2. **推送工具**：将 DataTalk Action 注册为 OpenCode 工具（name, description, parameters, callbackUrl）
3. **发送消息**：`POST /session/:id/message` → SSE 流响应
4. **接收事件**：SSE 流包含 OpenCode 事件（`OcEvent`），由 `OpenCodeEventTranslator` 翻译为 `DtEvent`

## OpenCode 事件 → DtEvent 映射

OpenCode 推送的原始事件通过 `OpenCodeEventTranslator` 转换为 DataTalk 内部的 `DtEvent` 类型。关键映射：

| OpenCode 事件 | DtEvent | 说明 |
|--------------|---------|------|
| session.created | SessionCreated | 会话创建（多客户端协作） |
| session.updated | SessionMetaUpdated | OpenCode 自动生成 / 更新 title；持久化到本地 SessionRepository（title_locked=0 时） |
| session.deleted | SessionDeleted | 会话删除 |
| session.idle | SessionIdle | 响应完成信号（用于流生命周期判定） |
| session.error | SessionError | 会话级错误 |
| session.compacted | SessionCompacted | 上下文压缩 |
| session.diff | SessionDiff | diff 事件（payload 待调研） |
| message.created | MessageCreated | 新消息（含 Part[] 内容） |
| message.updated | MessageUpdated | 消息内容更新 |
| message.completed | MessageCompleted | 消息处理完毕 |
| tool_call | ActionInvoke | AI 请求调用 DataTalk Action |

## 工具调用桥接 (ToolCallBridge)

当 OpenCode 发出 `tool_call` 时：

1. `OpenCodeEventTranslator` 识别并生成 `DtEvent.ActionInvoke`
2. `ToolCallBridge` 将调用路由给 `ActionDispatcher`
3. `ActionDispatcher` 通过 `ActionRegistry` 查找 Handler 并执行
4. 执行结果通过 `PendingCallRegistry` 回传给 OpenCode

## 后端实现类

| 类 | 模块 | 职责 |
|----|------|------|
| `OpenCodeGateway` | application | 工具推送 + 消息转发的编排器 |
| `OpenCodeEventTranslator` | application | OcEvent → DtEvent 翻译 |
| `ToolCallBridge` | application | 工具调用 → Action 分发桥接 |
| `OpenCodeHttpClient` | infrastructure | WebClient 实现 HTTP/SSE 通信 |
| `OpenCodeGatewayBeans` | adapter | Spring Bean 装配配置 |

## 测试策略

- `OpenCodeHttpClient` 使用 WireMock 3.x 模拟 OpenCode 服务端
- `OpenCodeGateway`、`ToolCallBridge`、`OpenCodeEventTranslator` 使用 stub 替身单元测试
- E2E smoke test (`EndToEndSmokeIT`) 使用 WireMock 作为 FakeOpenCodeServer
