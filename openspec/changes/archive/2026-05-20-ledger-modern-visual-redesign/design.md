## Context

ledger 是冻结快照式企业汇报文档生成能力：AI 产出结构化 `report.json`，服务端**确定性**渲染为 HTML（同步）+ PDF + Markdown（异步）。样式不由 AI 写，真正的视觉杠杆集中在三处：

- `assets/styles/ledger.css`（509 行，全部 CSS）
- `ReportRenderer.java` / `MarkdownRenderer.java`（block → HTML/MD 结构）
- `design-language.md` + `section-patterns.md`（AI 必须遵守的视觉与块契约）

当前 `design-language.md` 第 9 章是强硬禁用清单，把报告锁死为"单一 accent + 黑灰、纯线性流"的保守印刷品。本次有意推翻它，借鉴 Manus 数据报告的设计 DNA。调研结论（详见 proposal 动机）落到三条可执行抓手：**色彩角色系统**、**富视觉原语**、**证据驱动叙事**。

**约束（必须保留的不变量）**：自包含无外网依赖、`__LEDGER_READY__` 信号、Playwright A4 PDF、字体本地 `@font-face`、TOC 锚点、`cover.author` sanitize、`_assets/**` CORS 头、`::-webkit-scrollbar-button{display:none}`（BUG-0074 由 `LedgerCssScrollbarContractTest` 守护）、`<base href>` 解析（BUG-0077）。

**架构现实（决定"向后兼容"的真实边界）**：`ReportRenderer.toHtml` 把每份报告的 `accent` 在 promote 时**内联冻结**进存量 HTML（`ReportRenderer.java:78,88`：缺省 accent 落 `#1f4e79`，`<style>:root{--ledger-accent:…}</style>` 注入在 `<link styles/ledger.css>` 之后覆盖之），但存量 HTML 通过 `<link>` **实时引用共享 `ledger.css`**。推论：
- 存量报告的 `accent` 已冻结，改 Java 默认只影响**新报告**。
- 存量报告**没有**内联 `--ledger-primary` / `--ledger-surface`（当时不存在）→ 新 CSS 引用这些变量时，存量 HTML 回退到**新 `ledger.css` 的 `:root` 默认值**。
- 结论：**所有存量报告的结构性视觉都会随共享 CSS 实时变化**，与是否改 accent 默认无关。

因此本次的兼容承诺是**功能性兼容而非像素级兼容**：(a) 旧 `report.json` 仍能校验 + 无错渲染；(b) 旧 class 名仍产出连贯布局；(c) 显式 `theme.accent` 仍被尊重。**不承诺**旧报告像素不变——这是一次**有意的视觉刷新**。为此新 `ledger.css` 的 `:root` MUST 为 `--ledger-primary`/`--ledger-surface`/`--ledger-tint-*`/语义色提供合理默认，使仅内联了 `--ledger-accent` 的存量 HTML 也能连贯渲染。

**设计契约说明**：本变更不改 `client/` 任何源码（报告 HTML 由服务端渲染、在 Report Viewer iframe 内展示），故 `client/DESIGN.md` 的 Frontend Design Contract Gate 标记 **N/A**。本变更自身的设计契约是 ledger 的 `design-language.md`，由本 change 一并改写。

## Goals / Non-Goals

**Goals:**
- 用色彩角色系统替换单一强调色，让报告在不破"白底可打印"前提下获得现代层次感。
- 新增 5 种富视觉原语 + table 富单元格，支撑"视觉服务于结论"的数据叙事。
- 功能性向后兼容：历史报告（仅 `theme.accent`、仅旧 11 种块、无 `cellFormats`）仍能校验 + 无错渲染 + 布局连贯（**非像素级不变**，见 Context 架构现实）。
- 把"有理有据"沉淀为 `SKILL.md` 的撰写规则，约束 AI 的叙事质量。

**Non-Goals:**
- 不滑向 bezel 风格（暗色大屏 / 玻璃拟态 / 自由 grid 拖拽）——边界仍在。
- 不引入外部 CSS 框架或 CDN 资源、不新增字体文件。
- 不改 report 的存储模型 / SQLite schema / promote 流程状态机。
- 不动 `client/` Report Viewer 组件（除非 E2E 暴露 iframe 兼容回归）。

## Decisions

### D1：色彩角色系统 + 默认 token + 确定性派生

