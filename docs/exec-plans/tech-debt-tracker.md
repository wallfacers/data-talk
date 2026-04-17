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
| TD-002 | P1 | infrastructure | `OpenCodeHttpClient` 仅有 WireMock 测试，缺少对真实 OpenCode 服务端的集成验证 | Plan A Part 4 |
| TD-003 | P2 | domain | `DtEvent` 的 Jackson `@JsonSubTypes` 硬编码了 22 个子类型，新增事件需修改两处（枚举 + 注解） | Plan A |
| TD-004 | P2 | application | `SessionBus` 的 16ms flush 窗口是硬编码值，应可配置 | Plan A Part 2 |
| TD-005 | P2 | adapter | 缺少全局异常处理器（`@ControllerAdvice`），错误响应格式不统一 | Plan A |
| TD-006 | P1 | client | 前端 features/ 模块间的 API 类型定义（`types.ts`）与后端 DTO 缺乏自动同步机制 | Client Rebuild |
| TD-007 | P2 | client | ~~Demo 预览模式绕过真实 session/connection 流程~~ §3.1 P1 已于 2026-04-17 清理；[ui-demo-stage-animation-debt.md](ui-demo-stage-animation-debt.md) §3.2 / §3.3 仍有 P2 项（PanelResizeHandle / clip-unlock 动画 / HeroView 孤立等） | 2026-04-17 UI 预览会话 |
| TD-009 | P2 | client | `HeroView` / `ConnectionOverlay` 两个组件因 `SessionCanvas` 改为始终渲染 `SplitView` 而孤立（无 import），需确认后删除或重新启用 | 2026-04-17 UI 预览会话 |
| TD-010 | P2 | client | 自写 SplitView 移除了 `PanelResizeHandle`，用户无法拖拽调整左右面板宽度 | 2026-04-17 UI 预览会话 |
| TD-011 | P2 | client | `StageWindow` 偏离原 Stage-As-Computer spec：去掉 macOS 交通灯改为 X/Maximize 按钮，需决定是否回归 spec | 2026-04-17 UI 预览会话 |
| TD-012 | P2 | infrastructure | SQLite 未启用 `PRAGMA foreign_keys=ON`，删除 session 后 `artifacts` / `action_invocations` / `events` / `query_results` 行悬挂，不影响新流程但长期会积垃圾数据。`SessionService.delete` 暂只手动清 `messages` | 2026-04-17 Session CRUD |

## 已清除债务

| ID | 清除日期 | 原描述 | 清除方式 |
|----|----------|--------|----------|
| TD-008 | 2026-04-17 | `ChatHeader` 的重命名/删除仅 toast 占位，`services/api/session.ts` 缺 `renameSession` / `deleteSession` 端点 | `SessionController` 加 `PATCH`/`DELETE`，`session.ts` 加对应客户端方法，`chat-header.tsx` 用 `useMutation` 接通 |
