# 执行计划跟踪器

所有执行计划的集中索引。计划是一等工件，进行版本控制。

## 活跃计划

| 计划 | 状态 | 摘要 |
|------|------|------|
| [SQL Risk Classification & IT CI Gate](./2026-04-20-sql-risk-classification-it-ci-gate-plan.md) | in_progress | `TD-020`：在 `ActionDispatcher` 统一预处理层引入 Apache Calcite SQL AST 风险判级，并通过 `ActionContext` / action output metadata 透传动态风险；`TD-021`：在 adapter 模块接入 failsafe，让 `mvn clean verify` 自动执行 `*IT.java`。 |

## 已完成计划

| 计划 | 完成日期 | 摘要 |
|------|---------|------|
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
