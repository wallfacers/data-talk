## Why

聊天消息内的 SQL fenced code block 在 dark 主题下出现严重串色（BUG-0041）：keyword 显示成 github-light 的深红 `#D73A49`，identifier 显示成 github-light 的深紫 `#6F42C1`，普通文本显示成接近黑的 `#24292E`，但 wrapper 仍是 dark 主题的暗色背景。结果是：用户在 dark 主题下打开任何含 SQL 的 AI 回复（ingestion 流程的 CREATE TABLE 预览、execute_sql 工具卡片回显的 SQL、聊天 turn 中 LLM 写出来的 SQL）都看不清 identifier 与普通文本，违反 `client/DESIGN.md` 的 "Calm in Light, Crisp in Dark" 与 4.5:1 文本对比度可访问性要求。

根因：`sql-code-block.ts:applySqlHighlight` 在渲染时一次性读取 `document.documentElement.classList.contains('dark')` 作为 Shiki 主题选择，把决定后的颜色写死成内联 style；之后用户切主题不会重渲，时序错位时首次检测也可能错。

## What Changes

- **Shiki 改为双主题模式**：`sql-highlight.ts` 用 `themes: { light: 'github-light', dark: 'github-dark' }` + `defaultColor: false`，让 Shiki 在 `<pre>` 与每个 token `<span>` 同时输出 `--shiki-light` / `--shiki-dark` / `--shiki-light-bg` / `--shiki-dark-bg` 四个 CSS 变量，不再写固定 `color`。
- **移除一次性 isDark 检测**：`sql-code-block.ts:applySqlHighlight` 删掉 `documentElement.classList` 读取，`highlightSql(raw)` 签名只接 SQL 文本。
- **新增 CSS 主题映射**：`markdown.css` 用 `.dark` 祖先选择器（与 `globals.css` 的 `@custom-variant dark (&:is(.dark *))` 一致的 cascade 习惯）把 `pre.shiki` 与 `pre.shiki span` 的 `color` / `background-color` 在 light / dark 间切换 — 主题切换变成纯 CSS 行为，零 JS 重渲、零 flicker。
- **非 SQL 代码块不变**：`decorateCodeBlocks` 只装饰边框 / 按钮，非 SQL 代码渲染为 `<pre><code>` 纯文本、`background: transparent`、`color` 继承 `--foreground`，本就支持双主题，本变更不动它。

## Capabilities

### Added Capabilities

- `chat-sql-codeblock`（新建该 capability spec — 当前 `openspec/specs/` 下尚无聊天 SQL 代码块渲染相关 spec，本变更在 `openspec/changes/<name>/specs/chat-sql-codeblock/spec.md` 中新增对应规约，归档时落入 `openspec/specs/chat-sql-codeblock/`）。

## Impact

- **Files changed (client)**:
  - `client/src/features/chat/components/markdown/sql-highlight.ts` — `highlightSql` 签名改为单参 + 双主题输出
  - `client/src/features/chat/components/markdown/sql-code-block.ts` — `applySqlHighlight` 删掉 `isDark` 检测
  - `client/src/features/chat/components/markdown/markdown.css` — 新增 `.dark` 选择器映射 Shiki CSS 变量
- **No backend changes**.
- **No DB / data-source type compatibility** changes — `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is **N/A**（纯前端样式 / 渲染层修复，不涉及任何数据源 / SQL 方言）。
- **Design Inputs**: `client/DESIGN.md` 适用约束：
  - "Calm in Light, Crisp in Dark" — 两个主题需独立可读、不串色（本修复的核心目标）
  - 可访问性：body 文本对比度 ≥ 4.5:1 — github-dark 的 `#E1E4E8` over `#24292E` 满足；github-light 的 `#24292E` over `#FFFFFF` 满足
  - 双主题为 first-class theme，不允许组件级 theme-specific 行为（本修复改成 CSS 驱动，符合此原则）
- **BUG tracking**: `docs/bugs/BUG-0041-sql-code-block-theme-color-mismatch.md`（fixed），index.md 同步。
- **Open BUGs overlapping**: 已 grep `docs/bugs/` — 与 markdown / shiki / chat code-block 渲染相关只有 BUG-0010（图表轴标题被裁，不同模块），本变更不与其交叉。
- **Risks**:
  - Shiki 4.x 的 `defaultColor: false` 输出依赖 `--shiki-light-bg` / `--shiki-dark-bg` CSS 变量；如未来升级 Shiki 改变变量命名，CSS 规则需同步更新。
  - 现有 `markdown.test.tsx` 不断言 token 色值（用 jsdom 跑），本次改动对其透明，无需新增用例；色值验证通过 playwright-cli 在真实 chromium 上 `getComputedStyle` 实测完成。