放弃"单 accent + 黑灰"，定义三角色：`primary`（专业主色）/ `accent`（创新强调）/ `surface`（干净背景层），外加派生的 tint 梯度与语义数据色。默认 token（借鉴 Manus 深蓝-天蓝-浅蓝层级，待 apply 前用户确认）：

| 角色 | 默认 hex | 用途 |
|---|---|---|
| `--ledger-primary` | `#0F2A4A` | 封面主色块、章节标题 left-border、stat-highlight 主数字 |
| `--ledger-accent` | `#2F6FBF` | 强调装饰、callout-insight、图表主色 |
| `--ledger-surface` | `#F4F7FB` | callout/卡片浅底、表头底 |
| `--ledger-tint-1..4` | 派生 | accent 同色阶梯度（热力/条形/分隔） |
| `--ledger-positive` | `#1F7A4E` | 正向 delta（沿用） |
| `--ledger-negative` | `#B33A3A` | 负向 delta（沿用） |
| `--ledger-neutral` | `#5E5E5B` | 中性（沿用 text-muted） |

文字色阶（`#1A1A19`/`#34322D`/`#5E5E5B`/`#858481`）保留不变。

**`mix` 精确定义（消歧义）**：`mix(a, b, r)` 逐通道计算 `round(a_ch * (1 - r) + b_ch * r)`，`r ∈ [0,1]` 是**第二个颜色 `b` 的权重**。即 `mix(accent, #000, 0.30)` = 70% accent + 30% 黑（向黑压深）；`mix(accent, #FFF, 0.94)` = 6% accent + 94% 白（贴近白）。校验例：`mix(#2F6FBF, #000, 0.30) = #214E86`。

**派生规则（纯函数，sRGB 线性混合，便于 Java 实现与单测）**：
- 仅给 `accent` 时：`primary = mix(accent, #000, 0.30)`（向黑混合压深）；`surface = mix(accent, #FFF, 0.94)`（向白混合提亮）。
- `tint-k = mix(accent, #FFF, step_k)`，`step = [0.85, 0.70, 0.45, 0.15]`（由浅到深）。**tints 一律从 `accent` 派生，不从 `primary` 派生**（保证 heat/bar/divider 同属 accent 家族色阶；spec 与此对齐）。
- 所有派生输入合法 hex → 输出合法 hex，同输入同输出。

**注意（默认 token ≠ 从 accent 派生）**：上表的默认 `primary #0F2A4A` / `surface #F4F7FB` 是**独立挑选的设计 token**，不是用默认 `accent #2F6FBF` 跑派生公式得出的（派生公式对默认 accent 算出的是 `primary≈#214E86`、更偏蓝亮）。两条路径有意分开：
- 用户**显式给三角色** → 透传上表默认/用户值（精修过的设计色）。
- 用户**只给 accent** → 走派生公式补齐其余角色（保证任意自定义 accent 都有协调的 primary/surface）。
派生公式只是"用户没给时的兜底协调器"，不负责复刻默认 token。

**备选**：HSL 空间派生（更感知均匀）。否决理由：sRGB mix 实现最简、零依赖、确定性强，对报告场景足够；HSL 需额外转换代码，收益不抵复杂度。

### D2：theme schema 向后兼容 + 默认 accent 变更

`theme` 接受两形态：旧 `{accent}` 与新 `{primary?,accent?,surface?,tints?}`。validator 只校验出现的字段为合法 hex（`tints` 为 hex 数组），不强制任何角色必填——任意子集都能经 D1 派生补齐。`accent` 在两形态语义一致，旧 fixture 不破。无 DB migration（theme 存在 report.json blob 内）。

**默认 accent 变更（有意）**：Java 缺省 accent 由 `#1f4e79` 改为新默认 `#2F6FBF`，同步改 `ledger.css` 的 `:root --ledger-accent` 默认。这只影响**新提交且未显式指定 accent**的报告（存量已内联冻结，见 Context）。这是有意的视觉刷新，在 proposal 中明确记录。

**用户提供 `tints` 的处理**：validator 对 `tints` 数组加**软上限 ≤ 8**——超限 warn 但**不 reject**（避免阻断）。CSS 变量注入只取**前 4 个**作为 `--ledger-tint-1..4`，其余忽略；用户提供不足 4 个时，缺位 tint 由 `accent` 按 D1 派生补齐。保证无论用户传几个 tint，最终恒定输出 `--ledger-tint-1..4` 四个变量。

