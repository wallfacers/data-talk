# 执行计划跟踪器

所有执行计划的集中索引。计划是一等工件，进行版本控制。

## 活跃计划

| 计划 | 创建日期 | 摘要 |
|------|---------|------|

## 已完成计划

| 计划 | 完成日期 | 摘要 |
|------|---------|------|
| [SQL Editor Selection Run And Result Scroll](./2026-04-25-sql-editor-selection-run-result-scroll-plan.md) | 2026-04-25 | SQL 编辑器已支持工具栏与 `Ctrl/Cmd+Enter` 精确执行非空 Monaco 选区；无选区仍执行全文；多结果集按 `resultId` 独立保存并恢复上下、左右滚动位置。 |
| [Streaming Code Block Jitter](./2026-04-24-streaming-code-block-jitter-plan.md) | 2026-04-24 | assistant 流式 fenced code block 底部抖动已按“结构滚动版本 + 内容增长 observer/RAF + 未闭合代码围栏稳定 DOM”收口；后续移除 streaming-only `pre` 高度规则，并改为所有 code window 共享 `pre > code` 一行正文保底，使未闭合与完成态高度模型一致；前端 focused/full vitest、`npx tsc --noEmit` 与 `git diff --check` 通过，真实 DeepSeek/OpenCode live smoke 作为本地产品复测项记录在计划内。 |
| [User Bubble Scroll Jitter](./2026-04-24-user-bubble-scroll-jitter-plan.md) | 2026-04-24 | 修复聊天区存在滚动条时，用户新发送气泡在底部出现时的垂直位置抖动；在保留 `useAutoScroll` 同轮 observer 去重的同时，`SessionTurn` 现在会在 pending 阶段先渲染与真实 thinking indicator 同构的隐形壳，并禁止上一条未 completed 的 assistant turn 在下一次发送时突然长出 `Copy` footer。 |
| [Chat Jitter DeepSeek Alignment](./2026-04-24-chat-jitter-deepseek-alignment-plan.md) | 2026-04-24 | 聊天区发送抖动已按“先锁 turn 高度、再压缩 follow 触发”的窄范围策略收口：`SessionTurn` 现只在 assistant turn settled 后挂 footer/meta，`useAutoScroll` 将 mutation follow 合并到 `requestAnimationFrame`，`PacedMarkdown` 在 fenced code 流式阶段跳过本地 staged reveal；相关 chat 回归、`npx tsc --noEmit` 与人工复测已完成，无需额外 virtualization 计划。 |
| [Chat Tool Trigger Name Only](./2026-04-24-chat-tool-trigger-name-only-plan.md) | 2026-04-24 | 聊天区工具调用卡片的 trigger 已进一步收紧为只显示工具名称，所有输入参数统一下沉到展开内容；`object=workspace`、`action=open` 以及 `__dt*` 系统参数现在都只在展开后可见。 |
| [Reasoning Placeholder Flicker](./2026-04-24-reasoning-placeholder-flicker-plan.md) | 2026-04-24 | 修复聊天区“思考中…”在 reasoning 首包阶段因全局占位与 reasoning 占位快速交替而产生的抖动；空 reasoning shell 现在不会抢占 `SessionTurn` 占位，直到 reasoning 真正收到文本才接管界面。 |
| [Chat Tool Trigger System Args Suppression](./2026-04-24-chat-tool-trigger-system-args-suppression-plan.md) | 2026-04-24 | 聊天区工具调用卡片的 trigger 已进一步收紧为只保留工具主名称和普通可读参数，不再显示系统 bridge 参数名；完整 `key=value` 继续只在展开内容中可见。 |
| [Chat Tool System Arg Folding](./2026-04-24-chat-tool-system-arg-folding-plan.md) | 2026-04-24 | 聊天区工具调用卡片现在会把系统级长参数在 trigger 中折叠为名称展示，完整 `key=value` 下沉到展开内容；`GenericTool` 新增回归测试并保持普通短参数 `key=value` 呈现不变。 |
| [Chat Tool Call Overflow](./2026-04-24-chat-tool-call-overflow-plan.md) | 2026-04-24 | 修复聊天区工具调用卡片在长参数（如 session/call/nonce）场景下的触发行内容溢出；`BasicTool` 现将标题/副标题与参数分层排版，长参数可在卡片内断行，并补充前端回归测试。 |
| [OpenCode MCP Tool Migration](./2026-04-24-opencode-mcp-tool-migration-plan.md) | 2026-04-24 | DataTalk 已切到 MCP 单路径：后端新增 `/mcp` + nonce/session bridge、managed bootstrap/external reconcile、health degraded 状态；前端切换 `datatalk_*` renderer/prompt naming 并接入 degraded surface；legacy `/plugin/register-tool` / `/api/opencode-tool/*` / `shared-secret` callback 链路已删除。后端 `mvn compile -q`、定向 JUnit smoke，前端 vitest 与 `npx tsc --noEmit` 通过。 |
| [Reasoning Placeholder Chevron Sync](./2026-04-24-reasoning-placeholder-chevron-sync-plan.md) | 2026-04-24 | 修复 AI 首包占位“思考中…”箭头方向与“思考中自动展开”设置不同步的问题；`SessionTurn` 现直接消费 `autoExpandReasoning` 控制占位态 Chevron，并补充对应前端回归测试。 |
| [Reasoning Auto-Expand Setting](./2026-04-24-reasoning-auto-expand-setting-plan.md) | 2026-04-24 | `设置 > 通用` 新增“思考中自动展开”开关；默认关闭；关闭时 reasoning 面板在思考期间不自动展开；打开时思考开始自动展开；无论配置如何，思考完成后统一自动收起。 |
| [Agent Prompt Registry Alignment](./2026-04-24-agent-prompt-registry-alignment-plan.md) | 2026-04-24 | 运行时 `AGENTS.md` 已重写为精简英文版本，并收敛到当前真实可调用 action；未实现的 `workspace.open` 场景与误导性 chart/pin 描述已删除。生产工具面移除了遗留 `datatalk.demo.echo`，并补充了 prompt/action/client-handler 对齐回归测试；`mvn compile -q`、adapter 定向 JUnit、前端 vitest 与 `npx tsc --noEmit` 通过。 |
| [AI Text-to-Chart Fence](./2026-04-23-ai-text-to-chart-fence-plan.md) | 2026-04-24 | 聊天 ` ```chart` 围栏 + ECharts JSON 内联渲染已落地（含流式骨架、展开与复制、打开到工作台提升）；后端新增 `ChartArtifactService` 与 `POST /api/sessions/{id}/artifacts/chart`，`Artifact` 增加 `originMessageId / originPartId` 并贯穿事件/历史回放；Stage `ChartArtifact` 已改为共享 `echarts-for-react` 渲染器。2026-04-24 复核补齐 source/supersedes 语义、历史 artifact payload、前端错误韧性、V11 schema 文档和 `pnpm-lock` 去 `recharts`；`mvn -q clean verify`、`npx tsc --noEmit`、`npx vitest run` 通过，Tauri 桌面 smoke 保留为人工产品复测项。 |
| [Clear All Sessions](./2026-04-24-clear-all-sessions-plan.md) | 2026-04-24 | 设置-通用新增“清空全部会话”危险操作入口与确认弹窗；后端新增 `DELETE /api/sessions` 并复用单会话删除顺序清理 OpenCode 映射/会话与 SessionBus，依赖 FK 级联删除 artifacts/events/synthetic/session_data_context 等资源；前端调用后同步清空本地会话资源状态并自动拉起空白会话。 |
| [Implementation Roadmap](./2026-04-21-implementation-roadmap-plan.md) | 2026-04-23 | 总排期文档已完成 Batch E 收口：活跃/完成状态治理同步、历史债务文档治理结论落地（`ui-demo-stage-animation-debt` 标注 stale，live residue 迁回 `TD-026`）、二期开工前置条件确认完成并明确按单能力立项。 |
| [Composer Data Source Picker](./2026-04-20-composer-data-source-picker-plan.md) | 2026-04-23 | Composer 数据源选择器主实现、自动化回归与手动 smoke（6 个场景）均已收口；缺库自动拉起 chooser 并恢复动作、Stage 来源固化与“用此数据源继续”、`ui_exec(workspace, choose_connection)` 均已验证，文档状态同步完成。 |
| [SQL Tab Internal Activity Rail](./2026-04-23-sql-tab-internal-rail-plan.md) | 2026-04-23 | rail 已下移到 `SqlWorkbenchTab`，并完成 `src/features/stage` + `npm test` 自动化回归与手动视觉 smoke checklist；`query_editor` 保留 rail，`file_preview` 不再显示，文档状态已同步收口。 |
| [SQL Risk Classification & IT CI Gate](./2026-04-20-sql-risk-classification-it-ci-gate-plan.md) | 2026-04-23 | `TD-020` / `TD-021` 已收口：`ActionDispatcher` 动态 SQL 风险判级链路与 adapter failsafe 门禁已落地；2026-04-23 复跑 `mvn compile -q`、`mvn -q -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest,ActionDispatcherTest`、`mvn -q -pl data-talk-adapter -am verify` 均通过，`*IT.java` 已纳入 verify。 |
| [SQL Context Popover Schema Visibility](./2026-04-23-sql-context-popover-schema-visibility-plan.md) | 2026-04-23 | SQL 编辑器会话上下文小弹窗现已将 `Database` / `Schema` 改为可回显下拉；`Schema` 会按连接类型显隐，并对历史 schema 值保留兼容显示。目标 `vitest` 28 测试与 `npx tsc --noEmit` 通过。 |
| [Query Editor Object Actions](./2026-04-23-query-editor-object-actions-plan.md) | 2026-04-23 | `query_editor` 已收敛为由 `StageStore` 统一打开、命名、聚焦和编辑的对象；`WorkspaceAdapter` / `QueryEditorAdapter` 对 AI 暴露稳定的 `state + actions + capabilities`、`ui_patch(/content)` 与 `apply_text_edits` 文件式语义；`UIRouter`/client action pipeline 现保留结构化错误 detail，并同步更新 `ui-objects-reference` 与运行时 `AGENTS.md`。 |
| [Client Design System Foundation](./2026-04-23-client-design-system-foundation-plan.md) | 2026-04-23 | `client/DESIGN.md`、semantic token 映射、Button/InputGroup/Table、Sidebar、PromptComposer、Stage Foundation 表面已落地；direct `designmd` lint `errors = 0`（23 条 alias-schema warning 为已知工具边界），目标 vitest 5 文件 43 测试通过，`npx tsc --noEmit` 通过。 |
| [Chat Auto-Scroll Reentry](./2026-04-23-chat-auto-scroll-reentry-plan.md) | 2026-04-23 | 聊天区 auto-follow 状态机已修正：用户只要主动向上滚离开底部，后续流式更新不再强制滚底；只有重新回到底部后才恢复自动滚动。新增 hook 回归测试，`split-view` 相关测试与 `npx tsc --noEmit` 全部通过。 |
| [Stage UI Object Protocol Phase 1](./2026-04-20-stage-ui-object-protocol-plan.md) | 2026-04-23 | 前端 `UIRouter` + 4 个 CLIENT Action 桥接、`StageStore` 多 Tab、`WorkspaceAdapter` / `QueryEditorAdapter` 对象面、Composer `!` direct SQL → `query_editor` 打开链路与后端 `/api/query` + `SqlStatementGuard` 已全部落地；2026-04-23 手动联调、`npx tsc --noEmit`、`npx vitest run` 与 `mvn clean verify` 通过，计划与 `ARCHITECTURE.md` 已同步收口。 |
| [Read File Preview In Session Stage](./2026-04-22-read-file-preview-plan.md) | 2026-04-22 | `read` 文件结果现支持专属聊天 renderer 与当前会话 Stage 文件预览：点击后创建或聚焦 `file_preview` Tab，以只读 Monaco 高亮固定 `<path><type>file</type><content>` 形态中的正文；相关 62 个前端测试与 `npx tsc --noEmit` 通过。 |
| [Java 21 Build Guard](./2026-04-22-java21-build-guard-plan.md) | 2026-04-22 | `server` 父 POM 已增加 Java 21 fail-fast enforcer，JDK 8 现在会在 `validate` 阶段直接提示“DataTalk server build requires Java 21”，不再把 Java 21 语法误报成源码错误；同时修正了此前错误归因写入的计划说明与技术债记录。 |
| [SQL Error Markdown Diagnostics](./2026-04-22-sql-error-markdown-plan.md) | 2026-04-22 | `/api/sql/execute` 的连接级失败现在返回包含连接上下文、异常类型、驱动原始消息和排查建议的 Markdown 诊断块；Stage 错误 Tab 改为嵌入共享 Markdown renderer，并统一去掉纯文本居中布局。前端相关 vitest 与 `npx tsc --noEmit` 已通过；后端验证在切到 JDK 21 后可正常运行。 |
| [Stage SQL Editor Format](./2026-04-22-stage-sql-editor-format-plan.md) | 2026-04-22 | Stage Query Editor 已接入统一 SQL 格式化链路：新增 `format-sql` helper 封装 `sql-formatter` 与方言映射，工具栏 `Format` 与 `Cmd/Ctrl + Shift + F` 共用同一实现；相关 14 个 vitest 测试与 `npx tsc --noEmit` 通过。 |
| [Workspace And Backend I18n](./2026-04-22-workspace-backend-i18n-plan.md) | 2026-04-22 | 工作台相关前端静态文案、后端 action/object 显示名、SQL 结果标题与关键错误消息已全部接入双语 i18n；前端 stage 相关 vitest 与 `npx tsc --noEmit`、后端定向 JUnit/IT 与 `mvn compile -q` 通过。 |
| [Stage SQL Workbench Polish](./2026-04-22-stage-sql-workbench-polish-plan.md) | 2026-04-22 | 完成 Stage SQL 打磨：移除旧左侧资源栏并改为右侧 Activity Rail（Schema/History/Outline/AI），接入工具栏 Run/Cancel/Format/Limit/Save、tab override 上下文、Monaco 轮廓解析与 breadcrumb、状态栏、AI Assist 独立会话与会话列表过滤；相关 Stage/adapter/store/hook/workspace 测试全绿且 `npx tsc --noEmit` 通过。 |
| [PostgreSQL SQL Splitter](./2026-04-22-postgres-sql-splitter-plan.md) | 2026-04-22 | `/api/sql/execute` 已接入方言化 SQL splitter：`SqlExecuteService` 改为依赖 `SqlStatementSplitters`，PostgreSQL 连接走 PgJDBC parser，其他方言走 generic splitter；splitter 单测全绿，`SqlExecuteControllerIT` 通过且 PostgreSQL procedural case 在无 Docker 环境下自动跳过。 |
| [Stage SQL Workbench Rebuild](./2026-04-21-stage-sql-workbench-rebuild-plan.md) | 2026-04-21 | Stage SQL 主线已完成替换：前端迁移 Monaco + 编辑器工作区 + 结果集 Tab，后端将 `/api/sql/execute` 重构为多语句 / 多结果契约，并删除旧的非 SQL Stage 页面渲染路径。 |
| [Stage Window SQL Workbench](./2026-04-21-stage-window-sql-workbench-plan.md) | 2026-04-21 | Stage 升级为基于 shadcn/ui 的多面板 SQL 工作台，`query_editor` 成为唯一 SQL 工作页，`bang_query` 页面 / adapter / helper 退场，`resources/agents/AGENTS.md` 与 UI Object 协议文档同步完成；相关 vitest 与 `npx tsc --noEmit` 通过。 |
| [Session Data Context & AI Data Source Management](./2026-04-21-session-data-context-and-ai-datasource-management-plan.md) | 2026-04-21 | 建立 session 级 `connectionId + database + schema` 统一上下文，打通 `ResolvedExecutionContext`、`/api/query` / `/api/sql/execute` 自动表定位、前端 `!use/!select` / Query Editor / Bang Query 上下文继承、AI data-context / connection actions 以及连接更新后的 validate + 前端刷新；Batch E 追加修复旧 adapter IT 基线后，`cd server && mvn clean verify`、前端宽覆盖 vitest 与 `npx tsc --noEmit` 全部通过。 |
| [Send Failure Draft Restore](./2026-04-21-send-failure-draft-restore-plan.md) | 2026-04-21 | 修复普通 AI 消息发送失败后只能靠刷新恢复输入的问题：`useChannel.sendMessage` 返回成功/失败，`PromptComposer` 与 `usePendingPromptResume` 在失败时立即回填 composer 文本；相关 vitest 与 `npx tsc --noEmit` 通过。 |
| [Chat Send Transition Smoothing](./2026-04-21-chat-send-transition-smoothing-plan.md) | 2026-04-21 | 修复 AI 输入框首发普通消息时先落 HERO 再切消息态造成的闪动：新建发送目标会话直接进入 `SPLIT`，pending 用户气泡增加 `motion-safe` 上移入场动画；相关 vitest 与 `npx tsc --noEmit` 通过。 |
| [Stage Window Layout Refactor](./2026-04-21-stage-window-layout-refactor-plan.md) | 2026-04-21 | Stage 结构完成从底部 Dock + pill tabs 到左侧 sidebar + 右侧 workbench 的重构：统一 open-or-focus helper、顶部轻量工具行、连接资源浏览器、Chrome-inspired 顶部页签、WorkspaceAdapter 入口统一、Dock 移除；`87` 个 Stage 相关测试与 `npx tsc --noEmit` 全部通过，手工桌面 smoke checklist 留给人工执行。 |
| [Design Doc Governance Sync](./2026-04-21-design-doc-governance-sync-plan.md) | 2026-04-21 | 同步 2026-04-20 / 2026-04-21 最近新增设计文档到 `docs/design-docs/index.md`，按执行计划状态补 `draft / approved / shipped` 标记，并对 `Stage Window Layout Refactor Design` 显式标明其仍是需求备忘 / 粗稿。 |
| [Stage Query Editor](./2026-04-21-stage-query-editor-plan.md) | 2026-04-21 | Stage 特性全量接通 useStageStore，新增 Query Editor tab（CodeMirror SQL 编辑器 + 结果面板 + 双路径执行），后端新增 `POST /api/sql/execute` 端点；关闭 TD-022 / TD-023。 |
| [OpenCode Event Loop Defenses](./2026-04-21-opencode-event-loop-defenses-plan.md) | 2026-04-21 | `OpenCodeEventLoop` 的 part 绑定索引改为带时间戳的惰性清理缓存，补上 `message.part.removed` 路由时序修复与孤儿 session WARN + 残留绑定清理；`DtEvent` 移除中央 `@JsonSubTypes` 注册，改用 `@JsonTypeName` + sealed subtype resolver。 |
| [Bang Query Badge Minimization](./2026-04-21-bang-query-badge-minimization-plan.md) | 2026-04-21 | 将 bang-query 用户气泡从显式 `SQL 直查` 文字 badge 调整为右上角低存在感小图标，减少视觉打扰而不改变消息语义。 |
| [Bang Query Chat Visibility](./2026-04-21-bang-query-chat-visibility-plan.md) | 2026-04-21 | 为 `!select` / `!with` 直查补齐聊天区可见性与持久化：后端持久化 synthetic user message 并与 OpenCode 历史稳定合并排序；前端将直查消息显示为带 `SQL 直查` 标记的普通用户气泡，并在 Composer 命中直查模式时进入变色感知态，同时 bang-query 路径不再依赖 AI model、当前会话即时显示且失败时清理新建空白会话。 |
| [Query Password Propagation](./2026-04-21-query-password-propagation-plan.md) | 2026-04-21 | 修复 `/api/query` 两处连接参数回归：`DbConnection` 新增运行时密码字段，`QueryApplicationService` 通过 `ConnectionService.decryptPassword()` 注入密码，`DynamicSqlExecutionRepository` 不再固定 `using password: NO`；同时 legacy 直查路径改为复用 `JdbcUrlBuilder`，避免 `databaseName = null` 时拼出字面量 `null`。 |
| [Data Source Refresh And Query Lookup](./2026-04-21-data-source-refresh-and-query-lookup-plan.md) | 2026-04-21 | 修复两个回归：前端 `useConnectionStore` 现在持久化 `activeConnectionId`，刷新页面后当前活动数据源不再丢失；后端 `/api/query` 改为读取现有 `connections` 仓储并兼容 `postgres`/`postgresql`，不再对有效数据源误报 `CONNECTION_NOT_FOUND`。 |
| [Session Review Follow-Ups](./2026-04-21-session-review-followups-plan.md) | 2026-04-21 | 修复 `session.created/deleted` 仅失效单一会话列表缓存的问题，并为后台 SSE 恢复增加消息历史活性校验：仅在历史仍存在未完成 assistant turn 时恢复后台订阅，否则清理过期 `streamingBySession`，避免重启后孤儿订阅。 |
| [Tech Debt Batch — Session Events / POST Timeout / SSE Pool](./2026-04-20-tech-debt-batch-plan.md) | 2026-04-20 | 清除 TD-001/013/014/015/016/017/TD-MULTI-SESSION-SSE-POOL 共 7 项：前端 `buildEventSink` 消费 session.error/created/deleted/compacted/diff；后端 POST 超时提取为可配置项；H2 datasource 语义注释明确；`BackgroundSubscriber` + `useBackgroundSessionSubscribe` 实现多 session SSE 订阅池。19 tests PASS，编译零错误。 |
| [AI Message Table Actions](./2026-04-20-ai-message-table-actions-plan.md) | 2026-04-20 | 统一 Markdown 表格升级为带动作栏的表格卡片，新增 `TableModel` / serializer 层，并支持复制表格、CSV、TSV、Markdown、JSON 与下载 CSV；`npx vitest run src/features/chat/components/markdown` 26 测试全绿，`npx tsc --noEmit` 通过。 |
| [AI Message Code Window and Table](./2026-04-20-ai-message-code-window-and-table-plan.md) | 2026-04-20 | 前端 Markdown 渲染链路统一升级：代码块收口为带顶部 chrome 的浅色 code window，深色主题下仍保持亮面窗体；AI pipe table 新增窄范围规范化、滚动容器与 token 驱动样式；reasoning 容器改为更轻的承托层。目标测试与 `npx tsc --noEmit` 均通过，手动 light/dark 视觉烟测留给人工。 |
| [Blank Session List Actions](./2026-04-20-blank-session-list-actions-plan.md) | 2026-04-20 | 会话列表中的空白会话继续显示并可进入，但不再显示“更多”按钮，也不再允许通过列表触发重命名或删除；保持删除当前会话后的空白会话兜底机制不变。 |
| [Full-Stack I18n](./2026-04-20-full-stack-i18n-plan.md) | 2026-04-20 | 前端接入应用级 i18n provider、语言持久化和 `Accept-Language` 透传，覆盖设置/聊天/Stage/数据源等核心界面；后端增加 `MessageSource` + `Translator`，本地化异常消息、默认标题/名称、连接测试结果和 Action 描述；补齐前后端国际化相关测试与过期 schema 测试修复。 |
| [Composer Model Picker Dialog](./2026-04-18-composer-model-picker-dialog-plan.md) | 2026-04-20 | Composer 模型选择器完成 Popover → 960×540 双栏对话框迁移，补齐 provider 默认定位 / 搜索联动 / 空态引导 / 选中关闭测试；为适配并发 i18n 改动补充测试环境 `useI18n` mock 与过期 StageWindow 断言修正；`npm test`（151 tests）与 `npx tsc --noEmit` 均通过，手动烟测留给人工联调。 |
| [SSE Heartbeat & Async Timeout 治理](./2026-04-20-sse-heartbeat-plan.md) | 2026-04-20 | SSE GET 订阅改无限 timeout + 30s 心跳注释帧（`":\n\n"`）主动探活；`AsyncRequestTimeoutException` 降级 DEBUG；`SseHeartbeatScheduler` 新 bean + `AsyncTimeoutHandler` `@ControllerAdvice` |
| [Assistant Model Metadata Propagation](./2026-04-20-assistant-model-metadata-propagation-plan.md) | 2026-04-20 | `Message` record 新增 `providerID/modelID`；`OpenCodeEventLoop.parseMessage` 兼容 user 嵌套 / assistant 扁平两种 OpenCode 1.4.7 形态；清理 `DtEvent.MessageCompleted` 死事件 + 前端 `message.completed` 分支 + 过时字段名兼容链（`modelId/model_id/providerId/provider_id/createdAt/completedAt` 等）。`mvn clean verify` BUILD SUCCESS；Task 4 Step 3 手动端到端验证留给人工联调 |
| [Refresh-Resilient Streaming](./2026-04-19-refresh-resilient-streaming-plan.md) | 2026-04-19 | 刷新浏览器不中断 AI 流响应：`lastEventIdBySession` / `streamingBySession` 持久化到 sessionStorage；`buildEventSink` 消费 `session.idle` + `session.status=idle` 清零；后端 `ChannelController` POST 流等待 turn-done 信号而非定时 1s（Task 5 被并行 commit bad23aa 以更强方案取代）；R1-R4 手动验证场景留给端到端联调 |
| [Per-Session Streaming Indicator](./2026-04-19-per-session-streaming-indicator-plan.md) | 2026-04-19 | `useChannel.isStreaming` 提升到 `chat-parts-store.streamingBySession` 按 sessionId 分片；切回后台仍在跑的 session 正确显示 spinner；登记 TD-MULTI-SESSION-SSE-POOL (P2) |
| [Single Empty Session](./2026-04-19-single-empty-session-plan.md) | 2026-04-19 | 全局最多 1 个空白会话：后端 `SessionService.create` `synchronized` 幂等 + `reusedEmpty` 响应字段；前端 `app-sidebar` 本地查重 + `isPending` 短路；零 migration |
| [AI Message Rendering Migration](./2026-04-19-ai-message-rendering-migration-plan.md) | 2026-04-19 | 前端迁移 OpenCode 消息渲染（Markdown 增量 / PacedMarkdown / TextShimmer / BasicTool / ContextToolGroup / ToolRegistry）+ L1/L2/L3 风险徽章 + SQL 代码块 1a 流程 + Artifact 跳 Stage + 乐观 UI（pending user / 重试 / 删除）。Phase 0-5 完成；Phase 6 共 16 个手动验收场景留给人工联调 |
| [AI Message History Backend](./2026-04-19-ai-message-history-backend-plan.md) | 2026-04-19 | messages 表下沉到 OpenCode；HistoryService 透传 GET /session/:id/message；OpenCodeEventTranslator Part 透传 JsonNode；ActionDescriptor 扩展 riskLevel/category；7 个 Action 注解回填 |
| [History OpenCode Passthrough — 前后端同步文档](./2026-04-19-history-opencode-passthrough-sync.md) | 2026-04-19 | 删本地 messages 表，透传 OpenCode；修 AI 消息切换丢失 + 流式卡顿 + USER 消息 ID 一致性（后端完工，待前端联调） |
| [Datasource Name Field](./2026-04-19-datasource-name-plan.md) | 2026-04-19 | 数据源新增唯一 name 字段（Flyway V7 + 唯一索引）+ Controller 409 冲突处理 + 前端名称列/输入框 + 表格样式修复 |
| [OpenCode Session ID Persistence + Cascade Delete](./2026-04-18-opencode-session-id-persistence-plan.md) | 2026-04-18 | dtSid↔ocSid 绑定从内存 map 提升为 SQLite 持久化 + 启动预热；删 session 级联调 OpenCode `DELETE /session/:id`；修后端重启后 AI 多轮失忆 |
| [Request Logging & Tracing](./2026-04-18-request-logging-plan.md) | 2026-04-18 | HTTP 请求耗时统计、traceId 全链路日志、慢请求 WARN 告警（HandlerInterceptor + MDC） |
| [OpenCode 1.4.7 Outbound Schema Fix](./2026-04-18-opencode-147-outbound-schema-plan.md) | 2026-04-18 | DataTalk→OpenCode 出站 body 规范化：model 拆 `{providerID, modelID}`、part.id 加 `prt_` 前缀、`synthetic`/`ignored`/`time` 补默认 |
| [OpenCode 1.4.7 Envelope Adapter](./2026-04-18-opencode-147-envelope-adapter-plan.md) | 2026-04-18 | 适配 1.4.7 事件 envelope（payload 在 `properties.*` 下），AI 响应事件重新进 SessionBus；新增 9 份真实 fixture + 11 条 parse 单测 |
| [Message Parts Jackson Fix](./2026-04-18-message-parts-jackson-fix-plan.md) | 2026-04-18 | 修 `MessageRepository.save` 泛型擦除丢 `@JsonTypeInfo` 判别符，GET `/messages` 500 的根因 |
| [Session Canvas UX Fixes](./2026-04-18-session-canvas-ux-fixes-plan.md) | 2026-04-18 | 4 个前端 bug：composer 消失/位置/持久化/气泡样式（SplitView 底部 slot + session-store persist + meta-gated 渲染 + 错误 toast） |
| [API Prefix Decouple](./2026-04-18-api-prefix-decouple-plan.md) | 2026-04-18 | `VITE_API_BASE_URL` 只存 origin，前端集中管理 `/api` 前缀常量，防止 `/api/api` 重复 |
| [Connection Test Status Persistence](./2026-04-18-connection-test-status-persistence-plan.md) | 2026-04-18 | 持久化连接测试结果到 DB，重启后可见上次测试状态 |
| [Drop Connection Gate + Default Model](./2026-04-18-drop-connection-gate-default-model-plan.md) | 2026-04-18 | 去掉发消息对 DB 连接的依赖 + 自动选择首个可用模型 |
| [OpenCode Session Title Sync](./2026-04-18-opencode-session-title-sync-plan.md) | 2026-04-18 | 完整 session.* 事件家族翻译 + title 自动同步 + title_locked 锁定机制 |
| [Logging Standardization](./2026-04-18-logging-standardization-plan.md) | 2026-04-18 | logback-spring.xml 配置、System.err 修复、文件日志滚动输出 |
| [TD-006 API Types Sync](./2026-04-18-td006-api-types-sync-plan.md) | 2026-04-18 | 后端 DTO 统一提取 + SpringDoc OpenAPI + 前端类型生成 + 字段名统一 |
| [AI Settings · Part 2 · Frontend](./2026-04-17-ai-settings-part2-frontend.md) | 2026-04-17 | 设置中心 UI + 对话框 + Chat 模型选择器 |
| [AI Settings · Part 1 · Backend](./2026-04-17-ai-settings-part1-backend.md) | 2026-04-17 | OpenCode 代理 + 偏好持久化 + 连接 CRUD |
| [Plan B: MVP Actions](./2026-04-16-manus-b-mvp-actions.md) | 2026-04-17 | 6 个 MVP Action Handler + 真实 OpenCode SSE 集成 |
| [Plan C: Client Split View](./2026-04-16-manus-c-client-split-view.md) | 2026-04-17 | Manus 风格前端分屏交互、HERO→SPLIT 动画、工件时间线 |
| [Plan C2: Client Wiring Fixes](./2026-04-16-manus-c2-client-wiring-fixes.md) | 2026-04-17 | 合并 session store、补 sessions 端点、portal race、历史加载、SSE 常驻 |
| [Model Config Page](./2026-04-16-model-config-page-plan.md) | 2026-04-17 | 模型配置页面：提供商管理 / 模型可见性 / 自定义提供商（Mock 数据） |
| [OpenCode Embedded Process](./2026-04-16-opencode-embedded-process-plan.md) | 2026-04-17 | Spring Boot 嵌入管理 OpenCode 进程：自动下载 / 动态端口 / 生命周期 |
| [Stage Reveal Animation](./2026-04-17-stage-reveal-animation-plan.md) | 2026-04-17 | Stage 气泡式 clip-path 开/关动画 + 圆角内 bg-muted 色差修复 |
| [Stage As Computer](./2026-04-17-stage-as-computer-plan.md) | 2026-04-17 | 右栏外壳化（macOS 风格 titlebar）+ 小电脑按钮可关可开 + 智能自弹 + 删 /preview |
| [Plan A: Backend Platform (Part 1)](./2026-04-16-manus-a-backend-platform.md) | 2026-04-16 | Ontology/Action Registry, Domain 模型 |
| [Plan A Part 2](./2026-04-16-manus-a-backend-platform-part2.md) | 2026-04-16 | SessionBus, ChannelService, JSON-RPC |
| [Plan A Part 2→3 Adapter](./2026-04-16-manus-a-backend-platform-part2-to-part3-adapter.md) | 2026-04-16 | 适配层集成桥接 |
| [Plan A Part 3](./2026-04-16-manus-a-backend-platform-part3.md) | 2026-04-16 | Flyway SQLite, 持久化仓储 |
| [Plan A Part 4](./2026-04-16-manus-a-backend-platform-part4.md) | 2026-04-16 | OpenCode Gateway, ToolCallBridge, E2E Smoke Test |
| [Client Rebuild](./2026-04-16-client-rebuild-tauri-vite-plan.md) | 2026-04-16 | Tauri v2 + React 19 + Vite 客户端骨架 |
| [Tech Debt Tracker](./tech-debt-tracker.md) | — | 已知技术债务集中记录（P0/P1/P2 优先级） |
| [UI Demo Stage Animation Debt](./ui-demo-stage-animation-debt.md) | 2026-04-17 | 历史 debt 记录；2026-04-23 治理确认大部分项已由后续计划或主技术债台账吸收，文档本身转为 stale，剩余 live residue 已迁回 `tech-debt-tracker.md`。 |

## 工作流

1. 设计 spec 经评审通过后，创建执行计划
2. 计划提交到 `docs/exec-plans/` 并在本文件中登记
3. 执行过程中在计划文件内用 checkbox 标记进度
4. 完成后从「活跃」移到「已完成」
5. 发现的技术债务记录到 [tech-debt-tracker.md](tech-debt-tracker.md)
