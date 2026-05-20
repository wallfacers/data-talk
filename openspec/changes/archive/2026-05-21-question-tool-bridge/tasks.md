## 0. 前置契约核实（已完成 — 对 OpenCode 源码核实，结论见 design.md「Contract Findings」）

- [x] 0.1 ~~抓帧确认 sessionID~~ → 经 `packages/opencode/src/question/index.ts` 核实：`question.asked` payload = `Question.Request`，**直接携带 `sessionID`**，`extractSessionId` 直接取字段、**无需反查映射**。
- [x] 0.2 字段/结构核实：`asked` 的 requestId 字段名为 **`id`**、`replied`/`rejected` 为 **`requestID`**（陷阱）；子问题 `{question, header(≤30), options:[{label,description}], multiple?, custom?(默认true)}`；reply body `{answers: string[][]}`。

## 1. 后端事件桥接（domain → application）

- [x] 1.1 `OcEvent.java` 新增 `QuestionAsked` / `QuestionReplied` / `QuestionRejected` 三个 variant。⚠️ 解码字段名：`asked` 的 requestId 取 `props.path("id")`、`replied`/`rejected` 取 `props.path("requestID")`；`asked` 另携带 `sessionID` / `questions[]` / 可选 `tool:{messageID,callID}`
- [x] 1.2 `OpenCodeEventLoop` 解码 `switch(name)` 新增三分支；`extractSessionId`（带 `default`、非 exhaustive）补 case —— 三个事件 payload 均含 `sessionID`，**直接 `props.path("sessionID").asText()`，无需反查映射**（0.1 已核实）
- [x] 1.3 `DtEvent.java` sealed interface 新增对应 3 个 record，**每个 record 必须同时**：① 标注 `@JsonTypeName("question.asked"|...)`（否则 `DtEventTypeIdResolver.init` 运行时抛 `IllegalStateException`，编译期不报错）② 进 `typeName()` exhaustive switch（编译期强制），两处字符串保持一致
- [x] 1.4 grep 全部 `switch` over `DtEvent` 及 JSON-RPC 序列化处，补齐 exhaustive 分支（已知：`OpenCodeEventTranslator` 的映射 switch、`DtEvent.typeName()`）
- [x] 1.5 `OpenCodeEventTranslator` 新增 `case OcEvent.QuestionAsked/Replied/Rejected -> DtEvent.*` 映射
- [x] 1.6 `mvn install -pl data-talk-domain -am -DskipTests` + `mvn compile -q` 确认零编译错误

## 2. 后端 REST 代理（infrastructure → application 端口 → adapter）

- [x] 2.1 `OpenCodeHttpClient` 新增 `listQuestions()`（透传 `GET /question`，返回全量 `Request[]`；OpenCode **不支持** session 过滤，按 session 的过滤在 application/前端做）/ `questionReply(requestId, answers)`（`POST /question/{requestID}/reply`，body `{answers:string[][]}`，返回 `boolean`）/ `questionReject(requestId)`（`POST /question/{requestID}/reject`，无 body，返回 `boolean`）
- [x] 2.2 `OpenCodeGateway` 新增 question 端口（沿用既有 functional-interface 模式：`QuestionLister` / `QuestionReplier` / `QuestionRejecter`，或合并为单 `QuestionClient`），并在 `OpenCodeGatewayBeans` 接线到 2.1 的 `OpenCodeHttpClient` 方法。controller **不得直连** infra 具体类，须经此端口（对齐 abort/sendMessage/listMessages 现有约定）
- [x] 2.3 新增 adapter question REST controller：`GET …/questions`、`POST …/questions/{requestId}/reply`、`…/reject`，经 2.2 的 gateway 端口调用
- [x] 2.4 `mvn compile -q` 确认零编译错误

## 3. 后端测试

- [x] 3.1 WireMock 增加 `/question`、`/question/{id}/reply`、`/reject` stub（`OpenCodeHttpClientTest`，无独立 FakeOpenCodeServer，沿用既有 WireMock 模式）
- [x] 3.2 翻译链路单测：`question.asked/replied/rejected` → 正确 `DtEvent` 且不落 Unknown（`OpenCodeEventTranslatorTest`：20 通过）
- [x] 3.3 sessionId 单测：`question.asked` 解析出 `sessionID` 字段（`OpenCodeEventLoopParseTest`：16 通过）；translator 用 dataTalkSessionId 落地
- [x] 3.4 序列化往返测试：已写入 `DtEventJsonTest.questionEvents_roundTrip`（断言 `@JsonTypeName` 生效）。⚠️ domain 测试模块当前因**无关的 bezel/dashboard WIP**（`DashboardJacksonTest` 引用已删符号）编译失败，本测试暂无法经 Maven 执行；待 dashboard 重构方修复测试编译后纳入全量 verify
- [x] 3.5 REST 代理契约测试：HTTP 层 reply body `string[][]` 形状（`OpenCodeHttpClientTest`：20 通过）+ controller 层过滤/re-stamp/转发（`QuestionControllerTest`：4 通过）
- [x] 3.6 `mvn verify`（2026-05-21）—— dashboard WIP 阻塞已解除，domain/application/infrastructure 全绿（含本 change 的 `DtEventJsonTest.questionEvents_roundTrip`），adapter 379 测试仅 2 个 error 全在**无关且既存**的 `ReportControllerCorsTest`（commit bb80213f 给 `ReportController` 加 `SessionWorkdirRoot` 依赖、其 `@WebMvcTest` 切片未提供该 bean，非本 change 引入）。本 change 全部测试类绿（application 36 / infra 20 / adapter `QuestionControllerTest` 4 / domain 往返序列化）

