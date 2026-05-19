# Ledger Design Language — 印刷品排版规范

本文档定义 ledger 报告的**视觉契约**。报告产物（HTML / PDF / Markdown）**必须**严格遵守这套设计语言，与 DataTalk client 的 dual-theme research workbench、bezel 的深色科技大屏**视觉上完全区分**。

## 1. 视觉气质

> 企业咨询报告范式（参考麦肯锡、德勤、BCG 月报）。

- **白底**：报告整体浅色背景，不引入暗色面板
- **衬线中文 + 半衬线西文**：正文使用思源宋体（Noto Serif SC）+ 与之协调的 sans-serif 西文
- **黑灰文字 + 单一品牌强调色**：去饱和、低对比的咨询气质
- **章节编号 + 页眉页脚**：便于打印归档、邮件转发后他人引用某一章节
- **单页结构清晰**：封面 → 摘要 → 目录 → 章节 → 附录，PDF 打印结果与 HTML 屏上观感一致

## 2. 颜色

### 文字色阶（黑灰）

| Token | Hex | 用途 |
|---|---|---|
| `text-strong` | `#1A1A19` | 主标题（h1）、KPI 数字 |
| `text-base` | `#34322D` | 正文、表格数据 |
| `text-muted` | `#5E5E5B` | 次要信息（副标题、caption、元数据） |
| `text-faint` | `#858481` | 弱化（数据来源脚注、页码） |

### 背景与边界

| Token | Hex | 用途 |
|---|---|---|
| `bg-page` | `#FFFFFF` | 整页背景 |
| `bg-soft` | `#F7F6F2` | 表头底色、章节封面浅色区块 |
| `bg-card` | `#FAFAF8` | KPI 卡片底色（与 page 微差） |
| `border-default` | `#E5E3DC` | 表格边框、章节分隔线 |
| `border-strong` | `#34322D` | 头部 / 页脚 hairline |

### 品牌强调色（accent）

- 用户可在 `report.json` `theme.accent` 字段指定（必传 hex）
- 默认 `#1f4e79`（经典深蓝，咨询范式常用）
- **仅用于**：封面装饰条、KPI delta 上升箭头、章节小标题左侧 left-border、风险等级 high 标记
- **不可用于**：大面积底色、正文文字、装饰渐变

## 3. 字体

### 字族

| 用途 | 字体 | 备选 fallback |
|---|---|---|
| 正文中文 | NotoSerifSC（思源宋体） | `'Songti SC'`, `'STSong'`, `serif` |
| 正文西文 | 与衬线协调的 sans-serif（如 system-ui） | `system-ui`, `-apple-system`, `'Helvetica Neue'`, `sans-serif` |
| 数据/代码 | 等宽（如 `SF Mono`） | `'SF Mono'`, `Menlo`, `monospace` |

字体文件本地打包在 `assets/fonts/`，通过 `@font-face` 引相对路径，**不依赖任何 CDN**。

### 字号阶（6 级）

| 级别 | 用途 | font-size | line-height |
|---|---|---|---|
| h1 | 封面标题 / 报告大标题 | 36px | 1.25 |
| h2 | 章节标题 | 24px | 1.3 |
| h3 | 子章节 / 大表格 caption | 18px | 1.4 |
| body | 正文段落 | 14px | 1.7 |
| caption | 表格/图表说明、KPI label | 12px | 1.5 |
| micro | source 数据来源脚注、页码 | 11px | 1.4 |

> 字号阶整体偏小（相对一般 web），追求报告纸面密度——印刷品质感。

### 字重

- 中文衬线 Regular 字重为主（避免多字重，控制字体文件大小）
- 强调时用 `font-weight: 600` 模拟次字重（浏览器合成）；**不引入额外字重文件**

## 4. 章节结构

### 封面（cover）

- 顶部 4mm 高强调色装饰条
- 报告大标题（h1）
- 副标题（h3，text-muted）
- 元数据三行：作者 / 日期 / 模板版本（micro，text-faint）

### 章节标题

- h2 标题左侧 3px 强调色 left-border
- 标题与首段之间 `break-after: avoid`（孤行保护）

### KPI 条带（kpi-strip）

