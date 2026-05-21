# Ledger Design Language — 现代报告设计语言

本文档定义 ledger 报告的**视觉契约**。报告产物（HTML / PDF / Markdown）**必须**严格遵守这套设计语言。ledger 的目标是**现代化、富表现力且仍可打印归档**的企业数据汇报美学——借鉴 Manus 数据报告的设计 DNA（角色化色彩系统、富视觉原语、证据驱动叙事），与 bezel 的暗色科技大屏**视觉上完全区分**。

> **现代化 ≠ 暗色科技风。** 丰富感来自**同色阶层级 + 留白节奏**，而非高饱和霓虹堆砌或暗色玻璃拟态。

## 1. 视觉气质

> 现代企业数据报告范式：专业、有层次、有理有据，白底可打印。

- **白底**：报告整体浅色背景，保证打印可读，不引入暗色面板
- **衬线中文 + 协调 sans-serif 西文**：正文使用思源宋体（Noto Serif SC）+ 协调的 sans-serif 西文与数字
- **色彩角色系统**：专业主色 + 创新强调 + 干净背景层 + 同色阶 tint 梯度 + 语义数据色（见 §2）
- **视觉服务于结论**：富视觉原语（callout / stat-highlight / comparison / quote / divider）用于**强调结论性洞察**，不做纯装饰
- **单页结构清晰**：封面 → 摘要 → 目录 → 章节 → 附录，PDF 打印结果与 HTML 屏上观感一致

## 2. 色彩角色系统

ledger 用**色彩角色系统**而非单一强调色。每个角色承担明确职责；丰富的层次由 accent 同色阶派生的 tint 梯度提供。

### 2.1 三大色彩角色

| 角色 | 默认 hex | 用途 |
|---|---|---|
| `primary` | `#0F2A4A` | 专业主色：封面主色块、章节标题、stat-highlight 主数字、comparison 数值 |
| `accent` | `#2F6FBF` | 创新强调：装饰条、callout-insight、图表主系列色、图表/卡片 left/top-border |
| `surface` | `#F4F7FB` | 干净背景层：callout 浅底、stat-highlight 卡底、表头底 |

### 2.2 同色阶 tint 梯度（由 accent 派生）

| Token | 默认 hex | 用途 |
|---|---|---|
| `tint-1` | `#E0E9F5` | 最浅：heat 最低档底色 |
| `tint-2` | `#C1D4EC` | 较浅：heat / quote left-border |
| `tint-3` | `#8DB0DC` | 中：bar fill / heat 中档 |
| `tint-4` | `#4E85C9` | 最深：heat 最高档底色 |

> tint 一律从 `accent` 派生（`mix(accent, #FFF, step)`，step = [0.85, 0.70, 0.45, 0.15]），保证 heat / bar / divider 同属 accent 家族色阶。

### 2.3 语义数据色

| Token | 默认 hex | 用途 |
|---|---|---|
| `positive` | `#1F7A4E` | 正向 delta、低风险 |
| `negative` | `#B33A3A` | 负向 delta、严重风险、callout-warning |
| `neutral` | `#5E5E5B` | 中性、callout-note |
| `warning` | `#C76A21` | 风险等级 high |
| `caution` | `#9C7B12` | 风险等级 medium |

### 2.4 文字色阶（保留）

| Token | Hex | 用途 |
|---|---|---|
| `text-strong` | `#1A1A19` | 主标题、强调文字 |
| `text-base` | `#34322D` | 正文、表格数据 |
| `text-muted` | `#5E5E5B` | 次要信息（副标题、caption、label） |
| `text-faint` | `#858481` | 弱化（页码、元数据） |

### 2.5 theme 来源与覆盖

- 用户在 `report.json` `theme` 字段指定，支持两种形态（向后兼容）：
  - **旧形态**：仅 `{ "accent": "#xxxxxx" }`——其余角色色由 accent 派生（primary = `mix(accent,#000,.30)`，surface = `mix(accent,#FFF,.94)`，tints 由 accent 派生）。
  - **新形态**：`{ "primary?", "accent?", "surface?", "tints?" }`——任意子集合法，缺省角色由派生补齐。
