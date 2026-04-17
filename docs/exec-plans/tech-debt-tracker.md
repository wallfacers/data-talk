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

## 已清除债务

| ID | 清除日期 | 原描述 | 清除方式 |
|----|----------|--------|----------|
| TD-008 | 2026-04-17 | `ChatHeader` 的重命名/删除仅 toast 占位，`services/api/session.ts` 缺 `renameSession` / `deleteSession` 端点 | `SessionController` 加 `PATCH`/`DELETE`，`session.ts` 加对应客户端方法，`chat-header.tsx` 用 `useMutation` 接通 |
| TD-002 | 2026-04-18 | `OpenCodeHttpClient` 仅有 WireMock 测试，缺少对真实 OpenCode 服务端的集成验证 | 项目已可启动运行，真实集成验证已在日常开发中覆盖 |
| TD-004 | 2026-04-18 | `SessionBus` 的 16ms flush 窗口硬编码 | 已通过 `datatalk.channel.flush-interval` 配置项实现可配置，默认 PT0.016S |
| TD-006 | 2026-04-18 | 前端 `features/*/types.ts` 与后端 DTO 缺乏自动同步机制 | 后端 DTO 提取到统一 `dto` 包 + SpringDoc OpenAPI + 前端 `generated/api.ts` 类型定义 + 字段名统一（kind/databaseName） |
| TD-009 | 2026-04-18 | `HeroView` / `ConnectionOverlay` 孤立组件 | 文件已确认删除，无 import 引用 |
