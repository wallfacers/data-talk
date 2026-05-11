# Wave C 数据源并行执行编排计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 3 个并行 git worktree，分别调度独立 agent 执行 openGauss、OceanBase、Dameng 的 13-Task 实现计划，完成后统一联调验证。

**Architecture:** 使用 git worktree 创建 3 个独立工作目录，每个 worktree 在独立分支上工作。通过 dispatching-parallel-agents 技能分发 3 个 subagent，每个 subagent 携带对应数据源的完整计划提示词。openGauss 产出 PgForkReuseRule，OceanBase 产出 MultiModeConnectionShape，Dameng 独立无交叉依赖。

**Tech Stack:** Git worktree, Spring Boot 3.5, Java 21, JUnit 5, Testcontainers, React 19, TypeScript, Vitest

**Dependency Graph:**
```
[develop] ─┬─> feat/opengauss-first-class   (Step 2, 13 Tasks, 产出 PgForkReuseRule)
           ├─> feat/oceanbase-first-class  (Step 3, 13 Tasks, 产出 MultiModeConnectionShape v1)
           ├─> feat/dameng-first-class     (Step 5, 13 Tasks, 独立无依赖)
           └─> [WAIT] feat/kingbase-first-class (Step 4, 依赖 openGauss + OceanBase 产出)
```

---

## File Structure

**Created:**
- `worktrees/opengauss/` — git worktree for openGauss (branch: feat/opengauss-first-class)
- `worktrees/oceanbase/` — git worktree for OceanBase (branch: feat/oceanbase-first-class)
- `worktrees/dameng/` — git worktree for Dameng (branch: feat/dameng-first-class)

**Existing plans referenced (not modified):**
- `docs/exec-plans/2026-05-08-data-source-coverage-opengauss-plan.md` — openGauss 详细 13-Task 计划
- `docs/exec-plans/2026-05-08-data-source-coverage-oceanbase-plan.md` — OceanBase 详细 13-Task 计划
- `docs/exec-plans/2026-05-08-data-source-coverage-dameng-plan.md` — Dameng 详细 13-Task 计划

---

## Phase 1: 创建工作树

- [ ] **Step 1: 创建 openGauss worktree**

```bash
cd /home/wushengzhou/workspace/github/data-talk
mkdir -p worktrees
git worktree add worktrees/opengauss develop -b feat/opengauss-first-class
```

Expected: New worktree at `worktrees/opengauss/` on branch `feat/opengauss-first-class`

- [ ] **Step 2: 创建 OceanBase worktree**

```bash
git worktree add worktrees/oceanbase develop -b feat/oceanbase-first-class
```

Expected: New worktree at `worktrees/oceanbase/` on branch `feat/oceanbase-first-class`

- [ ] **Step 3: 创建 Dameng worktree**

```bash
git worktree add worktrees/dameng develop -b feat/dameng-first-class
```

Expected: New worktree at `worktrees/dameng/` on branch `feat/dameng-first-class`

- [ ] **Step 4: 验证 worktree 创建**

```bash
git worktree list
```

Expected output includes:
```
/home/wushengzhou/workspace/github/data-talk/worktrees/opengauss    <commit> [feat/opengauss-first-class]
/home/wushengzhou/workspace/github/data-talk/worktrees/oceanbase   <commit> [feat/oceanbase-first-class]
/home/wushengzhou/workspace/github/data-talk/worktrees/dameng      <commit> [feat/dameng-first-class]
```

---

## Phase 2: 并行分发 Subagent

> **策略:** 3 个 subagent 同时启动，每个携带对应数据源的完整实现提示词。每个 subagent 按照其数据源计划的 Task 顺序执行，每个 Task 一个 commit。所有 IT 测试保持 @Disabled 标记。

### Subagent A: openGauss (Task A)

