## Context

OpenCode 内置 `question` 工具的执行体调用 `Question.ask()`：创建一个 `Deferred`、发布 `question.asked` 事件、然后**阻塞等待** `reply()` / `reject()`。这意味着提问期间 OpenCode 的 assistant turn 一直 running，不会 idle —— 对应 DataTalk 的 `isStreaming` 恒真。回答只能经 OpenCode 的 `POST /question/:requestID/reply`（body `{answers: string[][]}`）或 `/reject` 解除，**普通聊天消息不触碰这个 Deferred**。`requestID` 在 `ask()` 内部新生成，仅通过 `question.asked` 事件下发（事件携带 `tool:{messageID, callID}` 用于关联工具 part）。

OpenCode app web 端（SolidJS）的做法已被通读，作为复刻蓝本：
- 全局 sync store 维护 `question: Record<sessionID, QuestionRequest[]>`，bootstrap 时 `question.list()` 填充，运行时 `question.asked` upsert、`question.replied`/`rejected` 移除。
- `sessionQuestionRequest()` 沿 session 树取第一个挂起问题；`blocked = !!questionRequest || !!permissionRequest`；`showComposer = !blocked` → 被问题 block 时隐藏主 composer，在原位渲染 `SessionQuestionDock`。
- dock：逐题翻页 + 进度点 + 选项（radio/checkbox）+「Type your own answer」textarea（`custom` 默认 true）+ Dismiss/Back/Next/Submit，键盘 ↑↓ 选、Cmd/Ctrl+Enter 进、Esc 拒。

DataTalk 现状：`question.asked` 落到 `OcEvent.Unknown` 被丢弃；无 reply/reject 代理；`assistant-stream.tsx:38` + `question.tsx:10` 在 pending/running 时隐藏 question part。DataTalk **无 subagent 子 session 概念**（`parentID` 零命中）。

## Goals / Non-Goals

**Goals:**
- 1:1 复刻 OpenCode app web 的 question 交互：用户可选选项或输入自定义文本回答，turn 随之解除阻塞。
- 事件桥接实时呈现/移除挂起问题；刷新（CTRL+R）/ 重连后能从服务端重建 dock。
- 模型侧零改动（工具、描述、prompt、skill 全不动）。

**Non-Goals:**
- 不实现 subagent 子 session 的问题冒泡（DataTalk 无此概念，去掉树遍历）。
- 不改 `isStreaming` 的判定规则；不引入 permission dock（仅 question）。
- 不把 question 重构成 DataTalk MCP action（已否决：需改 prompt，模型行为不稳定）。
- 不持久化挂起问题到 DataTalk 库（状态权威在 OpenCode 进程）。

## Decisions

### D1. 桥接事件（而非仅靠工具 part + 轮询）
新增 `question.asked` / `question.replied` / `question.rejected` 经 `OpenCodeEventLoop` 解码 → `OcEvent` → `OpenCodeEventTranslator` → `DtEvent` → SSE。
- **理由**：复刻 web 端的实时增删语义（多端一致：A 端回答，B 端 dock 立即消失）。仅靠 running 工具 part 无法拿到 `requestID`，且无法感知「已被其他端回答」。
- **Alternative（否决）**：前端从 running 工具 part 检测 + `GET /question` 拉 `requestID`。后端更省（零 domain 事件），但失去实时移除、与 web 端不一致、轮询时序脆弱。

### D2. reply/reject 走专用 REST 代理（不复用 action.invoke）
新增 adapter controller：`GET …/questions`（列挂起）、`POST …/questions/{requestId}/reply`、`…/reject`，经 `OpenCodeHttpClient` 透传到 OpenCode `/question` 端点。
- **理由**：question 的 `Deferred` 活在 OpenCode 进程，只有命中 OpenCode reply 端点才能解除。action.invoke 的 `CompletableFuture` 活在 DataTalk 进程，无法解开 OpenCode 的阻塞，二者不可混用。

### D3. 前端复刻 store + region + dock，按 `activeSessionId` 取（去树遍历）
- 新增 Zustand `question-store`：`Map<sessionId, QuestionRequest[]>`。`event-reducer` 处理 3 事件（asked upsert by id；replied/rejected remove by requestId）。
- `prompt-composer.tsx`：`pending = questionStore[activeSessionId]?.[0]`；`pending` 存在时在 composer-slot 渲染 `<QuestionDock>` 取代 textarea（镜像 web 的 `showComposer = !blocked`）。
- 新增 `QuestionDock`（React + shadcn）：移植 `session-question-dock.tsx` 的逐题翻页 / 进度点 / radio·checkbox 选项 / 自定义 textarea / Dismiss·Back·Next·Submit / 键盘流 / 逐题答案缓存。提交 → reply 端点；Dismiss → reject 端点。
- `assistant-stream.tsx:38` 维持隐藏 running question part；completed 后 `question.tsx` 已能渲染 `answerText`。

