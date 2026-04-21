# Implementation Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用一个总排期把当前 3 个待收尾的活跃计划与下一个 Stage 主线重构串成连续执行批次，避免实现状态、文档状态和实际优先级继续漂移。

**Architecture:** 本计划不替代现有单项执行计划，而是作为上层编排文档：先收尾 `Composer Data Source Picker`、`Stage UI Object Protocol Phase 1`、`SQL Risk Classification & IT CI Gate` 这 3 个 `in_progress` 计划，再启动 `Stage Window Layout Refactor` 作为下一实现主线，最后统一做文档与 backlog 治理。执行时遵守现有子计划的文件边界与验证命令；本计划只定义顺序、依赖、收尾标准与批次目标。

**Tech Stack:** React 19、TypeScript、Zustand、Vitest、Spring Boot 3.5、Java 21、JUnit 5、Maven、现有 `docs/exec-plans/*` 与 `docs/design-docs/index.md` 治理流程

---

## Context

- 当前执行计划总数：57
- 已完成：53
- 活跃：4
- 其中真正处于“代码已大体完成但计划未收口”的有 3 个：
  - `Composer Data Source Picker`
  - `Stage UI Object Protocol Phase 1`
  - `SQL Risk Classification & IT CI Gate`
- 当前唯一尚未开工的大块实现主线：`Stage Window Layout Refactor`
- 当前主技术债台账中仅剩 1 项未关闭债务：`TD-SINGLE-EMPTY-SESSION-MULTINODE`，不属于当前桌面单机主线阻塞项

## Inputs

- [docs/exec-plans/index.md](./index.md)
- [docs/design-docs/index.md](../design-docs/index.md)
- [docs/product-specs/index.md](../product-specs/index.md)
- [docs/exec-plans/2026-04-20-composer-data-source-picker-plan.md](./2026-04-20-composer-data-source-picker-plan.md)
- [docs/exec-plans/2026-04-20-stage-ui-object-protocol-plan.md](./2026-04-20-stage-ui-object-protocol-plan.md)
- [docs/exec-plans/2026-04-20-sql-risk-classification-it-ci-gate-plan.md](./2026-04-20-sql-risk-classification-it-ci-gate-plan.md)
- [docs/exec-plans/2026-04-21-stage-window-layout-refactor-plan.md](./2026-04-21-stage-window-layout-refactor-plan.md)

## Non-Goals

- 不在本计划中重写现有 4 份子计划
- 不在本计划中直接新增二期/三期功能 spec
- 不把 `TD-SINGLE-EMPTY-SESSION-MULTINODE` 强行前提化为当前迭代目标
- 不在没有明确新 spec 的前提下，提前展开 ER / Report / Dashboard / 审计日志等后续大功能实现

## Spec Mapping

- 产品路线图 `MVP / 二期 / 三期`：来自 [docs/product-specs/index.md](../product-specs/index.md)
- 当前活跃计划状态：来自 [docs/exec-plans/index.md](./index.md)
- 当前设计文档状态：来自 [docs/design-docs/index.md](../design-docs/index.md)
- 具体实现细节与验收要求：分别引用各子计划与对应 design doc

## File Structure Map

### Coordination Docs

| 文件 | 责任 |
|------|------|
| `docs/exec-plans/2026-04-21-implementation-roadmap-plan.md` | 作为总排期文档，定义批次顺序、依赖与收尾标准 |
| `docs/exec-plans/index.md` | 跟踪本总排期与各子计划的 Active / Completed 状态 |
| `docs/design-docs/index.md` | 跟踪 design doc 的 `approved / shipped` 状态，避免计划状态漂移 |

### Batch A — 收尾 Composer Data Source Picker

