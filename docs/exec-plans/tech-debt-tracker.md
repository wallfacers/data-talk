# 技术债务跟踪器

已知技术债务的集中记录。每项标注优先级和关联计划。

## 优先级说明

| 级别 | 含义 |
|------|------|
| P0   | 阻塞当前开发，需立即处理 |
| P1   | 影响质量或性能，在下一个 Plan 中处理 |
| P2   | 改善可维护性，在合适时机处理 |

## 当前债务

| ID | 优先级 | 模块 | 描述 | 来源 |
|----|-------|------|------|------|
| TD-005 | P2 | adapter | ~~缺少全局异常处理器~~ `AiSettingsExceptionHandler` 已合并到 `GlobalExceptionHandler`，统一错误响应格式 | 2026-04-18 已实现 |
| TD-007 | P2 | client | ~~Demo 预览模式绕过真实 session/connection 流程~~ §3.1 P1 已于 2026-04-17 清理；§3.2 clip 动画死代码已删除，`useComposerSlot` 依赖已修复 | 2026-04-18 已清理 |
| TD-010 | P2 | client | ~~自写 SplitView 移除了 `PanelResizeHandle`，用户无法拖拽调整左右面板宽度~~ 已添加 CSS drag handle + localStorage 持久化 | 2026-04-18 已实现 |
| TD-011 | P2 | client | ~~`StageWindow` 偏离原 Stage-As-Computer spec~~ 已回归 macOS 交通灯（红/黄/绿圆点） | 2026-04-18 已实现 |
| TD-012 | P2 | infrastructure | ~~SQLite 未启用 `PRAGMA foreign_keys=ON`~~ 已启用外键约束 + V3 迁移添加 `ON DELETE CASCADE`，`SessionService.delete` 简化为单调用 | 2026-04-18 已实现 |
| TD-013 | P2 | adapter / client | ~~`DtEvent.SessionIdle` 定义但未消费；`ChannelController.java` 流生命周期仍用 1000ms 恩典期~~ 前端 `buildEventSink` 已消费 `session.idle` + `session.status=idle` 清 `streamingBySession`；后端 `ChannelController.stream` 已由 1000ms 改为 CountDownLatch + turn-done watcher；~~剩余 P2：POST 流自身的 10 min 超时上限未按 AI 工作量做自适应调整~~ 已提取为 `datatalk.channel.post-stream-timeout-ms` 可配置项（默认 600000ms） | 2026-04-20 完成（Tech Debt Batch plan）|
| TD-014 | P2 | client | ~~`DtEvent.SessionError` 定义但未消费~~ `buildEventSink` 已消费：`markSessionTurnCompleted` + `setStreaming(false)` + `showErrorToast` | 2026-04-20 完成（Tech Debt Batch plan）|
| TD-015 | P2 | client | ~~`DtEvent.SessionCreated / SessionDeleted` 定义但未消费（多客户端协作场景）~~ `buildEventSink` 已消费：`queryClient.invalidateQueries(['sessions', ...])` | 2026-04-20 完成（Tech Debt Batch plan）|
| TD-016 | P2 | client | ~~`DtEvent.SessionCompacted` 定义但未消费（OpenCode 上下文压缩提示）~~ `buildEventSink` 已消费：`toast.info` 通知用户 | 2026-04-20 完成（Tech Debt Batch plan）|
| TD-017 | P2 | client | ~~`DtEvent.SessionDiff` 定义但未消费；payload 语义待调研~~ `buildEventSink` 已安全忽略（OpenCode 1.4.7 payload 语义仍未公开文档化，无可操作信息） | 2026-04-20 完成（Tech Debt Batch plan）|
| TD-026 | P2 | client | `client/src/features/session/hero-view.tsx` 仍保留为无引用孤立文件，且和 `SplitView` 当前空态内容重复；2026-04-23 文档治理确认 `ConnectionOverlay` 已退出代码路径，但 `HeroView` 残留尚未清理 | 2026-04-23 文档与状态治理批次 |
| TD-SINGLE-EMPTY-SESSION-MULTINODE | P2 | application | `SessionService.create` 的 `synchronized (createLock)` 仅在单 JVM 内有效。若未来扩展为多节点部署，需改为 DB 唯一约束（partial unique index `ON sessions(connection_id) WHERE has_ever_sent = 0`）。SQLite 原生不支持 partial unique，届时需配合数据库类型切换到 PG 一并处理。现状单机桌面应用无此需求 | Plan 2026-04-19 Single Empty Session |
| TD-MULTI-SESSION-SSE-POOL | P2 | client | ~~`useSessionSubscribe` 当前仅跟随 `activeSessionId` 订阅 GET SSE，后台 session 的服务端推送在 ring buffer 溢出后可能丢失~~ `BackgroundSubscriber` 组件 + `useBackgroundSessionSubscribe` hook 实现订阅池：streaming 的后台 session 维持 SSE 存活，`session.idle/error` 触发组件卸载自动关闭连接 | 2026-04-20 完成（Tech Debt Batch plan）|
| TD-001 | P1 | adapter | ~~`application.yml` 使用 H2 内存库作为 placeholder，需替换为正式的数据源配置策略~~ URL 改为 `jdbc:h2:mem:demodb;DB_CLOSE_DELAY=-1`，注释明确其为"演示/fallback datasource"而非临时占位 | 2026-04-20 完成（Tech Debt Batch plan）|
| TD-028 | P1 | adapter | `EndToEndSmokeIT` 用 `bridgeArgs()` 手工构造带 `__dt*` 的 `/mcp` 请求，只覆盖 backend endpoint，不跑真实 OpenCode→plugin→bridge 链路。曾导致 plugin 里 `output.args = args` 整体替换失效的 bug 一路漏到生产（-32602 missing session context）。需引入能启动真 `opencode serve` 的端到端夹具，断言 `datatalk_*` 工具调用到 backend 时 `__dt*` 字段齐全 | 2026-04-24 MCP 桥接 plugin bug 修复事件 |

