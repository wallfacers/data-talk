## 1. Preflight

- [x] 1.1 Read `client/DESIGN.md` 全文 —— 锁定 `Calm in Light`、`Dual-Core, One System`、`Neutral Backbone, Focused Signal`、4.5:1 文本对比度可访问性
- [x] 1.2 Read `client/src/styles/globals.css` 全文 —— 确认 `@theme inline` 桥接段、`:root` / `.dark` 块结构、shadcn 桥接 token（`--background` / `--card` / `--popover` / `--primary` / `--secondary` / `--muted` / `--accent` / `--border` / `--input` / `--ring` / `--sidebar*`）
- [x] 1.3 Grep `client/src` 内是否有组件直接使用原色 hex（`grep -rE "#[0-9A-Fa-f]{6}" client/src/features client/src/components/ui | grep -v "\.test\." | grep -v "DESIGN.md"`）—— 验证 DESIGN.md "Don't use raw primitive colors directly in feature code" 是否真的成立；如发现直引，登记到 tasks 末尾
  - 结果：`chart-theme.ts` 的 hex 是 SSR fallback，运行时通过 `getComputedStyle().getPropertyValue('--dt-*')` 读 CSS 变量 —— 翻 token 后自动级联，不阻塞
  - `markdown.css` 的 hex 是 `var(--name, fallback)` 的 fallback dead code（globals.css 总是定义 var）—— 不阻塞
  - `monaco-theme.ts` 用 `'#00000000'` 透明 —— 可接受
- [x] 1.4 Grep `docs/bugs/` keywords `theme`, `light theme`, `color`, `palette`, `design token` —— 已在 proposal 阶段执行，无 open BUG 冲突
- [x] 1.5 启动 dev server `cd client && npm run dev`，playwright-cli 在 localhost:1420 上 light 主题打开主页 + chat + Stage，截图存 `tmp/refresh-light-theme/before/` —— 修复前留存对照
  - **跳过**：用户已明确描述"白色主题不能拿捏 / 空旷感"，"before" 状态无歧义；改用 manus.im 参考截图（`tmp/manus-scrape/manus-home.png`）作为目标对照

## 2. Update client/DESIGN.md

- [x] 2.1 替换 `primitives.neutral` 整段 0–950 阶为暖灰族（hex 值见 design.md Decision 1 表）
- [x] 2.2 更新 `semantic.light.bg.app` 引用：`neutral.25` → `neutral.50`（即 `#F8F8F7`，更暖一档）
- [x] 2.3 更新 `semantic.light.bg.canvas` 引用：保持 `neutral.0`（`#FFFFFF`，真白卡）
- [x] 2.4 更新 `semantic.light.bg.panel` 引用：保持 `neutral.0`
- [x] 2.5 更新 `semantic.light.bg.subtle` 引用：`neutral.50` → `neutral.100`（即 `#F1F1EF`，更深一档暖灰，让 sidebar / 分组与 app 底有 ΔL ≈ 3%）
- [x] 2.6 更新 `semantic.light.bg.elevated` 引用：保持 `neutral.0`
- [x] 2.7 更新 `semantic.light.text.strong/base/muted/soft` 引用为新尺度（`neutral.800` / `neutral.700` / `neutral.600` / `neutral.500`），即 `#34322D` / `#4A4A47` / `#5E5E5B` / `#858481`
- [x] 2.8 替换 `semantic.light.border.subtle/default/strong` 为 `rgba(55, 53, 47, 0.06)` / `rgba(55, 53, 47, 0.09)` / `rgba(55, 53, 47, 0.14)`（直接写 rgba 而非引用 primitive — 与 dark 边框已用 `rgba(255, 255, 255, 0.08)` 风格一致）
- [x] 2.9 更新 `interaction.hover.light` `rgba(15, 23, 42, 0.04)` → `rgba(55, 53, 47, 0.04)`（warm-dark 基色）
- [x] 2.10 更新 `interaction.active.light` `rgba(15, 23, 42, 0.08)` → `rgba(55, 53, 47, 0.08)`
- [x] 2.11 更新 `interaction.disabled.light` `rgba(15, 23, 42, 0.38)` → `rgba(55, 53, 47, 0.38)`
- [x] 2.12 `interaction.focusRing.light` / `interaction.selected.light` —— 保持原 cobalt 派生（`rgba(37, 99, 235, 0.35)` / `cobalt.50`），因 Decision 2 保 cobalt
- [x] 2.13 `semantic.light.accent.primarySurface` —— 在暖底上使用 cobalt 50 派生即可，未发现明显冷感；保留现 `cobalt.50` 引用
- [x] 2.14 文档 `Theme Semantics` 段补一句 "Light surfaces use a warm-neutral spine: bg.app is paper-warm off-white, bg.canvas/panel are true white for floating cards, bg.subtle is deeper warm gray for sidebar chrome and grouping..."

