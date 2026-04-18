# AI 消息渲染迁移 · 从极简实现迁至 OpenCode 桌面端体验

**日期**：2026-04-19
**状态**：Design（等待 `/plan` 产出实施计划）
**来源**：`../opencode/packages/app/src/pages/session/message-timeline.tsx` + `../opencode/packages/ui/src/components/{session-turn,message-part,markdown,basic-tool,text-shimmer,text-reveal}*`
**协同文档**：[2026-04-19-history-opencode-passthrough-sync.md](../exec-plans/2026-04-19-history-opencode-passthrough-sync.md)（后端透传 OpenCode 消息 API 的同步约定）

## 1. 背景与动机

当前 DataTalk 桌面端 AI 消息渲染处于极简原型状态：
- `TextPart` 只用 `<div whitespace-pre-wrap>` 显示纯文本，**没有 Markdown**
- `ReasoningPart` 是 `<details>` 折叠，无打字机/流式节奏
- `ToolPartRenderer` 仅 Badge + tool id + description，无结构、无折叠
- 整体无流式打字节奏、无 shimmer 动效、无工具分组、无代码块复制、无错误卡

OpenCode 桌面端（SolidJS 实现）在消息渲染上积累了大量"绚烂质感"：morphdom 增量 DOM diff 让流式过程中光标/选择/滚动不跳；PacedMarkdown 按标点边界节拍推进文本；TextShimmer/TextReveal 为进行中状态提供可感知的活性；BasicTool 的折叠卡 + motion spring 高度动画；ContextToolGroup 把连续的上下文采集工具聚合成一个块。

本 spec 将上述机制**React 化**迁移到 DataTalk 客户端，同时做 **DataTalk 特化**（L1/L2/L3 风险等级、SQL 代码块增强、Artifact 跳转 Stage），并前置承接后端 history-passthrough 改造（同步文档已锁定）。

## 2. 目标 / 非目标

### 2.1 目标

- 所有 AI 文本/推理内容走 Markdown 增量渲染（marked + DOMPurify + morphdom）
- 流式文本平滑推进（PacedMarkdown，24ms 节拍 + 标点 snap）
- 工具卡具备折叠、进行中 shimmer、L1/L2/L3 边框/徽章
- 连续元数据工具合并为 ContextToolGroup 带 AnimatedCount
- ToolRegistry 内建 DataTalk 专属渲染器：`execute_sql` / `preview_sql` / `describe_table` / `list_tables` / `show_schema` / `artifact_created` / `question`
- User 消息保留气泡样式（按用户要求）；Assistant 采用 turn 平铺
- SQL 代码块增强：类型徽章 + 执行/解释/复制按钮；执行按钮走 composer 1a 流程
- 完整支持 OpenCode 原生 Part / Message shape
- 支持乐观 UI：pending user 消息 + 失败重试/删除

### 2.2 非目标（本次不做）

- `HighlightedText`（@mention 高亮）：推迟，占位组件透传纯文本（技术债 T-1）
- Provider/Model 显示名：本次仅显 `modelID`（技术债 T-2）
- Storybook / 视觉回归测试：另立项
- e2e 自动化：联调走手动 QA + 后续 Playwright 立项
- HighlightedText 对应的后端 reference metadata
- User 消息 revert / fork 操作（opencode 有，DataTalk 暂无对应后端能力）

## 3. 模块架构

### 3.1 新目录结构（`client/src/features/chat/`）

