# BUG 写作协议

> **AI 必读**：新建 / 修改 BUG 文档前，先读完本文件。本文件是 [docs/bugs/index.md](index.md) 的写作侧手册，与 BUG 文件 schema 共同构成完整契约。

## TL;DR

- **存哪**：`docs/bugs/BUG-NNNN-<kebab-slug>.md`，扁平不分目录
- **ID 怎么取**：读 `index.md` 的 "当前编号" 行拿到下一个值，写完 BUG 后把该行 +1
- **状态怎么改**：只改 frontmatter `status` 字段 + `index.md` 表格行，**绝不移动文件**
- **截图放哪**：`docs/bugs/assets/BUG-NNNN/screenshot-XX.png`，仅 PNG ≤ 500KB

## 完整文件模板（复制即用）

```markdown
---
id: BUG-NNNN
title: 一句话描述
status: open
priority: P1
source: e2e-playwright
modules: [stage]
discovered: YYYY-MM-DD
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary
一句话或两句话描述 BUG 本质（不是 title 复读）。

## Reproduction Steps
1. ...
2. ...
3. ...

## Expected vs Actual
- **Expected**: ...
- **Actual**: ...

## Environment
- Backend commit: <sha>
- Frontend commit: <sha>
- OS / Browser: ...
- Data source: MySQL 8.x / PG 16 / H2 / N/A

## Evidence
- ![截图1](assets/BUG-NNNN/screenshot-01.png) — 简短说明
- Trace（本地）: `tmp/playwright/YYYY-MM-DD/trace.zip`
- 控制台错误片段：
  ```
  ...
  ```

## Root Cause
TBD

## Fix
TBD

## Verification
TBD

## Notes
TBD
```

## frontmatter 字段语义

| 字段 | 必填 | 类型 | 候选值 / 格式 | 写入规则 |
|------|------|------|-------------|---------|
| `id` | 是 | string | `BUG-NNNN`（4 位） | 取 `index.md` 当前编号，写完 +1，永不复用 |
| `title` | 是 | string | 一句话 | 与文件名 slug 对应 |
| `status` | 是 | enum | `open` / `investigating` / `fixed` / `verified` / `closed` / `wontfix` / `duplicate` | 见下方状态流转 |
| `priority` | 是 | enum | `P0` / `P1` / `P2` | P0=阻塞，P1=影响质量，P2=改善（与 tech-debt-tracker 统一） |
| `source` | 是 | enum | `e2e-mcp` / `e2e-playwright` / `roadmap-validation` / `manual-report` | 仅描述发现渠道 |
| `modules` | 是 | string[] | 自由 tag | 建议复用 `client/src/features/` 下目录名（如 `stage`、`connection`、`chat`） |
| `discovered` | 是 | date | `YYYY-MM-DD` | 发现日期 |
| `discoveredBy` | 是 | enum | `agent` / `human` | AI 自动发现填 `agent`，人工报告填 `human` |
| `testRunId` | 否 | string | 任意 | 关联同批测试运行（如 `pw-2026-05-05-001`） |
| `fixCommit` | 否 | string | short SHA / PR URL | `fixed` 状态后必填 |
| `fixPlanRef` | 否 | path | `docs/exec-plans/...` | BUG 升级为修复计划时回填 |
| `duplicateOf` | 否 | string | `BUG-NNNN` | `status=duplicate` 时必填 |
| `regression` | 是 | bool | true / false | 是否之前修过又复现 |

## 状态流转

```
                       +---------+
                       |  open   |
                       +---------+
                          |
            +-------------+-----+----------+--------+
            |                   |          |        |
            v                   v          v        v
    +---------------+    +---------+  +----------+ +---------+
    | investigating |--->|  fixed  |  | wontfix  | |duplicate|
    +---------------+    +---------+  +----------+ +---------+
                              |
                              v
                          +----------+
                          | verified |
                          +----------+
                              |
                              v
                          +--------+
                          | closed |
                          +--------+
```

**态语义：**

