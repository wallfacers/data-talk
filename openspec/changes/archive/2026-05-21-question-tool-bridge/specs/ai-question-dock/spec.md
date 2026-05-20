## ADDED Requirements

### Requirement: 桥接 OpenCode question 事件

系统 SHALL 将 OpenCode 的 `question.asked` / `question.replied` / `question.rejected` 事件解码、翻译为对应的 `DtEvent`，并经现有 SSE 通道下发到客户端。`question.asked` 事件 MUST 携带 `requestId`、`sessionId`、子问题列表（含每个问题的 header / question / options / multiple / custom）以及关联的工具 `messageId` 与 `callId`。

#### Scenario: question.asked 翻译并下发
- **WHEN** OpenCode 发出一个 `question.asked` 事件
- **THEN** 系统将其翻译为 `DtEvent.QuestionAsked` 并经 SSE 推送给订阅该 session 的客户端，载荷包含 `requestId`、`sessionId`、子问题列表与关联的 `callId`

#### Scenario: question.replied / rejected 翻译并下发
- **WHEN** 某个挂起问题被 reply 或 reject
- **THEN** 系统分别下发 `DtEvent.QuestionReplied` / `DtEvent.QuestionRejected`，载荷至少包含 `sessionId` 与 `requestId`

#### Scenario: 未知事件不再误丢 question 事件
- **WHEN** 解码 `question.asked` / `question.replied` / `question.rejected`
- **THEN** 它们 MUST 命中专门的 `OcEvent` variant，而非落入 `OcEvent.Unknown` 被丢弃

### Requirement: question REST 代理端点

系统 SHALL 提供代理端点以列举挂起问题并提交回答/拒绝，转发到 OpenCode 的 `/question` 系列端点。

#### Scenario: 列举挂起问题
- **WHEN** 客户端请求列举某 session 的挂起问题
- **THEN** 系统返回该 session 当前所有挂起的 question 请求（每项含 `requestId` 与子问题列表）

#### Scenario: 提交回答
- **WHEN** 客户端对某 `requestId` 提交 `answers`（`string[][]`，长度等于子问题数）
- **THEN** 系统转发到 OpenCode reply 端点，使对应的 question 解除阻塞，turn 得以继续

#### Scenario: 拒绝/略过
- **WHEN** 客户端对某 `requestId` 提交拒绝
- **THEN** 系统转发到 OpenCode reject 端点，使该 question 以「用户略过」语义解除阻塞

#### Scenario: answers 形状契约
- **WHEN** 提交回答
- **THEN** `answers` MUST 为每个子问题提供一个 label 字符串数组；自定义文本作为一个 label 进入对应子问题的数组；未回答的子问题对应空数组

### Requirement: 挂起问题客户端状态

客户端 SHALL 按 session 维护挂起问题集合：bootstrap / 重连时经列举端点重建，运行时由 `question.asked` 新增、由 `question.replied` / `question.rejected` 移除。

#### Scenario: 收到 asked 后记录
- **WHEN** 客户端收到 `QuestionAsked` 事件
- **THEN** 该 question 被加入对应 session 的挂起集合（按 `requestId` 去重 upsert）

#### Scenario: 收到 replied/rejected 后移除
- **WHEN** 客户端收到 `QuestionReplied` 或 `QuestionRejected` 事件
- **THEN** 对应 `requestId` 的 question 从挂起集合移除

#### Scenario: 刷新/重连后重建
- **WHEN** 客户端刷新（CTRL+R）或重连，且 OpenCode 侧仍有挂起问题
- **THEN** 客户端经列举端点重建挂起集合，dock 恢复显示

### Requirement: Question Dock 取代 composer 呈现

当本 session 存在挂起问题时，客户端 SHALL 在 composer 位置呈现 Question Dock 取代普通输入框；无挂起问题时恢复普通 composer。Dock 必须支持选项选择（单选/多选）与自定义文本输入，并按 `client/DESIGN.md` 约束呈现。

#### Scenario: 挂起时显示 dock
- **WHEN** 本 session 有挂起问题
- **THEN** 主输入框被 dock 取代，dock 展示当前问题的文本、选项与「自定义答案」输入

#### Scenario: 选中态可辨识（不依赖颜色）
- **WHEN** 用户选中某选项
- **THEN** 选中状态以 radio dot / check 标记呈现，不仅依赖颜色区分

#### Scenario: 键盘可操作
- **WHEN** 焦点在 dock 内
- **THEN** 用户可用方向键移动选项、Cmd/Ctrl+Enter 推进或提交、Esc 略过

#### Scenario: 提交后恢复 composer
- **WHEN** 用户提交回答或略过，且对应 question 被移除
- **THEN** dock 关闭，普通 composer 恢复，AI turn 继续

### Requirement: running question 工具卡呈现

客户端 SHALL 在 question 工具处于 pending/running 时不在消息流中渲染该工具卡（由 dock 接管交互），并在其 completed 后展示用户的回答。

#### Scenario: running 时隐藏
- **WHEN** question 工具 part 状态为 pending 或 running
- **THEN** 它不在 assistant 消息流中渲染

#### Scenario: completed 时显示答案
- **WHEN** question 工具 part 变为 completed
- **THEN** 消息流展示该问题与用户提交的答案
