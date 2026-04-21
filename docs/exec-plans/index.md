# 执行计划跟踪器

所有执行计划的集中索引。计划是一等工件，进行版本控制。

## 活跃计划

| 计划 | 状态 | 摘要 |
|------|------|------|
| [Stage Window Layout Refactor](./2026-04-21-stage-window-layout-refactor-plan.md) | pending | 将 Stage 从底部 Dock + pill tabs 重构为左侧导航侧栏 + 右侧 Tabs 工作区的浏览器式工作台：顶部轻量工具行、下方连接资源浏览器、统一 tab identity / open-or-focus 规则、Chrome-inspired 顶部页签，以及 sidebar 收起/展开交互。 |
| [Session Data Context & AI Data Source Management](./2026-04-21-session-data-context-and-ai-datasource-management-plan.md) | in_progress | Batch A/B/C/D 已落地并完成专项验证：session data context API、`ResolvedExecutionContext`、`/api/query` 与 `/api/sql/execute` 自动表定位、前端 `!use/!select` / Query Editor / Bang Query 上下文继承、AI data-context/connection actions、连接更新后的 context validate + 前端刷新均已接通。剩余 Batch E：`mvn clean verify`、手工冒烟、文档最终收口。 |
| [Composer Data Source Picker](./2026-04-20-composer-data-source-picker-plan.md) | in_progress | 计划为 Composer 增加与模型并列的数据源选择器，接入全局 chooser host、缺库自动补选并恢复原动作、`ui_exec(workspace, choose_connection)` 适配器，以及 Stage 卡片来源数据源固化与显式回切。 |
| [Stage UI Object Protocol Phase 1](./2026-04-20-stage-ui-object-protocol-plan.md) | in_progress | 前端 `UIRouter` + 4 个 CLIENT Action 桥接已就位；`StageStore` 多 Tab 模型、`WorkspaceAdapter` / `BangQueryAdapter`、StageWindow 多 Tab UI、`BangQueryTab` 组件、Composer `!` 拦截均已落地；后端 `/api/query` 加 `SqlStatementGuard`。客户端 198 tests + 后端 179 tests 全绿。**剩余：手动端到端联调（plan Step 12.5）**。AI 展示路径（QueryEditor + Prompt 注入）归属 P2，不在此 plan。 |
| [SQL Risk Classification & IT CI Gate](./2026-04-20-sql-risk-classification-it-ci-gate-plan.md) | in_progress | `TD-020`：在 `ActionDispatcher` 统一预处理层引入 Apache Calcite SQL AST 风险判级，并通过 `ActionContext` / action output metadata 透传动态风险；`TD-021`：在 adapter 模块接入 failsafe，让 `mvn clean verify` 自动执行 `*IT.java`。 |
## 已完成计划

| 计划 | 完成日期 | 摘要 |
|------|---------|------|
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
| [UI Demo Stage Animation Debt](./ui-demo-stage-animation-debt.md) | 2026-04-17 | Demo 预览模式与 Stage 滑动动画技术债 |

## 工作流

1. 设计 spec 经评审通过后，创建执行计划
2. 计划提交到 `docs/exec-plans/` 并在本文件中登记
3. 执行过程中在计划文件内用 checkbox 标记进度
4. 完成后从「活跃」移到「已完成」
5. 发现的技术债务记录到 [tech-debt-tracker.md](tech-debt-tracker.md)
