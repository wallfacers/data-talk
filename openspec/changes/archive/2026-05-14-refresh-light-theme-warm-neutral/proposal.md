## Why

DataTalk 当前的白色主题在视觉上明显弱于黑色主题，主因是 **冷板岩底色 + 三层近白表面无差异**：

- `--dt-bg-app` `oklch(0.985 0.002 255)` ≈ `#FAFBFC`、`--dt-bg-canvas` `oklch(1 0 0)` = `#FFFFFF`、`--dt-bg-panel` 同为 `#FFFFFF`。三层背景几乎没有亮度差，借不到亮度做层级。
- 中性原色族 `neutral.*`（`#FCFDFE → #F1F5F9 → ... → #0F172A`）整体偏冷板岩（hue ≈ 255° 的蓝灰），与 `client/DESIGN.md` 自述的 light 主题策略 "Calm, paper-like research surface" 相悖 —— 纸面应是暖色、有微弱黄/灰底，不是冷色屏幕白。
- 内容稀疏时（如 chat 空状态、Stage 默认面板、新建会话），通版白色让大段留白显得空旷，缺少视觉锚点。

dark 主题之所以成立，是因为 `--dt-bg-app` `0.16` → `--dt-bg-canvas` `0.21` → `--dt-bg-subtle` `0.27` 之间天然有亮度差，层级一望即知。light 主题缺这个差。

参考站点 manus.im 的 light 主题（用 playwright-cli 在真实 chromium 上抓到完整 CSS 变量，见 [design.md](design.md) Decision 1）给出的解法：**暖色中性骨架（warm off-white `#F8F8F7` 做底，warm dark `#34322D` 做文字）+ 真白卡片漂浮其上 + α-warm-dark 极细边框**。这套方案让"白底+白卡"获得稳定的视觉分层，且整体读起来像研究室纸面，与 DataTalk 的 "AI-native data research workbench" 定位契合。

## What Changes

**仅迁移设计 token，不改业务组件** —— `client/` 内的特性代码已经走 `bg-bg-subtle` / `text-text-muted` / `bg-accent-primary` 等语义类（见 `globals.css` line 49-90 的 `@theme inline` 桥接），翻 token 即可级联。

- **client/DESIGN.md (frontmatter)**:
  - `primitives.neutral.*` 从冷板岩（hue 255°）迁到暖灰族（hue ≈ 80°，匹配 manus 的 `#34322D` 体系）。引入新的 `0 → 25 → 50 → 100 → 200 → 300 → 400 → 500 → 600 → 700 → 800 → 900` 暖色尺度。
  - `semantic.light.bg.*`：`app` 改为 `neutral.50`（暖 off-white `#F8F8F7`），`canvas` 保持 `neutral.0`（纯白 `#FFFFFF`），`panel` 保持 `neutral.0`，`subtle` 改为 `neutral.100`（`#F1F1EF` 暖中灰）。三层有 ΔL ≈ 3–5% 的明显梯度。
  - `semantic.light.text.*`：`strong` 改为 `neutral.900`（暖深棕黑 `#34322D`），`base` 改为 `neutral.800`（`#5E5E5B`），`muted` `neutral.600` (`#858481`)，`soft` `neutral.500`。
  - `semantic.light.border.*`：从冷板岩固定色切到 α-warm-dark — `subtle` = `rgba(55, 53, 47, 0.06)`、`default` = `rgba(55, 53, 47, 0.09)`、`strong` = `rgba(55, 53, 47, 0.14)`。
  - `interaction.hover` / `active` 等 overlay 同样切到 `rgba(55, 53, 47, ...)`。
  - **保留 cobalt accent 不变** — DataTalk 的品牌色是 cobalt blue，不切到 manus 的 azure `#0081f2`（见 design.md Decision 2）。但 `accent.primarySurface` 用 warm bg 上更柔和的 cobalt tint。
- **client/src/styles/globals.css (:root only)**:
  - 把上述 hex 全部翻成 OKLCH 形式写入 `--dt-bg-*` / `--dt-text-*` / `--dt-border-*` / `--dt-interaction-*`。
  - 不动 `.dark { ... }` 块 — 用户明确说 dark 没问题。