- 用户**完全不提供 theme** 时，使用上表的默认设计 token（深蓝系），**不**走派生公式。
- 渲染层把解析后的角色色注入 `:root` CSS 变量；ledger.css 规则体一律 `var(--ledger-*)` 引用，**不写裸 hex**。

## 3. 字体

### 字族

| 用途 | 字体 | 备选 fallback |
|---|---|---|
| 正文中文 | NotoSerifSC（思源宋体） | `'Songti SC'`, `'STSong'`, `serif` |
| 数字/西文 | NotoSansSC / 协调 sans-serif | `system-ui`, `-apple-system`, `'Helvetica Neue'`, `sans-serif` |
| 数据/代码 | 等宽（如 `SF Mono`） | `'SF Mono'`, `Menlo`, `monospace` |

字体文件本地打包在 `assets/fonts/`，通过 `@font-face` 引相对路径，**不依赖任何 CDN**。

### 字号阶（6 级，每级标 font-size + line-height）

| 级别 | 用途 | font-size | line-height |
|---|---|---|---|
| h1 | 封面标题 / 报告大标题 | 36px | 1.25 |
| h2 | 章节标题 | 24px | 1.3 |
| h3 | 子章节 / 大表格 caption / quote 文本 | 18px | 1.4 |
| body | 正文段落 | 14px | 1.7 |
| caption | 表格/图表说明、KPI/comparison label | 12px | 1.5 |
| micro | 页码、封面元数据 | 11px | 1.4 |

> stat-highlight 主数字使用超大字号（44px）作为 hero 视觉锚点，是字号阶之上的专用例外。

### 字重

- 中文衬线 Regular 字重为主；强调时用 `font-weight: 600`（浏览器合成），**不引入额外字重文件**

## 4. 章节结构

### 封面（cover）

- 顶部 4mm 高 accent 装饰条
- 报告大标题（h1）/ 副标题（h3，text-muted）/ 元数据（micro，text-faint）

### 章节标题

- h2 标题左侧 3px accent left-border；标题与首段 `break-after: avoid`

### KPI 条带（kpi-strip）

- 3-6 个并列 KPI 卡片：label（caption）/ value（h2、text-strong）/ delta（正向 `positive` / 负向 `negative`，含 ↑↓）

## 5. 富视觉原语规范

新增 5 种富视觉原语。色彩一律引用 §2 色彩角色系统，**不使用裸 hex 霓虹色**。全部 `break-inside: avoid`（防 A4 分页割裂）。

### 5.1 callout（key-insight 高亮块）

- 用途：强调一条结论性洞察 / 风险 / 提示。**禁止纯装饰**。
- 结构：左侧 3px 角色色 left-border + `surface` 浅底 + emoji 图标 + 可选标题 + markdown 正文。
- variant → 色彩映射：`insight`→`accent`（💡）/ `warning`→`negative`（⚠️）/ `success`→`positive`（✅）/ `note`→`neutral`（📝）。

### 5.2 stat-highlight（hero 关键指标）

- 用途：单个最重要指标的视觉锚点（如月度总 GMV）。
- 结构：超大数字（44px、`primary` 色）+ label + 可选 context + 可选 delta（正负着语义色）。

### 5.3 comparison（并列对比卡）

- 用途：2-4 个同维度指标横向对比（如各渠道 GMV）。
- 结构：等宽卡片并列，每张顶部 3px accent top-border，数值用 `primary` 色。

### 5.4 quote（pull-quote）

- 用途：引用关键论断 / 用户原话 / 方法论结论。
- 结构：4px `tint-2` left-border + 斜体 h3 文本 + 可选 attribution。

### 5.5 divider（章节视觉分隔）

- 用途：章节内逻辑段落的视觉分隔。
- 结构：细 hairline（`border-default`）；带 label 时居中文字两侧延伸 hairline。

## 6. 表格（含富单元格）

