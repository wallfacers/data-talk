# Manus 风格分屏交互 + Action Registry + 本体层（Ontology）设计

> 日期：2026-04-16
> 状态：已完成
> 上一份前置 spec：`2026-04-16-client-rebuild-tauri-vite-design.md`（本 spec 基于其已落地的三栏骨架继续改造）

---

## 0. 摘要

本 spec 一锅炖地交付三件事：

1. **Manus 风格的前端交互**——新会话进入 HERO（居中单输入框）状态，发送首条消息后用 FLIP + clip-path 动画"啪地裂成两半"进入 SPLIT（左思考 / 右干活）状态；右侧单主画布 + 顶部工件时间线胶囊条，追问可"原地变色"。
2. **Action Registry + 三类 executor**——面向 AI 暴露的一切能力（读 schema、执行 SQL、渲染图、画 ER、钉工件、版本替代）统一走注册表；扩展一个新能力 = 改 3 个文件、0 处核心代码改动。
3. **Palantir 风格的 Layered Ontology**——`ObjectType` 与 `ActionType` 是一等注册物；`LinkType`/`FunctionType` 留接口不实装；与 Ontology 对齐的持久化（SQLite）+ 广播（`ontology.updated`）一致。

通信协议采用 **Streamable HTTP**（单端点 JSON-RPC，响应可升级为 SSE；服务器可在流内回推 `action.invoke`，客户端以独立 POST 回传 `action_result`）。消息内容模型兼容 OpenCode 的 `Part` union（text/reasoning/tool/file/step-start/...）。

### 成功标准

1. HERO 态在未发送首条消息前居中只有一个 `<PromptComposer>`，侧边栏视觉弱化。
2. 发送后 260ms 内完成 HERO→SPLIT 的 FLIP + clip-path 动画；左右两列就位；composer 位置自然过渡至左列底部。
3. 一条典型 query（`查询用户表近一周注册趋势，画成折线图`）能跑完 read_schema → execute_sql → render_chart 的完整 AI 循环，右侧主画布依次出现表格、折线，时间线胶囊条 `[art1·表] [art2·图]`。
4. 对已有折线图追问 `换成绿色` 能原地替换（active 跟随 supersedes 链），胶囊条扩展为 `[art1·表] [art2·图·褪色] [art3·图·active]`。
5. abort 能在任意中间步骤中止，不留脏状态；重连可通过 `Last-Event-ID` 增量补齐或通过 `artifact.snapshot` 快照重建。
6. `GET /api/actions` 返回注册表内所有 action 的 descriptor；加入一个新的 `annotate_artifact` action 只需新建 `AnnotateArtifactHandler.java` + 前端 `annotate-left-card.tsx` + `registry.ts` 内一行 `registerAction({...})`。

### 非目标

- 用户认证 / 多用户 / 权限
- Function/Interface/Link Type 的实装（仅预留接口）
- 写入类 SQL（DDL/DML）；MVP 仅允许 SELECT
- ER 图的交互式编辑（MVP 仅展示）
- 图表建议引擎 / AI 自动选图
- 性能基准与 mutation testing
- Tauri 托盘、菜单栏自定义命令

---

## 1. 高层架构

### 1.1 三端分工

```
┌─────────────────────┐  Streamable HTTP  ┌──────────────────────┐  HTTP+SSE  ┌───────────┐
│   Client (Tauri)    │ ◄──────────────► │  Spring Boot Server   │ ◄────────►│  OpenCode │
│   React 19 + Vite   │                   │ Action Registry +     │            │  (AI)     │
│   Zustand + Query   │                   │ Ontology + SessionBus │            │           │
└─────────────────────┘                   └──────────────────────┘            └───────────┘
                                                   │
                                                   ▼
                                            ┌──────────┐
                                            │ SQLite   │
                                            └──────────┘
```

**Client**：UI 层（`WorkspaceLayout` 承载 hero↔split 状态机；侧边栏常驻）+ 协议层（`channel-client.ts` + `event-reducer.ts` + `action-dispatcher.ts`）+ 状态层（`session-store`、`ontology-store`、`chat-parts-store`、`action-registry-store`、`timeline-store`、`channel-store`）+ 注册层（`actions/registry.ts`）。

**Spring Boot**：
- `data-talk-domain`：ObjectType/ActionType 注解与纯类型；OpenCode `Part` union 的 Java 镜像。
- `data-talk-application`：`ActionRegistry`、`OntologyRegistry`、`SessionBus`、`SessionService`、`ChannelService`、`ActionDispatcher`、`OpenCodeGateway`、`OpenCodeEventTranslator`、`ToolCallBridge`、`PersistenceService`。
- `data-talk-infrastructure`：`ChannelController`（Streamable HTTP）、`OpenCodeHttpClient`、`JdbcQueryExecutor`、`ConnectionPoolRegistry`、`QueryResultStore`、Flyway schema、`SecretVault`。
- `data-talk-adapter`：MVP 6 个 action handler 的具体实现。

**OpenCode**：零改造。Spring Boot 通过其 plugin 机制注入 `datatalk.*` 工具，tool handler 全部 HTTP 回调回 Spring Boot。数据库密码永不出 Spring Boot。

### 1.2 边界与不变量

1. **唯一真理**：Action/Object schema 的真理在 Spring Boot 的 `ActionRegistry`；前端、OpenCode 均派生。
2. **Part 是线材格式**：消息内容统一用 OpenCode `Part` union（不扩展 Part 类型）；DataTalk 仅扩展**事件类型**（`action.invoke`、`action.cancel`、`artifact.snapshot`、`ontology.updated`）。
3. **Artifact 身份**：`(artifactId, version)` 是右侧时间线的唯一游标。`supersedes` 关系只能同 session 内。
4. **Hero↔Split 是前端本地状态**——不走持久化；打开 session 按 `has_ever_sent` 决定初始态。
5. **错误沿层表达**：L1/L2/L3/L4 各自的错误不越层伪造对方事件。

### 1.3 目录改动一览

```
client/src/
  features/
    chat/                # 保留，重写 message-item 为 part-renderer 分发
    workspace/           # 改造：tab 容器 → 单画布 + 时间线胶囊
    session/             # 小改：hero/split 状态绑定在这里
    connection/          # 不动
    ontology/            # 新增：ontology-store + artifact renderers
    actions/             # 新增：action registry + client handlers
  services/
    channel/             # 新增：Streamable HTTP 客户端
  layouts/
    workspace-layout.tsx # 改造：hero↔split 状态机

server/
  data-talk-domain/
    ontology/            # 新增：ObjectType/ActionType 基类 + 注解
  data-talk-application/
    registry/            # 新增：ActionRegistry + 发现端点
    session/             # 新增：SessionBus + OpenCodeGateway
    persistence/         # 新增：统一写入入口
  data-talk-infrastructure/
    channel/             # 新增：Streamable HTTP 控制器
    opencode/            # 新增：OpenCode SSE 消费者 + plugin 推送
  data-talk-adapter/
    actions/             # 新增：MVP 6 个 action 的 handler
```

