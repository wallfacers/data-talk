# OpenCode Event Loop Defenses Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理 `OpenCodeEventLoop` 的两个后端防御性缺口（TD-024/TD-025），并完成 TD-003 的实际代码收尾与技术债文档同步。

**Architecture:** `OpenCodeEventLoop` 继续保持单线程 SSE 消费模型，但把 `partId -> openCodeSessionId` 临时索引升级为带时间戳的惰性清理缓存，在事件入口按固定周期回收过期项，避免仅依赖 `message.part.removed`。孤儿 session 事件仍然丢弃，但日志级别提升为 WARN，并在可行时顺手清掉该 OpenCode session 对应的残留 part 绑定。`DtEvent` 则移除中央 `@JsonSubTypes` 注册，改为仅依赖各子类型 `@JsonTypeName`。

**Tech Stack:** Java 21, Spring Boot 3.5, Jackson, JUnit 5, AssertJ, WireMock

---

## File Map

- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopTest.java`
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`
- Modify: `server/data-talk-domain/src/test/java/com/datatalk/domain/event/DtEventJsonTest.java`
- Modify: `docs/exec-plans/tech-debt-tracker.md`
- Modify: `docs/exec-plans/index.md`

## Task 1: OpenCodeEventLoop 防御增强

- [x] 为 `partToOpenCodeSession` 引入时间戳与惰性定期清理，避免仅依赖 `message.part.removed`
- [x] 修正 `message.part.removed` 的路由时序，确保删除事件在清掉绑定前仍能找到 session
- [x] 孤儿 session 事件从 DEBUG 升为 WARN，并在丢弃时清理该 OpenCode session 的残留 part 绑定

## Task 2: 先补失败测试

- [x] 在 `OpenCodeEventLoopTest` 增加用例，覆盖 `message.part.removed` 路由、过期 part 绑定回收、孤儿 session WARN
- [x] 在 `DtEventJsonTest` 增加断言，要求 `DtEvent` 不再声明中央 `@JsonSubTypes`

## Task 3: TD-003 与文档收尾

- [x] 移除 `DtEvent` 上的中央 `@JsonSubTypes` 列表，仅保留 `@JsonTypeName`
- [x] 更新 `docs/exec-plans/tech-debt-tracker.md`：TD-024/TD-025 移入“已清除”，TD-003 若代码完成则保持“已清除”并修正文案
- [x] 更新 `docs/exec-plans/index.md`：本计划先登记到“活跃计划”，完成后移到“已完成计划”

## Verification

- [x] Run `cd server && mvn -q -pl data-talk-application,data-talk-domain test -Dtest=OpenCodeEventLoopTest,DtEventJsonTest`
- [x] Run `cd server && mvn compile -q`
