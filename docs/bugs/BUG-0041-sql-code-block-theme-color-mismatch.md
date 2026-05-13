---
id: BUG-0041
title: 聊天 SQL 代码块 Shiki 高亮在 dark 主题下串成 light 主题颜色，identifier 几乎不可见
status: fixed
priority: P2
source: manual-report
modules: [chat, markdown]
discovered: 2026-05-13
discoveredBy: human
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

`client/src/features/chat/components/markdown/sql-code-block.ts` 里 `applySqlHighlight` 渲染时一次性读取 `document.documentElement.classList.contains('dark')` 作为 Shiki 主题判定输入；Shiki 把决定后的主题颜色写进每个 token 的**内联 style**，之后主题切换 / 时序错位都不会重渲。

实际表现：dark 主题下，SQL 代码块的 keyword 显示为 github-light 的深红 `#D73A49`、identifier 显示为深紫 `#6F42C1`，落在 wrapper 的暗色背景上 → identifier / 普通文本几乎不可见（见 `assets/BUG-0041/before-dark.png`）。两个主题的色板被串到同一块代码上。

## Reproduction Steps

1. 设置应用主题为 dark（或处于 `system` 模式且 OS 为 dark）。
2. 触发任意输出 SQL fenced code block 的聊天回合，例如 ingestion 流程末尾的 `建议的表结构：` SQL 预览。
3. 观察 SQL 代码块中的 token 颜色。

## Expected vs Actual

- **Expected**：dark 主题下 SQL 代码块使用 github-dark 色板（keyword `#F97583` 粉红、identifier `#B392F0` 浅紫、普通文字 `#E1E4E8` 浅灰），与 wrapper 的暗色背景搭配清晰可读。
- **Actual**：dark 主题下使用 github-light 色板（keyword `#D73A49` 深红、identifier `#6F42C1` 深紫、普通文字 `#24292E` 接近黑），与 wrapper 暗色背景对比度极低，identifier 几乎不可见。

## Environment

- Frontend: client/src/features/chat/components/markdown/sql-highlight.ts, sql-code-block.ts, markdown.css
- Shiki: ^4.0.2
- 复现环境：WSL2 + chromium，前端 dev server 在 localhost:1420

## Evidence

- 修复前（dark 主题串成 light 色）：`assets/BUG-0041/before-dark.png`
- 修复后（dark 主题正确显示 github-dark 色板）：`assets/BUG-0041/after-dark.png`
- 修复后 light 主题验证（colors 完全隔离）：`assets/BUG-0041/after-light.png`

## Root Cause

两层叠加问题：

1. `applySqlHighlight` 在 `decorateSqlBlocks` 调用时一次性读 `documentElement.classList.contains('dark')` → 把布尔传给 `highlightSql(code, dark)` → Shiki 用单主题 `theme: 'github-dark' | 'github-light'` 输出固定颜色。
2. 即使首次检测对了，Shiki 把颜色写死成内联 style；之后用户切主题不会重渲；morphdom 把 `<temp>` 内的节点搬进 `<container>` 的时序也可能让首次检测的 `documentElement` 状态不一致。

## Fix Plan

改成 Shiki 4.x 原生支持的**双主题 + CSS 变量**模式：

1. `sql-highlight.ts`：`highlightSql(code)` 用 `themes: { light: 'github-light', dark: 'github-dark' }` + `defaultColor: false`，让 Shiki 在每个 span 同时输出 `--shiki-light` 与 `--shiki-dark` 两套 CSS 变量、且不带固定 `color`。
2. `sql-code-block.ts`：`applySqlHighlight` 不再读 `documentElement.classList`，签名简化为 `highlightSql(raw)`。
3. `markdown.css`：新增规则按祖先 `.dark` 选择器把 `pre.shiki` / `pre.shiki span` 的 `color` 与 `background-color` 映射到对应的 CSS 变量；纯 CSS 驱动主题切换，零 JS 重渲。

非 SQL 代码块不走 Shiki，渲染为 `<pre><code>` 纯文本，`pre` 背景透明 + 文字色从 `--foreground` 继承，本来就支持双主题，不需要改。

## Verification

`playwright-cli` 在 localhost:1420 上：
- 注入测试 SQL block，用 `getComputedStyle` 检查 token 颜色。
- 切 `documentElement.classList` 在 light / dark 之间，CSS 立刻把 `--shiki-light/--shiki-dark` 映射到对应颜色：
  - light: pre bg `rgb(255,255,255)`、color `rgb(36,41,46)`
  - dark: pre bg `rgb(36,41,46)`、CREATE/TABLE/INTEGER `rgb(249,117,131)`、ingested_data `rgb(179,146,240)`、`256` `rgb(121,184,255)`
- 同时注入非 SQL Python 块，dark 主题下 pre bg 透明、code 颜色继承 `--foreground` 浅色，无串色。

## Related

- 修复涉及文件：
  - `client/src/features/chat/components/markdown/sql-highlight.ts`
  - `client/src/features/chat/components/markdown/sql-code-block.ts`
  - `client/src/features/chat/components/markdown/markdown.css`
- 现有单测 `markdown.test.tsx` 20/20 通过，未涉及色值断言，无需新增用例。
