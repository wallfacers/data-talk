## Context

DataTalk 的 light 主题与 dark 主题视觉强度不对等。dark 通过 `bg.app(0.16) → bg.canvas(0.21) → bg.subtle(0.27) → bg.elevated(0.27)` 的 OKLCH 亮度梯度天然分层；light 把这个梯度压扁到 `0.985 → 1.0 → 1.0 → 0.97`，三层近白几乎贴在一起，且中性原色族 hue 选在 255° 冷板岩，与 DESIGN.md 自述的 "paper-like" 完全相反。

playwright-cli 在 chromium 上对 manus.im 抓取的 CSS 变量（saved to `tmp/manus-scrape/`）给了一个直接参考：

| Manus token              | Value         | 角色                              |
|--------------------------|---------------|-----------------------------------|
| `--background-gray-main` | `#F8F8F7`     | 应用底（暖 off-white）            |
| `--background-card`      | `#FAFAFA`     | 卡片（接近白，稍冷一点）          |
| `--background-menu-white`| `#FFFFFF`     | 真白 elevated 面板（弹层、输入框）|
| `--background-code-bg`   | `#F0F0EF`     | 代码块底（更深一档的暖灰）        |
| `--text-primary`         | `#34322D`     | 主文本（暖深棕黑）                |
| `--text-secondary`       | `#5E5E5B`     | 次文本                            |
| `--text-tertiary`        | `#858481`     | 弱文本 / 图标                     |
| `--text-disable`         | `#B9B9B7`     | 禁用                              |
| `--border-light`         | `rgba(0,0,0,.04)` | 极细分隔                       |
| `--border-main`          | `rgba(0,0,0,.06)` | 默认描边                       |
| `--border-dark`          | `rgba(0,0,0,.12)` | 强调描边                       |
| `--fill-tsp-gray-main`   | `#37352F0A`   | 透明覆层（hover/active 用）      |
| `--Button-blue`          | `#0081F2`     | 强调（azure）—— **不采用**       |

关键 takeaway：
1. **暖色中性 vs 冷色中性** —— manus 用 hue ≈ 80° 的暖灰；DataTalk 当前用 hue ≈ 255° 的冷板岩。同等亮度下暖色读起来更"自然纸面"。
2. **三层背景结构**：bg-main / card / menu-white 形成 `F8F8F7 → FAFAFA → FFFFFF` 的 ΔL 梯度，约 3%、5%。卡片在底上"漂浮"是靠亮度差，不靠边框。
3. **边框只是辅助** —— 4–12% 透明度的黑色覆层，几乎看不见，但提供必要的边界。
4. **强调色独立选** —— manus 用 azure，DataTalk 该保留 cobalt（品牌信号）。

## Goals / Non-Goals

**Goals**:
- 重塑 light 主题为"暖色纸面骨架 + 真白卡片漂浮 + 暖深棕黑文字 + α-warm-dark 极细描边"
- 不改任何业务组件代码 —— 全部通过 token 翻新做级联
- 通过可访问性对比度门槛（4.5:1 body、3:1 large text/icon）
- 与 dark 主题保持同一套语义命名（`bg.app` / `bg.canvas` / `text.strong` 等），切主题不需要组件分支
- 在 playwright-cli 真实 chromium 上端到端实测，落地截图存证

**Non-Goals**:
- **不改 dark 主题** —— 用户明确说 dark 不需要修
- **不切 brand accent** —— DataTalk 的 cobalt blue 是品牌信号，不切到 manus 的 azure `#0081F2`
- **不引入 serif body 文字** —— manus 用 serif 仅在 hero 标题，body 仍是 system sans；DataTalk 当前 body 已是 sans，不变
- **不调整 typography token / spacing / radius / motion** —— 这些是独立维度，本变更只动颜色
- **不动 Recharts series 色板** —— `--chart-1..5` 在 light 主题下仍是灰阶 `oklch(... 0 0)`；如果暖 bg 下显冷，单独再开 change（design.md Decision 4 风险段已记录）
- **不动 Shiki SQL 代码块色板** —— github-light / github-dark 是公开标准，BUG-0041 刚修好不动它
- **不增加 component-local 颜色值** —— DESIGN.md "Don't use raw primitive colors directly in feature code"，本变更不破坏这条

## Decisions

### Decision 1: 中性原色族从冷板岩迁到暖灰（hue 255° → ≈ 80°）

**Decision**: `primitives.neutral.*` 整族重铸，从 manus.im 的 `#F8F8F7 / #34322D / #5E5E5B / #858481 / #B9B9B7` 体系反推完整 0–950 阶。

