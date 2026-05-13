## Context

Shiki 是项目唯一在前端运行时做 SQL 语法高亮的库（仅 SQL 一种语言走高亮 — 见 `markdown.tsx:decorateCodeBlocks`，非 SQL 走纯 `<pre><code>`）。当前 `sql-highlight.ts` + `sql-code-block.ts` 的实现选择"运行时读 `documentElement.classList` 决定单主题"，本质把主题状态从 React 状态机搬到 DOM class，再让 Shiki 把决定后的颜色写死成内联 style — 失去了 CSS 级别的主题切换能力。

`globals.css` 已经把整个应用建立在 `.dark` 类驱动的 CSS 变量系统上（`--background`、`--foreground`、`--muted` 等），所有 shadcn 组件、表格、图表 chrome 都靠这套 cascade 切主题。SQL 代码块是唯一逃出这套系统的组件 — 本变更让它回归。

## Goals / Non-Goals

**Goals**:
- SQL 代码块在 light / dark 主题下渲染各自正确的色板，无串色、无低对比度文本
- 主题切换不需要 JS 重渲，纯 CSS cascade 驱动，与项目其它组件一致
- 修复对 `markdown.tsx` 主渲染流程（marked → sanitize → morphdom）零侵入

**Non-Goals**:
- 不引入更多语言的语法高亮（Python / JSON / TS 等仍走纯文本，符合 chat 信息密度优先的设计取舍）
- 不替换 Shiki — 这是 DataTalk 已经选定的高亮库
- 不动 chart / dashboard fence 等已有特殊代码块的渲染路径
- 不改 markdown wrapper 的 chrome（header bar、language badge、copy button、SQL 操作按钮）

## Decisions

### Decision 1: Shiki 双主题 + CSS 变量，不用 React state 同步

**Decision**: 用 Shiki 原生支持的 `themes: { light, dark }` + `defaultColor: false`，让 Shiki 在每个 token 同时输出两套 CSS 变量；再用 `.dark` 祖先选择器在 CSS 层切换。

**Alternatives Considered**:

- **A. 把 theme store 注入 markdown 组件，theme 变化时调 `applySqlHighlight` 重渲**
  - 缺点：每次主题切换都要重跑 highlighter（异步），有可视 flicker；morphdom 流程要么 invalidate cache key 要么手动遍历重写 DOM，复杂度高
- **B. 在 CSS 里硬编码 token 颜色，不用 Shiki 输出的色板**
  - 缺点：要自己维护两套与 github-light/dark 等价的色板；Shiki 升级 / 切换 grammar 时会脱节
- **C. 当前方案（双主题 CSS 变量）✓**
  - 优点：Shiki 自己计算两套颜色一并塞进 inline style 的 CSS vars；CSS 只做"哪一组生效"的二选一；切换零异步、零 flicker、零额外状态
  - 缺点：依赖 Shiki ≥ 0.13 的 `defaultColor: false` API — 当前 `package.json` 已是 `^4.0.2`，满足

**Why this matters**: 切主题在 DataTalk 是高频操作（chat 长时间会话、用户偏好），不能让 SQL 代码块拖累整页响应。

### Decision 2: CSS 用 `!important` 覆盖 Shiki 自带的 inline `background-color`

**Decision**: `pre.shiki { background-color: var(--shiki-light-bg) !important; }`，dark 变体同理。

**Reason**: Shiki 在 `<pre style="...">` 内除了 `--shiki-light-bg` / `--shiki-dark-bg` 两个 CSS 变量，仍会写一个 fallback `background-color: <某值>`（取决于 Shiki 内部默认行为）。内联 style 优先级高于普通选择器，必须用 `!important` 让 CSS 变量真正生效。

**Alternatives Considered**:

- **A. 在 `applySqlHighlight` 里手动 strip Shiki 输出的 `background-color`**
  - 缺点：要正则解析 inline style，脆弱；Shiki 输出格式升级会破坏
- **B. 用 `!important` ✓**
  - 缺点：`!important` 一般是反模式
  - 缓解：仅用于这一处、且只针对 Shiki 自己写出来的 `background-color`，影响面极窄（只匹配 `pre.shiki` 且只在 `[data-component="markdown-code"]` 内）

### Decision 3: token `<span>` 不需要 `!important`

**Decision**: `pre.shiki span` 的 `color` / `background-color` 不加 `!important`。

**Reason**: 因为用了 `defaultColor: false`，Shiki 在 `<span>` 上**只**写 CSS 变量（`--shiki-light` / `--shiki-dark`），不写固定 `color`。普通选择器即可生效。

**Verification**: playwright-cli 在 chromium 上实测 — 切换 `.dark` 类后所有 token 颜色立刻按主题切换，无需 `!important`。

### Decision 4: 仅 SQL 走高亮，其它语言继续纯文本

**Decision**: 本变更不引入更多语言的 Shiki bundle。

**Reason**:
- DataTalk 是 SQL-centric 工作台，AI 主要输出 SQL；其它语言（Python / TS / JSON）是辅助语境
- Shiki 每个 grammar bundle 都是 KB 级懒加载成本；当前 SQL 一种已能覆盖核心场景
- 非 SQL 块用 `[data-component="markdown-code"] pre { background: transparent; }` + `color: var(--foreground)` cascade 已经在两个主题下都可读 — 实测 dark 主题下 `getComputedStyle` 返回 `rgba(0,0,0,0)` bg + `oklch(0.985 0 0)` 浅色 foreground，对比度满足 4.5:1

**Future**: 如未来要扩展更多语言高亮，照本变更模式批量 `themes: { light, dark }` + 同一套 CSS 规则即可，无需重新设计。

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Shiki 升级改变 CSS 变量命名 | 锁定 `^4.0.2`，升级前 grep `--shiki-` 验证 |
| `!important` 用于 pre bg 是反模式 | 影响域窄（仅 `[data-component="markdown-code"] pre.shiki`），文档化在 CSS 注释 |
| 主题切换的 SQL 块未重渲 caching | 不存在 — 由 CSS cascade 立刻接管，DOM 不变 |
| jsdom 单测无法验色值 | 接受 — 改用 playwright-cli 在真实 chromium 上 `getComputedStyle` 实测，是更高 fidelity 的验证 |

## Migration Plan

无运行时迁移 — 单次部署生效：

1. 用户旧版本下打开的 SQL 代码块会保留 Shiki 单主题 inline style，但因为 wrapper 主题切换已经会重渲整段消息（markdown.tsx 在 props 变化时全量重跑），下次 streaming / 切 session / 刷新页面后即恢复正常。
2. 不存在跨版本数据兼容问题（纯渲染层修复）。