**工作目录:** `worktrees/opengauss/`
**分支:** `feat/opengauss-first-class`
**必读文档:**
1. `docs/exec-plans/2026-05-08-data-source-coverage-opengauss-plan.md`
2. `docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md`
3. `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
4. `client/DESIGN.md`
5. `docs/bugs/index.md`

**核心约束:**
- 驱动: `org.opengauss:opengauss-jdbc:5.1.0-og`
- 协议: PostgreSQL-fork 线协议，复用 PG 路径
- 产出: `PgForkReuseRule` 6 个 abstract base test class（KingbaseES 硬依赖）
- 容器: `enmotech/opengauss:6.0.0`（Testcontainers）
- 所有 IT 测试 `@Disabled` 标记保持不动
- 无 alias，kind 固定为 `"opengauss"`

**执行顺序（13 个 Task）:**
1. Task 1: 审批闸门 + 版本锁定 + spec status flip
2. Task 2: ConnectionKind.OPENGAUSS + JdbcUrlBuilder + ConnectionService
3. Task 3: Splitter 路由 + SqlExecuteService 分支
4. Task 4: Target Discovery + Schema Read + System Filter（12 项 system schema）
5. Task 5: OpenGaussDiagnosticsProvider + DiagnosticsService 分支
6. Task 6: classifyOpengaussSpecific 风险分类器（4 个 anchor pattern）
7. Task 7: PgForkReuseRule 6 个 abstract base test class
8. Task 8: 6 个 OpenGauss*ReuseIT concrete subclass + Testcontainers
9. Task 9: MCP ConnectionObjectType enum + AGENTS.md
10. Task 10: 前端 form/picker/toolbar/formatter/i18n
11. Task 11: 前端 vitest 单测
12. Task 12: 完整验证（mvn verify + tsc + manual smoke）— **第一阶段跳过，仅写代码**
13. Task 13: 文档 housekeeping

**第一阶段指令:** 按上述 Task 顺序实现所有代码，每个 Task 一个 commit。所有 IT 测试的 @Disabled 保持不动。不要运行 mvn verify / npm test 等完整验证命令。完成后报告完成状态。

---

### Subagent B: OceanBase (Task B)

**工作目录:** `worktrees/oceanbase/`
**分支:** `feat/oceanbase-first-class`
**必读文档:**
1. `docs/exec-plans/2026-05-08-data-source-coverage-oceanbase-plan.md`
2. `docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md`
3. `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
4. `client/DESIGN.md`
5. `docs/bugs/index.md`

**核心约束:**
- 驱动: `com.oceanbase:oceanbase-client:2.4.x`
- 协议: MySQL-mode first-class（仅支持 mysql 兼容模式）
- 产出: `MultiModeConnectionShape` v1（kind-neutral 抽象，KingbaseES 后续消费）
- Flyway V18: 3 新列（compatibility_mode + oceanbase_tenant + oceanbase_cluster）+ 3 CHECK 约束
- 容器: `oceanbase/oceanbase-ce:4.2.1-lts`（Testcontainers，log-based wait 180s）
- 用户名拼装: `<user>@<tenant>[#<cluster>]`
- 所有 IT 测试 `@Disabled` 标记保持不动

**执行顺序（13 个 Task）:**
1. Task 1: 审批闸门 + 版本锁定
2. Task 2: CompatibilityMode enum（mysql/oracle/pg）
3. Task 3: MultiModeConnectionShape v1（validateModeForKind + isDay1FirstClassMode）
4. Task 4: Flyway V18 迁移（3 列 + 3 CHECK）
5. Task 5: ConnectionKind.OCEANBASE + ConnectionRecord + JdbcUrlBuilder + pom
6. Task 6: ConnectionService.composeOceanBaseUsername
7. Task 7: Splitter 路由 + Discovery 分支 + 6 个 anchored risk pattern
8. Task 8: OceanBaseDiagnosticsProvider（9 hook dialect_unsupported）
9. Task 9: 6 个 OceanBase*ReuseIT（继承 MySqlProtocolReuseRule 抽象基）
10. Task 10: 前端 multi-mode-connection-fields.tsx + oceanbase-connection-fields.tsx
11. Task 11: MCP enum + AGENTS.md
12. Task 12: 完整验证 — **第一阶段跳过，仅写代码**
13. Task 13: 文档 housekeeping

**第一阶段指令:** 按上述 Task 顺序实现所有代码，每个 Task 一个 commit。所有 IT 测试的 @Disabled 保持不动。不要运行完整验证。完成后报告。

---

### Subagent C: Dameng (Task C)

**工作目录:** `worktrees/dameng/`
**分支:** `feat/dameng-first-class`
**必读文档:**
1. `docs/exec-plans/2026-05-08-data-source-coverage-dameng-plan.md`
2. `docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md`
3. `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
4. `client/DESIGN.md`
5. `docs/bugs/index.md`

**核心约束:**
- 驱动: `com.dameng:DmJdbcDriverX:8.1.x`（Maven Central，禁止 offline jar）
- 协议: Oracle-like 单模独立 kind
- URL: `jdbc:dm://<h>:<p>` port 5236（无 /<database> 后缀）
- `databaseName` 字段复用为 initial schema name（Oracle precedent）
- 无 alias：dm/dm8/DM/DM8/达梦 全部拒绝
- 不产出跨 kind 抽象基类（单消费者模式）
- 不消费 MultiModeConnectionShape / PgForkReuseRule
- 无 Testcontainers IT，使用 JDBC mock / 硬编码 fixture