| Step | Hex       | OKLCH 近似            | 用途                          |
|------|-----------|-----------------------|-------------------------------|
| 0    | `#FFFFFF` | `oklch(1 0 0)`        | 真白卡片 / elevated           |
| 25   | `#FCFCFB` | `oklch(0.985 0.003 80)` | bg.app 备选（更暖一档时用） |
| 50   | `#F8F8F7` | `oklch(0.97 0.004 80)`  | bg.app（应用底）            |
| 100  | `#F1F1EF` | `oklch(0.945 0.005 80)` | bg.subtle（sidebar / 分组） |
| 200  | `#E5E5E2` | `oklch(0.905 0.006 80)` | border 派生                  |
| 300  | `#D1D1CD` | `oklch(0.83 0.008 80)`  | 分隔线 / 输入框描边         |
| 400  | `#B9B9B7` | `oklch(0.76 0.005 80)`  | disabled / 占位             |
| 500  | `#858481` | `oklch(0.585 0.006 80)` | text.soft                   |
| 600  | `#5E5E5B` | `oklch(0.45 0.006 80)`  | text.muted                  |
| 700  | `#4A4A47` | `oklch(0.375 0.007 80)` | text.base 备选               |
| 800  | `#34322D` | `oklch(0.29 0.008 80)`  | text.strong（主文本）       |
| 900  | `#1A1A19` | `oklch(0.18 0.005 80)`  | 高对比按钮底（manus Sign in）|
| 950  | `#0F0F0E` | `oklch(0.13 0.004 80)`  | 极强反差用（弹层文字 on 浅底）|

**Why hue ≈ 80°**: manus 的 `#34322D` 在 OKLCH 里大约是 `(0.29, 0.008, 78°)` —— 暖灰偏微黄、绿、橙的过渡区。低 chroma（0.005–0.008）保证它读起来仍是"中性"，但有暖意。Hue 255° 冷板岩在低 chroma 下偏向"屏幕白"。

**Alternatives Considered**:
- **A. hue 50°（纯黄棕）**：太暖、太黄，纸面感过头，像 sepia 滤镜
- **B. hue 120°（绿灰）**：与品牌 cobalt 撞色，且绿灰在大面积铺底时会显病态
- **C. hue 80° ✓**：暖度恰好，与黑色文本 `#34322D` 同 hue，整套色板自洽
- **D. 保持 255° 冷板岩**：用户明确反馈"白色主题不能拿捏"，不可行

### Decision 2: 保留 cobalt accent，不切到 manus 的 azure

**Decision**: `accent.primary` 在 light 主题继续用 `cobalt.700` (`#1D4ED8`)，dark 主题继续用 `cobalt.400` (`#60A5FA`)。但 `accent.primarySurface` 从 `cobalt.50` 调整为 warm-bg 上更柔和的 tint（`#EFF6FF` 仍可用，因为在 `#F8F8F7` 底上对比足够；如显冷可改为 `#EEF1FA` 微调）。

**Why**: cobalt 是 DataTalk 的品牌信号 —— 工具卡、Stage focus、primary action、selected row 都靠它。切色等于换品牌。manus azure `#0081F2` 饱和度更高、偏天蓝，与"工具研究"的克制气质不符。

**Alternatives Considered**:
- **A. 完全切到 manus azure**：品牌断裂、所有 stage focus / primary action 视觉变 —— **拒绝**
- **B. 保留 cobalt 但加暖（往紫红方向偏）**：会破坏 cobalt 与 dark 主题的连贯，且偏离 DESIGN.md 既定 `cobalt.50–900` —— **拒绝**
- **C. 保 cobalt，仅微调 primarySurface 在暖底上的视觉 ✓**：风险最小、品牌延续、必要时再单独调

### Decision 3: 边框用 α-warm-dark 而非固定中性灰

**Decision**: `border.subtle` = `rgba(55, 53, 47, 0.06)`、`border.default` = `rgba(55, 53, 47, 0.09)`、`border.strong` = `rgba(55, 53, 47, 0.14)`。RGB `(55, 53, 47)` 即 `#37352F`，等同 manus 的透明覆层基色。

**Why**:
- 透明边框在不同底色上（`bg.app` `#F8F8F7` vs `bg.canvas` `#FFFFFF`）会自适应混色，自然过渡，不出现"白卡片上贴一条冷灰线"的撕裂感
- 与 `interaction.hover` / `active` 用同一基色（`rgba(55, 53, 47, 0.04 / 0.08)`），整套覆层视觉同源

**Alternatives Considered**:
- **A. 固定中性灰**（沿用现 `neutral.200` 等）：暖底上偏冷，视觉断层
- **B. α-pure-black**（`rgba(0, 0, 0, ...)`)：偏冷，与 manus 一致但与暖文本不同 hue —— 可接受但 hue 不一致
- **C. α-warm-dark `rgba(55, 53, 47, ...)` ✓**：与文本同 hue，整套色板自洽

### Decision 4: chart-1..5 在 light 主题先保持现状（灰阶）

