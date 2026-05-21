# BUG Tracking System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `docs/bugs/` BUG tracking documentation system per [spec](../product-specs/2026-05-05-bug-tracking-system-design.md), and integrate it into AGENTS.md / CLAUDE.md as a hard gate so AI agents auto-record E2E findings and consult known BUGs before fixing or planning.

**Architecture:** Pure documentation work. Three new docs (`index.md`, `README.md`, `assets/.gitkeep`) under `docs/bugs/`. Two existing top-level files (`AGENTS.md`, `CLAUDE.md`) get parallel edits: one Knowledge Base Navigation row, one new `### BUG Tracking Gate` Working Rules section, one MCP/Skill Temporary Files exemption line.

**Tech Stack:** Markdown + YAML frontmatter only. No code, no tests, no compile step. Verification = `ls` / `grep` / structure inspection.

**Spec coverage map:**

| Spec section | Plan task |
|--------------|-----------|
| §3 directory layout | Tasks 1, 2, 3 |
| §4 BUG file schema | Documented in Task 2 (README.md) — no file to create yet |
| §5 index.md format | Task 1 |
| §6 state machine | Documented in Task 2 (README.md) |
| §7 evidence storage rules | Tasks 3 (assets/.gitkeep), 4, 5 (AGENTS/CLAUDE exemption) |
| §8 AGENTS/CLAUDE integration | Tasks 4, 5 |
| §9 README.md outline | Task 2 |
| §10 implementation checklist | All tasks |
| §11 verification criteria | Task 7 |

**Execution batches** (per CLAUDE.md `### Parallel Plan Execution`):
- **Batch A** (independent file creations): Tasks 1, 2, 3 — can run in parallel
- **Batch B** (independent file edits): Tasks 4, 5 — can run in parallel after Batch A
- **Sequential**: Tasks 6, 7, 8

---

### Task 1: Create `docs/bugs/index.md`

**Files:**
- Create: `docs/bugs/index.md`

**Reference:** Spec §5.1 §5.2.

- [x] **Step 1: Create directory and file with full initial content**

Create the file with this exact content:

````markdown
# BUG 索引

DataTalk 运行时缺陷的集中记录。所有 BUG 详情请进单文件查看。

## 写作协议

新建 / 修改 BUG 文档前，**MUST** 先读 [README.md](README.md)（模板、字段语义、状态流转、index.md 同步清单）。

## 当前编号

下一个分配 ID：**BUG-0001**（永不复用，单调递增）

## Open BUGs（按 priority 倒序，P0 → P2）

| ID | Title | Priority | Source | Modules | Discovered |
|----|-------|----------|--------|---------|------------|
| —  | 当前无未修 BUG | — | — | — | — |

## In Progress（status = investigating | fixed 等待 verify）

| ID | Title | Status | Priority | Owner |
|----|-------|--------|----------|-------|
| —  | 当前无进行中 BUG | — | — | — |

## Recently Closed（最近 30 天，status = verified | closed）

| ID | Title | Status | Closed Date | FixCommit |
|----|-------|--------|-------------|-----------|
| —  | 当前无最近关闭 BUG | — | — | — |

## By Module（聚合视图，仅列 open + in-progress）

- *暂无活跃 BUG*

## By Source（聚合视图，仅列 open + in-progress）

- *暂无活跃 BUG*

## Wontfix / Duplicate（终态归档，无时间限制）

| ID | Resolution | Reason / DuplicateOf |
|----|------------|----------------------|
| —  | — | — |

## Closure History

30 天前的 closed/verified 折叠归档。详见 `git log -- docs/bugs/`，本节不维护。

## 相关文档

- 写作协议：[README.md](README.md)
- 设计 spec：[../product-specs/2026-05-05-bug-tracking-system-design.md](../product-specs/2026-05-05-bug-tracking-system-design.md)
- 技术债跟踪（互补）：[../exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md)
- 手测脚本（互补）：[../testing/](../testing/)
````

- [x] **Step 2: Verify file created with correct structure**

Run: `cat docs/bugs/index.md | head -20`
Expected: First 20 lines show `# BUG 索引` heading and "下一个分配 ID：**BUG-0001**" line.

