# DataTalk 消息历史改造 — 后端 → 前端 任务交接

**日期**：2026-04-19
**状态**：设计对齐完成 — 前端 spec 修订 v2 已通过后端评审，本文档作为后端 `/plan` 的输入
**归属**：后端（此文档作者）↔ 前端（另一个 AI 负责 `../opencode` 消息渲染组件迁移）
**前端 Spec**：[AI 消息渲染迁移设计](../product-specs/2026-04-19-ai-message-rendering-migration-design.md)（v2 修订完成，§7.0 后端依赖回流见下文）

## 背景

当前 DataTalk 架构下：
- 用户发的 USER 消息存在 DataTalk 本地 `messages` 表
- AI 的 ASSISTANT 消息**完全没存**（bug）
- 结果：切走再切回会话，AI 回复丢失

OpenCode 自己有完整的消息持久化（经实测：`GET http://localhost:4096/session/:id/message` 返回 `[{info: Message, parts: Part[]}]`，包含完整的 user/assistant 消息、reasoning、step-start/finish、tool 等）。

**后端决定**：删掉本地 `messages` 表，`GET /api/sessions/{id}/messages` 直接透传 OpenCode 的消息 API。前端对齐到 OpenCode 原生数据格式。

---

## 🔒 已固化的协议约定（后端侧已决定，前端对齐即可）

### 1. `GET /api/sessions/{id}/messages` — 响应格式透传 OpenCode 原生

**响应体**（数组，非包装对象）：
```json
[
  {
    "info": {
      "id": "msg_da1693b16001hRyX2V8qUfaGjc",
      "role": "assistant",
      "sessionID": "ses_25e96c502ffeR1vxYLQE4lzdT1",
      "providerID": "opencode",
      "modelID": "minimax-m2.5-free",
      "time": { "created": 1776529521430, "completed": 1776529524707 },
      "tokens": { "total": 12848, "input": 11026, "output": 30, "reasoning": 0,
                  "cache": { "write": 0, "read": 1792 } },
      "cost": 0,
      "finish": "stop",
      "parentID": "msg_da1693b0f001Oj5djcRUi4EU4K",
      "mode": "build",
      "agent": "build",
      "path": { "cwd": "...", "root": "/" }
    },
    "parts": [
      { "type": "step-start", "id": "prt_xxx",
        "sessionID": "ses_...", "messageID": "msg_..." },
      { "type": "reasoning", "text": "...",
        "time": { "start": "...", "end": "..." },
        "metadata": { "anthropic": { "signature": "..." } },
        "id": "prt_...", "sessionID": "ses_...", "messageID": "msg_..." },
      { "type": "text", "text": "...",
        "time": { "start": "...", "end": "..." },
        "id": "prt_...", "sessionID": "ses_...", "messageID": "msg_..." },
      { "type": "step-finish", "reason": "stop", "tokens": {}, "cost": 0,
        "id": "prt_...", "sessionID": "ses_...", "messageID": "msg_..." }
    ]
  }
]
```

**空会话**（OpenCode session 未绑定 / 新建未发送过）：返回 `[]`。

**关键字段命名**（保持 OpenCode 原生，不下划线化）：
- `sessionID` / `messageID`（不是 `session_id` / `messageId`）
- `type: "step-start"` / `"step-finish"`（连字符，不是下划线）
- 时间在 `info.time.created` / `part.time.start` 嵌套结构里（不扁平化成 `createdAt`）

### 2. SSE live 流 — Part payload 同样透传

`OpenCodeEventTranslator` 之前把 Part 反序列化成 DataTalk 的 `sealed interface Part`（下划线 type name），**后端会把这层翻译改成透传**。所以 SSE 事件 `message.part.created` / `message.part.updated` / `message.part.delta` 的 `part` 字段就是 OpenCode 原生 JSON：

```
event: message.part.updated
data: {"part": {"type":"text","text":"...","id":"prt_...",
                "sessionID":"ses_...","messageID":"msg_..."}}
```

这保证 "历史加载" 和 "流式增量" 看到的是同一种 shape，前端不用做两套。

### 3. USER 消息 ID 由 OpenCode 生成（前端要加乐观 UI）

**后端改动**：`ChannelService.sendMessage` 将不再本地生成 message id、不再发本地 `MessageCreated` / `MessagePartCreated` 事件。仅 forward 到 OpenCode + 发 `SessionStatus("busy")`。

**结果**：点击发送后 → 后端 HTTP 200 返回 → OpenCode 处理 → 回推 `message.part.updated`（USER 那条）到达前端。中间约 50-100ms 前端没有任何"自己发的消息"在页面上。

**前端需要**：发送按钮点击时立即在本地塞一条 pending message（临时 id，如 `pending_<uuid>`），等 SSE 收到首条 `message.part.updated` 带 `msg_xxx` 且 role=user 时，把 pending 条目替换为 OpenCode ID 的正式条目。

### 4. 事件 ID / SSE 断线重连 — 不变

`events` 表继续存全部 SSE 事件，`Last-Event-ID` 机制保留。切换会话时前端订阅逻辑可以保持现状。

---

## 📋 前端需要改的文件清单（建议）

