# BUG 跟踪系统设计

**日期**：2026-05-05
**作者**：基于用户与 AI 协作 brainstorming
**状态**：Spec — 待 plan 与实现

## 1. 背景与动机

DataTalk 当前的质量记录体系覆盖两类资产：

- `docs/exec-plans/tech-debt-tracker.md`：**已知设计/代码债**，主动登记
- `docs/testing/`：**手工测试脚本**（步骤 + 预期结果）

缺失的一环是**运行时观察到的产品行为偏差**——即 BUG。来源主要有两类：

1. **MCP / Playwright 端到端测试**（AI 通过 `mcp__playwright__*` 与 `playwright-cli` skill 自动驱动客户端）发现的 UI / 数据 / 流式 / 持久化偏差
2. **企业级 roadmap 验收**（人或 AI 在按 roadmap 推进时发现的功能缺陷或阻塞性问题）

这些 BUG 当前散落在对话历史、commit message、PR 描述里，AI 在下一次修复或写新功能时**无法系统性检索**——典型后果：重复修同一个 BUG、把已 wontfix 的问题当新问题再修、写新 feature 时不知道相邻区域有 open 风险。

本设计建立一套 **AI 主导写入、AI 友好检索**的 BUG 跟踪文档体系，并通过 AGENTS.md / CLAUDE.md 门控规则把它接入日常 AI 工作流。

## 2. 设计目标与非目标

### 目标

- AI 在 E2E 测试中发现偏差时，能**零思考**写出一条结构化、可检索的 BUG 文档
- AI 在修复前能用 `grep` / 读 `index.md` 一次性了解相关已知 BUG
- BUG 状态机简单到 AI 不会误判，强制 fixed→verified 双阶段闭环
- 与现有 `tech-debt-tracker` / `exec-plans` / `product-specs` 范式 100% 对齐，不引入新概念
- 关键证据（截图）随文档持久化，AI 修复时能"看到"问题表现

### 非目标

- 不替代 GitHub Issues / Linear 等外部 issue tracker（DataTalk 是单仓项目，本系统是仓内补充）
- 不做工作流编排（指派、SLA、看板）——只是一份结构化记录
- 不做实时通知 / 订阅
- trace / HAR / HTML dump 等大体积证据**不入 git**，仅指引到本地 `tmp/` 路径
- 不替代 `docs/testing/` 的手工测试脚本——它们是测试设计侧；本系统是测试结果记录侧

## 3. 系统总览

```
docs/bugs/
  index.md                              # 入口，多视图索引
  README.md                             # 写作协议（AI 零思考填表指南）
  BUG-0001-stage-tab-loses-content.md   # 单 BUG 文件，扁平
  BUG-0002-connection-test-stuck.md
  ...
  assets/
    BUG-0001/
      screenshot-01.png                 # ≤500KB，git 跟踪
      screenshot-02.png
    BUG-0002/
      ...
```

核心约束：

- **扁平 + 多视图 index**：BUG 文件按 `BUG-NNNN-<slug>.md` 平铺，所有筛选视图（Open / Closed / By Module / By Source）由 `index.md` 维护。AI 读 `index.md` 一次性拿全视图，不需遍历目录。
- **ID 永不复用**：单调递增 4 位数（BUG-0001、BUG-0002 ...），即便文件被 wontfix 或 duplicate 也保留。
- **状态变更不挪文件**：状态切换只改 frontmatter + index.md 表格行，文件路径永远稳定，外部链接不腐烂。
- **强结构化 frontmatter**：YAML 头部固定字段集，便于 AI grep / yq 解析。

## 4. BUG 文件 Schema

### 4.1 frontmatter 字段（YAML）