## 已清除债务

| ID | 清除日期 | 原描述 | 清除方式 |
|----|----------|--------|----------|
| TD-027 | 2026-04-24 | OpenCode 仍走 legacy plugin tool 注册/HTTP callback 链路，未来与 MCP 并存会导致同名 tool 重复暴露与维护双轨入口 | OpenCode MCP Tool Migration 已切到单一路径：后端新增 `/mcp` + `McpNameMapper` + nonce/session bridge + bootstrap/reconcile/health，前端切到 `datatalk_*` renderer/prompt naming，并删除 `/plugin/register-tool` / `/api/opencode-tool/*` / `shared-secret` callback 运行时与 smoke 基线 |
| TD-008 | 2026-04-17 | `ChatHeader` 的重命名/删除仅 toast 占位，`services/api/session.ts` 缺 `renameSession` / `deleteSession` 端点 | `SessionController` 加 `PATCH`/`DELETE`，`session.ts` 加对应客户端方法，`chat-header.tsx` 用 `useMutation` 接通 |
| TD-002 | 2026-04-18 | `OpenCodeHttpClient` 仅有 WireMock 测试，缺少对真实 OpenCode 服务端的集成验证 | 项目已可启动运行，真实集成验证已在日常开发中覆盖 |
| TD-004 | 2026-04-18 | `SessionBus` 的 16ms flush 窗口硬编码 | 已通过 `datatalk.channel.flush-interval` 配置项实现可配置，默认 PT0.016S |
| TD-006 | 2026-04-18 | 前端 `features/*/types.ts` 与后端 DTO 缺乏自动同步机制 | 后端 DTO 提取到统一 `dto` 包 + SpringDoc OpenAPI + 前端 `generated/api.ts` 类型定义 + 字段名统一（kind/databaseName） |
| TD-009 | 2026-04-18 | `HeroView` / `ConnectionOverlay` 孤立组件 | 当时清理目标已落地到主流程；2026-04-23 复核确认 `ConnectionOverlay` 已退出代码路径，但 `HeroView` 仍作为无引用残留文件存在，后续已迁移为 `TD-026` 单独跟踪 |
| TD-018 | 2026-04-18 | commit 2bbeb41 启用 `PRAGMA foreign_keys=ON` 后，`SessionControllerIT` / `SupersedeArtifactActionTest` 触发 `SQLITE_CONSTRAINT_FOREIGNKEY` | `SessionControllerIT` 加 `@BeforeEach` 用 `INSERT OR IGNORE` seed 所有连接 id 并清理 sessions/messages；`SupersedeArtifactActionTest` 在 `clean()` seed `c-default` connection + `s-1` session，并给 `datatalkJdbc` 字段补上 `@Qualifier("datatalkJdbc")`（之前被 `@Primary demoJdbcTemplate` 拦截，写到了错误的 H2 库）|
| TD-019 | 2026-04-18 | commit fc8a450 后 `ConnectionService.create` 不再接受客户端 id，`ConnectionControllerIT` / `LayoutErdActionIT` / `ReadSchemaActionIT` 硬编码 id 失效 | `ConnectionService.create(...)` 改为返回生成的 `String id`；`ConnectionController.POST` 返回新 DTO `ConnectionCreatedDto(id)`；5 处测试调用方（含 `ExecuteSqlActionIT` / `TypicalQueryE2EIT`）消费返回值，不再使用硬编码 id |
| TD-020 | 2026-04-20 | `preview_sql` 等 SQL-bearing action 的风险判级依赖前端正则粗判，未闭环“后端强制判级”规格 | 在 `ActionDispatcher` 统一预处理层接入 `SqlBearingActionInspector` + `CalciteSqlRiskAnalyzer`；动态风险通过 `ActionContext.metadata().sqlRisk()` 传入 handler，`ExecuteSqlAction` 已把 `riskLevel` / `riskReason` / `fallbackUsed` 回写到输出 metadata；前端正则仅保留为兼容旧数据 fallback。OpenCode `action_result → tool part state.metadata` 的端到端烟测仍待人工联调确认 |
| TD-021 | 2026-04-20 | `*IT.java` 未纳入 Maven/CI，`ChannelControllerIT` / `TypicalQueryE2EIT` 等默认不执行 | `data-talk-adapter/pom.xml` 接入 `maven-failsafe-plugin` 并显式纳入 `**/*IT.java`，`mvn clean verify` 现为后端完整回归入口 |
| TD-003 | 2026-04-21 | `DtEvent` 的 Jackson `@JsonSubTypes` 硬编码了 22 个子类型，新增事件需修改两处（枚举 + 注解） | 移除中央 `@JsonSubTypes` 列表，改为每个子类型 `@JsonTypeName` + 基于 sealed `permittedSubclasses` 的 `DtEventTypeIdResolver` 自动发现，新增事件无需再维护中央注册表 |
| TD-024 | 2026-04-21 | `OpenCodeEventLoop` 的 `partToOpenCodeSession` ConcurrentHashMap 依赖 `message.part.removed` 事件清理；若事件丢失或乱序，映射表持续积累，存在内存泄漏风险 | `partToOpenCodeSession` 升级为带时间戳的临时索引，事件入口按固定周期惰性回收过期绑定；同时保留 `message.part.removed` / `session.deleted` 的即时清理 |
| TD-025 | 2026-04-21 | `OpenCodeEventLoop` 对孤儿 session 的 global 事件仅静默丢弃，无 WARN 日志，排查困难 | 孤儿 session 事件改为 `WARN` 日志，并在丢弃时同步清理该 OpenCode session 对应的残留 part 绑定 |
| TD-022 | 2026-04-21 | `stage-window.tsx` `mockTabs` 硬编码三个 tab，切换逻辑用 `useState` 本地状态而非 `useStageStore`；SQL/ER tab 内容仅为 i18n 占位文本，Stage 功能尚未真正接通后端 | Stage Query Editor 计划：删除 mockTabs/useState，改由 `useStageStore` + `useShallow` 驱动 tab 列表与激活态；`StageTabContent` 路由至 `QueryEditorTab` |
| TD-023 | 2026-04-21 | `stage-dock.tsx` 四个工具按钮（SQL/ER/Report/Dashboard）无 `onClick` 实现，纯 UI 占位；`stage-tab-bar.tsx` 右键菜单（关闭此选项卡/关闭其他/全部关闭）无实现 | Stage Query Editor 计划：SQL 按钮接通 `openTab({type:'query_editor',...})`；tab-bar X 按钮与右键菜单全部接回调（onClose/onCloseOthers/onCloseAll/onCloseLeft/onCloseRight）|