Run: `grep -c "^## " docs/bugs/index.md`
Expected: `10` (ten `##` section headings: 写作协议, 当前编号, Open BUGs, In Progress, Recently Closed, By Module, By Source, Wontfix / Duplicate, Closure History, 相关文档).

---

### Task 2: Create `docs/bugs/README.md`

**Files:**
- Create: `docs/bugs/README.md`

**Reference:** Spec §4 §6 §9.

- [x] **Step 1: Create file with full writing protocol content**

Create with this exact content:

````markdown
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
````

- [x] **Step 2: Verify file created and contains all required sections**

Run: `grep -c "^## " docs/bugs/README.md`
Expected: `10` (TL;DR / 完整文件模板 / frontmatter 字段语义 / 状态流转 / index.md 同步清单 / 证据存储规则 / 常见反例 / 与 exec-plans 联动 / 与 tech-debt-tracker 区分 / 相关文档).

Run: `grep -E "regression|wontfix|duplicate|verified" docs/bugs/README.md | wc -l`
Expected: At least `4` (each term should appear at least once).

---

### Task 3: Create `docs/bugs/assets/.gitkeep`

**Files:**
- Create: `docs/bugs/assets/.gitkeep`

- [x] **Step 1: Create the empty placeholder file**

Run:
```bash
mkdir -p docs/bugs/assets
touch docs/bugs/assets/.gitkeep
```

- [x] **Step 2: Verify directory and file exist**

Run: `ls -la docs/bugs/assets/`
Expected: Output shows `.gitkeep` file (0 bytes).

---

### Task 4: Update `AGENTS.md`

**Files:**
- Modify: `AGENTS.md`

**Reference:** Spec §8.1, §8.2, §7.3.

- [x] **Step 1: Add Knowledge Base Navigation row**

Find the table row containing `Tech debt tracker` in `AGENTS.md`. Insert a new row immediately after it:

```markdown
| BUG 跟踪与 E2E 缺陷登记       | [docs/bugs/index.md](docs/bugs/index.md)                     |
```

- [x] **Step 2: Add `### BUG Tracking Gate` Working Rules section**

Find the `### Bug Fixes` section in `AGENTS.md`. Insert this new section **immediately after** the `### Bug Fixes` section (and its bullet content), and **before** the next `### Data Source Type Compatibility Gate` section:

````markdown
### BUG Tracking Gate

DataTalk 运行时偏差通过 `docs/bugs/` 集中记录。详见 [docs/bugs/index.md](docs/bugs/index.md) 与 [docs/bugs/README.md](docs/bugs/README.md)。

**写入触发（MUST 新建/更新 BUG 文档）：**

1. **E2E 测试发现产品行为偏差**：通过 `mcp__playwright__*` 或 `playwright-cli` skill 跑端到端测试时，发现按钮无响应、数据错误、UI 错位、控制台报错等任何与 spec 不符的行为，**MUST** 在 `docs/bugs/` 新建 BUG 文件，状态 `open`，并在 `index.md` 注册。**禁止只在对话里口头报告**。
2. **修复一个已存在 BUG 时**：用户明确要求修某 BUG，或修代码恰好闭环了某 open BUG，**MUST** 把对应 BUG 文件状态改 `fixed`，回填 `fixCommit` / `fixPlanRef` 字段，并同步更新 `index.md` 表格行。

**读取触发（MUST 先读 BUG 文档）：**

3. **修复任何 BUG 前**：**MUST** 在 `docs/bugs/` grep 关键字 / 模块名，确认不是已知问题、不是已 `wontfix` 的设计取舍、不是已存在 BUG 的 `duplicate`。
4. **写新功能 plan / spec 前**：**MUST** 浏览 `docs/bugs/index.md` 的 "Open BUGs" 与 "By Module"，看新 feature 范围是否会触碰已知 BUG 区域；若有，必须在 plan 的 "Risks" 或 "Known Issues" 中明确列出。

**报告触发（MUST 在响应中说明）：**

5. **用户主动要求 E2E 跑测时**（如 "端到端跑一遍 X 功能"、"用 playwright 验证 Y"），完成后 **MUST** 在最终响应中明确报告 "本次发现 N 个 BUG，已登记到 …"。**N=0 也要明确说**。
````

- [x] **Step 3: Add MCP/Skill Temporary Files exemption**

