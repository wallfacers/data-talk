# Data Source Coverage: Wave C Sub-Wave Roadmap

| 字段 | 值 |
|---|---|
| 日期 | 2026-05-08 |
| 状态 | Draft（伴随 wave-c umbrella 一同推进） |
| 上级文档 | [Wave C Umbrella Design](./2026-05-08-data-source-coverage-wave-c-design.md) |
| 兄弟文档 | [Diagnostics Day-2 Plan](../exec-plans/2026-05-08-diagnostics-day2-plan.md) |
| 目的 | 在 umbrella spec §6 "Recommended Implementation Order" 之上，给出**面向执行**的子任务启动顺序、并发策略、跨 kind 复用依赖图、readiness gate 与里程碑，作为接下来 4 个 child design + child plan 工作的导航。 |

## 1. 范围与边界

### 是什么

- 4 个未启动 Wave C kind 的 **child design 与 child plan 启动顺序**：`opengauss` / `oceanbase` / `kingbase` / `dameng`。
- 跨 kind 共享 reuse 套件（`MySqlProtocolReuseRule` / `PgForkReuseRule` / `MultiModeConnectionShape`）的 **产出/消费时序图**。
- 每个 kind 进入 brainstorming → design → plan → 实施各阶段的 **readiness gate**。
- 与 day2 plan 已落地工作的 **接续兼容点**。

### 不是什么

- **不重写** umbrella spec §5 / §6 / §7 / §8 已锁定的决策；本路线图引用而非取代。
- **不预设** 任一 child design 的具体技术决策（driver pinning、URL 形式、Day-1 unsupported 矩阵 etc.）—— 那是各 child design brainstorming 的产出。
- **不替代** child design 与 child plan；4 份 design + 4 份 plan 仍按 umbrella §10 的"独立完整生命周期"分别落地。
- **不规划** `gaussdb`：umbrella §6 步骤 6 / §10 终止条件已说明；本路线图只覆盖前 4 kind。

## 2. 已落地的前置依赖

接下来 4 个 kind 的工作 **不是从零开始**。下列已落地工件可直接消费：

| 工件 | 提供者 | 消费方向 |
|---|---|---|
| `MySqlProtocolReuseRule` 6-base abstract test kit | `tidb` child plan（已 verify，commit `273f1e1`） | `oceanbase` MySQL-mode 必须复用并提供 6 个 concrete IT 子类 |
| `MySqlSqlStatementSplitter` 等价测试模式 | `tidb` child plan | `oceanbase` MySQL-mode 复用 |
| `parseMySqlJsonPlan` 上移到 `AbstractDiagnosticsProvider` | day2 plan Task 0.3（已完成） | `oceanbase` MySQL-mode Day-2 EXPLAIN 升级直接复用 |
| `TabularLayout` / `TextPlanGrammar` records | day2 plan Task 0.1 / 0.2（已完成） | `dameng` Day-2 EXPLAIN 用 `TabularLayout` 自定义 grammar 时复用 |
| 5 个基类 helper（`parseScanType` / `mapTabularPlanToNodes` / `mapTextPlanToNodes` / `mapXmlPlanToNodes` / `mapPermissionOrDriverError`） | day2 plan Task 0.3（已完成） | 4 个 kind 的 Day-2 升级全部复用 |
| 11 kind Day-2 EXPLAIN/INDEX_HINTS 真实化模式 | day2 plan（已完成） | 4 个 kind 的 child design "Day-2 upgrade path" 节照此命名规范 |

**这意味着**：4 个 kind 的 child design 在写"Day-2 upgrade path"节时，**不需要再设计基类机制**，只需对齐 grammar / fixture / permission map / i18n key 命名。

## 3. 启动顺序与并发策略

### 3.1 推进顺序总图

```
[已落地]
  tidb child plan ─┐
                   ├──▶ MySqlProtocolReuseRule 套件
  day2 plan      ──┴──▶ AbstractDiagnosticsProvider helpers + TabularLayout/TextPlanGrammar

[本路线图启动范围]
                                                                 ┌───────────────┐
  Step 2:  opengauss design ──▶ opengauss plan ──▶ ship ────────▶│ PgForkReuseRule │
                                                                 └────────┬──────┘
  Step 3:  oceanbase design (含 MultiModeConnectionShape 决策)            │
                          └──▶ oceanbase plan ──▶ ship ─────────▶┌────────┴──────┐
                                                                 │ MultiModeConnShape│
  Step 5:  dameng design (含 Driver Decision)                    └────────┬──────┘
                          └──▶ dameng plan ──▶ ship                       │
                                                                          │
  Step 4:  kingbase design (复用上面两个套件) ◀───────────────────────────┘
                          └──▶ kingbase plan ──▶ ship
```