```
components/
  turn/
    session-turn.tsx          一个 turn 容器：user + 其后所有 assistant
    user-bubble.tsx           user 气泡（保留气泡样式 + 占位 HighlightedText）
    assistant-stream.tsx      assistant parts 分组渲染
    context-tool-group.tsx    连续元数据工具合并组
    part-dispatcher.tsx       PART_MAPPING 字典
    text-part.tsx             Markdown + PacedMarkdown + 末尾 copy
    reasoning-part.tsx        Markdown + PacedMarkdown + heading 提取
    tool-part.tsx             ToolRegistry 分派 + risk 徽章 + error 分支
    error-card.tsx            assistant 级 error 卡（unwrap JSON）
    unknown-part.tsx          未知 part.type 的统一占位
  effects/
    text-shimmer.tsx + .css   CSS 扫光
    text-reveal.tsx + .css    逐词渐入
    paced-markdown.tsx        节拍推进 wrapper
    animated-count.tsx        数字滚动
  markdown/
    markdown.tsx              marked + DOMPurify + morphdom + LRU
    markdown-stream.ts        流式 healing + 代码块分块
    markdown.css
    sql-code-block.ts         decorator：SQL header + 执行/解释/复制
  tools/
    basic-tool.tsx            折叠卡 + motion 高度动画 + trigger
    tool-registry.ts          按 action name 注册渲染器
    renderers/
      execute-sql.tsx         L1 直接执行结果
      preview-sql.tsx         L2/L3 预览 + 确认/取消
      describe-table.tsx
      list-tables.tsx
      show-schema.tsx
      artifact-created.tsx    跳转 Stage
      question.tsx            L3 强确认阻塞交互
      generic-tool.tsx        默认降级
      tool-error-boundary.tsx React ErrorBoundary 包装
  helpers/
    use-session-turns.ts      按 user 切块派生 Turn 列表
    use-pending-status.ts     working / pendingTurn / thinking 状态推导
    group-parts.ts            纯函数：metadata tool 合并
    reasoning-heading.ts      从 reasoning markdown 提取标题
    highlight-text.tsx        占位组件（本次只透传文本）
    risk.ts                   L1/L2/L3 → color/icon/label
```

### 3.2 旧文件命运

| 当前 | 动作 |
|---|---|
| `features/chat/components/message-stream.tsx` | **重写** 为 `turn/turn-list.tsx` |
| `features/chat/components/part-renderer.tsx` | **移至** `turn/part-dispatcher.tsx`，字典化 |
| `features/chat/components/text-part.tsx` | **重写** |
| `features/chat/components/reasoning-part.tsx` | **重写** |
| `features/chat/components/tool-part-renderer.tsx` | **合并进** `turn/tool-part.tsx` |
| `features/chat/components/generic-tool-card.tsx` | **重写** 为 `tools/renderers/generic-tool.tsx` |
| `features/chat/components/step-divider.tsx` | 保留，可能用于 compaction divider |

### 3.3 新增依赖

```
marked          latest stable  markdown 解析
dompurify       latest stable  HTML 消毒
morphdom        latest stable  增量 DOM diff
motion          latest stable  motion 的 React 入口（等价 framer-motion）
```

具体版本由实施计划阶段的 `npm install` 锁定，原则是与 opencode 同库（保证机制一致）、各自取当时 npm latest stable。

### 3.4 ActionDescriptor 扩展（非破坏）

```ts
type ActionDescriptor = {
  ...
  riskLevel?: 'L1' | 'L2' | 'L3' | null
  category?: 'metadata' | 'query' | 'mutation' | 'artifact' | 'ddl' | 'question' | 'misc'
}
```

**该字段是后端数据结构改动**，前端 spec 仅声明使用约定，具体由后端落到 `@DataTalkAction` 注解。详见 §7.0 后端依赖。

`category === 'metadata'` 的工具在 `group-parts` 里参与 ContextToolGroup 合并。
`category === 'question'` 的工具使用独立视觉（与风险色系解耦，见 §5.7）。

### 3.5 风险等级来源优先级链

工具卡的风险徽章/边框颜色按以下优先级确定：

```
1. part-level risk  （后端 AST 真值，通过 part.state.metadata.riskLevel 回传）
       ↓ 缺失
2. descriptor.riskLevel  （@DataTalkAction 注解静态值）
       ↓ 缺失
3. 前端正则粗判  （仅对 category ∈ {query, mutation, ddl} 的 SQL 类 part，基于首关键字）
       ↓ 无匹配
4. 不显示徽章/边框  （保守）
```

**本期全链路落地**（第 1 层本期起效即使后端回传恒为空；第 2-4 层本期完全实现）。后端 AST 上线（见 T-5）后第 1 层获得真值，前端零改动即升级为精确判级。

前端 `helpers/risk.ts` 暴露 `resolveRisk(part, descriptor): RiskLevel | null` 实现此链。

## 4. 数据流与状态

### 4.1 保留的现有基建（不动）

```
channel-client.ts                SSE 解析
use-channel.ts · buildEventSink  事件分发
chat-parts-store.ts              partsBySession / infoBySession / partIndexBySession
```