- **不改的**:
  - 业务组件、shadcn/ui 适配层、Recharts 图表 series 调色板、Shiki SQL 代码块色板（github-light / github-dark，已在 BUG-0041 内修好，本变更只验证 wrapper 暖白底与 github-light 白底的过渡视觉无割裂）。

## Capabilities

### Added Capabilities

- `client-design-tokens` —— 当前 `openspec/specs/` 没有 client design token 契约的 spec（已有 `chat-sql-codeblock` / `ingestion-ui-e2e-testing` / `sql-confirmation`，无 design-token 相关）。本变更新增该 capability，在 `specs/client-design-tokens/spec.md` 内固化 light 主题 token 的语义契约（GIVEN / WHEN / THEN），归档时落入 `openspec/specs/client-design-tokens/`。

## Impact

- **Files changed (client)**:
  - `client/DESIGN.md` —— frontmatter `primitives` / `semantic.light` / `interaction.*.light` 段落
  - `client/src/styles/globals.css` —— 仅 `:root { ... }` 块内的 `--dt-bg-*` / `--dt-text-*` / `--dt-border-*` / `--dt-interaction-*` / `--dt-accent-primary-surface` / `--dt-accent-primary-border`
- **No backend changes**.
- **No DB / data-source type compatibility** changes — `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 是 **N/A**（纯前端 token 迁移，不涉及任何数据源 / SQL 方言 / connection 流程）。
- **Design Inputs**: `client/DESIGN.md` 适用约束:
  - **"Calm in Light"** light 主题策略 —— 本变更把"声称纸面但实际是冷板岩白"修成真正的暖纸面，是对这一策略的兑现，非偏离
  - **"Dual-Core, One System"** —— 两个主题共用同一 token 名（`bg.app` / `text.strong` 等），本变更只翻 light 取值，dark 取值不动，仍是同系统
  - **可访问性 4.5:1 body 文本对比度** —— 新色（`#34322D` on `#F8F8F7` ≈ 13.6:1、`#34322D` on `#FFFFFF` ≈ 14.5:1、`#5E5E5B` on `#F8F8F7` ≈ 6.5:1）均通过，详见 design.md
  - **"Neutral Backbone, Focused Signal"** —— 中性骨架担纲、cobalt 仅负责焦点 / 选中 / 主要操作，本变更不动这条原则（保 cobalt）
- **BUG tracking**: 已 grep `docs/bugs/` 关键词 `theme`, `light theme`, `color`, `palette`, `design token`:
  - BUG-0041（SQL 代码块串色，已 fixed）—— 与本变更相邻但不交叉（本变更不动 markdown.css 与 Shiki）
  - BUG-0010（图表轴标题被裁，wontfix-design）—— 与色 token 无关
  - 没有 open 状态 BUG 与 "light theme too empty / palette" 相关。本变更不引入新 BUG 修复，是主动改进。
- **User memory cited**: `feedback_test_before_docs.md` —— 必须 playwright-cli 端到端实测主题切换、对比度、关键页面截图后才能 claim 完成。tasks.md §5 已显式列出该步骤。
- **Risks**:
  - **Recharts series colors**：当前 `chart-1..5` 用纯灰阶 `oklch(... 0 0)`，在暖 bg 上略偏冷；不阻塞，但需 playwright-cli 视觉验收（design.md Decision 4）
  - **shadcn 组件 hover / focus**：`--secondary` / `--muted` 桥接的二级表面颜色翻新后，按钮 hover 视觉感会变 —— 风险低，token 已统一管理
  - **Shiki SQL wrapper 过渡**：github-light 是 `#FFFFFF` 底，与新 `bg.canvas` `#FFFFFF` 完全一致；与 `bg.app` `#F8F8F7` 有 ΔL ≈ 2% 视觉边界，恰好提供"代码区与文字区分明"的体感（非缺陷）
  - **用户存量截图 / 录屏**：DESIGN.md / docs 内若有截图，色值会与新主题不一致；非阻塞，后续如发现可单独跟进