- 3-6 个并列 KPI 卡片，每张：
  - label（caption，text-muted）
  - value（h2 字号、text-strong、不衬线西文 weight-600）
  - delta（caption，正向 #1F7A4E / 负向 #B33A3A，含 ↑↓ 箭头）

### 数据来源脚注（source）

- block 下方 `<div class="ledger-block-source">`
- micro 字号，text-faint，前缀图标 "▸"
- 内容：`mysql-prod · sales_summary as of 2026-04-30`

## 5. 表格

- 表头底色 `bg-soft`，文字 `text-strong` weight-600
- 行底色交替 `bg-page` / `bg-card`（zebra striping）
- 列边框 `border-default` 1px
- 数字列右对齐，文本列左对齐
- 长表格 `break-inside: avoid` 失效时强制分页 `break-before: page`（class `ledger-table--paged`）

## 6. 图表

- 图表容器宽度 100%，高度 320-400px
- 图表标题：caption 字号 text-base
- 图表内字号统一 12px（ECharts `textStyle.fontSize: 12`）
- ECharts 主题色板使用单色阶（accent + 衍生）或灰阶；**避免**多色调色板
- chart 容器 DOM 加 `data-ledger-chart-id` 属性，便于 ChartCaptureRenderer 定位截图

## 7. 风险/行动项列表

- 每项：`severity emoji + 描述`
  - critical: 🔴 文字 `#B33A3A`
  - high: 🟠 文字 `#C76A21`
  - medium: 🟡 文字 `#9C7B12`
  - low: 🟢 文字 `#1F7A4E`
- 含 owner / dueDate 时附 micro 字号脚注

## 8. PDF 分页规则

- A4 纸张，边距 20mm（上下）/ 18mm（左右）
- 页眉：报告标题（左）/ 章节名（右），文字 micro 字号 text-faint，下方 1px border-strong hairline
- 页脚：页码 `N / M`（右下），micro 字号 text-faint
- 章节标题前 `break-before: page`（除第一章外）
- chart / kpi-strip / 短表格 `break-inside: avoid`
- 长表格（> 30 行）class `ledger-table--paged`，强制起新页

## 9. 禁用项（Don't）

以下视觉元素**绝对禁止**出现在 ledger 报告中，违反将导致设计语言失效：

### ✗ 禁用：玻璃拟态（glassmorphism）

- `backdrop-filter: blur(...)`
- 半透明白色叠层
- 这是 bezel 的视觉资产，不属于汇报文档

### ✗ 禁用：暗色背景

- `background: #1a1a1a` / `#0d0d0d` 等深色面板
- 报告全程白底

### ✗ 禁用：霓虹色 / 高饱和强调

- 紫色 / 青色 / 粉色霓虹渐变
- 高饱和绿色 `#00ff80` / 高饱和蓝色 `#0080ff` 等
- 强调色一律 `theme.accent`（默认 `#1f4e79`），不引入第二个强调色

### ✗ 禁用：装饰动画

- CSS keyframes（fade-in / slide-up 等）
- transition 时长 > 200ms 的过渡
- 报告是静态印刷品，不是 web app

### ✗ 禁用：bezel 风格元素

- "舞台聚光灯"、"全息投影"、"科技大屏边框"等装饰
- 自由 grid 拖拽布局
- ledger 用严格的章节-段落顺序流，不用 grid

### ✗ 禁用：emoji 装饰过度

- 标题里穿插 🎉🚀✨ 等装饰 emoji
- emoji 仅在 risk-list severity 标记中使用（🔴🟠🟡🟢）

### ✗ 禁用：圆角 > 4px

- 整体边角风格保守，最大 border-radius 4px（KPI 卡片）
- 不用 12px / 16px 大圆角（这是消费级 UI 资产）

## 10. 视觉一致性 cheatsheet

构建一个 ledger 块时反问自己：

1. 如果打印到 A4 纸上，能不能直接装订进汇报 binder？
2. 上级看到这页 PDF 第一反应是"专业 / 正式"还是"科技感 / 大屏"？
3. 是否有任何元素让人联想到 bezel dashboard？
4. 字号、行高、配色是否在本文档定义的 token 之内？

如果任何一条答案是"否"，重新检查设计。