**SSE payload 透传契约**：`buildEventSink` 假设 SSE 事件 `part` payload 为 OpenCode 原生 shape（`sessionID` / `messageID` / `type:"step-start"` 等），由后端 `OpenCodeEventTranslator` 透传保证（见同步文档 §2）。若在阶段 0 mock 期间 SSE 未就绪，可先只测历史加载路径，SSE 联调走阶段 6。

### 4.2 新增派生层

```ts
useSessionTurns(sessionId): Turn[]
// 从 infoBySession + partsBySession 派生
// 按 info.time.created 排序，按 role='user' 切 turn
// 处理孤立 assistant 归入"无 user 的 turn"
// 处理 pending user 参与切块

usePendingStatus(sessionId): {
  working: boolean
  pendingUserMessageId?: string
  status: 'idle' | 'thinking' | 'running-tool' | 'streaming-text'
}

groupParts(parts: Part[]): PartGroup[]  // 纯函数
// 连续 category=metadata 的 tool → { type:'context-group', refs:[...] }
// 其它 → { type:'part', ref:... }
```

### 4.3 关键状态映射

| 视觉状态 | 数据来源 | 条件 |
|---|---|---|
| 工具 title shimmer | `part.state.status` | `'pending' / 'running'` |
| "思考中" shimmer | `usePendingStatus` + turn 是否最新 | `working && isLastTurn && 还没有可见 part` |
| reasoning heading reveal | 从 `reasoning.text` 提取 | `working && showReasoningSummaries && heading` |
| BasicTool 默认展开 | props `defaultOpen` | L2/L3 preview/question 默认展开；L1 默认折叠 |
| 流式禁止折叠 | `status ∈ pending/running` | `locked=true` |

### 4.4 Stage 联动（artifact-created）

```ts
onClick: () => useStageStore.getState().openSession(sessionId)
// Stage 内部 timeline 条自动定位到对应 artifact
```

## 5. 组件契约

### 5.1 顶层

```tsx
<TurnList sessionId={string | null} />
<SessionTurn sessionId userMessageId?={string} isLastTurn />
  // userMessageId 可选 — 缺失时渲染"无 user 的 turn"（只显示 assistant parts）
<UserBubble info={MessageInfo} parts={Part[]} />
<AssistantStream messages={AssistantMessage[]} working showAssistantCopyPartID />
<ContextToolGroup parts={ToolPart[]} busy />
```

### 5.2 Part 渲染字典

```ts
const PART_MAPPING: Record<string, PartComponent> = {
  text:          TextPart,
  reasoning:     ReasoningPart,
  tool:          ToolPart,
  'step-start':  StepDivider,
  'step-finish': StepDivider,
  compaction:    CompactionDivider,
}
// 未知 type → UnknownPart（统一占位，可展开原始 JSON），不静默跳过
```

### 5.3 工具渲染字典

```ts
const ToolRegistry = {
  register: (name: string, r: ToolRenderer) => Map.set(name, r),
  get: (name: string) => Map.get(name),
}
// 优先级：actions/registry customRenderer > ToolRegistry > GenericTool
```

内建注册：`execute_sql` / `preview_sql` / `describe_table` / `list_tables` / `show_schema` / `artifact_created` / `question`。各 Action 默认 `riskLevel` / `category` 见 §7.0。

**question 独立视觉**：BasicTool 读取 descriptor.category，当 `category === 'question'` 时走独立样式（蓝色问号图标 + 柔和强调边框，表达"需要你回答"），**不** 走 risk 色系（L1/L2/L3 红黄绿）。风险色保留给真正的数据库风险语义。

### 5.4 BasicTool

```tsx
<BasicTool
  icon                                    // 图标
  risk?: 'L1' | 'L2' | 'L3'              // 驱动左 dot 颜色 + 边框色
  trigger: TriggerTitle | ReactNode      // 结构化 or 自由
  status?: 'pending'|'running'|'completed'|'error'
  defaultOpen / forceOpen / locked
  hideDetails / animated
>
  {children}
</BasicTool>
```

`title` 在 pending/running 时自动包 `<TextShimmer active>`。

### 5.5 Markdown

```tsx
<Markdown text cacheKey streaming />
```

内部流程：
1. `temp = document.createElement('div'); temp.innerHTML = sanitize(marked.parse(text))`
2. `decorateCodeBlocks(temp)` — 包复制按钮 + SQL header
3. `morphdom(containerRef.current, temp, { childrenOnly: true })`
4. LRU：`Map<cacheKey:blockIndex:mode, {hash, html}>` max=200