### D3：新增 5 种富视觉原语

均为 `chapter.blocks[]` 内的同级块，schema 见 specs。要点：
- `callout`：`{variant: insight|warning|note|success, title?, markdown}`，色彩按 variant 映射到角色色（insight→accent，warning→negative，success→positive，note→neutral），底用对应色的 surface tint。
- `stat-highlight`：`{value, label, context?, delta?, source?}`，value 用最大字号 + primary 色；delta 正负着语义色。
- `comparison`：`{items:[{label,value,caption?}]}`（2-4），等宽卡片并列。
- `quote`：`{text, attribution?}`，pull-quote 排版。
- `divider`：`{label?}`，细 hairline + 可选居中标签。

Markdown 降级：callout→blockquote 带 **emoji + 加粗中文标签**前缀（`> 💡 **洞察**：…` / `> ⚠️ **警告**：…` / `> 📝 **提示**：…` / `> ✅ **成功**：…）。emoji 在 GitHub/Obsidian/Typora/Notion/飞书 等目标环境均能渲染为字形（比 GFM `[!NOTE]` 扩展语法通用——后者在非 GitHub 环境会显示为字面文本）；即使 emoji 字体缺失，加粗中文标签仍承载语义，优雅降级。stat-highlight→`**¥3.2M** 总 GMV（同比 +18%）`；comparison→GFM 表；quote→blockquote；divider→`---`。

### D4：table 富单元格 `cellFormats[]`

列级声明，长度对齐 `columns`，取值 `text`(默认)/`bar`/`delta`/`heat`。HTML 渲染按列修饰：`bar` 在 cell 内叠加宽度正比数值的条（tint 色）；`delta` 对 +/- 值着语义色；`heat` 按数值映射 surface→tint 底色深浅。**未声明则完全退化为旧纯文本表格**。Markdown 一律忽略修饰输出纯 GFM 表（保持可移植）。数值解析失败的 cell MUST 安全回退为纯文本，不抛异常。

### D5：渲染器与 validator 改动面

- `ReportRenderer`：新增 `injectThemeVars(theme)` 产出 `:root` CSS 变量块；新增 `renderCallout/renderStatHighlight/renderComparison/renderQuote/renderDivider`；`renderTable` 支持 `cellFormats`。
- **ECharts 调色板映射（明确规则）**：当前 `renderChartBootstrapScript`（`ReportRenderer.java:147-188`）只 `inst.setOption(c.option)`，未设 `color`（用 ECharts 内置板）。改为在 `initOne` 中**先**注入由 theme 解析出的具体 hex 调色板再 setOption：`inst.setOption({ color: PALETTE }, false); inst.setOption(c.option);`——两次 merge，**报告自己在 echartsOption 里显式指定的 series 色仍然胜出**（只在未指定时用 PALETTE）。PALETTE 为 8 色，由 Java 把角色色解析为字面 hex 注入 JS：`[accent, primary, tint-2, tint-3, positive, negative, neutral, tint-1]`（accent 打头作主系列色，后续覆盖多系列；超过 8 系列 fallback 到 ECharts 内置）。
- `MarkdownRenderer`：对应 5 个降级分支 + table 忽略 cellFormats。
- `ReportSchemaValidator`：`ALLOWED_BLOCK_TYPES` 增 5 种；新增各块必填字段校验（callout.variant 枚举、stat-highlight.value 必填、comparison.items 长度 2-4、quote.text 必填）；**table `cellFormats` 校验**（若存在：长度必须 = `columns.length`，每项 ∈ {text,bar,delta,heat}）；theme 字段 hex 格式校验 + `tints` 软上限 ≤ 8（超限 warn 不 reject）；新增对应 `RECOVERY_HINTS`。
- 沿用 collect-all 校验风格，新违规复用现有 errorCode 模式（如 `REPORT_BLOCK_FIELD_INVALID`、`REPORT_TABLE_CELLFORMAT_INVALID`）。

### D6：CSS 扩展与硬编码颜色重构（非从零重写）

`ledger.css` 现已有成熟变量体系（33 行 `:root`：文字 4 阶 + 背景 3 阶 + 边框 2 阶 + accent + 字号 6 阶 + 行高 6 阶）。本次是**扩展 + 消除硬编码颜色**，**不是删 509 行从零重写**（避免不必要回归）：
- **新增变量**：`--ledger-primary`、`--ledger-surface`、`--ledger-tint-1..4`、`--ledger-positive/negative/neutral`，并在 `:root` 给出合理默认（供仅内联 accent 的存量 HTML 兜底，见 Context）。
- **重构硬编码**：把 KPI delta 的 `#1F7A4E`/`#B33A3A` 与 risk-list 的 4 个 severity 硬编码色改为 `var(--ledger-*)` 引用。
- **新增 ~200 行**：5 种新块 + `cellFormats` 修饰样式类（全部 `break-inside: avoid`）。
- **保留 ~400 行不动**：`@font-face`（本地路径）、所有 `break-*`、`::-webkit-scrollbar-*`（含 button display:none）、`@media print` 白底、既有 block 样式（保证旧 HTML 连贯）。
- 规则体内颜色一律 `var(--ledger-*)`；裸 hex 只允许出现在 `:root{}` 的 token 定义处。