---

## 2. Ontology & Action Registry 契约

### 2.1 ObjectType

```java
// data-talk-domain/ontology/ObjectType.java
public interface ObjectType<T> {
  String id();                    // "datatalk.artifact"
  String displayName();
  JsonSchema propertySchema();
  List<String> primaryKey();
  Optional<String> titleField();
  Class<T> javaType();
}
```

**MVP 内建对象**：

| id | primaryKey | 关键 property |
|---|---|---|
| `datatalk.connection` | `[id]` | kind, host, port, database, schemaDigest |
| `datatalk.session` | `[id]` | connectionId, title, createdAt |
| `datatalk.artifact` | `[id, version]` | sessionId, kind(table\|chart\|erd), producedByCallId, payloadRef, supersedes? |
| `datatalk.action_invocation` | `[callId]` | sessionId, actionId, status, input, output, error?, startedAt, endedAt? |

每个 ObjectType 对应 SQLite 一张表；`propertySchema` 驱动迁移脚本；写入前后用 Jackson JsonSchema validator 校验。

### 2.2 ActionType

```java
// data-talk-domain/action/DataTalkAction.java
@Retention(RUNTIME) @Target(TYPE)
public @interface DataTalkAction {
  String id();
  Executor executor();              // OPENCODE | SERVER | CLIENT
  String description();
  String[] produces() default {};
  boolean requiresConnection();
  int timeoutMs() default 30_000;
}

// data-talk-domain/action/ActionHandler.java
public interface ActionHandler<I, O> {
  JsonSchema inputSchema();
  JsonSchema outputSchema();
  OntologyEffect[] sideEffects();    // CREATE_ARTIFACT | PATCH_ARTIFACT | NONE
  CompletionStage<O> handle(ActionContext ctx, I input);
}
```

**MVP 6 个 Action**：

| actionId | executor | input 关键字段 | output 关键字段 | sideEffects |
|---|---|---|---|---|
| `datatalk.read_schema` | OPENCODE | connectionId, tables? | schema: TableSchema[] | NONE |
| `datatalk.execute_sql` | SERVER | connectionId, sql, pageSize? | artifactId, version, handle, columns, preview(rows[]), rowCount, durationMs | CREATE_ARTIFACT(table) |
| `datatalk.render_chart` | SERVER | sourceArtifactId, echartsOption, supersedes? | artifactId, version | CREATE_ARTIFACT / PATCH_ARTIFACT |
| `datatalk.layout_erd` | SERVER | connectionId, tables[], layoutAlgo? | artifactId, version, nodes, edges | CREATE_ARTIFACT(erd) |
| `datatalk.pin_artifact` | CLIENT | artifactId | pinned: true | PATCH_ARTIFACT |
| `datatalk.supersede_artifact` | SERVER | newArtifactId, oldArtifactId, reason | ok: true | PATCH_ARTIFACT |

> **`supersedes` / `sourceArtifactId` 引用语义**：action input 里这两个字段都只填 `artifactId`（不带 version），服务端自动解析为该 id 下**最新的** version；内部存储时 artifact 行的 `supersedes_id` + `supersedes_ver` 记录完整 (id, version) 二元组。

### 2.3 Executor 语义

- `OPENCODE`：注册到 OpenCode 作为 plugin tool；handler 仍由 Spring Boot 持有（回调端点），但前端 UI 把它视为"AI 循环的只读上下文工具"，默认折叠、可按 OpenCode web 端的 `ContextToolGroup` 风格归并展示。
- `SERVER`：注册到 OpenCode，handler 由 Spring Boot 执行（有副作用、访问数据库、产工件）。
- `CLIENT`：注册到 OpenCode，但 dispatch 时通过 SessionBus 下发 `action.invoke` 给前端执行；前端以 `POST /channel {method:"action_result"}` 回传结果；服务端以 `callId` 配对 `CompletableFuture` 并在 tool handler 响应里返回给 OpenCode。

### 2.4 发现端点

```
GET /api/ontology             → { objects: ObjectTypeDescriptor[] }
GET /api/ontology/:id         → ObjectTypeDescriptor
GET /api/actions              → { actions: ActionDescriptor[] }
GET /api/actions/:id          → ActionDescriptor
GET /api/actions.schema.json  → 合并后的 JSON Schema（AI prompt 注入用）
```

`ActionDescriptor = { id, executor, description, inputSchema, outputSchema, produces, sideEffects, requiresConnection, timeoutMs }`。前端启动时 `GET /api/actions` 并缓存到 `action-registry-store`；渲染 ToolPart 时按 `part.tool === actionId` 查找对应描述符与 renderer。

### 2.5 OpenCode 工具注册

Spring Boot 启动完成后，`OpenCodeGateway.registerTools()` 把**三类 executor 的 action 全部注册到 OpenCode**——因为 AI 的 tool 发起入口一律在 OpenCode，三类 executor 的区别只在 handler 跑在哪里：

1. 对每个 `ActionDescriptor` 生成 plugin tool 定义 `{ name: action.id, description, parameters: inputSchema, callbackUrl: "http://localhost:8080/api/opencode-tool/" + action.id }`。
2. 通过 OpenCode plugin 注册接口推送。
3. OpenCode 在任意 action 被调用时，统一 HTTP POST 回 `callbackUrl`；Spring Boot 侧 `ToolCallBridge` 收到后路由到 `ActionDispatcher`，`ActionDispatcher` 再按 executor 决定 handler 跑在哪里（SERVER/OPENCODE：本地；CLIENT：下发给 client）。

### 2.6 扩展新 action 的三步（不变量）

**Step 1**：Spring Boot 写 `XxxHandler.java`，加 `@DataTalkAction` 注解；启动时 `ActionRegistry` 自动发现 → `OpenCodeGateway` 自动注册给 OpenCode → `/api/actions` 立即包含。

**Step 2**：Client 在 `features/actions/registry.ts` 一行 `registerAction({ id, leftCard: XxxLeftCard, rightArtifact?: XxxRightArtifact })`。

