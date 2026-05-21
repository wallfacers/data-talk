## 1. 色彩角色系统与派生（基础，先行）

- [x] 1.1 在 application 层新增 `LedgerThemePalette`（解析 theme JSON → 完整角色 token）与 sRGB 混合派生工具（纯函数 `mix(hex, hex, ratio)`、`deriveFromAccent`、`tints`），覆盖 D1 默认 token 与派生规则
- [x] 1.2 为派生工具写单测：仅 accent 派生 primary/surface/tints、完整 theme 透传、同输入同输出稳定、非法 hex 处理
- [x] 1.3 apply 前用 AskUserQuestion 确认默认色彩 token（深蓝默认 vs 多预设），落定 design.md Open Question

## 2. Validator 扩展

- [x] 2.1 `ReportSchemaValidator`：`ALLOWED_BLOCK_TYPES` 增 `callout`/`stat-highlight`/`comparison`/`quote`/`divider`
- [x] 2.2 新增各新块必填字段校验（callout.variant 枚举、stat-highlight.value、comparison.items 长度 2-4、quote.text）+ table `cellFormats` 校验（若存在：长度必须 = `columns.length`，每项 ∈ {text,bar,delta,heat}，违规 `REPORT_TABLE_CELLFORMAT_INVALID`）+ 对应 `RECOVERY_HINTS`
- [x] 2.3 theme 字段 hex 格式校验（accent/primary/surface/tints[]），任意子集合法、缺省不报错；`tints` 软上限 ≤ 8（超限 warn 不 reject）
- [x] 2.4 `ReportSchemaValidatorTest`：新块接受、字段缺失拒绝（collect-all）、旧 11 块仍接受、旧 `{accent}` theme 接受、新调色板 theme 接受、`cellFormats` 长度不符/非法值拒绝、`tints` 超 8 仅 warn 不 reject

## 3. HTML 渲染器（ReportRenderer）

- [x] 3.1 `injectThemeVars(theme)`：注入 `:root` CSS 变量（primary/accent/surface/tint-1..4/positive/negative/neutral），缺省经 §1 派生补齐；用户 `tints` 取前 4 个、不足由 accent 派生补齐；Java 缺省 accent 由 `#1f4e79` 改为 `#2F6FBF`（`ReportRenderer.java:78`）
- [x] 3.2 实现 `renderCallout`/`renderStatHighlight`/`renderComparison`/`renderQuote`/`renderDivider`，色彩仅引用 CSS 变量
- [x] 3.3 `renderTable` 支持列级 `cellFormats`（text/bar/delta/heat），未声明退化为纯文本；数值解析失败安全回退
- [x] 3.4 `renderChartBootstrapScript`：在 `initOne` 中 setOption 前注入 8 色 PALETTE（`[accent,primary,tint-2,tint-3,positive,negative,neutral,tint-1]` 解析为字面 hex），`inst.setOption({color:PALETTE},false)` 先于 `inst.setOption(c.option)`——报告显式 series 色仍胜出

## 4. Markdown 渲染器（MarkdownRenderer）

- [x] 4.1 新增 5 种新块的 GFM 降级分支（callout→blockquote+**emoji+加粗中文标签**`> 💡 **洞察**：…`/`> ⚠️ **警告**：…`/`> 📝 **提示**：…`/`> ✅ **成功**：…；stat-highlight→加粗数字行、comparison→GFM 表、quote→blockquote、divider→`---`）
- [x] 4.2 table 渲染忽略 `cellFormats`，始终输出纯文本 GFM 表

## 5. CSS 扩展与硬编码颜色重构（ledger.css，非从零重写）

- [x] 5.1 **扩展而非重写**：保留既有 ~400 行（`@font-face` 本地路径、所有 `break-*`、`::-webkit-scrollbar-*` 含 button display:none、`@media print` 白底、既有 block 样式）；`:root` 新增 `--ledger-primary`/`--ledger-surface`/`--ledger-tint-1..4`/`--ledger-positive/negative/neutral`，**默认值 = D1 token 表**（`--ledger-primary:#0F2A4A`、`--ledger-accent:#2F6FBF`、`--ledger-surface:#F4F7FB`、`--ledger-positive:#1F7A4E`、`--ledger-negative:#B33A3A`、`--ledger-neutral:#5E5E5B`、`--ledger-tint-1..4` 按 D1 从 accent 派生的具体 hex），使仅内联了 `--ledger-accent` 的存量 HTML 在新变量上回退到协调默认；把 KPI delta 与 risk-list 的硬编码 severity 色重构为 `var(--ledger-*)` 引用
- [x] 5.2 新增 `.ledger-callout(--insight/--warning/--note/--success)`、`.ledger-stat-highlight`、`.ledger-comparison`、`.ledger-quote`、`.ledger-divider` 样式，全部 `break-inside: avoid`
- [x] 5.3 富单元格样式：`bar`/`delta`/`heat` 的 cell 修饰类
- [x] 5.4 确认规则体内无裸 hex（颜色一律 `var(--ledger-*)`）：跑 `grep -nP '#[0-9a-fA-F]{6}' ledger.css`，逐条确认**每个命中都落在 `:root{}` token 定义块内**（角色色/语义色/文字色阶的变量声明处),规则体（选择器 `{}` 内的 property 值）MUST NOT 出现裸 hex；如有则改为 `var(--ledger-*)`

## 6. Skill 文档与模板

- [x] 6.1 改写 `design-language.md`：色彩角色系统 + 默认 token + 6 级字号阶 + 富视觉原语规范 + 保留 bezel 边界说明
- [x] 6.2 扩展 `section-patterns.md`：原 11 + 新 5 = 16 种块的 schema/HTML/分页；table `cellFormats` schema 与示例
- [x] 6.3 `SKILL.md`：新增"数据叙事撰写规则"（论点挂证据、一图一观点、视觉服务结论禁数据倾倒、渐进披露）+ 引导使用新原语
- [x] 6.4 `templates/monthly-business-review.md` 与 `incident-postmortem.md`：示例 JSON 增补新原语片段作为 few-shot

## 7. 验证与回归

- [x] 7.1 `cd server && mvn install -pl data-talk-application -am -DskipTests` 后跑 `ReportRendererTest`/`ReportSchemaValidatorTest`/`LedgerReportE2EFixtureGeneratorTest`/`LedgerCssScrollbarContractTest` 全绿
- [x] 7.2 `mvn clean verify` 全套测试通过
- [x] 7.3 用 playwright-cli 端到端跑：AI chat→promote→Report Library→Report Viewer，实看 HTML 与 PDF 视觉效果；确认 BUG-0073/74/75/76/77 不复发；任何偏差按 BUG Gate 建档并在最终响应报告 "Found N BUGs…"
- [x] 7.4 向后兼容验证：(a) 仅含 `theme.accent` + 旧 11 种块的历史 fixture 重渲染零回归；(b) **混合形态 fixture**：旧 `theme.accent` + 旧 11 块 + 新增一个 `callout`，确认旧渲染路径不受新块存在影响、新块也正确渲染
