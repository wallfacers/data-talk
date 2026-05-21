# Design Doc Governance Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 同步最近新增设计文档的索引与状态，明确标记仍属粗稿的 memo 型设计文档。

**Architecture:** 本次只做文档治理，不改业务代码。通过 `docs/design-docs/index.md` 统一登记最近新增设计文档的状态，并在 `docs/product-specs/index.md` 对 memo 级粗稿补显式说明，避免“已登记但成熟度不明”。

**Tech Stack:** Markdown 文档、仓库内设计 spec / 执行计划索引

---

### Task 1: 梳理最近新增设计文档及状态映射

**Files:**
- Modify: `docs/design-docs/index.md`
- Reference: `docs/product-specs/2026-04-20-*.md`
- Reference: `docs/product-specs/2026-04-21-*.md`
- Reference: `docs/exec-plans/index.md`

- [x] **Step 1: 盘点最近新增设计文档**

定位范围：

```text
docs/product-specs/2026-04-20-*.md
docs/product-specs/2026-04-21-*.md
```

- [x] **Step 2: 建立状态映射规则**

采用以下治理规则：

```text
粗稿 / 需求备忘 / 尚无执行计划：draft
已有活跃执行计划：approved
对应执行计划已完成：shipped
```

- [x] **Step 3: 明确本次需特别标记的粗稿**

需显式标记的文档：

```text
docs/product-specs/2026-04-21-stage-window-layout-refactor-design.md
```

原因：文档正文已写明“需求备忘，待后续深入分析与细化”，尚不具备实现级细节。

### Task 2: 同步设计索引与产品规格索引

**Files:**
- Modify: `docs/design-docs/index.md`
- Modify: `docs/product-specs/index.md`

- [x] **Step 1: 为最近新增设计文档补索引状态**

更新 `docs/design-docs/index.md`，补入 2026-04-20 / 2026-04-21 新增设计文档，并写明 `draft / approved / shipped` 状态。

- [x] **Step 2: 为粗稿 spec 补显式说明**

更新 `docs/product-specs/index.md` 中 `Stage Window Layout Refactor Design` 摘要，将其标明为“需求备忘 / draft”，避免被误读为已进入可实施规格。

- [x] **Step 3: 在设计索引中补充状态判定说明**

补一句治理说明，解释本次状态同步依据来自执行计划完成度与文档成熟度。

### Task 3: 校验与收尾

**Files:**
- Modify: `docs/exec-plans/2026-04-21-design-doc-governance-sync-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: 进行文档专项校验**

运行以下命令确认索引项与关键标记已落地：

```bash
rg -n 'stage-window-layout-refactor-design|session-data-context-and-ai-datasource-management-design|bang-query-chat-visibility-design' docs/design-docs/index.md docs/product-specs/index.md
```

预期：命中设计索引与产品规格索引中的新增条目及 draft 标记。

- [x] **Step 2: 完成计划文件收尾**

本计划所有 checkbox 已完成；本次为文档治理任务，不涉及编译 / 类型检查。

- [x] **Step 3: 在执行计划索引登记完成状态**

将本计划登记到 `docs/exec-plans/index.md` 的 Completed 区域，摘要说明为“同步新增设计文档状态并显式标记粗稿 memo”。