```yaml
---
id: BUG-0001                          # 必填，永不复用
title: Stage tab 内容刷新后丢失           # 必填，简短描述
status: open                          # 必填，5 主态 + 2 旁路态
priority: P1                          # 必填，P0 / P1 / P2
source: e2e-playwright                # 必填，发现来源
modules: [stage, persistence]         # 必填，自由 tag 数组（便于 grep）
discovered: 2026-05-05                # 必填，YYYY-MM-DD
discoveredBy: agent                   # 必填，agent | human
testRunId: pw-2026-05-05-001          # 选填，关联测试运行标识
fixCommit: null                       # 修复后回填，short SHA 或 PR URL
fixPlanRef: null                      # 选填，关联 exec-plan 路径
duplicateOf: null                     # status=duplicate 时必填，BUG-XXXX
regression: false                     # 是否为回归 BUG（true/false）
---
```

#### 字段语义表

| 字段 | 必填 | 类型 | 候选值 / 格式 |
|------|------|------|-------------|
| `id` | 是 | string | `BUG-NNNN`，4 位单调递增 |
| `title` | 是 | string | 一句话描述，与文件名 slug 对应 |
| `status` | 是 | enum | `open` / `investigating` / `fixed` / `verified` / `closed` / `wontfix` / `duplicate` |
| `priority` | 是 | enum | `P0` / `P1` / `P2`（与 `tech-debt-tracker` 统一口径） |
| `source` | 是 | enum | `e2e-mcp` / `e2e-playwright` / `roadmap-validation` / `manual-report`（仅描述发现渠道；"是否回归"由独立字段 `regression` 表达） |
| `modules` | 是 | string[] | 自由 tag，建议复用现有 feature 目录名（如 `stage`、`connection`、`chat`） |
| `discovered` | 是 | date | `YYYY-MM-DD`，发现日期 |
| `discoveredBy` | 是 | enum | `agent`（AI 自动发现） / `human`（人工报告） |
| `testRunId` | 否 | string | 任意可识别测试运行的字符串，便于回溯同批问题 |
| `fixCommit` | 否 | string | 修复 commit SHA 或 PR URL；fixed 状态后必填 |
| `fixPlanRef` | 否 | path | 关联 `docs/exec-plans/...` 路径；BUG 升级为修复计划时回填 |
| `duplicateOf` | 否 | string | `status=duplicate` 时必填，指向原始 BUG ID |
| `regression` | 是 | bool | 是否为回归（之前修过又复现） |

### 4.2 必填章节（Markdown body）

```markdown
## Summary
一句话描述 BUG 的本质（不是 title 的复读，是更完整的 1-3 行）。

## Reproduction Steps
1. ...
2. ...
3. ...

## Expected vs Actual
- **Expected**: 应该出现什么
- **Actual**: 实际出现什么

## Environment
- Backend commit: <sha>
- Frontend commit: <sha>
- OS / Browser: ...
- Data source: MySQL 8.x / PG 16 / H2 / N/A

## Evidence
- ![截图1](assets/BUG-0001/screenshot-01.png) — 简短说明
- Trace（本地）: `tmp/playwright/2026-05-05/trace.zip`
- 控制台错误片段：
  ```
  ...
  ```

## Root Cause
（investigating / fixed / verified 状态时填，open 状态可写 "TBD"）

## Fix
（fixed / verified / closed 状态时填——commit、改动文件、相关 plan 链接）

## Verification
（verified / closed 状态时填——验证 E2E 测试名、回归用例链接、人工验证步骤）

## Notes
（自由附加：相关讨论、放弃修复的理由、duplicate 链接、Slack 讨论摘要等）
```

**严格规则：**

- 章节顺序固定，**绝不省略章节**——未知用 `TBD` 或 `N/A` 显式占位
- 章节标题用 `##` 二级标题
- 章节里允许子标题、列表、代码块、图片
- 写完后必须更新 `index.md` 的对应表格行（详见 §5）

## 5. `index.md` 格式

`index.md` 是 BUG 跟踪系统的**唯一入口**，所有视图都在这里。AI 第一次接触本系统时只读它即可。

### 5.1 结构