**Step 3**（仅当 executor=CLIENT）：`registerClientHandler('datatalk.xxx', async (input, ctx) => {...})`。

**契约**：不改核心代码（ToolPartRenderer、ActionDispatcher、ChannelController、Event translator 均不变）。

### 2.7 AI Prompt 注入模板

Spring Boot 向 OpenCode 建 session 时，将以下 system prompt 一次性写入：

```
你是 DataTalk 助手。当前数据库连接：{connection.kind} on {connection.host}:{port}/{database}。

可用工具：
{/api/actions 的简要清单，含 description 和 executor 类别}

规则：
1. 遇到数据库查询需求，必须先 datatalk.read_schema 再 datatalk.execute_sql。
2. 执行 execute_sql 后若需可视化，调用 render_chart，sourceArtifactId 填上一步 output 的 artifactId。
3. 用户要求"修改/调整"已有图表 → render_chart 带 supersedes=上一张图的 artifactId；用户要求"另外再画一张" → 不带 supersedes。
4. 若问题为数据库相关但 session 无 connection 绑定，回复询问用户想用哪个连接。
5. 禁止 DDL/DML（CREATE/UPDATE/DELETE/DROP/INSERT/ALTER），发现用户意图如此时明确告知 MVP 暂不支持。
```

---

## 3. 流协议（Streamable HTTP）

### 3.1 端点

| 端点 | 用途 | 响应 |
|---|---|---|
| `POST /api/sessions/:id/channel` | JSON-RPC 请求（send_message / action_result / abort / hello） | `text/event-stream` 或 `application/json`（方法决定） |
| `GET  /api/sessions/:id/channel` | 只读订阅 | `text/event-stream` |
| `GET  /api/query-results/:handle` | 大结果集分页拉取 | `application/json` |
| `GET  /api/sessions/:id/messages` | 历史消息（冷启动/切会话） | `application/json`，含 Part[] |
| `GET  /api/sessions/:id/artifacts` | 历史 artifact 快照 | `application/json` |
| `POST /api/opencode-tool/:actionId` | OpenCode → Spring Boot 的 tool 回调 | 同步 `application/json` |

### 3.2 公共请求头

```
DataTalk-Session-Id:   <uuid>       // 客户端会话实例 id
DataTalk-Client-Rev:   1            // 协议版本
Last-Event-ID:         42           // 仅重连时
```

### 3.3 JSON-RPC 信封

```ts
type RpcRequest =
  | { jsonrpc: '2.0'; id: string; method: 'send_message';  params: { parts: Part[] } }
  | { jsonrpc: '2.0'; id: string; method: 'action_result'; params: { callId: string; ok: boolean; output?: unknown; error?: ErrorInfo } }
  | { jsonrpc: '2.0'; id: string; method: 'abort';         params: {} }
  | { jsonrpc: '2.0'; id: string; method: 'hello';         params: { clientRev: number; lastEventId?: number } }
```

### 3.4 下行事件集

每个 SSE 事件形如：

```
id: 1234
event: message.part.updated
data: {"sessionId":"…","part":{…}}
```

| event | payload 要点 | 来源 |
|---|---|---|
| `connected` | `{serverRev, sessionId}` | Spring Boot，流打开首帧 |
| `session.status` | `{status: "idle"\|"busy"\|"retry", retryInfo?}` | OpenCode 透传 |
| `message.created` | `{message: Message}` | OpenCode → translator |
| `message.updated` | `{message: Message}` | OpenCode → translator |
| `message.part.created` | `{part: Part}` | translator 首次出现时显式化 |
| `message.part.updated` | `{part: Part}` | OpenCode 透传，整块替换 |
| `message.part.delta` | `{partId, field, delta}` | OpenCode 透传，字段级 append |
| `message.part.removed` | `{partId}` | OpenCode 透传 |
| `action.invoke` | `{callId, actionId, input, timeoutMs}` | DataTalk 扩展，仅 CLIENT executor |
| `action.cancel` | `{callId, reason}` | DataTalk 扩展 |
| `artifact.snapshot` | `{artifacts: Artifact[]}` | DataTalk 扩展，历史恢复 |
| `ontology.updated` | `{objectType, id, op:"upsert"\|"delete", patch}` | DataTalk 扩展 |
| `heartbeat` | `{ts}` | 每 15s |
| `error` | `{code, message, fatal}` | 流级错误；fatal=true 后关流 |

### 3.5 callId 配对

```
OpenCode 发起 tool_call(actionId, input) → toolCallId
  ↓
POST /api/opencode-tool/{actionId} with X-OpenCode-Call-Id
  ↓
Spring Boot callId = toolCallId（复用）
  ↓
若 executor=CLIENT:
  ├ 下行 action.invoke {callId}
  ├ pendingClientCalls[callId] = CompletableFuture + watchdog
  └ 等 POST action_result {callId}
         ↓
      future.complete(output) → HTTP 响应体返回给 OpenCode
```

**校验**：`action_result` 的 `callId` 必须在 pendingClientCalls 中（否则 404）；`output` 通过该 action 的 outputSchema 校验（否则 422 + future completeExceptionally）。

### 3.6 事件序号与断线续传

- Session-scoped 单调 `eventId`，启动时从 SQLite `max(event_id)+1` 续编。
- 内存环形缓冲：最近 500 条或 5 分钟。
- 重连带 `Last-Event-ID`：窗内补发；过窗回 `error {code:"resume_out_of_window"}` + `artifact.snapshot` 重建。
- 客户端大部分情况下过窗 = 直接走"冷启动"（`GET /messages` + `GET /artifacts`）更稳。

### 3.7 合批

- 服务端 16ms 窗口：同 partId 的 `message.part.delta` 合并；`message.part.updated` 不合并。
- 客户端不再合批，reducer 直接消费。

### 3.8 历史恢复两模式

**快模式**（正常打开旧 session）：前端先 `GET /messages` + `GET /artifacts` 拉齐，再 `GET /channel` 订阅增量。

**慢模式**（重连过窗）：服务端注入 `artifact.snapshot` 批量帧；前端清空并重建。

进入恢复后的 session 默认 SPLIT，右侧主画布显示"最新一个未被 supersede 的 artifact"。

### 3.9 OpenCode 事件 → DataTalk 事件翻译表

| OpenCode event | 转换 |
|---|---|
| `server.connected` | 吞掉，触发 `connected` 一次 |
| `message.updated`（新 message 首次出现） | `message.created` |
| `message.updated`（已存在） | `message.updated` |
| `message.part.updated`（新 partId） | `message.part.created` |
| `message.part.updated`（已存在） | `message.part.updated` |
| `message.part.delta` | 透传 |
| `message.part.removed` | 透传 |
| `session.status` | 透传 |
| 其他 | 丢弃 |

