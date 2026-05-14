# chat-sql-codeblock Specification

## Purpose

定义聊天消息渲染流程中 SQL fenced code block 的语法高亮行为：在 light / dark 两个主题下分别使用正确的色板、主题切换不依赖 JS 重渲、与 `client/DESIGN.md` 的 dual-theme 契约一致。

本 spec 在归档时作为 capability `chat-sql-codeblock` 的基线落入 `openspec/specs/chat-sql-codeblock/`。

## Added Requirements

### Requirement: SQL 代码块 SHALL 在 dark 主题下使用 github-dark 色板

聊天消息内任何 fenced code block，info-string 为 `sql`（或 alias `language-sql`）的，渲染后在 ancestor `.dark` 类生效时，其 token 颜色 SHALL 来自 Shiki `github-dark` 主题色板。

#### Scenario: dark 主题下渲染 CREATE TABLE 语句

- **GIVEN** `documentElement.classList` 含 `dark`
- **AND** 聊天消息含 fenced code block: ` ```sql\nCREATE TABLE ingested_data (id INTEGER, name VARCHAR(256));\n``` `
- **WHEN** 消息渲染完成、`decorateSqlBlocks` 与 `applySqlHighlight` 跑完
- **THEN** `<pre class="shiki ...">` 的 `getComputedStyle().backgroundColor` SHALL 为 `rgb(36, 41, 46)`（github-dark `#24292e`）
- **AND** `CREATE` / `TABLE` / `INTEGER` / `VARCHAR` 等 keyword `<span>` 的 `getComputedStyle().color` SHALL 为 `rgb(249, 117, 131)`（github-dark keyword `#F97583`）
- **AND** `ingested_data` 等 identifier `<span>` 的 `color` SHALL 为 `rgb(179, 146, 240)`（github-dark `#B392F0`）
- **AND** 数字字面量 `256` 的 `color` SHALL 为 `rgb(121, 184, 255)`（github-dark `#79B8FF`）
- **AND** 普通文本（标点、空白）SHALL 为 `rgb(225, 228, 232)`（github-dark `#E1E4E8`）

### Requirement: SQL 代码块 SHALL 在 light 主题下使用 github-light 色板

ancestor 没有 `.dark` 类时，SQL 代码块 SHALL 用 Shiki `github-light` 主题色板。

#### Scenario: light 主题下渲染同样语句

- **GIVEN** `documentElement.classList` 不含 `dark`（含 `light` 或没有任一）
- **AND** 同样的 SQL fenced block
- **WHEN** 渲染完成
- **THEN** `<pre class="shiki ...">` 的 `backgroundColor` SHALL 为 `rgb(255, 255, 255)`（github-light `#fff`）
- **AND** 默认文本 `color` SHALL 为 `rgb(36, 41, 46)`（github-light `#24292e`）

### Requirement: 主题切换 SHALL 立即生效，无需 JS 重渲

用户改变主题（manual 切换或 system prefers-color-scheme 变化）SHALL 立刻反映到已渲染的 SQL 代码块上，**不依赖** 重新调用 `applySqlHighlight` 或 Shiki。

#### Scenario: dark → light 实时切换

- **GIVEN** 一段 SQL 代码块已经在 dark 主题下渲染完成（DOM 已稳定）
- **WHEN** `documentElement.classList.remove('dark')` 并 `add('light')` 在主线程同一 tick 内执行
- **THEN** 同一 SQL 代码块的 `<pre>` 与所有 `<span>` 的 `getComputedStyle()` 颜色 SHALL 立刻变为 github-light 色板
- **AND** 期间 SHALL NOT 发生新的 `highlightSql` 调用
- **AND** SHALL NOT 发生 DOM 重建（同一 `<pre>` 节点引用保持有效）

#### Scenario: light → dark 实时切换

- **GIVEN** SQL 代码块已在 light 主题下渲染
- **WHEN** `documentElement.classList` 切到 `dark`
- **THEN** 颜色立刻切到 github-dark 色板，无 DOM 重建、无 highlightSql 重跑

### Requirement: Shiki 输出 SHALL 同时携带两套 CSS 变量

`highlightSql(code)` 返回的 HTML 内，`<pre>` 与每个 token `<span>` SHALL 同时携带 `--shiki-light` / `--shiki-dark` 两套 CSS 变量；`<pre>` SHALL 额外携带 `--shiki-light-bg` / `--shiki-dark-bg` 两套背景变量。

#### Scenario: Shiki 输出包含双主题变量

- **GIVEN** `import { highlightSql } from '.../sql-highlight'`
- **WHEN** `await highlightSql('CREATE TABLE x (id INTEGER);')` 完成
- **THEN** 返回的 HTML 字符串 SHALL 匹配正则 `<pre[^>]*style="[^"]*--shiki-light:[^"]*--shiki-dark:[^"]*--shiki-light-bg:[^"]*--shiki-dark-bg:`
- **AND** 至少一个 token `<span>` 的 `style` SHALL 同时含 `--shiki-light:` 与 `--shiki-dark:`
- **AND** 任意 `<span>` 的 `style` SHALL NOT 含未命名为 `--shiki-*` 的 `color:` 直接赋值（即没有固定 color，全部走 CSS 变量）

### Requirement: SQL 代码块 chrome 在两个主题下都 SHALL 保持可读

外层 wrapper `[data-component="markdown-code"]`（含 language badge、SQL execute / explain / copy 按钮、风险标识）在 light / dark 主题下 SHALL 各自使用 `var(--background)`、`var(--muted)`、`var(--foreground)` 等语义 token，**不出现** 与代码块内的 Shiki 色板串色。

#### Scenario: dark 主题下 chrome 与内容色板分明

- **GIVEN** dark 主题已生效
- **WHEN** 渲染含 SQL 的消息
- **THEN** wrapper 的背景 SHALL 来自 `var(--dt-bg-canvas)` 系派生（不是 `--shiki-dark-bg`）
- **AND** language badge "SQL" 文字 SHALL 来自 `var(--muted-foreground)`
- **AND** 内部 `<pre class="shiki">` 的背景独立由 `--shiki-dark-bg` 决定 — 两层背景视觉上有明确边界

### Requirement: 非 SQL fenced code block 渲染 SHALL 不受本变更影响

`decorateCodeBlocks` 对非 SQL 语言只装饰 wrapper chrome（语言 badge、copy 按钮），不引入 Shiki 高亮。其渲染产物 SHALL 是 `<pre><code class="language-<lang>">...</code></pre>`，`<pre>` 背景 `transparent`，`<code>` 文字色 `inherit`（实际来自 `var(--foreground)`）。

#### Scenario: dark 主题下 Python 块仍是纯文本

- **GIVEN** 聊天消息含 ` ```python\ndef hello(): return 'world'\n``` `
- **AND** dark 主题生效
- **WHEN** 渲染完成
- **THEN** Python 块的 `<pre>` `getComputedStyle().backgroundColor` SHALL 为 `rgba(0, 0, 0, 0)`（透明）
- **AND** `<code>` 的 `color` SHALL 为 `oklch(0.985 0 0)` 或同等浅色 foreground 派生值
- **AND** SHALL NOT 出现任何 Shiki token `<span>` 与 `--shiki-*` CSS 变量
- **AND** wrapper chrome (language badge "Python", copy 按钮) 与 SQL 块一致使用语义 token