```markdown
# BUG 索引

DataTalk 运行时缺陷的集中记录。所有 BUG 详情请进单文件查看。

## 当前编号
下一个分配 ID：**BUG-0042**（永不复用，单调递增）

## Open BUGs（按 priority 倒序，P0→P2）

| ID | Title | Priority | Source | Modules | Discovered |
|----|-------|----------|--------|---------|------------|
| [BUG-0041](BUG-0041-stage-tab-loses-content.md) | Stage tab 内容刷新后丢失 | P1 | e2e-playwright | stage, persistence | 2026-05-05 |

## In Progress（status = investigating | fixed 等待 verify）

| ID | Title | Status | Priority | Owner |
|----|-------|--------|----------|-------|

## Recently Closed（最近 30 天，status = verified | closed）

| ID | Title | Status | Closed Date | FixCommit |
|----|-------|--------|-------------|-----------|

## By Module（聚合视图，仅列 open + in-progress）

- **stage**: BUG-0041, BUG-0038
- **connection**: BUG-0040
- **chat**: BUG-0039

## By Source（聚合视图，仅列 open + in-progress）

- **e2e-playwright**: BUG-0041, BUG-0040
- **e2e-mcp**: BUG-0039
- **roadmap-validation**: BUG-0038

## Wontfix / Duplicate（终态归档，无时间限制）

| ID | Resolution | Reason / DuplicateOf |
|----|------------|----------------------|

## Closure History（30 天前的 closed/verified 折叠归档）

详见 git history `git log -- docs/bugs/`，本节不维护。
```

### 5.2 更新规则

| 操作 | 必须同步更新的位置 |
|------|------------------|
| 新建 BUG | "当前编号"+1、Open BUGs 表插入行、By Module、By Source |
| `open` → `investigating` | Open BUGs 移除、In Progress 插入 |
| `open` → `fixed` | Open BUGs 移除、In Progress 插入（带 status=fixed） |
| `fixed` → `verified` | In Progress 移除、Recently Closed 插入 |
| `verified` → `closed` | Recently Closed 状态字段更新 |
| `open` → `wontfix` / `duplicate` | Open BUGs 移除、Wontfix/Duplicate 表插入 |

## 6. 状态机

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

- `open`：刚发现、待分流。任何人/AI 都可以建。
- `investigating`：AI 或人正在定位根因。**Root Cause 章节开始填写**。
- `fixed`：代码改动已 commit 或 PR open，等待回归验证。**Fix 章节必填**。
- `verified`：E2E 跑通过且通过、或人工验证通过。**Verification 章节必填**。
- `closed`：归档（默认 verified 后 30 天自动过渡，或人工显式关闭）。
- `wontfix`：决定不修（设计取舍 / 上游问题 / 已弃用功能）。Notes 必须给出原因。
- `duplicate`：与已存在 BUG 重复。`duplicateOf` frontmatter 必填，Notes 链接原 BUG。

**关键约束：**

- `fixed` ≠ `closed`：必须经过 `verified` 才能进 `closed`，避免 AI 改完代码就当问题闭环
- 反向跳转受限：`closed`/`verified` → `open` 仅当出现 regression 时允许，且必须创建新 BUG（带 `regression: true`）而非复用旧 ID

## 7. 证据存储规则与 CLAUDE.md 豁免

### 7.1 默认规则（与 CLAUDE.md 保持一致）

- Playwright trace、HAR、HTML dump、控制台日志全文等**大体积或临时证据**：留在 `/home/wallfacers/project/data-talk/tmp/playwright/<timestamp>/`，**不入 git**
- BUG 文件 Evidence 章节**只引用本地路径**，不复制内容到 git

### 7.2 唯一豁免：归档截图

- BUG 关键截图（说明问题表现的 PNG）允许入 git，存放路径**强制**为 `docs/bugs/assets/<BUG-ID>/<file>.png`
- **大小约束**：单张 ≤ 500 KB；超出必须先压缩或裁剪
- **数量约束**：建议 1-3 张，最多 5 张；更多场景请用录屏挂 trace（trace 不入 git）
- **仅限 PNG**：不接受 JPG（避免有损压缩损失文字清晰度）、不接受 MP4 / GIF（体积大且仓库不友好）

### 7.3 CLAUDE.md 规则补丁

`### MCP / Skill Temporary Files` 段尾部追加一条：