非浏览器环境兜底：`escape + replace(\n, <br>)`。

### 5.6 PacedMarkdown

步长 `step(size)` 自适应 2/4/8/24；`next(text, start)` snap 到标点；`setInterval 24ms` 推进 shown；streaming=false 立即对齐。

### 5.7 SQL 代码块增强

`pre > code.language-sql` → 外层包装：
```
┌─[SQL · SELECT]───────────── [执行] [解释] [复制] ┐
│  SELECT * FROM users ...                          │
└───────────────────────────────────────────────────┘
```

**L1 判断（前端正则粗判）**：此判断服务于 §3.5 优先级链的第 3 层，仅用于缺少 part-level 和 descriptor 级值时的降级显示；真实判级以后端 AST（T-5）上线后的 part-level 值为准。

规则（大小写不敏感，先 strip `--` 行注释和 `/* */` 块注释）：
```
L1 (绿)：  ^(?:SELECT|EXPLAIN|SHOW|DESC(?:RIBE)?)\b
L2 (黄)：  ^(?:INSERT|UPDATE|CREATE\s+(?:INDEX|VIEW))\b
L3 (红)：  ^(?:DELETE|DROP|ALTER|TRUNCATE|GRANT|REVOKE)\b
WITH 开头 / 其他 / 无匹配：  不显示徽章（保守不猜）
```

**WITH 保守不猜的理由**：`WITH x AS (...) UPDATE y ...` 类 CTE 可能内含任意 DML，正则无法可靠识别，强行归入 L1 会产生风险敞口。等后端 AST 接管。

"执行"按钮仅对"正则判定为 L1"或"part-level/descriptor 明确为 L1"的 SQL 显示。

**点击流程（1a 方案）**：
1. 打开 composer（如收起）
2. **Composer 空**时：填入 SQL → 自动 submit
3. **Composer 非空**时：SQL 追加到末尾 → **不** 自动 submit，toast 提示"已追加 SQL，请确认后发送"（避免把用户正在编辑的内容一起发出去）
4. 失败时保留 SQL 在 composer，不清空，toast 提示

### 5.8 UserBubble（含 pending 态）

| 状态 | 视觉 |
|---|---|
| 正常 | 右对齐浅蓝气泡；附件缩略图；copy 按钮；meta 行 |
| `info.__pending=true` | 气泡半透明（0.85）；右下 `<Spinner xs>`；无 copy/meta/revert |
| `info.__pending` 且 `info.__failed=true` | 红边；`⚠️` 图标；"重试" + "删除" 按钮 |

### 5.9 HighlightedText（占位）

本次仅透传文本，不识别 `@mention`。等 composer 支持 @mention 再升级。见技术债 T-1。

## 6. 错误处理与降级

### 6.1 Markdown 层

| 故障 | 处理 |
|---|---|
| `marked.parse` 抛错 | fallback：`escape(text) + replace(\n, <br>)` |
| DOMPurify 不可用 | 同上 |
| morphdom patch 抛错 | **raise 到 ErrorBoundary**（不降级 innerHTML），生产环境兜底"[本段渲染出错，点击查看原文]" |
| healing 失败 | 不调 heal，直接给 marked；最坏代码块边界闪一下 |
| LRU 缓存碰撞 | 三元组 key + hash 校验 |

### 6.2 工具层

| 故障 | 处理 |
|---|---|
| 无 descriptor | `fallbackDescriptor(name)` + GenericTool |
| 无 renderer | GenericTool |
| renderer 抛异常 | `<ToolErrorBoundary>` 降级 GenericTool + `console.error` |
| `status=error` | 跳过 renderer，渲染 `<ToolErrorCard>`（unwrap 后） |
| output 类型异常 | renderer 内类型保护，走"无输出"分支 |

### 6.3 Part / Turn 层

| 故障 | 处理 |
|---|---|
| 未知 `part.type` | **渲染 UnknownPart 占位**（灰框 + type 名 + 折叠查看原始 JSON） |
| Message info 未到 | 不渲染该 message（现状保留） |
| 孤立 assistant（无前置 user） | 归入"无 user 的 turn" |
| Assistant `info.error` | turn 末尾 `<ErrorCard>`，unwrap 嵌套 JSON |
| `MessageAbortedError` | 显示"已中断" divider，不作为 error |

### 6.4 SQL 代码块交互