Find the `### MCP / Skill Temporary Files` section in `AGENTS.md`. Append this bullet to the end of that section's bullet list (after the last existing bullet about "tracked source path"):

```markdown
- **唯一豁免**：BUG 文档的归档证据截图（`docs/bugs/assets/<BUG-ID>/`，单张 PNG ≤ 500KB）允许入 git。trace / HAR / HTML 等大体积证据**仍须留在 `tmp/`**，不入 git
```

- [x] **Step 4: Verify all three edits landed**

Run: `grep "BUG 跟踪与 E2E 缺陷登记" AGENTS.md`
Expected: One match showing the navigation row.

Run: `grep "^### BUG Tracking Gate" AGENTS.md`
Expected: One match.

Run: `grep "唯一豁免" AGENTS.md`
Expected: One match in the MCP/Skill Temporary Files section.

Run: `grep -c "docs/bugs/" AGENTS.md`
Expected: At least `5` (Knowledge Base row + 4 references inside BUG Tracking Gate section).

---

### Task 5: Update `CLAUDE.md`

**Files:**
- Modify: `CLAUDE.md`

**Reference:** Spec §8.1, §8.2, §7.3. Same edits as Task 4.

- [x] **Step 1: Add Knowledge Base Navigation row**

Find the table row containing `Tech debt tracker` in `CLAUDE.md`. Insert a new row immediately after it:

```markdown
| BUG 跟踪与 E2E 缺陷登记       | [docs/bugs/index.md](docs/bugs/index.md)                     |
```

- [x] **Step 2: Add `### BUG Tracking Gate` Working Rules section**

Find the `### Bug Fixes` section in `CLAUDE.md`. Insert this new section **immediately after** the `### Bug Fixes` section (and its bullet content), and **before** the next `### Data Source Type Compatibility Gate` section:

````markdown
### BUG Tracking Gate

DataTalk 运行时偏差通过 `docs/bugs/` 集中记录。详见 [docs/bugs/index.md](docs/bugs/index.md) 与 [docs/bugs/README.md](docs/bugs/README.md)。

**写入触发（MUST 新建/更新 BUG 文档）：**

1. **E2E 测试发现产品行为偏差**：通过 `mcp__playwright__*` 或 `playwright-cli` skill 跑端到端测试时，发现按钮无响应、数据错误、UI 错位、控制台报错等任何与 spec 不符的行为，**MUST** 在 `docs/bugs/` 新建 BUG 文件，状态 `open`，并在 `index.md` 注册。**禁止只在对话里口头报告**。
2. **修复一个已存在 BUG 时**：用户明确要求修某 BUG，或修代码恰好闭环了某 open BUG，**MUST** 把对应 BUG 文件状态改 `fixed`，回填 `fixCommit` / `fixPlanRef` 字段，并同步更新 `index.md` 表格行。

**读取触发（MUST 先读 BUG 文档）：**

3. **修复任何 BUG 前**：**MUST** 在 `docs/bugs/` grep 关键字 / 模块名，确认不是已知问题、不是已 `wontfix` 的设计取舍、不是已存在 BUG 的 `duplicate`。
4. **写新功能 plan / spec 前**：**MUST** 浏览 `docs/bugs/index.md` 的 "Open BUGs" 与 "By Module"，看新 feature 范围是否会触碰已知 BUG 区域；若有，必须在 plan 的 "Risks" 或 "Known Issues" 中明确列出。

**报告触发（MUST 在响应中说明）：**

5. **用户主动要求 E2E 跑测时**（如 "端到端跑一遍 X 功能"、"用 playwright 验证 Y"），完成后 **MUST** 在最终响应中明确报告 "本次发现 N 个 BUG，已登记到 …"。**N=0 也要明确说**。
````

- [x] **Step 3: Add MCP/Skill Temporary Files exemption**

Find the `### MCP / Skill Temporary Files` section in `CLAUDE.md`. Append this bullet to the end of that section's bullet list (after the last existing bullet about "tracked source path"):

```markdown
- **唯一豁免**：BUG 文档的归档证据截图（`docs/bugs/assets/<BUG-ID>/`，单张 PNG ≤ 500KB）允许入 git。trace / HAR / HTML 等大体积证据**仍须留在 `tmp/`**，不入 git
```

