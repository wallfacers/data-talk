## 1. Preflight

- [x] 1.1 Read `client/DESIGN.md` — 锁定 dual-theme strategy、`Calm in Light, Crisp in Dark`、4.5:1 文本对比度可访问性约束
- [x] 1.2 Read `client/src/features/chat/components/markdown/sql-highlight.ts` 与 `sql-code-block.ts` 全文（约 25 + 95 行）
- [x] 1.3 Read `client/src/features/chat/components/markdown/markdown.css` 全文，找到 `[data-component="markdown-code"]` 相关规则与 `<pre>` 默认样式
- [x] 1.4 Read `client/src/hooks/use-theme.ts` 与 `client/src/styles/globals.css` 的 `.dark` cascade 入口
- [x] 1.5 Grep `docs/bugs/` 确认无相同 / 重复条目，登记 BUG-0041
- [x] 1.6 确认 Shiki 版本：`client/package.json` 写明 `^4.0.2`，支持 `themes: { light, dark }` + `defaultColor: false`

## 2. Shiki 双主题输出

- [x] 2.1 改造 `sql-highlight.ts:highlightSql` 签名：从 `highlightSql(code, dark: boolean)` 改为 `highlightSql(code)` 单参
- [x] 2.2 `codeToHtml` 参数从 `theme: dark ? 'github-dark' : 'github-light'` 改为 `themes: { light: 'github-light', dark: 'github-dark' }`
- [x] 2.3 增加 `defaultColor: false`，让 Shiki 在每个 token 只输出 `--shiki-light` / `--shiki-dark` CSS 变量，不写固定 `color`

## 3. 简化 applySqlHighlight

- [x] 3.1 移除 `sql-code-block.ts:applySqlHighlight` 内 `const isDark = document.documentElement.classList.contains('dark')`
- [x] 3.2 `highlightSql(raw, isDark)` 改为 `highlightSql(raw)`
- [x] 3.3 注释说明主题由 CSS 驱动、`markdown.css` 通过祖先 `.dark` 类切换 CSS 变量

## 4. CSS 主题映射

- [x] 4.1 在 `markdown.css` 已有的 `[data-component="markdown-code"] pre > code { ... }` 块之后追加 Shiki 主题映射段
- [x] 4.2 light 默认：`pre.shiki { background-color: var(--shiki-light-bg) !important; color: var(--shiki-light); }` + `pre.shiki span { color: var(--shiki-light); background-color: var(--shiki-light-bg); }`
- [x] 4.3 dark 覆盖：`.dark [data-component="markdown-code"] pre.shiki { background-color: var(--shiki-dark-bg) !important; color: var(--shiki-dark); }` + 同样的 span 规则
- [x] 4.4 注释清楚说明：`!important` 仅用于覆盖 Shiki 写在 `<pre style="">` 内联的 fallback `background-color`，影响域局限在 `[data-component="markdown-code"]` 内

## 5. 验证

- [x] 5.1 `cd client && npx tsc --noEmit` — 改动 3 个文件零类型错误（messages.ts 1732 是预存在错误，与本变更无关）
- [x] 5.2 `npx vitest run src/features/chat/components/markdown/__tests__/markdown.test.tsx` — 20/20 通过
- [x] 5.3 playwright-cli 在 localhost:1420 真实 chromium 上注入测试 SQL 块，`getComputedStyle` 实测：
  - light: pre bg `rgb(255,255,255)`、color `rgb(36,41,46)` ✓
  - dark: pre bg `rgb(36,41,46)`、CREATE/TABLE/INTEGER `rgb(249,117,131)`、ingested_data `rgb(179,146,240)`、`256` `rgb(121,184,255)` ✓
- [x] 5.4 playwright-cli 注入非 SQL Python 块，dark 下 pre bg 透明、code `oklch(0.985 0 0)` 浅色 foreground — 无串色，符合 Non-Goal "不动其它语言"
- [x] 5.5 截图存证：`docs/bugs/assets/BUG-0041/before-dark.png`（修复前用户原图）、`after-dark.png`、`after-light.png`

## 6. BUG 登记

- [x] 6.1 新建 `docs/bugs/BUG-0041-sql-code-block-theme-color-mismatch.md`，status=`fixed`、priority=P2、source=`manual-report`、modules=[`chat`, `markdown`]
- [x] 6.2 更新 `docs/bugs/index.md`：下一个 ID → BUG-0042；In Progress 表追加 BUG-0041 行；By Module 的 `markdown` / `chat`、By Source 的 `manual-report` 聚合视图同步追加

## 7. OpenSpec 归档（本次仅创建 change，留待用户决定是否归档）

- [ ] 7.1 `/opsx:archive fix-sql-codeblock-theme`：将本目录移到 `openspec/changes/archive/2026-05-13-fix-sql-codeblock-theme/`
- [ ] 7.2 把 `specs/chat-sql-codeblock/spec.md` 内 `## Added Requirements` 章节合并到 `openspec/specs/chat-sql-codeblock/spec.md`（capability 基线）