翻译位于 `OpenCodeEventTranslator`（纯函数），单测核心。

---

## 4. Client 架构

### 4.1 Session 模式状态机

```
NOSESS ──select/new──▶ HERO ──send_message 200──▶ SPLIT
  │                                                  ▲
  │                     session.has_ever_sent=true   │
  └───────────────────────────────────────────────────┘
```

状态绑定在 session 上（Zustand）；首次发送后永久 SPLIT，清空消息也不回 HERO。

**合法跃迁表**（唯一合法，其它全部禁止）：

| 从 | 到 | 触发 |
|---|---|---|
| NOSESS | HERO | 侧边栏"新建会话"或进入 `has_ever_sent=false` 的会话 |
| NOSESS | SPLIT | 进入 `has_ever_sent=true` 的会话 |
| HERO | SPLIT | `send_message` RPC 200 后（不是"按下发送"，是服务端确认后） |
| SPLIT | HERO | **永远不允许** |
| 任意 | NOSESS | 侧边栏取消选中 / 关闭 session |

> **"发送即分屏但等服务端 200" 的权衡**：故事里"按下回车啪地裂开"在理想情况几乎无感；若服务端 400（schema 校验失败、无连接但又是 db 问题）时应保持 HERO 让用户改输入。若延迟 >300ms，`PromptComposer` 发送按钮转一个 loading 指示。

### 4.2 组件树

```
<WorkspaceLayout>
  <PanelGroup direction="horizontal">
    <Panel size={sidebarSize}>
      <Sidebar>                        ← 常驻，HERO 态 opacity 0.5 + 去背景
        <ConnectionList/>
        <SessionList/>
      </Sidebar>
    </Panel>
    <PanelResizeHandle/>
    <Panel>
      <SessionCanvas sessionId={active}/>
    </Panel>
  </PanelGroup>
</WorkspaceLayout>

<SessionCanvas>
  match mode:
    NOSESS → <EmptyState/>
    HERO   → <HeroView>
               <HeroHeader/>
               <PromptComposer id="composer"/>    ← 共享节点（FLIP）
             </HeroView>
    SPLIT  → <SplitView>
               <PanelGroup>
                 <Panel size={48}>
                   <ChatColumn>
                     <MessageStream/>              ← Part 分发渲染
                     <PromptComposer id="composer"/>  ← 同实例
                   </ChatColumn>
                 </Panel>
                 <PanelResizeHandle/>
                 <Panel size={52}>
                   <StageColumn>
                     <ArtifactTimelineStrip/>
                     <ArtifactCanvas/>
                   </StageColumn>
                 </Panel>
               </PanelGroup>
             </SplitView>
```

### 4.3 HERO → SPLIT 动画

手写 FLIP：

1. send_message 成功即将切状态前读 `<PromptComposer>` 的 `getBoundingClientRect()`（First）。
2. React 提交切到 SPLIT 子树。
3. `useLayoutEffect` 读新位置（Last）。
4. 用 `transform: translate(dx,dy) scale(sx,sy)` 先置于原位，下一帧清空 transform 并设 `transition: transform 260ms cubic-bezier(.22,.61,.36,1)`。

**clip-path 裂开**：`SessionCanvas` 在切换瞬间加一层 `clip-path: inset(0 50% 0 0)` 覆盖层，260ms 内过渡到 `inset(0 0 0 0)`，形成横向解锁效果。

**降级**：`prefers-reduced-motion: reduce` 下关闭动画，用 150ms opacity crossfade 代替。

### 4.4 Zustand Stores

```
stores/
  session-store.ts          // activeSessionId, Map<sessionId, SessionMode>, lockSplit
  channel-store.ts          // 连接状态、lastEventId、pendingCallIds
  ontology-store.ts         // Map<artifactId, Artifact>, Map<objectType, Map<id, Object>>
  chat-parts-store.ts       // Map<messageId, Message>, Map<messageId, Part[]>（按 id 排序）
  action-registry-store.ts  // descriptors + renderers + clientHandlers
  timeline-store.ts         // 每 session：artifactId[] + activeArtifactId + manuallyPinned
```

**不合并**：职责清晰、re-render 范围小、持久化边界清。

**timeline-store.setActive 规则**：
- 新 artifact 无 supersedes、`!manuallyPinned` → active 跟随
- 新 artifact 的 supersedes 指向当前 active → active 跟随到新版
- 其他情况 → active 不变，胶囊条多一个
- 用户点胶囊 → `manuallyPinned = true`；点最新一个解锁

### 4.5 Part 渲染分发

```tsx
// features/chat/components/part-renderer.tsx
export function PartRenderer({ part, message }: Props) {
  switch (part.type) {
    case 'text':       return <TextPart part={part}/>
    case 'reasoning':  return <ReasoningPart part={part}/>
    case 'tool':       return <ToolPartRenderer part={part}/>
    case 'file':       return <FilePart part={part}/>
    case 'step-start':
    case 'step-finish':return <StepDivider part={part}/>
    case 'subtask':    return <SubtaskPart part={part}/>
    default:           return null
  }
}

function ToolPartRenderer({ part }: { part: ToolPart }) {
  const descriptor = useActionRegistry(s => s.descriptors[part.tool])
  const Custom     = useActionRegistry(s => s.renderers[part.tool]?.leftCard)
  return Custom
    ? <Custom part={part} descriptor={descriptor}/>
    : <GenericToolCard part={part} descriptor={descriptor}/>  // 兜底
}
```

### 4.6 右列：主画布 + 时间线胶囊

```tsx
<StageColumn>
  <ArtifactTimelineStrip>
    {artifactIds.map(id => (
      <ArtifactChip key={id} artifact={byId[id]}
        active={id === activeArtifactId}
        onClick={() => timeline.setActive(id)}/>
    ))}
  </ArtifactTimelineStrip>
  <ArtifactCanvas>
    {activeArtifact && <ArtifactDispatcher artifact={activeArtifact}/>}
    {!activeArtifact && <StageEmpty/>}
  </ArtifactCanvas>
</StageColumn>

function ArtifactDispatcher({artifact}) {
  switch (artifact.kind) {
    case 'table': return <TableArtifact artifact={artifact}/>   // TanStack Table + 虚拟滚动
    case 'chart': return <ChartArtifact artifact={artifact}/>   // 见下方图表渲染器说明
    case 'erd':   return <ErdArtifact   artifact={artifact}/>   // React Flow（后续）
  }
}
```