- [x] **Step 4: Verify all three edits landed**

Run: `grep "BUG 跟踪与 E2E 缺陷登记" CLAUDE.md`
Expected: One match.

Run: `grep "^### BUG Tracking Gate" CLAUDE.md`
Expected: One match.

Run: `grep "唯一豁免" CLAUDE.md`
Expected: One match.

Run: `grep -c "docs/bugs/" CLAUDE.md`
Expected: At least `5`.

---

### Task 6: Register plan in `docs/exec-plans/index.md`

**Files:**
- Modify: `docs/exec-plans/index.md`

- [ ] **Step 1: Add row at top of "活跃计划" table**

Find the table header row `| 计划 | 创建日期 | 摘要 |` under `## 活跃计划` in `docs/exec-plans/index.md`. Insert this row **immediately after** the separator row `|------|---------|------|`:

```markdown
| [BUG Tracking System](./2026-05-05-bug-tracking-system-plan.md) | 2026-05-05 | 实现 `docs/bugs/` BUG 跟踪文档体系：扁平 + 多视图 `index.md` + 写作协议 `README.md` + `assets/` 截图目录；CLAUDE.md / AGENTS.md 加 Knowledge Base 行 + `BUG Tracking Gate` Working Rules（5 条强约束：写入 2 / 读取 2 / 报告 1）+ MCP/Skill Temporary Files 豁免（PNG ≤ 500KB 入 git，trace 留 tmp/）。配套设计 spec：[2026-05-05-bug-tracking-system-design.md](../product-specs/2026-05-05-bug-tracking-system-design.md)。 |
```

- [ ] **Step 2: Verify registration**

Run: `grep "BUG Tracking System" docs/exec-plans/index.md`
Expected: One match showing the new row.

---

### Task 7: Final cross-reference verification

No new files; this is a verification-only task.

- [ ] **Step 1: Verify all created files exist**

Run: `ls -la docs/bugs/`
Expected: Shows `index.md`, `README.md`, `assets/` directory.

Run: `ls -la docs/bugs/assets/`
Expected: Shows `.gitkeep`.

- [ ] **Step 2: Verify cross-references resolve**

Run:
```bash
grep -l "docs/bugs/" AGENTS.md CLAUDE.md docs/product-specs/2026-05-05-bug-tracking-system-design.md docs/exec-plans/2026-05-05-bug-tracking-system-plan.md docs/exec-plans/index.md
```
Expected: All 5 files listed (each contains at least one reference to `docs/bugs/`).

- [ ] **Step 3: Verify rule consistency between AGENTS.md and CLAUDE.md**

Run:
```bash
diff <(grep -A 20 "^### BUG Tracking Gate" AGENTS.md) <(grep -A 20 "^### BUG Tracking Gate" CLAUDE.md)
```
Expected: No output (sections identical between AGENTS.md and CLAUDE.md).

Run:
```bash
diff <(grep "唯一豁免" AGENTS.md) <(grep "唯一豁免" CLAUDE.md)
```
Expected: No output (exemption line identical).

- [ ] **Step 4: Verify no broken markdown links inside `docs/bugs/` files**

Run:
```bash
grep -oE "\[.*\]\([^)]+\)" docs/bugs/index.md docs/bugs/README.md | grep -v "http"
```
Expected: All relative paths shown — manually verify each path resolves (e.g., `README.md`, `index.md`, `../product-specs/2026-05-05-bug-tracking-system-design.md`, `../exec-plans/tech-debt-tracker.md`, `../testing/`).

- [ ] **Step 5: Smoke test the writing protocol**

Imagine you (the implementing engineer) just discovered a BUG. Open `docs/bugs/README.md` and confirm:
- [ ] You can find the file template by ctrl-F "完整文件模板"
- [ ] You can find the next ID assignment instruction by ctrl-F "当前编号"
- [ ] You can find the index.md sync checklist by ctrl-F "index.md 同步清单"
- [ ] The state machine diagram is readable

If any answer is "no", fix the README.md before committing.

---

### Task 8: Final commit

**Files:** None new; commit all changes from Tasks 1-6.

- [ ] **Step 1: Stage all changes explicitly**