> **唯一豁免**：BUG 文档的归档证据截图（`docs/bugs/assets/<BUG-ID>/`，单张 PNG ≤ 500KB）允许入 git。trace / HAR / HTML 等大体积证据**仍须留在 `tmp/`**，不入 git。

## 8. AGENTS.md / CLAUDE.md 集成

### 8.1 Knowledge Base Navigation 表新增条目

在 AGENTS.md 与 CLAUDE.md 的"Knowledge Base Navigation (docs/)"表格中新增一行：

```markdown
| BUG 跟踪与 E2E 缺陷登记       | [docs/bugs/index.md](docs/bugs/index.md)                     |
```

### 8.2 Working Rules 新增门控

在两个文件的"Working Rules"段插入新一节 `### BUG Tracking Gate`：

```markdown
### BUG Tracking Gate

DataTalk 运行时偏差通过 `docs/bugs/` 集中记录。详见 [docs/bugs/index.md](docs/bugs/index.md) 与 [docs/bugs/README.md](docs/bugs/README.md)。

**写入触发（MUST 新建/更新 BUG 文档）：**

1. **E2E 测试发现产品行为偏差**：通过 `mcp__playwright__*` 或 `playwright-cli` skill 跑端到端测试时，发现按钮无响应、数据错误、UI 错位、控制台报错等任何与 spec 不符的行为，**MUST** 在 `docs/bugs/` 新建 BUG 文件，状态 `open`，并在 `index.md` 注册。**禁止只在对话里口头报告**。
2. **修复一个已存在 BUG 时**：用户明确要求修某 BUG，或修代码恰好闭环了某 open BUG，**MUST** 把对应 BUG 文件状态改 `fixed`，回填 `fixCommit` / `fixPlanRef` 字段，并同步更新 `index.md` 表格行。

**读取触发（MUST 先读 BUG 文档）：**

3. **修复任何 BUG 前**：**MUST** 在 `docs/bugs/` grep 关键字 / 模块名，确认不是已知问题、不是已 `wontfix` 的设计取舍、不是已存在 BUG 的 `duplicate`。
4. **写新功能 plan / spec 前**：**MUST** 浏览 `docs/bugs/index.md` 的 "Open BUGs" 与 "By Module"，看新 feature 范围是否会触碰已知 BUG 区域；若有，必须在 plan 的 "Risks" 或 "Known Issues" 中明确列出。

**报告触发（MUST 在响应中说明）：**

5. **用户主动要求 E2E 跑测时**（如"端到端跑一遍 X 功能"、"用 playwright 验证 Y"），完成后 **MUST** 在最终响应中明确报告"本次发现 N 个 BUG，已登记到 …"。**N=0 也要明确说**。
```

### 8.3 与现有规则的互动

- 与 `### Bug Fixes` 段的"主动检视相关代码"原则**叠加**：修 BUG 时既要查代码也要查 BUG 历史
- 与 `### Plan Mode` 段的 plan 流程**联动**：plan 文档若涉及已知 BUG 修复，必须在 frontmatter 的 fixPlanRef 互链
- 与 `### Data Source Type Compatibility Gate` 的范式**对称**：都是"读取 → 写入 → 报告"三段式约束

## 9. `docs/bugs/README.md`（写作协议）

为 AI 提供"零思考填表"的实操文档。内容大纲：

1. **TL;DR**：3 行说明系统是什么、ID 怎么取、文件放哪
2. **完整文件模板**（可直接复制）
3. **frontmatter 字段语义表**（同本 spec §4.1）
4. **状态流转图与态语义**（同本 spec §6）
5. **index.md 同步清单**（同本 spec §5.2）
6. **常见反例**（不要做什么）：
   - ❌ 不要复用 ID
   - ❌ 不要在状态变化时移动文件路径
   - ❌ 不要把 trace / HAR 入 git
   - ❌ 不要省略章节（用 TBD / N/A 占位）
   - ❌ 不要直接 open → closed（必须经过 fixed → verified）