**图表渲染器**：协议上 `render_chart` 的 input 字段 `echartsOption` 是一份 **ECharts option schema**（语法子集：`xAxis` / `yAxis` / `series[] (line|bar|pie|scatter)` / `color` / `title` / `tooltip` / `legend`）。选 ECharts 的原因是 AI 对它的生成质量远高于 Recharts 的 JSX 配置。MVP 客户端没有依赖 echarts 库，`<ChartArtifact>` 内部有一个 `echartsOptionToRechartsProps()` 适配器把子集翻译成现有 Recharts 组件（`LineChart` / `BarChart` / `PieChart` / `ScatterChart`）；未支持的字段（如 `dataZoom`、`visualMap`）目前在适配器里直接忽略并记 warning。**升级路径**：未来把 `<ChartArtifact>` 内部换成 `echarts-for-react`，协议不变、AI 产出不变、适配器删除。

**"原地变色"**：`<ChartArtifact>` 内部 `useMemo(artifact.payload.echartsOption)` 重算 Recharts props，React 同位 reconcile + 180ms opacity 0.6→1 强调。

### 4.7 连接对话式设置

```
composer.submit(text)
  ↓ classifyIntent(text)    // MVP 本地启发式：SQL/表/查询/字段… 关键字表
  ↓
若 db_related 且 activeConnectionId 为空：
  ① 不发送 RPC，不写 chat-parts-store
  ② 在 composer 上方显示**临时 UI overlay**（不是 chat message）：
     「这是数据库相关问题但没选连接，请选择：
       1. mysql-local
       2. pg-prod
       或直接继续输入连接名」
  ③ session-store 暂存 pendingPrompt = text
  ④ composer 的下一次 submit 先经过 pending-prompt-resolver：
       若输入匹配任一连接名/序号 → activeConnectionId = 解析结果
                                    → overlay 消失
                                    → 发送 pendingPrompt 作为真正的 send_message
       否则 → 清 pendingPrompt + overlay，按普通输入处理
  ↓
else：正常 send_message。
```

> **为什么用临时 overlay 而不是假 assistant message**：§7.8 "渲染就是真相"——前端不本地伪造 chat message；连接选择提示是 UI 层的临时对话，状态活在 session-store 的 `pendingConnectionPrompt` 字段，不进 chat-parts-store。

升级路径：future 加 `POST /api/classify-intent` 轻模型端点做防抖调用替代启发式。

### 4.8 Tauri 细节

- 密码经 Tauri `invoke('store_secret', ...)` 存 keyring，前端仅持 handle。
- 关闭窗口时若有 pending action.invoke，弹确认。
- Menu 自动派生（后续）；MVP 不做。

---

## 5. Server 架构

### 5.1 模块分层（Maven）

```
server/
├── data-talk-domain/           只依赖 JDK + Jackson
│   ontology/ action/ part/
├── data-talk-application/       依赖 domain
│   registry/ session/ opencode/ persistence/
├── data-talk-infrastructure/    依赖 application
│   channel/ opencode/ query/ persistence/ security/
└── data-talk-adapter/           依赖 application + infrastructure
    actions/ (ReadSchema / ExecuteSql / RenderChart / LayoutErd / PinArtifact / SupersedeArtifact)
```

依赖方向严格单向：adapter → infrastructure → application → domain。

### 5.2 SessionBus

```java
public class SessionBus {
  private final String sessionId;
  private final Deque<NumberedEvent> ringBuffer;    // 500 条或 5min
  private final AtomicLong eventIdSeq;
  private final Map<String, Subscriber> subscribers;
  private final BlockingQueue<Event> inbound;
  private final ScheduledExecutorService flusher;   // 16ms tick
}
```

**Flusher**：16ms 取 inbound、按 §3.7 合批、分配 eventId、入 ringBuffer、异步批量写 SQLite `events` 表、推所有 subscribers。连续 3 次推送失败的 subscriber 踢下线，客户端靠 `Last-Event-ID` 重连恢复。

**SessionBusRegistry**：`Map<sessionId, SessionBus>`，LRU 淘汰冷会话（无订阅者 + 无事件 > 30min）。

### 5.3 OpenCodeGateway 生命周期

```
应用启动
  1. ActionRegistry 扫描（Spring 启动顺序保证）
  2. registerTools()：for each action → POST OpenCode /plugin/register-tool
                     { name, description, parameters, callbackUrl }
  3. startEventLoop()：GET OpenCode /event (SSE 长连接)
                      每帧 → OpenCodeEventTranslator → SessionBus.publish()

运行期
  - Session 创建：POST OpenCode /session → 映射 dataTalkSessionId ↔ openCodeSessionId
  - 用户发消息：ChannelService.sendMessage() → POST OpenCode /session/:id/message
  - tool 回调：POST /api/opencode-tool/:id → ToolCallBridge → ActionDispatcher
  - Abort：POST OpenCode /session/:id/abort + 本地 action.cancel 所有 pending

崩溃/断线
  - 指数退避 1s→2s→4s cap 30s；session.status=retry 可见
  - >3 次失败：session.status=idle + error{code:"upstream.unavailable", fatal:true}
  - Spring Boot 重启：从 sessions 表恢复活跃 session 映射
```

### 5.4 ActionDispatcher

```java
public CompletionStage<Object> dispatch(
    String actionId, Object input, String callId, SessionContext ctx) {
  ActionDescriptor desc     = registry.require(actionId);
  ActionHandler<?,?> handler = registry.handler(actionId);
  validate(desc.inputSchema(), input);
  switch (desc.executor()) {
    case SERVER   -> handler.handle(ctx, input).thenApply(out -> {
                       validate(desc.outputSchema(), out);
                       applyEffects(desc.sideEffects(), out, ctx);
                       return out;
                     });
    case CLIENT   -> {
      var fut = new CompletableFuture<Object>();
      ctx.bus().registerPendingClientCall(callId, fut, desc.timeoutMs());
      ctx.bus().publish(Event.actionInvoke(callId, actionId, input, desc.timeoutMs()));
      return fut;
    }
    case OPENCODE -> handler.handle(ctx, input);  // 只读上下文工具，handler 也在 Spring Boot
  }
}
```

`applyEffects` 的副作用（`CREATE_ARTIFACT` / `PATCH_ARTIFACT`）写 SQLite + 广播 `ontology.updated`。

### 5.5 ToolCallBridge

