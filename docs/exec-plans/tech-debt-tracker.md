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
| TD-001 | P1 | adapter | `application.yml` 使用 H2 内存库作为 placeholder，需替换为正式的数据源配置策略 | Plan A |
| TD-003 | P2 | domain | `DtEvent` 的 Jackson `@JsonSubTypes` 硬编码了 22 个子类型，新增事件需修改两处（枚举 + 注解） | ~~Plan A~~ 2026-04-18 已改为 `@JsonTypeName` |
| TD-005 | P2 | adapter | ~~缺少全局异常处理器~~ `AiSettingsExceptionHandler` 已合并到 `GlobalExceptionHandler`，统一错误响应格式 | 2026-04-18 已实现 |
| TD-007 | P2 | client | ~~Demo 预览模式绕过真实 session/connection 流程~~ §3.1 P1 已于 2026-04-17 清理；§3.2 clip 动画死代码已删除，`useComposerSlot` 依赖已修复 | 2026-04-18 已清理 |
| TD-010 | P2 | client | ~~自写 SplitView 移除了 `PanelResizeHandle`，用户无法拖拽调整左右面板宽度~~ 已添加 CSS drag handle + localStorage 持久化 | 2026-04-18 已实现 |
| TD-011 | P2 | client | ~~`StageWindow` 偏离原 Stage-As-Computer spec~~ 已回归 macOS 交通灯（红/黄/绿圆点） | 2026-04-18 已实现 |
| TD-012 | P2 | infrastructure | ~~SQLite 未启用 `PRAGMA foreign_keys=ON`~~ 已启用外键约束 + V3 迁移添加 `ON DELETE CASCADE`，`SessionService.delete` 简化为单调用 | 2026-04-18 已实现 |
| TD-013 | P1 | adapter / client | `DtEvent.SessionIdle` 定义但未消费；`ChannelController.java` 流生命周期仍用 1000ms 恩典期 | Plan 2026-04-18 opencode-session-title-sync |
| TD-014 | P2 | client | `DtEvent.SessionError` 定义但未消费 | 同上 |
| TD-015 | P2 | client | `DtEvent.SessionCreated / SessionDeleted` 定义但未消费（多客户端协作场景） | 同上 |
| TD-016 | P2 | client | `DtEvent.SessionCompacted` 定义但未消费（OpenCode 上下文压缩提示） | 同上 |
| TD-017 | P2 | client | `DtEvent.SessionDiff` 定义但未消费；payload 语义待调研 | 同上 |
| TD-020 | P2 | application | `preview_sql` 等 mutation Action 的风险判级本期靠前端正则粗判（仅看 SQL 首关键字，不识别 WHERE 缺失 / 批量 DELETE / CTE 内含 DML）。目标：后端引入 SQL AST 解析器（JSqlParser / Calcite）在 ActionHandler 执行前完成真实判级，通过 `part.state.metadata.riskLevel` 回传前端；前端 `resolveRisk` 优先级链（part-level > descriptor > 正则）保证前端零改动升级 | Plan 2026-04-19 AI Message Rendering Migration |
| TD-SINGLE-EMPTY-SESSION-MULTINODE | P2 | application | `SessionService.create` 的 `synchronized (createLock)` 仅在单 JVM 内有效。若未来扩展为多节点部署，需改为 DB 唯一约束（partial unique index `ON sessions(connection_id) WHERE has_ever_sent = 0`）。SQLite 原生不支持 partial unique，届时需配合数据库类型切换到 PG 一并处理。现状单机桌面应用无此需求 | Plan 2026-04-19 Single Empty Session |

## 已清除债务

| ID | 清除日期 | 原描述 | 清除方式 |
|----|----------|--------|----------|
| TD-008 | 2026-04-17 | `ChatHeader` 的重命名/删除仅 toast 占位，`services/api/session.ts` 缺 `renameSession` / `deleteSession` 端点 | `SessionController` 加 `PATCH`/`DELETE`，`session.ts` 加对应客户端方法，`chat-header.tsx` 用 `useMutation` 接通 |
| TD-002 | 2026-04-18 | `OpenCodeHttpClient` 仅有 WireMock 测试，缺少对真实 OpenCode 服务端的集成验证 | 项目已可启动运行，真实集成验证已在日常开发中覆盖 |
| TD-004 | 2026-04-18 | `SessionBus` 的 16ms flush 窗口硬编码 | 已通过 `datatalk.channel.flush-interval` 配置项实现可配置，默认 PT0.016S |
| TD-006 | 2026-04-18 | 前端 `features/*/types.ts` 与后端 DTO 缺乏自动同步机制 | 后端 DTO 提取到统一 `dto` 包 + SpringDoc OpenAPI + 前端 `generated/api.ts` 类型定义 + 字段名统一（kind/databaseName） |
| TD-009 | 2026-04-18 | `HeroView` / `ConnectionOverlay` 孤立组件 | 文件已确认删除，无 import 引用 |
| TD-018 | 2026-04-18 | commit 2bbeb41 启用 `PRAGMA foreign_keys=ON` 后，`SessionControllerIT` / `SupersedeArtifactActionTest` 触发 `SQLITE_CONSTRAINT_FOREIGNKEY` | `SessionControllerIT` 加 `@BeforeEach` 用 `INSERT OR IGNORE` seed 所有连接 id 并清理 sessions/messages；`SupersedeArtifactActionTest` 在 `clean()` seed `c-default` connection + `s-1` session，并给 `datatalkJdbc` 字段补上 `@Qualifier("datatalkJdbc")`（之前被 `@Primary demoJdbcTemplate` 拦截，写到了错误的 H2 库）|
| TD-019 | 2026-04-18 | commit fc8a450 后 `ConnectionService.create` 不再接受客户端 id，`ConnectionControllerIT` / `LayoutErdActionIT` / `ReadSchemaActionIT` 硬编码 id 失效 | `ConnectionService.create(...)` 改为返回生成的 `String id`；`ConnectionController.POST` 返回新 DTO `ConnectionCreatedDto(id)`；5 处测试调用方（含 `ExecuteSqlActionIT` / `TypicalQueryE2EIT`）消费返回值，不再使用硬编码 id |