| 文件 / 区域 | 责任 |
|-------------|------|
| `docs/exec-plans/2026-04-20-composer-data-source-picker-plan.md` | 勾完剩余手工冒烟与文档回写步骤 |
| `client/src/features/session/data-source-picker/**` | 如联调发现问题，修 chooser / trigger / recent 排序 |
| `client/src/features/session/prompt-composer.tsx` | 验证缺库自动恢复原动作链路 |
| `client/src/features/actions/ui-handlers.ts` | 验证 `workspace.choose_connection` 结果透传 |
| `client/src/features/stage/**` | 验证来源数据源显示与“用此数据源继续”行为 |

### Batch B — 收尾 Stage UI Object Protocol Phase 1

| 文件 / 区域 | 责任 |
|-------------|------|
| `docs/exec-plans/2026-04-20-stage-ui-object-protocol-plan.md` | 根据真实进度补 checklist、记录联调结果与完成状态 |
| `client/src/services/ui-router/**` | 验证 `ui_read / patch / exec / list` 主链路 |
| `client/src/features/stage/adapters/**` | 验证 `WorkspaceAdapter` / `BangQueryAdapter` 行为 |
| `client/src/features/stage/utils/open-bang-query-tab.ts` | 验证 `!sql` 直查到 Stage 的打开链路 |
| `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java` | 验证 `/api/query` guard 行为仍符合预期 |

### Batch C — 收尾 SQL Risk Classification & IT CI Gate