```
POST /api/opencode-tool/{actionId}
  headers: X-OpenCode-Call-Id, X-OpenCode-Session-Id, X-OpenCode-Secret
  body: {input}

  → 校验 secret
  → openCodeSessionId → dataTalkSessionId 映射
  → ActionDispatcher.dispatch(...)
  → 200 + {output}   (CLIENT executor 时阻塞等 future，watchdog timeoutMs+3s)
```

用 Java 21 虚拟线程承载阻塞等待。

### 5.6 SQLite Schema（V1__init.sql）

```sql
CREATE TABLE connections (
  id            TEXT PRIMARY KEY,
  kind          TEXT NOT NULL,
  host          TEXT NOT NULL,
  port          INTEGER NOT NULL,
  database      TEXT,
  username      TEXT NOT NULL,
  password_enc  BLOB NOT NULL,
  schema_digest TEXT,
  created_at    INTEGER NOT NULL
);

CREATE TABLE sessions (
  id             TEXT PRIMARY KEY,
  connection_id  TEXT REFERENCES connections(id),
  title          TEXT NOT NULL,
  has_ever_sent  INTEGER NOT NULL DEFAULT 0,
  opencode_sid   TEXT,
  created_at     INTEGER NOT NULL,
  updated_at     INTEGER NOT NULL
);

CREATE TABLE messages (
  id         TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES sessions(id),
  role       TEXT NOT NULL,
  parts_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_messages_session ON messages(session_id, created_at);

CREATE TABLE artifacts (
  id             TEXT NOT NULL,
  version        INTEGER NOT NULL,
  session_id     TEXT NOT NULL REFERENCES sessions(id),
  kind           TEXT NOT NULL CHECK(kind IN ('table','chart','erd')),
  produced_by    TEXT NOT NULL,
  payload_ref    TEXT NOT NULL,         -- INLINE:<json> | HANDLE:<queryHandle>
  payload_size   INTEGER NOT NULL,
  supersedes_id  TEXT,
  supersedes_ver INTEGER,
  pinned         INTEGER NOT NULL DEFAULT 0,
  created_at     INTEGER NOT NULL,
  PRIMARY KEY(id, version)
);
CREATE INDEX idx_artifacts_session ON artifacts(session_id, created_at);

CREATE TABLE action_invocations (
  call_id     TEXT PRIMARY KEY,
  session_id  TEXT NOT NULL REFERENCES sessions(id),
  action_id   TEXT NOT NULL,
  status      TEXT NOT NULL,            -- pending|running|completed|error|cancelled
  input_json  TEXT NOT NULL,
  output_json TEXT,
  error_json  TEXT,
  started_at  INTEGER NOT NULL,
  ended_at    INTEGER
);

CREATE TABLE events (
  event_id     INTEGER NOT NULL,
  session_id   TEXT NOT NULL REFERENCES sessions(id),
  event_type   TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  ts           INTEGER NOT NULL,
  PRIMARY KEY(session_id, event_id)
);
CREATE INDEX idx_events_ts ON events(ts);

CREATE TABLE query_results (
  handle       TEXT PRIMARY KEY,
  session_id   TEXT NOT NULL REFERENCES sessions(id),
  columns_json TEXT NOT NULL,
  rows_ndjson  TEXT NOT NULL,
  row_count    INTEGER NOT NULL,
  created_at   INTEGER NOT NULL,
  ttl_at       INTEGER NOT NULL
);
```

**大 payload 策略**：`payload_ref` 约定 `INLINE:<json>`（<256KB）或 `HANDLE:<queryHandle>`（行数据外挂 `query_results`）。

**事件表回收**：保留最近 5000 条或 7 天，超出定时清理。

### 5.7 密码安全

- 前端存 keyring，只送 handle 给 Spring Boot。
- Spring Boot `SecretVault` 用主密钥（`DATATALK_MASTER_KEY` 或 Tauri 首 RPC 注入）AES-GCM 加密 `connections.password_enc`。
- 建 JDBC 时解密 → HikariCP → 擦除。
- 日志 Appender 配 `MaskingConverter` 过滤敏感字段。

### 5.8 线程模型

- Spring Boot 3.x + Java 21 + 虚拟线程作为 MVC 执行器。
- OpenCode 上游 SSE 消费：单条虚拟线程 per active session。
- SessionBus flusher：平台线程 ScheduledExecutor，保证 16ms 节拍。

### 5.9 配置（`application.yml`）

```yaml
datatalk:
  persistence:
    sqlite-path: ${DATATALK_HOME:~/.data-talk}/datatalk.db
  opencode:
    base-url: ${OPENCODE_URL:http://localhost:4096}
    plugin-callback-base: http://localhost:8080
    shared-secret: ${OPENCODE_SHARED_SECRET:}    # 启动时若为空则生成一次
  channel:
    ring-buffer-size: 500
    ring-buffer-ttl: 5m
    heartbeat-interval: 15s
  artifact:
    inline-size-limit: 256KB
    query-result-ttl: 7d
  action:
    default-timeout: 30s
    client-watchdog-grace: 3s
```

### 5.10 启动时序

```
1. Flyway 迁移
2. SecretVault 解锁主密钥
3. ConnectionPoolRegistry 预热
4. ActionRegistry 扫描 @DataTalkAction
5. OntologyRegistry 扫描 ObjectType
6. OpenCodeGateway.registerTools()
7. OpenCodeGateway.startEventLoop()
8. SessionBusRegistry 恢复活跃 session
9. 暴露 HTTP 端点
```

6/7 失败**不退出**，进入"无 AI 降级模式"：可管理 connection、看历史、手动 SQL，但 `send_message` 立即返回 `upstream.unavailable`。

---

## 6. 端到端数据流（典型 query 全程）

**场景**：HERO 态新会话，已选连接 `pg-prod`，用户输入"查询用户表近一周注册趋势，画成折线图"。

### 6.1 首轮完整流