### 3.2 并发策略矩阵

| 阶段 | opengauss | oceanbase | dameng | kingbase | 并发策略 |
|---|---|---|---|---|---|
| **brainstorming + child design 草稿** | ✅ 立即 | ✅ 立即 | ✅ 立即 | ⏸ 等 opengauss + oceanbase design 草稿 | 三 kind 并发；kingbase 串行 |
| **child design 用户审批 + codex review** | 串行（一份一审） | 串行 | 串行 | 串行 | 顺序固定：opengauss → oceanbase → dameng → kingbase |
| **child plan 草稿** | 待 design 通过 | 待 design 通过 | 待 design 通过 | 待 design 通过 + 上游套件就位 | 严格 blocked on design approval |
| **实施（写代码）** | 单 kind 串行 | 单 kind 串行 | 单 kind 串行 | 单 kind 串行 | 同时进行多 kind 实施会重蹈 ad4c1f0 越界，**禁止** |

### 3.3 为什么 kingbase 必须等 opengauss + oceanbase

`kingbase` 是 wave-c 唯一 **同时复用两个跨 kind 套件** 的 kind：

- `PgForkReuseRule`（由 `opengauss` 产出）：PG-fork 协议复用 `PostgresJdbcSqlStatementSplitter` / metadata / risk 的等价测试模式。
- `MultiModeConnectionShape`（由 `oceanbase` 产出）：`compatibilityMode` 字段、conditional 连接表单、target resolution 多模式骨架。

如果 kingbase design 在两个套件落地前先写，会面临两个上游接口未定的风险，导致 design 反复修订或 plan 实施时被迫 retro-fit。等待 1–2 周成本远低于并发返工。

`dameng` 与上面两个套件无依赖（Oracle-like 专有协议、单 mode），可与 opengauss / oceanbase 并发推进 design 阶段。

## 4. 跨 Kind 复用依赖图

```
                                     [day2 plan 基类 helper 层]
                                              ↑（全部复用）
                                              │
   ┌──────────────────────────────────────────┴──────────────────────────────────┐
   │                                                                              │
   ▼                                                                              ▼
[opengauss]                                                                  [dameng]
   ├── 复用 PostgresJdbcSqlStatementSplitter（splitter）                        ├── 自定义 splitter（PL/SQL block 暂不支持，generic single-stmt）
   ├── 复用 PostgreSQL metadata path                                            ├── 自定义 risk classifier
   ├── 自定义 risk classifier（pgxc_*, opengauss-specific）                      ├── 自定义 system schema filter（SYS, SYSDBA, CTISYS, DMHR, ...）
   ├── 产出 PgForkReuseRule abstract test kit                                   ├── Day-2: 自定义 DamengTabularGrammar
   └── Day-2: 自定义 PostgresJsonPlanParser (text+json)                          └── Day-2: 暂不推荐 INDEX_HINTS
            │
            │（消费）
            ▼
[kingbase]                            [oceanbase]
   ├── 复用 PgForkReuseRule（消费）       ├── 复用 MySqlSqlStatementSplitter（splitter）
   ├── 复用 PostgresJdbcSqlStatementSplitter ├── 复用 MySqlProtocolReuseRule（已落地，必须 6 concrete IT 子类）
   ├── 复用 MultiModeConnectionShape（消费）  ├── 产出 MultiModeConnectionShape（含 compatibilityMode 字段 + Flyway 迁移）
   ├── 自定义 risk classifier（SYS_*, kingbase-specific） ├── kind-specific 字段：tenant（ConnectionRecord 列 + Flyway）
   └── Day-2: 复用 PostgresJsonPlanParser    └── Day-2: 复用 parseMySqlJsonPlan（已上移到基类）
                                                         (Oracle-mode dialect_unsupported, 不计入 Day-1)
```

### 4.1 产出/消费时序约束

1. `opengauss` ship 之前，`PgForkReuseRule` abstract base + opengauss concrete IT 子类必须落地；这是 kingbase plan 启动的硬门槛。
2. `oceanbase` ship 之前，`MultiModeConnectionShape` 必须以"包含 `compatibilityMode` 字段、Flyway 迁移、目标解析多模式骨架"的可消费形态落地；这是 kingbase plan 启动的硬门槛。
3. `dameng` 与上面 2 项无依赖，可独立 ship。
4. `kingbase` 是 wave-c 4 个中的最后一个。

## 5. 每 Kind Readiness Gate