| 文件 | 动作 |
|---|---|
| `client/src/features/session/hooks/use-session-history.ts` | 响应体解析从 `{messages: [...]}` → 直接数组 `[{info, parts}]`；去掉对 DataTalk `Part` 字段的假设（`messageId`、`createdAt` 等都变成 OpenCode 原生字段） |
| `client/src/stores/chat-parts-store.ts` | Part 索引用 `messageID` / `id`（不是 `messageId`）；meta 从 `info.role` / `info.time.created` 取；**`clearSession` 时机改成"加载完成后原子替换"**，而不是先 clear 再加载（否则切换过程有空白） |
| `client/src/services/channel/types.ts` | `Part` 类型定义改为 OpenCode 原生 shape（或直接从 OpenCode 组件库 re-export） |
| `client/src/services/channel/use-channel.ts` `buildEventSink` | `message.part.delta` 追加逻辑不变，但用 OpenCode 字段命名；`message.part.created/updated` 直接 upsert 原生 part |
| `client/src/services/channel/use-channel.ts` `sendMessage` | **加乐观 UI**：点击发送 → 立即 `upsertMeta` + `upsertPart` 一条 pending user message；SSE 收到对应 `msg_xxx` 时用 OpenCode ID 替换 pending |
| `client/src/features/chat/components/*` | 迁到 OpenCode 原生渲染组件；TextPart/ReasoningPart/StepStartPart 等可直接用 `../opencode` 搬过来的版本 |

---

## ⚠️ 后端 / 前端 边界

### 后端负责

#### 第一批 — 消息持久化与透传（修复 bug 的最小集）

- 删 `MessageRepository` / `JdbcMessageRepository` / `messages` 表（加 Flyway drop migration）
- 改 `HistoryService` 走 `OpenCodeGateway.listMessages(ocSid)` 透传；`OpenCodeHttpClient` 新增 `GET /session/{ocSid}/message` 调用
- 改 `ChannelService.sendMessage` 不再生成本地 message / 不再发本地 MessageCreated / MessagePartCreated 事件（`SessionStatus("busy")` 保留）
- 改 `OpenCodeEventTranslator` Part 字段改为透传 OpenCode 原生 JSON（不再反序列化成 DataTalk `sealed Part`，保留 `sessionID` / `messageID` / `type:"step-start"` 等原生命名）
- 保留：`events` 表、`artifacts` 表、`sessions` 表、SSE 断线重连、`opencode_sid` 绑定

#### 第二批 — ActionDescriptor 风险分级扩展（承接前端 spec §7.0）

- **domain 层**：新增 `RiskLevel` 枚举（`L1` / `L2` / `L3`）、`Category` 枚举（`METADATA` / `QUERY` / `MUTATION` / `ARTIFACT` / `DDL` / `QUESTION` / `MISC`）
- **application 层**：
  - `ActionDescriptor` 新增两个可空字段 `riskLevel: RiskLevel?` / `category: Category?`
  - `@DataTalkAction` 注解新增同名属性，Registry 注册时透传到 `ActionDescriptor`
  - REST（`GET /api/actions`）和 SSE（`action.registered`）下发时携带两个字段（缺失序列化为 `null`）
- **7 个现有 Action 注解回填**（见前端 spec §7.0.2）：
  - `execute_sql` → `L1` + `QUERY`；**ActionHandler 执行前校验 SQL 只能是 SELECT/EXPLAIN/SHOW/DESCRIBE，否则 reject 并返回明确错误**
  - `preview_sql` → `null` + `MUTATION`（运行时判级，由 T-5/TD-020 落地前端正则兜底）
  - `describe_table` / `list_tables` / `show_schema` → `L1` + `METADATA`
  - `artifact_created` → `L1` + `ARTIFACT`
  - `question` → `null` + `QUESTION`（视觉与 risk 色系解耦，前端独立样式）

#### 第三批 — part-level 风险字段通道预留（为 TD-020 铺路）

- `part.state.metadata.riskLevel` 在协议中预留为可空字段，本期后端回传恒为 `null`；前端 `resolveRisk` 优先级链（part-level > descriptor > 正则）的第 1 层占位。**本期不实现 AST 判级**（TD-020 异步迭代），但协议字段就位，避免 T-5 上线时再动契约。

### 前端负责

- 以上前端文件清单
- 乐观 UI
- 如果 OpenCode 渲染组件不完全匹配（例如组件内部假设某个字段），由前端决定在前端做适配 **还是** 让后端做字段补齐

---

## ✅ 联调验收场景（完工后都要能跑通）

1. **切走再切回不丢 AI 消息**：A 会话 AI 回复完整段落 → 切到 B → 切回 A → AI 那段完整显示（包括 reasoning / step-start/finish）
2. **刷新页面不丢**：同上，但用浏览器刷新代替切会话
3. **流式实时感**：AI 生成 500 字回复时，用户看到文字逐步增长（而不是卡顿后一股脑出现）
4. **乐观 UI 发送**：点击发送 → 立即看到自己的消息 → OpenCode 回推后消息 ID 无缝切换为 `msg_xxx`（不闪烁、不重复）
5. **空会话**：刚创建的、从未发消息的会话打开时 `/api/sessions/{id}/messages` 返回 `[]`，页面不报错
6. **OpenCode 离线**：OpenCode 进程停掉后打开会话，后端返回 500 / 具体错误码，前端显示"AI 服务不可用"提示，而不是空白页

---

## 🚩 冲突同步机制

如果以下任一点和前端当前的实现方向冲突，**先同步后再动工**，避免双改：

- 渲染组件期望的 Part shape 不是 OpenCode 原生（比如已做了字段改名或 type 归一化）
- 希望 SSE live 流的 part 是其它格式
- 希望保留现在的 `{messages: [...]}` 包装对象而不是直接数组
- 乐观 UI 的方向有不同设计（例如想让后端先返回一个 placeholder msgId）

其它细节按上文默认走即可。

---

## 建议实施顺序

1. 前端先改数据格式适配 + 渲染组件迁移（可用后端 mock response 先跑通）
2. 后端改造（删本地存储 + 透传 OpenCode）
3. 合并联调上面 6 个验收场景