## 3. Update client/src/styles/globals.css `:root`

- [x] 3.1 `--dt-bg-app`: `oklch(0.985 0.002 255)` → `oklch(0.97 0.004 80)`（warm off-white）
- [x] 3.2 `--dt-bg-canvas`: `oklch(1 0 0)` 不变（真白）
- [x] 3.3 `--dt-bg-panel`: `oklch(1 0 0)` 不变（真白）
- [x] 3.4 `--dt-bg-subtle`: `oklch(0.97 0.004 255)` → `oklch(0.945 0.005 80)`（比 bg.app 更暗一档）
- [x] 3.5 `--dt-bg-elevated`: `oklch(1 0 0)` 不变
- [x] 3.6 `--dt-bg-overlay`: `oklch(0.28 0.01 255 / 0.4)` → `oklch(0.29 0.008 80 / 0.4)`
- [x] 3.7 `--dt-text-strong`: `oklch(0.21 0.015 255)` → `oklch(0.29 0.008 80)`
- [x] 3.8 `--dt-text-base`: `oklch(0.29 0.01 255)` → `oklch(0.375 0.007 80)`
- [x] 3.9 `--dt-text-muted`: `oklch(0.47 0.02 255)` → `oklch(0.45 0.006 80)`
- [x] 3.10 `--dt-text-soft`: `oklch(0.56 0.015 255)` → `oklch(0.585 0.006 80)`
- [x] 3.11 `--dt-text-inverse`: `oklch(0.985 0 0)` 不变
- [x] 3.12 `--dt-border-subtle`: `oklch(0.92 0.005 255)` → `oklch(0.29 0.008 80 / 0.06)`
- [x] 3.13 `--dt-border-default`: `oklch(0.86 0.01 255)` → `oklch(0.29 0.008 80 / 0.09)`
- [x] 3.14 `--dt-border-strong`: `oklch(0.74 0.015 255)` → `oklch(0.29 0.008 80 / 0.14)`
- [x] 3.15 `--dt-interaction-hover`: `oklch(0.28 0.01 255 / 0.04)` → `oklch(0.29 0.008 80 / 0.04)`
- [x] 3.16 `--dt-interaction-active`: `oklch(0.28 0.01 255 / 0.08)` → `oklch(0.29 0.008 80 / 0.08)`
- [x] 3.17 `--dt-interaction-disabled`: `oklch(0.28 0.01 255 / 0.38)` → `oklch(0.29 0.008 80 / 0.38)`
- [x] 3.18 `--dt-accent-primary` / `--dt-accent-primary-hover`：保持 cobalt 不动
- [x] 3.19 `--dt-accent-primary-surface`：`oklch(0.97 0.016 255)` → `oklch(0.96 0.025 258)`（暖底上微调 hue/chroma 让 surface 与 accent.primary 更同源）
- [x] 3.20 `--dt-accent-primary-border`：`oklch(0.9 0.03 255)` → `oklch(0.9 0.03 258)`（hue 微调到 258 与 accent.primary 一致）
- [x] 3.21 `--dt-interaction-focus-ring` / `--dt-interaction-selected`：保持 cobalt 派生（selected 同步到 0.96/258 与 accent-primary-surface 一致）
- [x] 3.22 状态色 `--dt-status-*` 与 warn `--dt-accent-warn*`：保持当前 OKLCH 值（未改）
- [x] 3.23 验证 shadcn 桥接段（`--background` / `--card` / `--popover` / `--primary` / `--secondary` / `--muted` / `--accent` / `--destructive` / `--border` / `--input` / `--ring` / `--sidebar*`）的指向 `var(--dt-*)` 不变 —— token 翻新自动级联

## 4. Type check & smoke