每个阶段进入下一阶段必须满足的 **明确条件**。Gate 不达标 = 不启动。

### 5.1 opengauss

| Gate | 条件 | 当前状态 |
|---|---|---|
| brainstorming 启动 | wave-c umbrella spec §10 已批准 | ✅（umbrella spec 已注册到 product-specs/index.md） |
| child design 完成 | brainstorming 决策记录完整：driver GAV pinning、URL 参数、splitter 复用边界、Day-1 unsupported 矩阵、T1 fixture（`enmotech/opengauss`）选型、Day-2 upgrade path | ⏳ 待启动 |
| child design 批准 | user review pass + codex review pass | ⏳ 待 |
| child plan 启动 | child design 已批准 + Driver Decision 签字 | ⏳ 待 |
| child plan ship | `mvn verify` BUILD SUCCESS + L1+L2+L3 测试通过 + L4 testcontainers IT `@Disabled` 钩子就位 | ⏳ 待 |
| 升级 first-class | DATA_SOURCE_TYPE_COMPATIBILITY.md Snapshot 行更新 + AGENTS.md 加 openGauss 段 | ⏳ 待 |

### 5.2 oceanbase

| Gate | 条件 | 当前状态 |
|---|---|---|
| brainstorming 启动 | wave-c umbrella 批准 | ✅ |
| child design 完成 | 同上 + 多模式专项决策：`compatibilityMode` 字段形状、`tenant` 列 vs username 组合（推荐独立 column）、Flyway 迁移版本号、Oracle-mode `dialect_unsupported` 范围 | ⏳ 待启动 |
| child design 批准 | user + codex review | ⏳ 待 |
| child plan 启动 | child design 批准 + Flyway 迁移评审 | ⏳ 待 |
| child plan ship | `mvn verify` SUCCESS + 6 concrete `OceanBase*ReuseIT` 子类继承 `MySqlProtocolReuseRule` + Flyway 迁移落地 | ⏳ 待 |
| 升级 first-class | Snapshot + AGENTS.md OceanBase 段 + ConnectionRecord 多列已 ship | ⏳ 待 |

### 5.3 dameng

| Gate | 条件 | 当前状态 |
|---|---|---|
| brainstorming 启动 | wave-c umbrella 批准 | ✅ |
| child design 完成 | 同 5.1 + **Driver Decision 必须签字**：`Dm8JdbcDriver` 还是 `DmJdbcDriver18` artifact、license 二次分发权、Maven 镜像 vs 离线 jar 路径、CI bootstrap 任务设计 | ⏳ 待启动 |
| child design 批准 | user + codex review + Driver Decision sign-off（commercial driver 路径必须明确） | ⏳ 待 |
| child plan 启动 | child design 批准 + 离线 jar 路径或镜像可达 | ⏳ 待 |
| child plan ship | `mvn verify` SUCCESS + L2 fixture（trial license）smoke 通过 | ⏳ 待 |
| 升级 first-class | Snapshot + AGENTS.md Dameng 段（含 dm/dm8 alias 归一） | ⏳ 待 |

### 5.4 kingbase

| Gate | 条件 | 当前状态 |
|---|---|---|
| brainstorming 启动 | wave-c umbrella 批准 + opengauss design 草稿可见 + oceanbase design 草稿可见 | ⏳ 待 opengauss + oceanbase design 草稿 |
| child design 完成 | 同上 + Driver Decision（`kingbase8` driver 分发路径）+ kingbasees alias 归一 + 复用 PgForkReuseRule + 复用 MultiModeConnectionShape | ⏳ 待 |
| child design 批准 | user + codex review + Driver Decision sign-off | ⏳ 待 |
| child plan 启动 | child design 批准 + opengauss + oceanbase 已 ship | ⏳ 待 |
| child plan ship | `mvn verify` SUCCESS + L2 fixture smoke 通过 | ⏳ 待 |
| 升级 first-class | Snapshot + AGENTS.md KingbaseES 段 | ⏳ 待 |

## 6. 工作量估计与里程碑

> **免责**：估计是基于 tidb（已 ship）+ apache_doris（已 ship）+ day2 plan（已 ship）的实际节奏外推；child design brainstorming 可能引入未预期决策点导致估计漂移。