**Decision**: 本变更**不动** `:root` 内的 `--chart-1..5`（`oklch(0.87 0 0)` 到 `oklch(0.269 0 0)`）。

**Why**: 改图表 series 是独立工程（涉及 Recharts 数据呈现一致性、跨模式对比、品牌色与数据色的优先级），不该塞进 token 迁移。本变更先解决"底色与文本"的 ΔL 与 hue 问题；图表如果在新暖底上显冷，**作为已知风险登记**，由后续 change 解决。

**Verification**: tasks.md §5.3 要求 playwright-cli 截图 Stage 内含图表的页面，肉眼对比；如冷暖偏差不可忍受，需要在 tasks.md 末尾追加 "open follow-up change" 项。

### Decision 5: `globals.css` 仅改 `:root`，`.dark` 完全不动

**Decision**: 在 `client/src/styles/globals.css` 内，只修改 line 92-161 的 `:root { ... }` 块；line 163-232 的 `.dark { ... }` 块原样保留。

**Why**: 用户明确"黑色主题没问题"。改 dark 等于扩大风险面、违背最小变更原则。dark 主题用 `oklch(... 255)` 冷蓝色族在低亮度下不会有 light 的"冷板岩白屏"问题（深色面板的 hue 几乎不可见，只感觉到亮度梯度）。

### Decision 6: 不引入运行时主题状态、不动 `useThemeStore`

**Decision**: 不增加新的 React state、不改 `theme-store.ts` / `use-theme.ts`。主题切换仍是 `.dark` 祖先类 cascade 触发。

**Why**: 这是 token 翻新，不是主题机制重设计。`<html>` 上 `.dark` 类切换的逻辑已经稳定（与 BUG-0041 修复的 Shiki 双主题机制一致）。本变更只是改 `:root` 内变量值，不增加新分支。

### Decision 7: 端到端验收用 playwright-cli + screenshot diff，不用单元测试

**Decision**: 不写单元测试断言 CSS 变量字符串值（写了也就是抄一次 globals.css，无意义）。改用 playwright-cli 在 `npm run dev` 启动的真实 chromium 上：
- 截图关键页面（chat 空状态、chat 含消息、Stage 含表格、Stage 含图表、sidebar、composer 焦点态、dialog）
- `getComputedStyle` 抽样验 `body` / `.sidebar` / `[data-stage-panel]` / `.composer` 的 background-color，确认翻到目标 hex
- 切换主题 dark ↔ light 各一次，验证无 flicker、无残留旧色
- 把截图存 `tmp/refresh-light-theme/screenshots/`（git-ignored，符合 CLAUDE.md MCP 临时文件规则）

**Why**: token 翻新的语义验证靠"看上去对不对"。单元测试在这种纯样式 / 纯视觉迁移上提供的信号近乎零。

## Risks / Trade-offs

| 风险 | 等级 | 缓解 |
|------|------|------|
| Recharts series 色板在暖底显冷 | 中 | tasks.md §5.3 视觉验证；若不可忍受，登记 follow-up change |
| shadcn 组件二级表面（`--secondary` / `--muted`）hover 视觉变 | 低 | 已统一桥接到 `--dt-bg-subtle` / `--dt-interaction-hover`，token 翻新自动级联；playwright-cli 验证 |
| Shiki SQL 代码块底（`github-light` 白底）与 `bg.canvas` 真白接缝突兀 | 低 | github-light 是 `#FFFFFF`、新 `bg.canvas` 也是 `#FFFFFF` —— 完全一致，零接缝。与 `bg.app` `#F8F8F7` 有 ΔL ≈ 2%，反而提供"代码区分明"的体感 |
| 暖色底色 + cobalt accent 看起来"红蓝撞"主观感受 | 低 | cobalt `#1D4ED8` 在 OKLCH 是 `(0.4, 0.18, 262°)`，与暖灰 hue 80° 形成"互补"而非"撞"。manus 用 azure 也是同样的暖底+冷点缀关系 |
| 存量截图 / 录屏 / docs 引用旧色值 | 低 | 非阻塞，后续被发现可单独修 |
| 用户其它界面（如 Settings 页、Connection 表单）依赖 `--background` / `--card` / `--muted` 默认样式 | 低 | 已桥接 `var(--dt-*)`，自动级联；playwright-cli 截图覆盖 |

## Migration Plan

无迁移成本 —— 设计 token 是运行时 CSS 变量，应用启动即生效。无数据迁移、无 schema 变更、无 API 变更。

回退方案：`git revert` 一次提交即可恢复旧主题。

## Open Questions

无。Decision 2（保 cobalt）/ Decision 4（不动 chart）/ Decision 5（不动 dark）三处主观判断已在 proposal.md "Why" 段交代清楚，无需进一步澄清。