| 文件 / 区域 | 责任 |
|-------------|------|
| `docs/exec-plans/2026-04-20-sql-risk-classification-it-ci-gate-plan.md` | 记录验证结果、判断是 completed 还是拆出 follow-up plan |
| `server/data-talk-adapter/pom.xml` | 核对 failsafe 已纳入 `*IT.java` |
| `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java` | 验证动态风险预处理链路 |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java` | 验证风险 metadata 回写 |
| `server/data-talk-adapter/src/test/java/**` | 区分环境依赖失败与真实回归失败 |

### Batch D — 启动 Stage Window Layout Refactor

| 文件 / 区域 | 责任 |
|-------------|------|
| `docs/exec-plans/2026-04-21-stage-window-layout-refactor-plan.md` | 作为下一个正式主线执行计划 |
| `client/src/stores/stage-store.ts` | sidebar 导航态、selection、expanded state |
| `client/src/features/stage/components/stage-window.tsx` | Stage 两栏 workbench 重构 |
| `client/src/features/stage/components/stage-tab-bar.tsx` | Chrome-inspired 顶部 tabs |
| `client/src/features/stage/components/stage-sidebar.tsx` | 新建左侧侧栏总入口 |
| `client/src/features/stage/components/stage-tool-row.tsx` | 顶部轻量工具入口 |
| `client/src/features/stage/components/stage-resource-browser.tsx` | 连接资源树与工具动作项 |
| `client/src/features/stage/utils/open-or-focus-stage-tool-tab.ts` | tab identity / open-or-focus 规则 |

### Batch E — 文档与 Backlog 治理

| 文件 | 责任 |
|------|------|
| `docs/exec-plans/index.md` | 移动已完成计划、更新活跃列表 |
| `docs/design-docs/index.md` | 将已交付设计文档从 `approved` 更新为 `shipped` |
| `docs/exec-plans/ui-demo-stage-animation-debt.md` | 判断归档、清理或拆分剩余仍有效项 |
| `docs/product-specs/index.md` | 若二期 backlog 有实际进入执行的新 spec，再做登记 |

## Batch Plan

### Task 1: 本周 Batch A — 收尾 Composer Data Source Picker

**Files:**
- Modify: `docs/exec-plans/2026-04-20-composer-data-source-picker-plan.md`
- Verify: `client/src/features/session/data-source-picker/**`
- Verify: `client/src/features/session/prompt-composer.tsx`
- Verify: `client/src/features/actions/ui-handlers.ts`
- Verify: `client/src/features/stage/**`

- [ ] **Step 1.1: 执行手工冒烟清单**
  - 验证未选数据源时发送普通消息会直接拉起 chooser
  - 验证未选数据源时输入 `!select 1` 会直接拉起 chooser 并自动恢复执行
  - 验证手动点击数据源 trigger 支持搜索、切换与最近使用排序
  - 验证 Stage 历史卡片来源展示与“用此数据源继续”行为
  - 验证 `ui_exec(workspace, choose_connection)` 可返回用户选择结果

- [ ] **Step 1.2: 修掉联调发现的问题**
  - 只修与 chooser / pending resume / stage source binding 直接相关的问题
  - 不在本批次顺手展开新的 Stage 布局重构

- [ ] **Step 1.3: 运行前端验证**
  - `cd client && npx vitest run src/features/session/data-source-picker src/features/session/hooks src/features/actions src/services/ui-router src/features/stage`
  - `cd client && npx tsc --noEmit`

- [ ] **Step 1.4: 完成计划 housekeeping**
  - 勾完 `2026-04-20-composer-data-source-picker-plan.md` 剩余 checkbox
  - 在 `docs/exec-plans/index.md` 中将该计划移到 Completed
  - 如无偏差，将对应 design doc 状态从 `approved` 更新到 `shipped`

### Task 2: 本周 Batch B — 收尾 Stage UI Object Protocol Phase 1

**Files:**
- Modify: `docs/exec-plans/2026-04-20-stage-ui-object-protocol-plan.md`
- Verify: `client/src/services/ui-router/**`
- Verify: `client/src/features/stage/adapters/**`
- Verify: `client/src/features/stage/utils/open-bang-query-tab.ts`
- Verify: `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`

- [ ] **Step 2.1: 对照索引摘要核实真实已完成范围**
  - 确认 `UIRouter`、4 个 CLIENT Action 桥接、`StageStore` 多 Tab、`BangQueryTab`、Composer `!` 拦截已在代码中存在
  - 只把真正未做的部分留在 checklist 中

- [ ] **Step 2.2: 执行端到端联调**
  - 验证 `!sql -> /api/query -> bang_query tab`
  - 验证 `workspace.open / focus / close` 主链路
  - 验证 `workspace.choose_connection` 通过 UI Router 返回结果
  - 验证 bang query 与 Query Editor 共存时的 tab 行为不冲突

- [ ] **Step 2.3: 运行前后端验证**
  - `cd client && npx vitest run src/services/ui-router src/features/actions src/features/stage`
  - `cd client && npx tsc --noEmit`
  - `cd server && mvn compile -q`

- [ ] **Step 2.4: 完成计划与索引收尾**
  - 把 `2026-04-20-stage-ui-object-protocol-plan.md` 的 checklist 与实际状态同步
  - 在 `docs/exec-plans/index.md` 中移动到 Completed
  - 在 `docs/design-docs/index.md` 中将 `stage-ui-object-protocol-design` 从 `approved` 更新到 `shipped`

### Task 3: 本周 Batch C — 收尾 SQL Risk Classification & IT CI Gate

**Files:**
- Modify: `docs/exec-plans/2026-04-20-sql-risk-classification-it-ci-gate-plan.md`
- Verify: `server/data-talk-adapter/pom.xml`
- Verify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
- Verify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Verify: `server/data-talk-adapter/src/test/java/**`

- [ ] **Step 3.1: 复跑计划列出的验证命令**
  - `cd server && mvn compile -q`
  - `cd server && mvn -q -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest,ActionDispatcherTest`
  - `cd server && mvn -q -pl data-talk-adapter -am verify`

- [ ] **Step 3.2: 对验证结果做分类**
  - 若失败源于 Docker / Testcontainers 不可用，记录为环境限制
  - 若失败源于既存回归用例，记录为真实待修问题
  - 不把“环境限制”伪装成“功能未完成”

- [ ] **Step 3.3: 判断收尾路径**
  - 若验证闭环成立，则直接 completed
  - 若仍有真实失败项，则新建 follow-up plan 专治测试基线，再把当前计划以“主体完成，验证遗留拆出”的方式收口

- [ ] **Step 3.4: 同步更新索引与设计状态**
  - 更新 `docs/exec-plans/index.md`
  - 如判定主体已交付，在 `docs/design-docs/index.md` 中将 `sql-risk-classification-and-it-ci-gate-design` 从 `approved` 更新到 `shipped`

### Task 4: 下周 Batch D — 启动 Stage Window Layout Refactor

**Files:**
- Modify: `docs/exec-plans/2026-04-21-stage-window-layout-refactor-plan.md`
- Implement: `client/src/stores/stage-store.ts`
- Implement: `client/src/features/stage/components/stage-window.tsx`
- Implement: `client/src/features/stage/components/stage-tab-bar.tsx`
- Create: `client/src/features/stage/components/stage-sidebar.tsx`
- Create: `client/src/features/stage/components/stage-tool-row.tsx`
- Create: `client/src/features/stage/components/stage-resource-browser.tsx`
- Create: `client/src/features/stage/utils/open-or-focus-stage-tool-tab.ts`

- [ ] **Step 4.1: 按现有子计划拆成并行批次**
  - Batch D1：`StageStore` 导航态 + `open-or-focus` helper
  - Batch D2：`StageWindow` 两栏骨架 + 删除 `StageDock`
  - Batch D3：`StageSidebar` + `ToolRow` + `ResourceBrowser`
  - Batch D4：`StageTabBar` 视觉重构 + adapter 兼容验证

- [ ] **Step 4.2: 严格按子计划执行**
  - 不跳过现有 `stage-window-layout-refactor-plan.md` 的测试、type-check、文档回写步骤
  - 若执行中发现要改需求，先回 design / plan，不直接边写边漂移

- [ ] **Step 4.3: 将 Stage 作为下一个唯一主实现面**
  - 本批次不并行开启新的 ER / Report / Dashboard 功能计划
  - 先把承载工作台的基础布局做稳

### Task 5: 后续 Batch E — 文档与 Backlog 治理

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/design-docs/index.md`
- Modify: `docs/exec-plans/ui-demo-stage-animation-debt.md`
- Review: `docs/product-specs/index.md`

- [ ] **Step 5.1: 统一 active/completed 状态**
  - 清掉“代码已完成但计划还在 in_progress”的失真状态
  - 清掉“计划已完成但 design 仍停在 approved”的失真状态

- [ ] **Step 5.2: 治理历史债务文档**
  - 审核 `ui-demo-stage-animation-debt.md` 中哪些项已过时
  - 仍有效的项迁回主技术债台账或拆成新的明确计划
  - 失效项归档或标注 stale，避免继续误导

- [ ] **Step 5.3: 为二期 backlog 准备进入条件**
  - Stage workbench 稳定后，再评估是否启动以下能力的 spec / plan：
    - ER 图设计器
    - Report / Dashboard
    - 查询分页 / 虚拟滚动 / 查询历史 / 导出
    - DDL / DML 分级执行闭环

## Ordering

1. 必须先完成 Task 1-3 中至少 2 个收尾，避免活跃计划长期堆积
2. `Stage Window Layout Refactor` 必须在收尾批次结束后再进入主实现面
3. 二期 backlog 扩展必须以 Stage workbench 稳定为前提，不提前开工

## Exit Criteria

- 当前 3 个 `in_progress` 计划全部收口，或明确拆出 follow-up plan 后从原计划退出
- `docs/exec-plans/index.md` 与 `docs/design-docs/index.md` 状态一致
- `Stage Window Layout Refactor` 进入正式执行
- backlog 重新按“立即收尾 / 下一个主线 / 后续二期能力”三层结构整理完毕

## Notes

- 当前唯一仍开放的主技术债 `TD-SINGLE-EMPTY-SESSION-MULTINODE` 保持观察，不纳入本轮桌面单机迭代阻塞项
- 若 `SQL Risk Classification & IT CI Gate` 的剩余问题被确认主要是 CI / Docker 环境约束，应拆成测试基线治理，不要阻塞 Stage 前端主线