| Kind | 子 design 工作量 | 子 plan 工作量 | 实施工作量 | 总计 | 关键风险 |
|---|---|---|---|---|---|
| `opengauss` | 1 day | 1 day | 3 day（PG-fork reuse + 6 IT 子类） | 5 day | T1 fixture 已就绪，技术风险低 |
| `oceanbase` | 2 day（多模式 + tenant + Flyway 决策） | 1 day | 4 day（含 ConnectionRecord 字段 + Flyway + 6 reuse IT 子类） | 7 day | Flyway 迁移评审、tenant 字段形状 |
| `dameng` | 2 day（Driver Decision 评审） | 1 day | 4 day（自定义 splitter + risk + system schema + L2 smoke） | 7 day | 商业 driver 分发路径不可控 |
| `kingbase` | 1.5 day（复用上游套件） | 1 day | 3 day（消费 PgForkReuseRule + MultiModeConnectionShape） | 5.5 day | trial license 可用性 |

**总工作量上限**：约 24.5 day（理想线性串行）；并发优化后约 15 day（design 阶段并发 + 实施串行）。

### 6.1 里程碑（建议）

```
M1: 4 个 child design 草稿全部完成（opengauss + oceanbase + dameng 并发，kingbase 串接）
    ─ 预计 2026-05-15

M2: 4 个 child design 全部审批通过（user + codex review）
    ─ 预计 2026-05-20

M3: 4 个 child plan 草稿全部完成
    ─ 预计 2026-05-22

M4: opengauss ship
    ─ 预计 2026-05-27

M5: oceanbase + dameng ship（并发实施可考虑，但需各自独立 PR）
    ─ 预计 2026-06-03

M6: kingbase ship（消费 M4 + M5 套件）
    ─ 预计 2026-06-08

M7: wave-c 4 kind 全部 first-class，DATA_SOURCE_TYPE_COMPATIBILITY.md Snapshot 收尾
    ─ 预计 2026-06-10
```

> 里程碑日期是规划假设。任一 child design brainstorming 引入超预期决策、driver 分发卡住、或 fixture 不可达，对应 milestone 顺延，不强行赶工。

## 7. 与 day2 Plan 的兼容点

day2 plan 的 11 kind Day-2 升级已完成（实施于 2026-05-08）；wave-c 4 kind 的 Day-2 候选已通过 day2 plan 末尾的 "Day-3 Candidate" 节占位。本路线图与 day2 plan 的双向锚点：

| 锚点 | day2 plan 一侧 | wave-c child design 一侧 |
|---|---|---|
| Grammar 命名 | `MYSQL_GRAMMAR` / `DORIS_GRAMMAR` / `TRINO_GRAMMAR` 等 | 命名规则 `<KIND>_GRAMMAR` 或复用 PG/MySQL grammar，照搬不发明 |
| i18n key 命名 | `diagnostics.{capability}.unsupported.{kind}_{reason}` | 4 个 kind 各自的 child design 必须沿用此前缀 |
| Fixture tier 标签 | T1（自动 Docker） / T2（trial license） / T3（不可达） | child design 必须明确归类 |
| Permission error map | `mapPermissionOrDriverError(SQLException, capability, kind)` | child design 必须列出该 kind 的 permission 错误码识别规则 |

**这意味着**：4 个 kind 的 child design 写"Day-2 EXPLAIN/INDEX_HINTS upgrade path"节时，不在自由发挥，而是**对齐已有规范**。这是减少未来 Day-3 实施歧义的关键约束。

## 8. 与 wave-c Umbrella 的关系

本路线图是 **umbrella spec §6 / §9 的执行延伸**，不取代 umbrella 任何条款。Umbrella 中的硬约束在本路线图内全部保留：

- §4 Non-Goals：本路线图不动 `ConnectionKind` 常量、`JdbcUrlBuilder` 分支、splitter、provider —— 直到对应 child plan 启动。
- §7.1 Driver Distribution & License Gate：dameng / kingbase child design 启动条件中明确包含。
- §7.2 Compatibility-Mode Policy：oceanbase child design 启动条件中明确包含。
- §7.5 AGENTS.md / Prompt Contract Rule：每个 kind 的 AGENTS.md 段加入时机绑定到 child plan verify。
- §10 Termination conditions：本路线图沿用，dameng / kingbase / gaussdb 任何阶段达成终止条件，路线图同步标注 "Blocked: <reason>" 并记录到 tech-debt-tracker。

## 9. 启动信号

本路线图被 user 接受 + 注册到 [docs/product-specs/index.md](./index.md) §8 之后，立即启动 5.1 opengauss brainstorming（umbrella §6 步骤 2 的硬序列）。

后续 4 个 kind 的 child design / child plan 全部以独立 PR 落地；本路线图不再追加内容，里程碑漂移直接反映在 [docs/exec-plans/index.md](../exec-plans/index.md) 的 Active 表格里。