| 故障 | 处理 |
|---|---|
| composer 不存在 | 忽略 + `console.warn` |
| composer 已有文本 | **追加**到末尾，toast "已追加 SQL" |
| 1a 自动 submit 失败 | **保留** SQL，不清空；toast 错误 |
| "解释"按钮失败 | 同上 |

### 6.5 动效兼容

`prefers-reduced-motion: reduce` → TextShimmer / TextReveal / BasicTool 高度 → 瞬时切换（CSS `@media` 已实现）。

### 6.6 空/加载状态

| 场景 | 处理 |
|---|---|
| 切会话瞬间 | TurnList 无 parts → null |
| 最新 turn 思考中 | `<TextShimmer>思考中…</TextShimmer>` |
| 有 reasoning heading | 下方 `<TextReveal>` |
| 空会话（历史返回 `[]`） | TurnList 返回 null |
| OpenCode 离线（history fetch 500） | TurnList 显示"AI 服务不可用：<原因>" |

## 7. 与 history-opencode-passthrough 的兼容性

以下改动**本 spec 强制承接**，避免先按老结构实现再二次改。

### 7.0 后端依赖（必须回流到后端 /plan）

本 spec 依赖后端新增以下数据结构与契约：

#### 7.0.1 ActionDescriptor 字段扩展

`domain` / `application` 层 `ActionDescriptor` 新增两个可空字段：

```java
// 建议在 @DataTalkAction 注解上加属性
@DataTalkAction(
  id = "execute_sql",
  riskLevel = RiskLevel.L1,      // 新增
  category  = Category.QUERY     // 新增
)
```

Registry 注册时把注解值透传到 `ActionDescriptor`，经 REST / SSE 下发到前端。字段缺失前端有 fallback（§3.5 优先级链的第 2 层退化为第 3 层正则粗判）。

#### 7.0.2 现有 Action 的默认值

| Action | riskLevel | category | 备注 |
|---|---|---|---|
| `execute_sql` | L1 | query | **ActionHandler 必须校验**：只接受 SELECT / EXPLAIN / SHOW / DESCRIBE，其它语句 reject。标 L1 的前提是执行器在服务端强制只读语义 |
| `preview_sql` | null（运行时判） | mutation | 真实判级由后端 AST 在 part.state.metadata.riskLevel 回传（T-5 未上线前前端用正则粗判） |
| `describe_table` | L1 | metadata | 参与 ContextToolGroup 合并 |
| `list_tables` | L1 | metadata | 参与 ContextToolGroup 合并 |
| `show_schema` | L1 | metadata | 参与 ContextToolGroup 合并 |
| `artifact_created` | L1 | artifact | |
| `question` | null | question | 视觉与风险色系解耦；question 独立样式（见 §5.3） |

#### 7.0.3 part-level riskLevel 通道

为承载 §3.5 优先级链的第 1 层，后端在 `part.state.metadata.riskLevel` 上预留字段：
- 当前（本期）：该字段恒为空，前端 resolveRisk 退化到 descriptor 或正则
- 未来（T-5 落地后）：后端 AST 分析完成后回填该字段，前端零改动获得精确判级

这三条（7.0.1 / 7.0.2 / 7.0.3）已与后端同步，会进入后端 `/plan` 并同步更新到 `2026-04-19-history-opencode-passthrough-sync.md`。

### 7.1 Part / Message 类型切换

| 当前 | 改后 |
|---|---|
| `services/channel/types.ts` 自定义 Part | 直接用 OpenCode 原生 Part 联合（TextPart / ReasoningPart / ToolPart / StepStartPart / StepFinishPart / FilePart / CompactionPart / AgentPart） |
| `chat-parts-store.MessageMeta = { id, role, createdAt }` | `MessageInfo`（= OpenCode `info` 整块）：`{ id, role, sessionID, time:{created,completed?}, providerID?, modelID?, tokens?, parentID?, agent?, mode?, path?, error?, finish? }`；store key 改名 `infoBySession` |
| 派生取 `meta.createdAt` / `meta.role` | 派生取 `info.time.created` / `info.role` |

### 7.2 乐观 UI（pending user）

