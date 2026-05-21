## Why

OpenCode 的内置 `question` 工具（人机交互确认）目前在 DataTalk 里是**死路**：工具调用阻塞在 OpenCode 进程内的 `Deferred` 上等待回答，而 DataTalk 既没桥接 `question.asked` 事件、也没代理 reply/reject 端点，前端还把 running 的 question part 直接隐藏。结果是 AI 一旦提问，整个 turn 永久阻塞（`isStreaming` 恒真、主输入框锁死），只能等超时或中止。ledger 报告技能第一步「确认报告目的与时间窗口」强依赖这个能力，因此它是 load-bearing 的缺口。

## What Changes

- **桥接 OpenCode question 事件流**：在事件解码 / 翻译链路新增 `question.asked` / `question.replied` / `question.rejected`，一路透传到客户端 SSE。
- **代理 question REST 端点**：新增 `GET 列挂起问题`、`POST reply`、`POST reject`，转发到 OpenCode 的 `/question` 系列端点。
- **前端复刻 OpenCode app web 的 question 子系统**：新增按 session 维护的挂起问题 store（bootstrap 列举 + 事件增删），并新增 `QuestionDock` 组件——挂起期间替换主 composer，提供选项（单选/多选）+「自定义答案」文本输入 + 逐题翻页 + 提交/上一题/下一题/略过，提交走 reply、略过走 reject。
- **composer 阻塞语义**：本 session 有挂起问题时隐藏主输入框、由 dock 接管；回答后 turn 继续、`isStreaming` 随下一个 idle 自然翻转。
- 行为对齐 OpenCode app web 端实现，**不修改任何模型 prompt / skill 文本 / question 工具描述**——模型「何时提问」的行为保持不变。

## Capabilities

### New Capabilities
- `ai-question-dock`: AI 通过 OpenCode `question` 工具发起结构化提问时，客户端在 composer 位置呈现可交互的问答 dock（选项 + 自定义文本），用户的回答经 reply 端点回传以解除 turn 阻塞；略过经 reject 回传。覆盖事件桥接、REST 代理、前端挂起问题状态与 dock 交互。

### Modified Capabilities
<!-- 无现有 capability 的需求级行为变化。composer-streaming-state 的 streaming 判定不变，仅新增「挂起问题时由 dock 接管输入」这一与之正交的呈现分支。 -->

## Impact

- **domain**：`DtEvent` sealed interface 新增 3 个 question 事件 record —— 每个 record **必须同时**标注 `@JsonTypeName`（否则 `DtEventTypeIdResolver.init` 运行期抛 `IllegalStateException`，**编译期不报错**）并补进 `typeName()` exhaustive switch（编译期强制）；并排查 `OpenCodeEventTranslator` 的映射 switch。
- **application**：`OcEvent` 新增 3 个 variant；`OpenCodeEventLoop` 解码 `switch(name)` 与 `extractSessionId`（带 `default`、非 exhaustive，须手动补 case，sessionId 来源待 0.1 核实）；`OpenCodeEventTranslator` 映射新增分支；`OpenCodeGateway` 新增 question 端口（functional interface，对齐 abort/sendMessage 既有模式）。
- **infrastructure / adapter**：`OpenCodeHttpClient` 新增 `listQuestions` / `questionReply` / `questionReject`；`OpenCodeGatewayBeans` 接线新端口；新增 question REST controller（adapter 经 gateway 端口调用，**不直连 infra 具体类**）。
- **client**：新增 question store + `event-reducer` 处理；`prompt-composer.tsx` 新增「挂起问题→渲染 dock」分支；新增 `QuestionDock` 组件；`assistant-stream.tsx` 维持隐藏 running question part。
- **协议**：复用现有 Streamable HTTP + SSE 通道下发事件；reply/reject 为新的 REST 往返（不经 action.invoke）。
- **不涉及**数据库 / 数据源类型变更（`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 各项 **N/A**：无连接 UI、JDBC、schema、SQL 执行/拆分、诊断、MCP schema、runtime prompt 改动）。

## Design Inputs (client/DESIGN.md)

- **Composer 令牌**：dock 占据 composer 位置，沿用 `components.composer` —— `bg.panel` / `border.default` / `interaction.focusRing`（DESIGN.md L222-225, L313）。
- **选中态不靠颜色**：选项的单选/多选选中状态必须带 radio dot / check 标记，不能仅靠 `interaction.selected` 颜色区分（L288, L335 "State cannot be communicated by color alone"）。
- **accent 克制**：`accent.primary` 仅用于主操作（Submit）与选中强调，不滥用（L279, L353）。
- **键盘可达**：dock 必须支持键盘操作（↑↓ 选择、Cmd/Ctrl+Enter 推进/提交、Esc 略过），与「Keyboard access must cover composer」一致（L336）。
- **动效即确认 + reduced-motion**：dock 进出 / 提交反馈用 `motion.*` 令牌，`prefers-reduced-motion` 下退化（L263, L337）。
- **密度**：dock 属 comfortable 密度（对话 lane 内的工作控件，L206）。

## Risks / Known Issues

- **composer streaming 状态历史脆弱**：composer 与 streaming flag 的交互有多起历史 BUG（BUG-0037/0038/0046/0052，刷新/replay 时 stop↔send 翻转、streaming flag race）。本change 在「挂起问题」期间改变 composer 呈现（dock 接管），必须确保刷新（CTRL+R）/ 重连 replay 时 dock 能从 `GET 列挂起问题` 正确重建，且不与 streaming flag 判定打架。
- **多子问题 + 自定义文本** 的 `answers: string[][]` 映射需与 OpenCode 端格式严格一致（每子问题一个 label 数组，自定义文本作为 label 进入对应数组），否则 reply 被 OpenCode 拒绝或模型误读。
- 当前 composer 仍有未关闭 BUG（BUG-0057/0059/0063，文件 chip / eager upload），与本change 正交，但改 `prompt-composer.tsx` 时需避免回归这些路径。
- ~~**`question.asked` 的 sessionId 来源未坐实**~~ **已核实关闭**：OpenCode 源码确认三个 question 事件 payload 均直接含 `sessionID`，`extractSessionId` 直接取字段即可，无需反查映射（详见 design.md「Contract Findings」）。注意 requestId 字段名陷阱：`asked`=`id`、`replied`/`rejected`=`requestID`。