- 表头底色 `surface`，文字 `text-strong` weight-600；行底 zebra（`bg-page` / `bg-card`）；列边框 `border-default` 1px
- 富单元格（可选，列级 `cellFormats`，取值 `text`/`bar`/`delta`/`heat`）：
  - `bar`：cell 内迷你条形，宽度正比数值，`tint-3` 填充
  - `delta`：+/- 值着语义色（`positive`/`negative`）+ ↑↓
  - `heat`：按数值映射 `tint-1..4` 底色深浅
  - **未声明则退化为纯文本**；数值解析失败安全回退纯文本
- 长表格（> 30 行）class `ledger-table--paged` 强制起新页

## 7. 图表

- 图表容器宽度 100%，高度约 360px；标题用 caption 字号
- **协调配色板由 theme 调色板驱动**：渲染层在 setOption 前注入 8 色 PALETTE（`[accent, primary, tint-2, tint-3, positive, negative, neutral, tint-1]`）；报告若在 `echartsOption` 显式指定 series 色则**显式色胜出**
- 一图一观点（见 SKILL.md 数据叙事规则）；避免高饱和霓虹调色板
- chart 容器 DOM 加 `data-ledger-chart-id` 属性，便于 ChartCaptureRenderer 定位截图

## 8. 风险/行动项列表

- 每项：`severity emoji + 描述`
  - critical 🔴 `negative` / high 🟠 `warning` / medium 🟡 `caution` / low 🟢 `positive`
- 含 owner / dueDate 时附 micro 字号脚注

## 9. PDF 分页规则

- A4 纸张，边距 20mm（上下）/ 18mm（左右）
- 章节标题前 `break-before: page`（除第一章外）
- chart / kpi-strip / 短表格 / **callout / stat-highlight / comparison / quote** 全部 `break-inside: avoid`
- 长表格（> 30 行）class `ledger-table--paged` 强制起新页

## 10. 保留克制边界（与 bezel 的视觉分界）

ledger 现代化但**不滑向 bezel 风格**。以下**绝对禁止**：

### ✗ 禁用：暗色大屏 / 暗色面板

- `background: #1a1a1a` / `#0d0d0d` 等深色背景——报告全程白底
- 暗色科技大屏、"舞台聚光灯"、"全息投影"、"科技大屏边框"等装饰

### ✗ 禁用：玻璃拟态（glassmorphism）

- `backdrop-filter: blur(...)`、半透明白色叠层——这是 bezel 的视觉资产

### ✗ 禁用：自由 grid 拖拽布局

- ledger 用严格的章节-段落顺序流，不用自由 grid 拖拽（这是 bezel 的大屏布局）

### ✗ 禁用：高饱和霓虹色

- 紫/青/粉霓虹渐变、`#00ff80` / `#0080ff` 等高饱和色
- 色彩一律取自 §2 色彩角色系统（角色色 / tint 梯度 / 语义色），**不引入系统外的霓虹强调**

### ✗ 禁用：装饰动画

- CSS keyframes（fade-in / slide-up 等）、transition > 200ms——报告是静态印刷品

### ✗ 禁用：圆角 > 4px

- 最大 `border-radius: 4px`，不用 12px / 16px 大圆角（消费级 UI 资产）

### ✗ 禁用：emoji 过度装饰

- 标题里穿插 🎉🚀✨ 等装饰 emoji
- emoji 仅用于：risk-list severity 标记（🔴🟠🟡🟢）、callout 图标（💡⚠️✅📝）

## 11. 视觉一致性 cheatsheet

构建一个 ledger 块时反问自己：

1. 如果打印到 A4 纸上，能不能直接装订进汇报 binder？
2. 上级看到这页 PDF 第一反应是"专业、有层次、有理有据"还是"科技感 / 大屏"？
3. 是否有任何元素让人联想到 bezel dashboard（暗色 / 玻璃 / 自由 grid）？
4. 富视觉原语是在**强调结论**还是在**装饰**？
5. 字号、行高、配色是否都落在 §2 / §3 定义的 token 之内？

如果任何一条答案不对，重新检查设计。
