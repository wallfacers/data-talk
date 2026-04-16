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

## 已清除债务

（暂无）