```
Client → POST /channel method=send_message {parts:[TextPart]}
Server：PersistenceService.saveMessage() → SessionBus.publish
Server：HTTP 200 + Content-Type:text/event-stream   ← ★ HERO→SPLIT FLIP 在这一刻触发
Client ← SSE #1 connected
Client ← SSE #2 message.created (user msg)
Client ← SSE #3 message.part.created (TextPart)
Client ← SSE #4 session.status:busy

Server → POST OpenCode /session/:ocSid/message
OpenCode (推理中)
Server ← OpenCode event: reasoning part delta × N
Client ← SSE #5..#20 (reasoning created + deltas)

OpenCode → POST /api/opencode-tool/datatalk.read_schema {callId:c1}
Server ActionDispatcher (executor=OPENCODE) → ReadSchemaHandler → JDBC information_schema
Server → 200 {schema}
Client ← SSE #21..#23 (tool pending → running → completed)

OpenCode 继续推理，产出 SQL 解释文本
Client ← SSE #24..#40 (text created + deltas)

OpenCode → POST /api/opencode-tool/datatalk.execute_sql {callId:c2}
Server ExecuteSqlHandler → JDBC SELECT (120ms) → 7 rows → INLINE payload →
       创建 Artifact(art1,v1,table) → applyEffects(CREATE_ARTIFACT) →
       publish ontology.updated
Client ← SSE #41..#44 (tool pending → running → ontology.updated → completed)
                                          ★ 右侧 StageEmpty → TableArtifact(art1)
                                          时间线：[art1·表]

OpenCode → POST /api/opencode-tool/datatalk.render_chart {callId:c3}
Server RenderChartHandler → 校验 echartsOption schema →
       创建 Artifact(art2,v1,chart) → publish ontology.updated
Client ← SSE #45..#48
                                          ★ active: art1 → art2 (跟随)
                                          主画布 TableArtifact → ChartArtifact 淡入
                                          时间线：[art1·表] [art2·图·active]

OpenCode 总结文本
Client ← SSE #49 (text deltas)
Client ← SSE #50 session.status:idle → 服务端关流
```

### 6.2 追问 "换成绿色"（演示原地变）

```
Client → POST /channel method=send_message {parts:[TextPart("换成绿色")]}
...
OpenCode → render_chart {input:{sourceArtifactId:"art1",
                                echartsOption:{color:["#22c55e"],...},
                                supersedes:"art2"}, callId:c4}
Server → Artifact(art3,v1,chart,supersedes=(art2,v1))
Client ← SSE tool.created / ontology.updated{art3 new} /
             ontology.updated{art2 supersededBy=art3} / tool.completed
                    ★ 前端 timeline-store 规则：active=art2 且未手动切 → active=art3
                    胶囊：[art1·表] [art2·图·褪色] [art3·图·active]
                    ChartArtifact useMemo 重算 option → 同位 reconcile
                    180ms opacity 0.6→1 "变绿"动画
```

### 6.3 关键时序不变量

1. ToolPart 事件严格序：`part.created(pending) → part.updated(running) → [ontology.updated] → part.updated(completed|error)`。
2. `ontology.updated` **必须在** 对应 `tool.completed` **之前**（避免前端闪白）。
3. `supersedes` 只能同 session；违者 422 tool error。
4. Active 切换规则见 §4.4 `timeline-store`。
5. Abort 中途：OpenCode abort + 所有 pendingClientCalls → action.cancel + `session.status:idle`；SPLIT 保持；ToolPart 标 cancelled。

### 6.4 细节注释

- `connectionId` **不在** send_message 里——session 创建时就绑定 connection，写入 OpenCode system prompt 和工具默认参数。避免每条消息重复与 AI 越权改连接。
- `render_chart` 的 `echartsOption` 由 AI 直接生成（ECharts option 子集，见 §4.6）；后端不解读图表语义，客户端自带 echartsOption→Recharts 适配器。future 的"图表建议引擎"是独立的 `suggest_chart` action。
- 历史恢复不重放 event 流（避免副作用重复）——走 `GET /messages` + `GET /artifacts` REST 拉齐再订阅增量。

---

## 7. 错误处理

### 7.1 错误分层

| 层 | 范围 | 责任人 | 策略 |
|---|---|---|---|
| L1 传输 | Streamable HTTP、SSE 断线、JSON 解析 | ChannelController + channel-client.ts | 自动重连 + Last-Event-ID；>3 次转 L2 |
| L2 上游 | OpenCode 宕机/超时/协议 | OpenCodeGateway | 指数退避 + session.status:retry；触发降级模式 |
| L3 业务 | Action 执行失败（SQL 错、timeout、校验） | ActionDispatcher + handlers | ToolPart state=error + AI 自我纠正 |
| L4 语义 | supersedes 指向不存在等 | schema + 业务规则 | tool error，不写持久化 |

**原则**：不越层伪造。

### 7.2 `ErrorInfo` 形状

```ts
type ErrorInfo = {
  code: string
  message: string
  retriable: boolean
  details?: unknown
}
```

### 7.3 MVP 必须覆盖的错误码

| code | 触发 | retriable | AI 重试 | 前端 UX |
|---|---|---|---|---|
| `schema.input_invalid` | L4 输入校验 | true | 调整参数 | ToolCard error + "AI 正在调整" |
| `schema.output_invalid` | L4 输出校验（handler bug） | false | 停 | 红 ToolCard |
| `connection.missing` | execute_sql 无 connection | false | 停 | ToolCard + 弹连接选择 |
| `connection.unreachable` | JDBC 失败 | true | 限次重试 | ToolCard + "检查连接" |
| `sql.syntax_error` | SQL 语法 | true | 含位置重试 | ToolCard + 展开详情 |
| `sql.timeout` | JDBC >30s | false | 告知 | 黄 ToolCard + "考虑加 LIMIT" |
| `sql.forbidden` | DDL/DML | false | 停 | "MVP 不支持写入" |
| `artifact.supersedes_not_found` | supersedes 指无存 | true | 不带 supersedes 重试 | 静默 |
| `artifact.too_large` | 超 HANDLE 也拒 | false | 告知 | ToolCard + 下载兜底 |
| `action.timeout` | Handler 超 timeoutMs | false | 告知 | 红 ToolCard |
| `action.cancelled` | abort/级联 | false | 停 | 灰 ToolCard |
| `client_action.unreachable` | action.invoke 下发时 client 断 | false | 以 timeout 告终 | 无 |
| `upstream.unavailable` | OpenCode 不可达 | true (L2) | N/A | 全局 Banner + retry |
| `channel.resume_out_of_window` | Last-Event-ID 过期 | false | N/A | 前端切冷启动 |

**错误码稳定性**：`docs/superpowers/specs/error-codes.md` 单独维护；只增不删不改语义。

### 7.4 并发与竞态

| 场景 | 处理 |
|---|---|
| 多 tab 发 send_message | 服务端允许，广播给所有订阅者；OpenCode 内天然串行 |
| abort 时 JDBC 正跑 | statement.cancel()；结果丢；artifact 不写；ToolPart cancelled |
| action.invoke 后 client 断线重连 | Last-Event-ID 补发；过窗则 server timeout 触发 cancel |
| 胶囊连续切 | 纯前端 state，不发 RPC |
| 同 callId 二次到达 | 幂等：返回之前结果 |
| SQLite 写冲突 | 事务 + ON CONFLICT；artifacts PK (id, version) 强约束 |