```ts
// chat-parts-store.ts 新增
upsertPendingUser(sessionId, text): string
  // 返回 pendingId = `pending_<uuid>`
  // 写 infoBySession / partsBySession，标 `__pending: true`
promotePendingUser(sessionId, pendingId, realId): void
  // 原地重命名 id，清除 __pending

markPendingUserFailed(sessionId, pendingId, reason): void
  // __pending 保留，追加 __failed=true + __failReason
retryPendingUser(sessionId, pendingId): void
  // 清 __failed / __failReason，标 __retrying，触发 sendMessage
  // 再次失败回到 __failed 态（允许无限重试，由用户决定何时放弃）
removePendingUser(sessionId, pendingId): void
```

`use-channel.sendMessage` 发送流程：
1. `upsertPendingUser` → `pendingId`
2. 调 `client.sendMessage`
3. sink 监听首个 `part.messageID !== pending* && role=user` 的 `message.part.updated` → `promotePendingUser`
4. sendMessage 抛错 → `markPendingUserFailed`

### 7.3 历史加载

```ts
// use-session-history.ts
const list: Array<{ info: MessageInfo, parts: Part[] }> = await fetch(...)
useChatPartsStore.getState().replaceSession(sessionId, list)  // 原子替换
```

**新增 store 方法** `replaceSession(sessionId, list)`：一次性构造完整 byMessage/index/info Map，最后 set 一次，避免"先空再填"闪白。

原有 `clearSession` 保留（会话删除 / 登出时使用）；`upsertMany` 保留（SSE 事件批量到达时使用）。`replaceSession` 仅用于 history 初始加载路径。三者并存无冲突。

### 7.4 OpenCode 离线

`useSessionHistory` 的 error 暴露到 TurnList，渲染 `<ServiceUnavailable message={err.message}/>`。

### 7.5 字段命名 checklist

全 spec 遵守 OpenCode 原生命名：
- `sessionID` / `messageID` / `part.id`（不是 `sessionId` / `messageId`）
- `part.type = "step-start"` / `"step-finish"`（连字符）
- `info.time.created`（不扁平化 `createdAt`）
- `part.state.status` / `part.state.input` / `part.state.output` / `part.state.metadata`

写 `messageId`（小写 d）或 `createdAt` 的地方都是 bug。

### 7.6 落地顺序

**阶段 0 + 后端透传完工即可解除原 bug**（AI 消息持久化、切换不丢失、流式 ID 一致）；阶段 1-5 为视觉增强，可独立迭代上线，不阻塞 bug 修复联调。如果进度吃紧，优先把阶段 0 + 后端透传合入并单独联调验收，其余阶段按需灰度发布。

对齐同步文档"前端先改、后端后改"：

| 阶段 | 内容 | 可用 mock |
|---|---|---|
| 0 | store/types 切到 OpenCode shape + 乐观 UI + replaceSession | ✅ |
| 1 | Markdown + PacedMarkdown + TextShimmer + TextReveal | ✅ |
| 2 | BasicTool + ToolRegistry + 各 renderer | ✅ |
| 3 | TurnList + SessionTurn + UserBubble + AssistantStream + ContextToolGroup | ✅ |
| 4 | UnknownPart + ErrorCard + ToolErrorBoundary | ✅ |
| 5 | SQL 代码块增强（1a 流程）+ artifact-created 跳转 | ✅ |
| 6 | 联调（后端完工后走 history-passthrough 的 6 个验收场景） | ❌ 需真实后端 |

## 8. 测试策略

vitest + testing-library/react + jsdom。按层组织。

### 8.1 纯函数层

- `markdown-stream.test.ts`：stream 分块、refs 识别、heal 健壮性
- `group-parts.test.ts`：metadata 工具连续合并、穿插切断
- `reasoning-heading.test.ts`：ATX / Setext / 加粗 / HTML 多路径提取
- `risk.test.ts`：L1/L2/L3 映射 + 未知降级
- `use-session-turns.test.ts`：切 turn + 孤立 assistant + pending + promote 稳定性

### 8.2 效果组件

- `text-shimmer.test.tsx`：data-active 切换；220ms swap；reduced-motion
- `text-reveal.test.tsx`：mount 过渡；text 变化重置
- `paced-markdown.test.tsx`：fake timer 24ms 推进；streaming=false 立即对齐；回退立即 sync
- `markdown.test.tsx`：XSS sanitize；代码块 copy 按钮；morphdom 节点稳定引用；LRU 命中断言
- `animated-count.test.tsx`：enter/update 动画；mount 静态

### 8.3 工具卡