7. **与 exec-plans 联动指引**：BUG 升级为修复计划时如何互链
8. **与 tech-debt-tracker 区分指引**：什么时候登 BUG、什么时候登技术债

## 10. 实施清单

新建：

- [ ] `docs/bugs/` 目录
- [ ] `docs/bugs/index.md`（按 §5.1 模板）
- [ ] `docs/bugs/README.md`（按 §9 大纲）
- [ ] `docs/bugs/assets/.gitkeep`（占位空目录）

修改：

- [ ] `AGENTS.md`：Knowledge Base Navigation 表新增行（§8.1）+ Working Rules 新增 `BUG Tracking Gate`（§8.2）
- [ ] `CLAUDE.md`：同上
- [ ] `CLAUDE.md` 与 `AGENTS.md` 的 `### MCP / Skill Temporary Files` 段追加豁免说明（§7.3）

不修改但要登记本 spec：

- [ ] `docs/product-specs/index.md`：§8 "个别设计文档" 新增本 spec 链接

## 11. 验收标准

实现完成后，必须通过以下检查：

1. AI 在新对话里被问"DataTalk 怎么记 BUG" → 能直接给出 `docs/bugs/index.md` 链接和 `docs/bugs/README.md` 路径
2. AI 跑一次 Playwright E2E 测试发现一个偏差 → 自动按模板新建 BUG 文件、更新 index.md、在响应里说明"已登记到 BUG-NNNN"
3. AI 被要求"修 BUG-XXXX" → 修完后状态变 fixed 并回填 fixCommit
4. AI 被要求"写一个新功能 plan" → 在 plan 里有 Risks 或 Known Issues 章节引用相关 open BUG
5. CLAUDE.md / AGENTS.md 的 MCP/Skill Temporary Files 规则与 BUG 证据豁免一致，不矛盾

## 12. 未来可扩展

本 spec 不实现，但预留空间：

- **自动化检查**：CI / pre-commit hook 校验 BUG 文件 frontmatter schema、index.md 一致性、assets 目录大小
- **状态自动流转**：30 天内未触摸的 verified BUG 自动转 closed（脚本 + cron / GitHub Actions）
- **跨工具同步**：将 open BUG 镜像到 GitHub Issues（单向 push）
- **统计仪表板**：周报、月报自动汇总（`docs/bugs/reports/`）
- **BUG 关联**：frontmatter 加 `relatedBugs: [BUG-XXXX]`，建立缺陷网络

## 13. 设计决策记录（来自 brainstorming 对话）

记录关键的备选方案与最终选择，便于后续追溯：

| 决策点 | 备选方案 | 最终选择 | 理由 |
|--------|---------|---------|------|
| 主要写入者 | A. AI 主导 / B. 人主导 / C. 双向 | **A** | E2E 测试自动产出，AI 是主力 |
| 目录结构 | A. 扁平按日期 / B. 按状态分目录 / C. 按模块分目录 / D. 扁平+多视图 index | **D** | 与 `exec-plans/` `product-specs/` 范式 100% 对齐 |
| 状态机 | A. 3 态 / B. 5 态 / C. 7 态 / D. 双轴 | **B** | 5 态平衡精度与简洁，强制 fixed→verified 双阶段闭环 |
| 优先级模型 | A. P0/P1/P2 单维 / B. severity+priority 双维 / C. severity 单维 / D. P+regression 标签 | **A** | 与 `tech-debt-tracker` 统一口径，AI 跨表判断不分裂 |
| 证据存储 | A. 严守 tmp / B. 例外条款入 assets / C. 全部入 git / D. 外链 | **B** | 精简而完整，仅 PNG ≤500KB 入 git，trace 留 tmp/ |

## 14. 相关文档

- 技术债跟踪：[docs/exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md)（互补关系）
- 手测脚本目录：[docs/testing/](../testing/)（互补关系）
- 产品规格全景：[docs/product-specs/index.md](./index.md)
- 执行计划目录：[docs/exec-plans/index.md](../exec-plans/index.md)
- 项目工作约定：[/CLAUDE.md](../../CLAUDE.md) / [/AGENTS.md](../../AGENTS.md)