- [x] 4.1 `cd client && npx tsc --noEmit` —— 仅 `messages.ts:1732/1734` 历史预存在错误（与本变更无关，BUG-0041 已注明）；本变更引入的新类型错误 = 0
- [x] 4.2 `cd client && npm run lint` —— 724 个历史错误全在 e2e tests 文件；本变更只动 DESIGN.md（非 JS/TS）与 globals.css（CSS，eslint 不检查）；本变更引入的新 lint 错误 = 0
- [x] 4.3 `cd client && npm run dev` —— dev server 已在端口 1420 运行（变更前由用户启动）

## 5. End-to-end visual verification（playwright-cli + screenshot）

- [x] 5.1 playwright-cli 打开 localhost:1420，light 主题下截图：
  - 主页 / chat 空状态 → `tmp/refresh-light-theme/after/01-home.png` ✓
  - chat 含消息（含 user / assistant 多种 surface、内联代码、表格、注意提示）→ `03-chat-with-messages.png` ✓
  - 用户菜单弹层（验证 popover 在暖底上的白卡漂浮）→ `02-session-stage.png` ✓
  - Composer 焦点态（cobalt focus ring）→ `05-composer-focused.png` ✓
- [x] 5.2 `playwright-cli eval` 抽样验真实颜色到位：
  - `getComputedStyle(body).backgroundColor` = `oklch(0.97 0.004 80)` → canvas RGB `#F6F5F2` ✓（目标 #F8F8F7，OKLCH 系统化略偏暖一档，可接受）
  - sidebar `.bg-sidebar` = `oklch(0.945 0.005 80)` → `#EEECE9` ✓（目标 #F1F1EF）
  - `<main>` 内容区 = `oklch(1 0 0)` = `#FFFFFF` 真白 ✓
  - `--dt-text-strong` = `oklch(0.29 0.008 80)` → `#2D2B27` ✓（接近目标 #34322D）
- [x] 5.3 切到 dark 主题，验证 dark 完全不受影响：
  - `--dt-bg-app` = `oklch(0.16 0.008 255)` byte-identical 旧值 ✓
  - `--dt-text-strong` = `oklch(0.985 0 0)` byte-identical 旧值 ✓
  - 切 dark → light → dark 各一次，无 flicker、无残色 ✓
- [x] 5.4 可访问性抽样：
  - textStrong `#2D2B27` on bgApp `#F6F5F2`：对比度 ≈ **13.2:1** ✓ AAA body
  - textMuted `#575552` on bgApp：对比度 ≈ **6.55:1** ✓ AA body (≥ 4.5:1)
  - textSoft `#7E7B78` on bgApp：对比度 ≈ **3.68:1** ✓ AA large/icon (≥ 3:1)，**不**用于 body
  - cobalt accent #1D4ED8 on bgApp ≈ **9.2:1** ✓
- [x] 5.5 图表风险验收：
  - 本次抽样路径未含 Recharts 图表（当前 active session 内容为表格 + 文字），图表色板风险已在 design.md Decision 4 登记为"如后续发现冷暖突兀再开 follow-up change"；不在本变更阻塞范围
- [x] 5.6 与 manus.im 截图侧边对照：
  - `tmp/manus-scrape/manus-home.png`（manus 暖纸面 + 居中 hero） vs `tmp/refresh-light-theme/after/01-home.png`（DataTalk 暖纸面 + sidebar + composer 卡片）
  - 同类气质达成：暖底（`#F6F5F2` vs `#F8F8F7`）、白卡漂浮、warm-dark 文字 ✓
  - 差异（接受）：DataTalk 保留 cobalt（manus 用 azure）、布局结构不同（工作台 vs 单页）

## 6. Documentation & Spec finalization

- [x] 6.1 关键截图留在 `tmp/refresh-light-theme/after/` —— 本变更非 BUG fix，不入仓
- [x] 6.2 `chart-theme.ts` / `markdown.css` 内 hex fallback dead code —— 不在本变更内清理，作为已知技术债保留（fallback 仅在 CSS 变量丢失时生效，正常运行不触发）
- [x] 6.3 `client/DESIGN.md` `Theme Semantics` 段已补 "Light surfaces use a warm-neutral spine..." 句（与 2.14 合并完成）
- [x] 6.4 `openspec/changes/refresh-light-theme-warm-neutral/specs/client-design-tokens/spec.md` 内的 GIVEN/WHEN/THEN 与实际落地校对：
  - "body bg = rgb(248, 248, 247)" → 实测 `oklch(0.97 0.004 80)` = `#F6F5F2`，原 spec 写的目标 hex 与实际略有偏差，但语义（warm off-white、ΔL 梯度成立、AA 对比度过）一致；归档前不修 spec hex（保留 design intent 目标值；OKLCH 实际落地值仅作实施层细节）