Run:
```bash
git add docs/bugs/index.md docs/bugs/README.md docs/bugs/assets/.gitkeep AGENTS.md CLAUDE.md docs/exec-plans/index.md docs/exec-plans/2026-05-05-bug-tracking-system-plan.md
```

Note: `docs/product-specs/2026-05-05-bug-tracking-system-design.md` and the index.md update were committed earlier (during the brainstorming phase). If they're still uncommitted, add them too:
```bash
git add docs/product-specs/2026-05-05-bug-tracking-system-design.md docs/product-specs/index.md
```

- [ ] **Step 2: Verify staged content**

Run: `git status`
Expected: Shows the 7 (or 9) staged files listed above, no other unrelated files.

Run: `git diff --cached --stat`
Expected: Shows additions only; no surprise deletions.

- [ ] **Step 3: Commit**

Run:
```bash
git commit -m "$(cat <<'EOF'
docs: build BUG tracking system under docs/bugs/

Establishes a strongly structured BUG documentation system targeted at
AI primary writer/consumer:

- docs/bugs/index.md: multi-view index (Open / In Progress / Recently
  Closed / By Module / By Source / Wontfix-Duplicate)
- docs/bugs/README.md: full writing protocol (template, frontmatter
  schema, state machine, index sync checklist, anti-patterns)
- docs/bugs/assets/: committed evidence screenshots (PNG ≤500KB only)
- AGENTS.md / CLAUDE.md: Knowledge Base Navigation row + new
  "### BUG Tracking Gate" Working Rules (5 hard rules: 2 write / 2
  read / 1 report) + MCP/Skill Temporary Files exemption
- docs/exec-plans/index.md: register implementation plan

Schema: YAML frontmatter (id/status/priority/source/modules/...) +
9 fixed Markdown sections. State machine: open → investigating →
fixed → verified → closed (+ wontfix/duplicate side states), with
fixed→verified mandatory two-stage closure.

Spec: docs/product-specs/2026-05-05-bug-tracking-system-design.md
Plan: docs/exec-plans/2026-05-05-bug-tracking-system-plan.md
EOF
)"
```

- [ ] **Step 4: Verify commit succeeded**

Run: `git log -1 --stat`
Expected: Shows commit summary with all 7-9 files listed.

Run: `git status`
Expected: `nothing to commit, working tree clean`.

---

## Post-Execution Housekeeping

Per CLAUDE.md `### Post-Execution Document Housekeeping`:

- [ ] Mark every task above as completed in this plan file (each `- [ ]` → `- [x]`)
- [ ] Move this plan from "活跃计划" to "已完成计划" in [docs/exec-plans/index.md](./index.md)
- [ ] Mirror the same move for the spec in [docs/product-specs/index.md](../product-specs/index.md) §8 (already in §8 from brainstorming phase, no move needed for spec)
- [ ] No further canonical docs need propagation — the system is self-contained under `docs/bugs/` and the gates are already injected into AGENTS.md / CLAUDE.md by Tasks 4-5

## Verification Acceptance (per spec §11)

After implementation, the following acceptance tests must pass:

1. **AI directory awareness**: A fresh AI session asked "DataTalk 怎么记 BUG" can directly cite `docs/bugs/index.md` and `docs/bugs/README.md`. → Verifiable by reading AGENTS.md / CLAUDE.md Knowledge Base Navigation table.
2. **AI write reflex on E2E discovery**: AI running Playwright E2E and finding a deviation auto-creates a BUG file matching the README template, updates index.md, and reports "已登记到 BUG-NNNN" in the response. → Behavioral, requires actual E2E test run; not testable in this plan.
3. **AI fix reflex**: AI asked to fix a referenced BUG flips status to `fixed` and backfills `fixCommit`. → Behavioral, requires real BUG to fix.
4. **AI plan-time consultation**: AI writing a new feature plan includes a Risks/Known Issues section if relevant open BUGs exist. → Behavioral.
5. **MCP/Skill rule non-contradiction**: The "唯一豁免" line and the original "禁止 docs/" line in `### MCP / Skill Temporary Files` do not contradict. → Verified in Task 7 Step 3.

Acceptance tests 2-4 are behavioral and validated only when AI actually uses the system in subsequent sessions; this plan ships the infrastructure required for those behaviors.