- `open`：刚发现、待分流
- `investigating`：AI 或人正在定位根因。**Root Cause 章节开始填写**
- `fixed`：代码改动已 commit 或 PR open，等待回归验证。**Fix 章节必填**
- `verified`：E2E 跑通过且通过、或人工验证通过。**Verification 章节必填**
- `closed`：归档（默认 verified 后 30 天自动过渡，或人工显式关闭）
- `wontfix`：决定不修。Notes 必须给出原因
- `duplicate`：与已存在 BUG 重复。`duplicateOf` 必填，Notes 链接原 BUG

**关键约束：**

- `fixed` ≠ `closed`：必须经过 `verified` 才能进 `closed`
- `closed`/`verified` → `open` 仅当出现 regression 时允许，且 **MUST** 创建新 BUG（带 `regression: true`）而非复用旧 ID

## index.md 同步清单

每次状态变化必须同步更新 `docs/bugs/index.md`：

| 操作 | 必须更新 index.md 的位置 |
|------|------------------------|
| 新建 BUG | "当前编号" +1、Open BUGs 表插入行、By Module、By Source |
| `open` → `investigating` | Open BUGs 移除、In Progress 插入 |
| `open` / `investigating` → `fixed` | Open BUGs / In Progress 移除、In Progress 插入（status=fixed） |
| `fixed` → `verified` | In Progress 移除、Recently Closed 插入 |
| `verified` → `closed` | Recently Closed 状态字段更新 |
| `open` → `wontfix` / `duplicate` | Open BUGs 移除、Wontfix/Duplicate 表插入 |

## 证据存储规则

- **截图**：仅 PNG，单张 ≤ 500KB，路径 `docs/bugs/assets/BUG-NNNN/<file>.png`，建议 1-3 张最多 5 张
- **trace / HAR / HTML dump 等大体积证据**：留在本地 `tmp/playwright/<timestamp>/`，**不入 git**，BUG 文件 Evidence 章节只引用本地路径
- **不接受 JPG / MP4 / GIF**

这是 CLAUDE.md `### MCP / Skill Temporary Files` 规则的**唯一豁免**。

## 常见反例（不要做）

- ❌ 复用已存在的 ID（即便原 BUG 是 wontfix）
- ❌ 状态变化时移动文件（破坏外部链接）
- ❌ 把 trace / HAR / 录屏入 git
- ❌ 省略章节（用 `TBD` / `N/A` 显式占位，绝不删章节）
- ❌ 直接 `open` → `closed`（必须经过 `fixed` → `verified`）
- ❌ 把 BUG 当技术债登（行为偏差登 BUG，已知设计/代码债登 [tech-debt-tracker](../exec-plans/tech-debt-tracker.md)）

## 与 exec-plans 联动

当 BUG 修复需要多步实现时：

1. 在 `docs/exec-plans/YYYY-MM-DD-fix-bug-NNNN-<topic>-plan.md` 新建修复 plan
2. 在 BUG 文件 frontmatter 回填 `fixPlanRef: docs/exec-plans/YYYY-MM-DD-fix-bug-NNNN-<topic>-plan.md`
3. 在 plan 文件的 "Goal" 或 "Background" 章节链接回 BUG 文件
4. plan 完成后，BUG 状态推进到 `fixed`，`fixCommit` 回填

## 与 tech-debt-tracker 区分

| 触发场景 | 登记到 |
|----------|-------|
| E2E 测试发现按钮没响应 / 数据错 | **BUG**（运行时偏差） |
| 代码 review 发现某模块耦合过深 | **tech-debt** |
| 修一个 feature 时发现旁路逻辑硬编码 | **tech-debt** |
| 用户反馈某操作报错 | **BUG** |
| 已知某 SQL 类型未覆盖（功能空缺） | **tech-debt**（或 product-specs 待办） |

简言之：**实际跑出来的偏差登 BUG，看代码看出来的隐患登 tech-debt**。

## 相关文档

- BUG 索引（入口）：[index.md](index.md)
- 设计 spec：[../product-specs/2026-05-05-bug-tracking-system-design.md](../product-specs/2026-05-05-bug-tracking-system-design.md)
- 技术债跟踪：[../exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md)
- 手测脚本：[../testing/](../testing/)