### D4. answers 映射
`answers: string[][]`，长度等于子问题数，每项为该问题被选中的 label 数组；自定义文本作为一个 label 进入对应问题的数组（与 OpenCode `custom` 语义一致）。Submit 时 `questions.map((_, i) => store.answers[i] ?? [])`。

### D5. 刷新 / 重连恢复
前端在订阅 session（或挂载 composer）时调 `GET …/questions` 重建 store。OpenCode 的 `Deferred` 跨 DataTalk 前端刷新存活（只要 OpenCode 进程与 turn 在），无需 DataTalk 落库。

### D6. DtEvent sealed 同步（三类义务，并非都编译期强制）
`DtEvent` 新增 3 record 后有三处必须同步，**只有第一处编译期强制**，另两处漏了会在运行期/逻辑上静默出错：
1. **`typeName()` exhaustive switch**（无 default）—— 编译期强制，必补。
2. **`@JsonTypeName` 注解**（每个 record）—— `DtEventTypeIdResolver.init` 遍历 permitted subclasses，缺注解抛 `IllegalStateException`；**编译期不报错**，启动/序列化时才炸。
3. **`extractSessionId`（`OpenCodeEventLoop`）与 `OpenCodeEventTranslator` 映射** —— 前者带 `default` 兜底（漏补则事件路由不到 session、dock 不显示），后者需新增 `case`。均非编译期强制（或仅 translator 强制），实施时 grep 逐一补。

## Risks / Trade-offs

- **composer 呈现分支引入 streaming 回归** → 复用 composer-slot 与既有 `isStreaming` 判定，dock 只是 slot 内的条件渲染分支，不改 streaming flag；E2E 覆盖 CTRL+R / 重连 replay 下 dock 的重建（呼应 BUG-0037/0038/0046/0052）。
- **answers 格式与 OpenCode 不符被拒** → 以 WireMock(FakeOpenCodeServer) 固定 reply 端点契约测试，断言 `string[][]` 形状。
- **reply 后 turn 未及时 idle / dock 未消失** → 依赖 `question.replied` 事件移除 store 项；同时 reply 成功后乐观清除本地挂起项兜底。
- **事件丢失导致 dock 残留** → D5 的 `GET 列挂起` 在重连时作为权威重建，纠正任何漂移。

## Migration Plan

纯增量、无数据迁移。后端先上（事件 + 端点），前端后上（store + dock）。回滚：移除前端 dock 分支即可退回「问题被隐藏」的旧行为（功能缺失但不破坏）；后端新增事件/端点对旧前端无副作用（未消费）。

## Resolved Decisions

- **Dismiss（reject）语义**：沿用 OpenCode 的 reject —— 模型收到「用户略过」，dock 文案按 i18n 规范定（tasks 8.2）。
- **单问题捷径**：`questions.length === 1` 且非多选时沿用 OpenCode「选中即提交、无 Confirm」捷径以减少点击（已落入 tasks 5.2）。

## Contract Findings（已对 OpenCode 源码核实 — `packages/opencode/src/question/{index,schema}.ts` + `server/routes/question.ts`）

tasks 0.1 / 0.2 的核实结论（权威来源为 OpenCode 源码本身，优于抓帧）：

- **`question.asked` 直接携带 `sessionID`**（事件 payload = `Question.Request`，含 `id` / `sessionID` / `questions[]` / 可选 `tool:{messageID,callID}`）。故 `extractSessionId` 对 `QuestionAsked` 直接 `props.path("sessionID")` 取值，**无需** `partId/callId→session` 反查映射。`replied` / `rejected` payload 仅含 `sessionID` + `requestID`（+ replied 的 `answers`）。
- **requestId 字段名不一致（陷阱）**：`question.asked` 里该字段叫 **`id`**；`question.replied` / `question.rejected` 里叫 **`requestID`**（值相同）。解码时 asked 取 `props.path("id")`，replied/rejected 取 `props.path("requestID")`。
- **REST 端点**（base `/question`）：`GET /question` 列**所有 session** 的 pending（**无 session 过滤参数**，返回 `Request[]`）；`POST /question/{requestID}/reply`（body `{answers: string[][]}`，返回 `boolean`）；`POST /question/{requestID}/reject`（无 body，返回 `boolean`）。
- **reply 形状确认**：`answers: string[][]`，按 `questions` 顺序，每项为该问题被选中的 label 数组——与 D4 一致，无需调整。
- **子问题结构**：`{ question, header(≤30 chars), options:[{label, description}], multiple?, custom?(默认 true) }`。
- **影响 listQuestions 签名**：OpenCode 端不支持按 session 过滤，故 `listQuestions()` 拉全量后由 DataTalk（application 层或前端）按 `sessionID` 过滤。

## Open Questions

- 无（阻塞项已由源码核实关闭）。