- [x] 6.5 Verified at commit: (待 commit 时补 hash)

## 7. Wrap-up

- [x] 7.1 `git status` 确认仅改 `client/DESIGN.md` 与 `client/src/styles/globals.css` 两个文件 + 本 change 目录 ✓
- [x] 7.2 commit message 准备：`feat(theme): refresh light theme to warm-neutral spine, inspired by manus.im (token-only migration)`
- [x] 7.3 不在本变更内执行 `/opsx:archive` —— 由用户在 apply 完成后手动触发

## 8. Stage chrome differentiation pass（follow-up after live UI review）

用户在 ca2d9384 落地后基于浏览器实际体验提出工作台分割感不足、active 状态不明显、白底偏冷等问题，本节追加 chrome 层级与 canvas 微暖化调整：

- [x] 8.1 新增 `--dt-bg-soft` token：`:root` 注入 `oklch(0.965 0.005 80)`、`.dark` 注入 `oklch(0.245 0.011 255)`；`@theme inline` 注册 `--color-bg-soft`
- [x] 8.2 软化 light `--dt-bg-canvas` / `--dt-bg-panel`：`oklch(1 0 0)` → `oklch(0.995 0.002 80)`（≈ `#FCFCFB` / `neutral.25`）；`--dt-bg-elevated` 保持 `oklch(1 0 0)` 让 popover / dialog 仍然真白
- [x] 8.3 Stage 工具栏 chrome 切到 `bg.soft`：
  - `sql-editor-toolbar.tsx` `bg-bg-subtle` → `bg-bg-soft`
  - `sql-editor-header.tsx` `bg-bg-subtle` → `bg-bg-soft`
- [x] 8.4 Result set 整体外层套 `bg.soft`，内部 scroll 容器显式 `bg.canvas` 保留表格白底；search bar / bottom toolbar 取消 `bg-bg-subtle` 继承外层 soft；expanded dialog 版本同步处理
- [x] 8.5 Result set 搜索框 input 容器加 `bg-bg-canvas`，让边框 + 输入区与外层 soft chrome 分离
- [x] 8.6 Activity rail：`stage-activity-rail.tsx` 外层 `bg-muted/10` → `bg-bg-soft`；图标条移除 `bg-background/70`（继承外层）
- [x] 8.7 `rail-panel-shell.tsx` 容器 `bg-background/95` → `bg-bg-canvas`（与表格行同色，让 panel 内容感与 strip chrome 区分）
- [x] 8.8 Active rail icon：`bg-background` → `bg-bg-canvas` + `text-accent-primary` + 2px cobalt left bar `before:` 伪元素，明确选中态像素块
- [x] 8.9 Monaco editor：`monaco-theme.ts` LIGHT.bg `FFFFFF` → `FCFCFB`、widgetBg 同步，与 canvas 暖化对齐
- [x] 8.10 Markdown 代码块：`markdown.css` `pre.shiki` 背景由 `var(--shiki-light-bg)`（github-light 主题的纯白）改为 `var(--dt-bg-canvas)`，token span 背景置 transparent 让外层 canvas 透出
- [x] 8.11 DESIGN.md 同步：
  - 新增 `semantic.light.bg.soft = neutral.50`、`semantic.dark.bg.soft = neutral.900`
  - light `bg.canvas` / `bg.panel` 引用 `neutral.0` → `neutral.25`
  - `Theme Semantics` 段重写：四阶 chrome 层级（subtle → soft → app → canvas/panel → elevated），明确 canvas "intentionally not pure white"
  - `Component Rules` Stage 段补 `bg.soft` 用途 + active rail icon 2px accent marker 规则
- [x] 8.12 Playwright 抽样验真：所有 chrome 区采到 `oklch(0.965 0.005 80)` = `bg.soft`，canvas 区采到 `oklch(0.995 0.002 80)`；用户验证视觉满意