### D7：模板与文档

`design-language.md` / `section-patterns.md` / `SKILL.md` / `templates/*.md` 同步更新（specs 已定需求）。`templates/monthly-business-review.md` 示例 JSON 增补使用新原语的片段，作为 AI few-shot。

## Risks / Trade-offs

- **回归到已修 BUG 区域** → 缓解：保留 `LedgerCssScrollbarContractTest` 不改其断言；E2E 用 playwright-cli 实跑 HTML+PDF 复核 BUG-0073/74/75/76/77 不复发；任何 E2E 偏差按 BUG Gate 建档。
- **PDF 分页割裂新卡片块** → 缓解：新块全部 `break-inside: avoid`（已写入 spec），E2E 检查 A4 分页。
- **存量报告随共享 CSS 视觉刷新（已知、有意）** → 这是设计决定而非缺陷（见 Context）：兼容承诺为功能性（校验/无错渲染/布局连贯/显式 accent 被尊重），非像素级。缓解：新 `:root` 给全部新变量合理默认，保证仅内联 accent 的旧 HTML 连贯；保留旧 fixture 测试 + 新增"仅 accent theme""仅旧 11 块""旧 theme+一个新块"专项 scenario；派生纯函数单测覆盖。
- **审美主观、apply 阶段反复返工** → 缓解：D1 默认 token 在 apply 前经 AskUserQuestion 确认；先出一份样例报告截图供过目再批量改文档。
- **富单元格数值解析脆弱** → 缓解：解析失败安全回退纯文本，单测覆盖非数值 cell。

## Migration Plan

1. 无数据库 migration。theme/blocks 存在 report.json blob，schema_version 仍为 1（新字段全部可选，旧 JSON 合法）。
2. 资源文件内容变更触发 `SkillResourceSyncer` SHA-256 marker 失效 → 启动时整体重新同步到 `.opencode/skills/ledger/`。
3. 历史报告：存量 report.json 不重渲染；如用户"重新生成"则按新渲染器产出新 version。可选提供一次性 re-render 脚本（参考 `RerenderExistingReportTest`），非必需。
4. **回滚**：还原 `ledger.css` + 三个 renderer/validator Java 文件 + skill 文档即可，无状态残留。

## Open Questions

均已在 review 中拍板（2026-05-20）：

- **[已定] 默认色彩 token = 深蓝系单套**（`#0F2A4A`/`#2F6FBF`/`#F4F7FB`），不做多预设。理由：当前默认 `#1f4e79` 已是深蓝系、用户从未抱怨缺主题切换；深蓝是金融/企服汇报通用基调；theme 开放 schema 已留扩展空间（用户随时可在 report.json 覆盖任意角色色）。仍保留 task 1.3 的 AskUserQuestion 走一遍默认 token 视觉确认。
- **[已定] tint = 4 档离散，不做连续映射**。理由：现状 KPI delta 仅 2 硬编码色、risk-list 仅 4 色，4 档已比现状丰富；heat/bar 是 table cell 级迷你可视化，4 档足够区分"很高/较高/中等/偏低"；连续映射需额外值域→插值函数，小尺度无实质收益；离散值单测可精确 assert 颜色 class。
- **[已定] 不新建模板，在现有两个模板内增补新原语片段**。理由：`section-patterns.md` 已 493 行 + 2 模板，再加新模板会增大 AI 选择成本；新旧原语混用更能教 AI"何时用 callout、何时用 narrative"，比"全新原语炫技模板"教学价值高；避免模板膨胀。