### 7.5 降级模式

**触发**：OpenCode 启动/运行 3 次 retry 失败。

**允许**：管理 connection、看历史、创建 session（但发消息立即 `upstream.unavailable`）、手动 `POST /api/query` 直连 SQL。

**禁止**：send_message 及任何 AI 相关 action。

**恢复**：OpenCodeGateway 后台 1/2/4/…/30s 退避；连上后广播 `session.status:idle` 清 banner。

### 7.6 可观察性

- `/api/health`：`{opencode:"ok"|"degraded", db:"ok", uptime, activeSessions}`
- 结构化 JSON 日志（`sessionId` / `callId` / `actionId`）。
- MVP 不加 APM，但字段留出 OpenTelemetry 扩展位。

### 7.7 安全边界

- SQL 注入：MVP 仅 SELECT（正则白名单 + JDBC 语句类型探测）；写入类在未来的独立 action 开关。
- 跨 session artifact 引用：422。
- OpenCode callback：`X-OpenCode-Secret` 验签。
- Tauri：Spring Boot 只监听 `127.0.0.1:8080`。
- 密码日志过滤。

### 7.8 "渲染就是真相"原则

前端不本地伪造 tool state / artifact / ontology。任何状态必须来自 SSE 或 REST：

- 用户点发送 → 不预插 user message，等 `message.created` 回来。
- pin_artifact（CLIENT executor）在 client handler 里也是：先发 action.invoke → server → ontology.updated 回放。
- 保证多端一致性 + SQLite 唯一真实状态。

---

## 8. 测试策略

### 8.1 分层矩阵

| 层 | 风格 | 工具 | 覆盖重点 |
|---|---|---|---|
| L0 | 单元 | JUnit 5 / Vitest | OpenCodeEventTranslator、classifyIntent、FLIP 计算 |
| L1 Domain | 单元 | JUnit 5 | ObjectType/ActionType schema 序列化、reducer 纯函数 |
| L2 Application | 集成 | Spring Boot Test + Testcontainers | ActionRegistry 扫描、Dispatcher、SessionBus 合批/续传 |
| L3 Adapter | 集成 | Testcontainers (PG + MySQL) | 每个 Handler 的真实 JDBC |
| L4 Channel 契约 | pact | 共享 pact 文件 | SSE 帧序列、JSON-RPC 信封、错误码 |
| L5 Client 组件 | 单元/组件 | Vitest + RTL | PartRenderer 分发、timeline 规则、ontology reducer |
| L6 E2E | e2e | Playwright | HERO→SPLIT、完整 AI 轮、原地变绿 |

### 8.2 关键验收用例

**协议（L4）**
- [ ] 16ms 合批正确（10 deltas → 1 帧）
- [ ] `artifact.supersedes_not_found` 不触发 ontology.updated
- [ ] abort 后 OpenCode 延迟事件被 translator 丢弃
- [ ] Last-Event-ID 窗内/过窗分支
- [ ] 同 callId 二次请求幂等

**Action Registry（L2）**
- [ ] 动态注册 action → `/api/actions` 立刻包含
- [ ] CLIENT executor future 超时 completeExceptionally
- [ ] `CREATE_ARTIFACT` 副作用 → `ontology.updated` → `tool.completed` 严格顺序

**状态机（L5）**
- [ ] HERO→SPLIT 只在 send_message 成功后
- [ ] SPLIT→HERO 永远不发生
- [ ] has_ever_sent=true 的旧 session 直接 SPLIT

**时间线（L5）**
- [ ] 新 artifact 无 supersedes + !manuallyPinned → 跟随
- [ ] supersedes 指向 active → 跟随
- [ ] 手动切后 AI 产物不抢 active

**E2E（L6）**
- [ ] §6.1 完整场景
- [ ] §6.2 "换成绿色" 原地变色 + active 跟随
- [ ] Abort 中途 → SPLIT + idle 无脏状态
- [ ] OpenCode 断线 banner + 重连恢复

### 8.3 测试替身

- **FakeOpenCodeServer**（WireMock）：模拟 `/session`、`/message`、`/event`；脚本回放 Part 事件序列；前后端共用。
- **Testcontainers**：真实 PostgreSQL 14 + MySQL 8，不用 H2。
- **时钟注入**：Spring `Clock` Bean + `FixedClock` / vi.setSystemTime。
- **ID 注入**：`IdGenerator` Bean，测试下 counter、生产下 UUID v7。

### 8.4 覆盖率目标

- L0–L3 ≥ 70% 语句覆盖（软指标）
- L4 pact：每 event 至少 1 happy + 1 edge
- L5：HERO/SPLIT 状态机、ontology reducer、action dispatcher 必测
- L6：§8.2 最后 4 项

**不做**：硬门禁、mutation、性能基准。

### 8.5 编写顺序建议

1. Translator / reducer 纯函数
2. pact 契约（驱动前后端对齐）
3. ActionRegistry 集成测试
4. 状态机组件
5. E2E

---

## 9. 扩展性契约（codex 评估重点）

> **不变量**：新增一个"AI 可调用且 UI 有专属展示"的能力 = 改 **3 个文件** + **0 处核心代码改动**。
>
> 改动仅限：
> 1. 新建 `XxxHandler.java` 并加 `@DataTalkAction` 注解
> 2. 新建 `xxx-left-card.tsx`（+ 可选 `xxx-right-artifact.tsx`）
> 3. `features/actions/registry.ts` 内一行 `registerAction({...})`
>
> 其他路径（ToolPartRenderer、ActionDispatcher、ChannelController、OpenCodeEventTranslator、SessionBus）不变。

---

## 10. 未来扩展的预留接口

- `LinkType` 注册器与 `/api/ontology/links` 端点（本 spec 不实装）
- `FunctionType` 注册器（只读计算，给 AI 上下文注入用）
- `InterfaceType`（Palantir 独有抽象，MVP 不做）
- `execute_mutation`（写入类 SQL）作为独立 action，带权限开关
- `suggest_chart`（AI 图表建议引擎）作为独立 action
- `classify-intent` 轻模型端点，替代本地启发式
- ER 图交互式编辑（React Flow 双向）
- 多用户 / 权限 / 审计日志
- OpenTelemetry 接入