**执行顺序（13 个 Task）:**
1. Task 1: 审批闸门 + 版本锁定
2. Task 2: ConnectionKind.DAMENG + 拒绝所有 alias 测试
3. Task 3: 驱动互斥测试 + pom + JdbcUrlBuilder dameng 分支
4. Task 4: URL 测试 + ConnectionRecord 字段 + SET SCHEMA 注入
5. Task 5: Splitter 路由（复用 genericSplitter）+ Discovery（5 schema filter）
6. Task 6: DamengRiskClassifier 双通道 5+3 anchored pattern
7. Task 7: 3 个 kind-private 等价测试（Splitter / Metadata / Normalization）
8. Task 8: DamengDiagnosticsProvider + i18n
9. Task 9: manual smoke 脚本 + ?schema vs SET SCHEMA 决策
10. Task 10: 前端 dameng-connection-fields.tsx（单模，不渲染 multi-mode 组件）
11. Task 11: MCP enum + AGENTS.md
12. Task 12: 完整验证 — **第一阶段跳过，仅写代码**
13. Task 13: 文档 housekeeping

**第一阶段指令:** 按上述 Task 顺序实现所有代码，每个 Task 一个 commit。所有测试保持 @Disabled。不要运行完整验证。完成后报告。

---

## Phase 3: 统一联调（所有 subagent 完成后执行）

> **Gate:** 仅在 Phase 2 所有 3 个 subagent 报告完成后执行。

- [ ] **Step 1: 逐个 worktree 编译验证**

```bash
# openGauss worktree
cd worktrees/opengauss && mvn clean compile -q && cd client && npx tsc --noEmit
# OceanBase worktree
cd worktrees/oceanbase && mvn clean compile -q && cd client && npx tsc --noEmit
# Dameng worktree
cd worktrees/dameng && mvn clean compile -q && cd client && npx tsc --noEmit
```

- [ ] **Step 2: 取消 @Disabled 并运行测试**

每个 worktree 中：
```bash
# 取消 IT 测试的 @Disabled 注解（openGauss / OceanBase）
# Dameng 无 Testcontainers IT，跳过此步
mvn clean verify
```

- [ ] **Step 3: 修复编译/类型错误**

根据 Step 1-2 结果修复所有问题，提交修复。

- [ ] **Step 4: 报告联调结果**

汇总 3 个 worktree 的编译、测试通过率。

---

## Phase 4: KingbaseES（等待 Phase 3 完成后启动）

> **Gate:** KingbaseES 依赖 openGauss 的 PgForkReuseRule 和 OceanBase 的 MultiModeConnectionShape v1。必须在 Phase 3 完成且两个分支合并到 develop 后，才能创建 KingbaseES worktree 并开始实现。

KingbaseES 的详细计划在 `docs/exec-plans/2026-05-08-data-source-coverage-kingbase-plan.md`。启动时参照该计划创建新的 subagent。

---

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 3 个 subagent 同时修改共享文件（如 i18n/messages.ts、ConnectionObjectType.java）产生冲突 | 每个 subagent 在独立分支工作，冲突在 Phase 3 合并时解决；subagent 提示词中明确标注可能冲突的文件 |
| openGauss 的 PgForkReuseRule 包路径与 OceanBase 的 MultiModeConnectionShape 包路径冲突 | 两个产出在不同包下（pgfork/ vs multimode/），不太可能冲突 |
| Testcontainers 容器启动慢导致 IT 测试超时 | 第一阶段不运行 IT 测试；Phase 3 联调时单独处理 |
| Dameng 驱动不在 Maven Central 或需要认证 | Task 1 已包含驱动可达性检查，失败时立即报告 |

---

## 验证清单

- [x] 3 个 worktree 创建成功 *(实际执行方式：各 child plan 直接在 develop 分支上通过独立 subagent 完成，未使用 worktree 隔离)*
- [x] 3 个 subagent 启动成功
- [x] openGauss: 13 个 Task 全部完成，代码已 commit — [child plan](./2026-05-08-data-source-coverage-opengauss-plan.md) shipped 2026-05-09
- [x] OceanBase: 13 个 Task 全部完成，代码已 commit — [child plan](./2026-05-08-data-source-coverage-oceanbase-plan.md) shipped 2026-05-09
- [x] Dameng: 13 个 Task 全部完成，代码已 commit — [child plan](./2026-05-08-data-source-coverage-dameng-plan.md) shipped 2026-05-09
- [x] Phase 3 联调：所有 child plan 编译通过（mvn compile SUCCESS）
- [x] Phase 3 联调：IT 测试通过
- [x] KingbaseES child plan 完成（Phase 4）— [child plan](./2026-05-08-data-source-coverage-kingbase-plan.md) shipped 2026-05-09

## Completion Note (2026-05-12)

本计划原设计为 3 个 git worktree 并行执行。实际执行中，5 个 Wave C child plan（TiDB → openGauss → OceanBase → KingbaseES → Dameng）各自通过独立 subagent 在 develop 分支上顺序/并行完成，未使用 worktree 隔离。所有 5 个 kind 已 ship 为 first-class，仅 GaussDB 保留为文档占位。本编排计划随所有 child plan 完成而关闭。