- `basic-tool.test.tsx`：pending title shimmer + 无箭头；completed 可展开；locked 禁切；defaultOpen / forceOpen
- `context-tool-group.test.tsx`：多个 metadata 工具合并；AnimatedCount；busy shimmer
- `tool-registry.test.ts`：register/get/miss/覆盖
- `execute-sql.test.tsx`：completed 结果；error → ErrorCard；L1 执行按钮触发 1a 流程
- `preview-sql.test.tsx`：影响行数显示；执行/取消发 action_result
- `question.test.tsx`：pending/running 不渲染；answered 后显示
- `artifact-created.test.tsx`：点击调 `openSession`；类型图标
- `generic-tool.test.tsx`：未知 tool 兜底
- `tool-part.test.tsx`：customRenderer > ToolRegistry > GenericTool 优先级链
- `tool-error-boundary.test.tsx`：renderer 抛错降级 + console.error

### 8.4 顶层集成

- `turn-list.test.tsx`：空/多 turn/isLastTurn
- `session-turn.test.tsx`：思考中 / reasoning heading / 已中断 / ErrorCard / unwrap
- `user-bubble.test.tsx`：正常 / pending / failed + 重试/删除
- `assistant-stream.test.tsx`：混合 parts 分组 + copy 按钮位置
- `unknown-part.test.tsx`：未知 type 占位 + 原始 JSON
- `message-stream.flow.test.tsx`：完整 SSE 序列端到端

### 8.5 乐观 UI 与历史

- `optimistic-user.test.tsx`：pending → promote 流程
- `optimistic-user-failure.test.tsx`：failed → 重试 / 删除
- `replace-session.test.ts`：一次 set；订阅仅 1 次重渲
- `session-history.test.ts`：OpenCode 原生响应解析；空数组；500 错误展示

### 8.6 性能

定性验证为主，关键路径有硬指标：
- 1000 字流式文本：PacedMarkdown + Markdown 整体 < 500ms
- morphdom 单次 patch < 50ms

### 8.7 Fixture

`client/src/features/chat/__tests__/fixtures/`：
- `mock-messages.ts`：覆盖所有 part type + 所有 tool + 错误态，**严格用 OpenCode 原生字段命名**
- `mock-sse-sequences.ts`：完整对话 SSE 序列
- `mock-tool-outputs.ts`：典型 output

## 9. 决策记录

| 问题 | 决定 | 理由 |
|---|---|---|
| 迁移范围 | 方案 A 全量 + DataTalk 特化 | 保留 opencode 绚烂核心 + 适配 DataTalk 风险分级/SQL/Artifact |
| 新增依赖 | 允许：marked / dompurify / morphdom / motion | 与 opencode 同栈，保证机制完整性 |
| 布局 | user 气泡保留，assistant turn 平铺 | 用户指定 |
| HighlightedText | D 推迟（占位透传） | 当前 composer 无 @mention 能力，强行做无 ROI；留技术债 T-1 |
| SQL 代码块"执行"按钮 | 1a：填入 composer + 自动 submit | 用户指定 |
| 未知 part.type | UnknownPart 占位（不静默） | 防止内容丢失；防止后端加 part type 前端静默失效 |
| morphdom patch 失败 | raise 到 ErrorBoundary | 降级 innerHTML 会掩盖 bug；生产环境 ErrorBoundary 兜底 |
| 1a 自动 submit 失败 | 保留 SQL 在 composer | 保护用户输入 |
| replaceSession 原子替换 | 加 | 切会话消灭闪白；N+1 重渲 → 1 次 |
| 乐观 UI 失败 fallback | B 保留 + 重试 / 删除 | 保护用户长文本不丢 |
| 阶段顺序 | 前端用 mock 先行（0-5），后端完工后联调 6 | 对齐同步文档 |
| 测试 | vitest + testing-library；不做 storybook/e2e | 本次范围；视觉/e2e 另立项 |
| 性能基准 | 关键路径硬指标，其他定性 | 避免回归但不过度约束 |
| SQL 判级来源 | 优先级链（part-level > descriptor > 正则 > 不显示） | 后端 AST 上线时前端零改动升级 |
| execute_sql 风险等级 | 静态 L1 + ActionHandler 服务端强制只读 | 执行器语义保证，不靠 LLM 自律 |
| question 视觉 | category='question' 独立蓝色样式，不走 risk 色 | 风险色专表数据风险，question 是交互强度，语义分离 |
| SQL 正则粗判 | WITH 开头保守不猜；UPDATE/DELETE 无 WHERE 不检测 | 正则无法可靠识别 CTE 内 DML；详细判级归后端 AST（T-5） |

