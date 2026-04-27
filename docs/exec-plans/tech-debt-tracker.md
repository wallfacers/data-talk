# 技术债务跟踪器

已知技术债务的集中记录。每项标注优先级和关联计划。

## 优先级说明

| 级别 | 含义 |
|------|------|
| P0   | 阻塞当前开发，需立即处理 |
| P1   | 影响质量或性能，在下一个 Plan 中处理 |
| P2   | 改善可维护性，在合适时机处理 |

## 当前债务

（当前无未清除债务）

## 已清除债务

| ID | 清除日期 | 原描述 | 清除方式 |
|----|----------|--------|----------|
| TD-SINGLE-EMPTY-SESSION-MULTINODE | 2026-04-27 | `SessionService.create` 的 `synchronized (createLock)` 仅在单 JVM 内有效，多节点部署需改为 DB 唯一约束 | 移除 `synchronized (createLock)` 及 `createLock` 字段。当前为单机桌面应用，无需多节点并发保护；若未来扩展多节点，应配合数据库切换到 PG 并添加 partial unique index |
| TD-026 | 2026-04-27 | `client/src/features/session/hero-view.tsx` 为无引用孤立文件，和 `SplitView` 空态内容重复 | 删除 `hero-view.tsx`。`SplitView` 已有完整的空态实现（含 `composer-slot`），`HeroView` 无任何引用 |
| TD-028 | 2026-04-27 | `EndToEndSmokeIT` 用 `bridgeArgs()` 手工构造带 `__dt*` 的 `/mcp` 请求，只覆盖 backend endpoint，不跑真实 OpenCode→plugin→bridge 链路。曾导致 plugin 里 `output.args = args` 整体替换失效的 bug 一路漏到生产（-32602 missing session context） | 2026-04-27 代码审查确认：`RealOpenCodeMcpBridgeIT` 已存在并提供 opt-in 真实 E2E 夹具（`DATATALK_REAL_OPENCODE_E2E=true` + `DATATALK_REAL_OPENCODE_MODEL`），启动真实 `opencode serve` 验证 plugin→bridge 全链路；`EndToEndSmokeIT` 头部注释已正确指向该测试作为补充。风险已闭环 |
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
