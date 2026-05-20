## 1. 后端事件桥接（domain → application）

- [ ] 1.1 `OcEvent.java` 新增 `QuestionAsked` / `QuestionReplied` / `QuestionRejected` 三个 variant（携带 requestId、sessionId、子问题 JsonNode、关联 callId/messageId）
- [ ] 1.2 `OpenCodeEventLoop` 解码 `switch(name)` 新增 `question.asked` / `question.replied` / `question.rejected` 分支，并在 sessionId 解析处补齐对应 case
- [ ] 1.3 `DtEvent.java` sealed interface 新增对应 3 个 record
- [ ] 1.4 grep 全部 `switch` over `DtEvent` 及 JSON-RPC 序列化处，补齐 exhaustive 分支（已知：`OpenCodeEventTranslator`）
- [ ] 1.5 `OpenCodeEventTranslator` 新增 `case OcEvent.QuestionAsked/Replied/Rejected -> DtEvent.*` 映射
- [ ] 1.6 `mvn install -pl data-talk-domain -am -DskipTests` + `mvn compile -q` 确认零编译错误

## 2. 后端 REST 代理（infrastructure → adapter）

- [ ] 2.1 `OpenCodeHttpClient` 新增 `listQuestions(sessionId?)` / `questionReply(requestId, answers)` / `questionReject(requestId)`，透传 OpenCode `GET /question`、`POST /question/{id}/reply|reject`
- [ ] 2.2 新增 adapter question REST controller：`GET …/questions`、`POST …/questions/{requestId}/reply`、`…/reject`
- [ ] 2.3 `mvn compile -q` 确认零编译错误

## 3. 后端测试

- [ ] 3.1 WireMock(FakeOpenCodeServer) 增加 `/question`、`/question/:id/reply`、`/reject` stub
- [ ] 3.2 翻译链路单测：`question.asked/replied/rejected` → 正确 `DtEvent` 且不落 Unknown
- [ ] 3.3 REST 代理契约测试：reply body 形状为 `string[][]`、正确转发与状态码
- [ ] 3.4 `mvn verify` 全测通过

## 4. 前端状态层

- [ ] 4.1 新增 `question-store`（Zustand）：`Map<sessionId, QuestionRequest[]>`，提供 upsert(by requestId) / removeByRequestId / setForSession
- [ ] 4.2 `event-reducer.ts` 处理 `QuestionAsked`（upsert）/ `QuestionReplied` / `QuestionRejected`（remove）
- [ ] 4.3 新增 API client：列举挂起问题、reply、reject
- [ ] 4.4 订阅 session / 挂载 composer 时调列举端点重建 store（刷新/重连恢复）
- [ ] 4.5 `npx tsc --noEmit` 确认零类型错误

## 5. 前端 QuestionDock 组件

- [ ] 5.1 移植 `session-question-dock.tsx` → React `QuestionDock`（逐题翻页 + 进度点 + radio/checkbox 选项 + 自定义答案 textarea + 逐题答案缓存）
- [ ] 5.2 底部操作：Dismiss(reject) / Back / Next / Submit；单问题非多选走「选中即提交」捷径
- [ ] 5.3 键盘流：↑↓/←→ 选择、Cmd/Ctrl+Enter 推进或提交、Esc 略过
- [ ] 5.4 提交映射 `answers = questions.map((_,i)=>store.answers[i] ?? [])` → reply；Dismiss → reject
- [ ] 5.5 按 `client/DESIGN.md` 应用令牌：`bg.panel`/`border.default`/`focusRing`，选中态带 radio·check 标记（不靠颜色），`accent.primary` 仅用于 Submit/选中，`prefers-reduced-motion` 退化
- [ ] 5.6 `npx tsc --noEmit` 确认零类型错误

## 6. composer 集成与工具卡呈现

- [ ] 6.1 `prompt-composer.tsx`：`pending = questionStore[activeSessionId]?.[0]`，存在时在 composer-slot 渲染 `<QuestionDock>` 取代 textarea，无则恢复
- [ ] 6.2 确认 `assistant-stream.tsx:38` 维持隐藏 running question part；completed 后 `question.tsx` 正确显示答案
- [ ] 6.3 校验挂起期间不与 `isStreaming` 判定冲突（dock 仅为 slot 内条件分支）

## 7. 前端测试

- [ ] 7.1 `question-store` + `event-reducer` 单测：asked upsert / replied·rejected remove / 重建
- [ ] 7.2 `QuestionDock` 组件测试：单选即提交、多选 toggle、自定义文本、翻页缓存、键盘流、reject
- [ ] 7.3 composer 集成测试：挂起时显示 dock、回答后恢复 composer

## 8. 端到端验证与收尾

- [ ] 8.1 playwright-cli 端到端：触发 AI 提问 → dock 出现 → 选项+自定义回答 → turn 继续；并验证 CTRL+R 刷新后 dock 重建。按 BUG 追踪门禁登记本轮发现
- [ ] 8.2 i18n：dock 全部文案接入 i18n（无硬编码中文/英文）
- [ ] 8.3 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 各检查项标注 N/A（本change 不涉数据源类型）