## 10. 技术债

| ID | 标题 | 描述 | 后续触发条件 |
|---|---|---|---|
| T-1 | HighlightedText @mention 高亮 | 本次占位组件只透传文本。未来 composer 支持 `@连接/@表/@字段` 选择器后，需要在 user message part 上（metadata 或新 FilePart/AgentPart）携带 reference offset，前端组件按 offset 染色 | composer 加入 @mention 交互时 |
| T-2 | Provider / Model 显示名 | 本次 TextPart 的 meta 行仅显示 `modelID`；opencode 是从 `data.store.provider.all[providerID].models[modelID].name` 取友好名。需要前端 `useProviderStore`（后端接口已具备） | AI 设置中心完成提供商管理后 |
| T-3 | User 消息 revert / fork | opencode 有这两个操作，DataTalk 后端暂无对应能力；UserBubble 暂不渲染对应按钮 | 后端提供会话回滚 / 分叉能力时 |
| T-4 | OpenCode 离线错误样式 | TurnList 的"AI 服务不可用"目前用最简文本展示，无视觉打磨 | 设计侧提供错误态 UX 后 |
| T-5 | SQL AST 风险判级 | `preview_sql` 等 mutation 类 Action 本期靠前端正则粗判，仅看 SQL 首关键字，不识别 WHERE 缺失 / 批量 DELETE / CTE 内部 DML。目标：后端引入 SQL AST 解析（JSqlParser / Calcite）在 ActionHandler 执行前完成真实判级，通过 `part.state.metadata.riskLevel` 回传；前端优先级链（§3.5）保证前端零改动升级。同步登记至 `docs/exec-plans/tech-debt-tracker.md` 作为 TD-020 | 正则误判引起生产事故；或 mutation 使用量上升需更精准判级 |

## 11. 验收标准

### 11.1 来自 history-passthrough 同步文档的 6 个场景

1. **切走再切回不丢 AI 消息**
2. **刷新页面不丢**
3. **流式实时感**：500 字回复逐步增长
4. **乐观 UI 发送**：立即看到自己的消息；OpenCode 回推后 ID 无缝切换（不闪烁不重复）
5. **空会话**：`/api/sessions/{id}/messages` 返回 `[]` 时页面不报错
6. **OpenCode 离线**：显示"AI 服务不可用"，不空白

### 11.2 新增 UI 验收

7. **Markdown 流式平滑**：粘贴 500 字 markdown（含代码块、列表、表格）流式输出时，光标/选中/滚动位置不跳
8. **代码块复制**：鼠标移入代码块右上角出现复制按钮，点击后变 ✓ 2 秒
9. **SQL 代码块 1a 流程**：SELECT 代码块显示"执行"按钮，点击后 composer 出现同样的 SQL 并自动发送；失败时 SQL 保留在 composer
10. **工具进行中态**：工具 pending/running 时标题有 shimmer 扫光；completed 后停止
11. **思考中态**：AI 还没出任何 part 时显示"思考中…" shimmer；reasoning 出 heading 后其下方用 TextReveal 显示 heading
12. **ContextToolGroup**：连续 `describe_table`/`list_tables`/`show_schema` 被合并为"Gathered schema · N items"折叠块
13. **风险等级边框**：execute_sql(L1) 绿边、preview_sql(L2) 黄边、preview_sql DELETE(L3) 红边
14. **Artifact 跳转**：点击 `artifact_created` 卡片自动打开 Stage
15. **L3 强确认**：preview_sql 批量 DELETE 显示影响行数 + 执行/取消；点"取消"后卡片置灰，点"执行"后 action_result 发出
16. **错误恢复**：OpenCode 临时离线时 sendMessage 失败；pending 气泡变红，点"重试"可再发
17. **未知 part.type**：模拟一条 `type: 'future_feature'` part，页面显示占位（不白屏、不丢其它内容）

## 12. 相关文档

- [History OpenCode Passthrough Sync](../exec-plans/2026-04-19-history-opencode-passthrough-sync.md)
- [ARCHITECTURE.md](../../ARCHITECTURE.md)
- [Product Specs Index](./index.md)
- [Frontend Guide](../FRONTEND.md)
- OpenCode 源：`../opencode/packages/{app,ui}/src/...`