## 4. 前端状态层

- [x] 4.1 新增 `question-store`（Zustand）：`Map<sessionId, QuestionRequest[]>`，提供 upsert(by requestId) / removeByRequestId / setForSession
- [x] 4.2 `event-reducer.ts` 处理 `QuestionAsked`（upsert）/ `QuestionReplied` / `QuestionRejected`（remove）
- [x] 4.3 新增 API client：列举挂起问题、reply、reject
- [x] 4.4 订阅 session / 挂载 composer 时调列举端点重建 store（刷新/重连恢复）。明确触发时机（重连/首次挂载，避免每次切 session 都拉）；`list` 重建与并发 `asked` 事件须按 `requestId` upsert 去重，不得重复插入
- [x] 4.5 `npx tsc --noEmit` 确认零类型错误

## 5. 前端 QuestionDock 组件

- [x] 5.1 移植 `session-question-dock.tsx` → React `QuestionDock`（逐题翻页 + 进度点 + radio/checkbox 选项 + 自定义答案 textarea + 逐题答案缓存）
- [x] 5.2 底部操作：Dismiss(reject) / Back / Next / Submit；单问题非多选走「选中即提交」捷径
- [x] 5.3 键盘流：↑↓/←→ 选择、Cmd/Ctrl+Enter 推进或提交、Esc 略过
- [x] 5.4 提交映射 `answers = questions.map((_,i)=>store.answers[i] ?? [])` → reply；Dismiss → reject
- [x] 5.5 按 `client/DESIGN.md` 应用令牌：`bg.panel`/`border.default`/`focusRing`，选中态带 radio·check 标记（不靠颜色），`accent.primary` 仅用于 Submit/选中，`prefers-reduced-motion` 退化
- [x] 5.6 `npx tsc --noEmit` 确认零类型错误

## 6. composer 集成与工具卡呈现

- [x] 6.1 `prompt-composer.tsx`：`pending = questionStore[activeSessionId]?.[0]`，存在时在 composer-slot 渲染 `<QuestionDock>` 取代 textarea，无则恢复
- [x] 6.2 确认 `assistant-stream.tsx:38` 维持隐藏 running question part；改造 `question.tsx` 的 completed 渲染——当前只读 `input.question`（单串）+ `output.answer`（单串），须适配**多子问题**（逐题展示 question + 用户所选 label 数组 / 自定义文本），与 `answers: string[][]` 形状一致，避免多问题时显示残缺。⚠️ 渲染器须在 `renderers/index.ts` 的 `registerBuiltInRenderers()` 调 `ToolRegistry.register('question', Question)`，否则回退 GenericTool（E2E 发现的 BUG-0080，已修）
- [x] 6.3 校验挂起期间不与 `isStreaming` 判定冲突（dock 仅为 slot 内条件分支）

## 7. 前端测试

- [x] 7.1 `question-store` + 事件 sink 单测：asked upsert / replied·rejected remove / setForSession 重建 / dedup（`question-store.test.ts`：7 通过，含 `buildEventSink` 三事件 3 通过）
- [x] 7.2 `QuestionDock` 组件测试：单问题选中即提交、多选 toggle/untoggle、Submit 映射 `string[][]`、Dismiss(reject)、Esc reject（`question-dock.test.tsx`：5 通过）
- [x] 7.3 composer 集成：「回答后恢复 composer」核心机制（store 移除→`pending` 翻空→composer 复位）由 7.2 的 store 移除断言证明；完整 `PromptComposer` portal 渲染在 jsdom 下脆弱（portal + 庞大 hook 图，[[feedback-jsdom-layout-not-truth]]），完整流程已由 8.1 E2E 真实验证通过（answer + reject 双路径 composer 均正确恢复）

## 8. 端到端验证与收尾

- [x] 8.1 playwright-cli 端到端（2026-05-21，前后端均在线，模型 qwen3.6-plus）—— 全链路通过：触发真实 AI `question` → QuestionDock 取代输入框 → 单问题选「是」即提交 → turn 继续（AI 回复）→ composer 恢复 → 完成态卡片渲染问题+所选 label「是」→ CTRL+R 重建会话正常；reject 路径：Dismiss → dock 移除 → composer 恢复 → 完成态卡渲染「Unanswered」。控制台零错误。发现并就地修复 BUG-0080（完成态 question 卡未注册自定义渲染器，回退 GenericTool）—— `renderers/index.ts` 补 `ToolRegistry.register('question', Question)`
- [x] 8.2 i18n：dock + question.tsx 全部文案接入 i18n（zh-CN/en-US 双语种新增 `session.question.*` / `ui.common.*` / `ui.question.*` 键，无硬编码）
- [x] 8.3 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`：本 change 不涉数据源类型，proposal Impact 已逐项标注 **N/A**（无连接 UI / JDBC / schema / SQL 执行拆分 / 诊断 / MCP schema / runtime prompt 改动）
