## Why

ledger 当前的视觉契约（`design-language.md` 第 9 章禁用清单）刻意把汇报文档锁死成保守的"麦肯锡黑白印刷品"：单一强调色 + 黑灰、纯线性段落流、禁卡片/高亮/多色。这套设计安全但表现力弱，难以承载"有理有据、视觉上有层次"的现代数据汇报。本次有意识地推翻该禁用清单,借鉴 Manus 数据报告的设计 DNA（角色化色彩系统、富视觉原语、数据可视化多样性、证据驱动叙事），把 ledger 升级为现代化、富表现力且仍可打印归档的报告美学。

## What Changes

- **BREAKING（视觉契约）**：改写 `design-language.md`，用"现代报告设计语言"替换保守禁用清单。核心是把"单一 `accent` + 黑灰"换成**色彩角色系统**：`primary`（专业主色）/ `accent`（创新强调）/ `surface`（干净背景层）+ 语义数据色（正向/负向/中性）。丰富感来自同色阶层级与留白节奏，而非高饱和霓虹。
- **theme 调色板扩展**：`report.json` 的 `theme` 由单 `accent` 字段扩展为可选调色板（`primary` / `accent` / `surface` / `tints`）；旧报告只含 `theme.accent` 时仍能校验并连贯渲染（缺省派生其余角色色）。
- **默认 accent 变更 + 有意的视觉刷新**：缺省 accent 由 `#1f4e79` 改为 `#2F6FBF`；且因存量报告 HTML 通过 `<link>` **实时引用共享 `ledger.css`**，重写后所有存量报告的结构性视觉都会刷新。本变更**明确承认这是一次有意的视觉刷新**——兼容承诺为**功能性**（旧 JSON 仍校验/无错渲染/布局连贯/显式 `theme.accent` 被尊重），**非像素级**（不承诺旧报告外观不变）。
- **新增 section block 原语**：`callout`（key-insight 高亮块）、`stat-highlight`（hero 关键指标）、`comparison`（并列对比卡）、`quote`（pull-quote）、`divider`（章节视觉分隔）。扩展 `ALLOWED_BLOCK_TYPES`。
- **富表格单元格**：`table` 列支持可选 cell 修饰（内嵌迷你条形 / delta 箭头 / 热力底色），通过列级 `cellFormat` 声明，不破坏纯文本表格的向后兼容。
- **数据可视化多样化**：扩展受控 ECharts 图表类型与协调配色板，由 theme 调色板驱动。
- **证据驱动叙事（"有理有据"）**：强化 论点→证据→来源 链条；`SKILL.md` 新增数据叙事撰写规则（"一图一观点"、渐进披露、视觉服务于结论、禁数据倾倒）；可选方法论/假设表达。
- **重写 `ledger.css`** 实现新设计语言；**改 `ReportRenderer.java` / `MarkdownRenderer.java`** 渲染新块与富表格；扩展 `section-patterns.md` 给出新原语 schema + HTML 示例 + PDF 分页规则。
- **更新 `SKILL.md` + `templates/*.md`**：让 AI 善用新原语并落实叙事/证据规则。

## Capabilities

### New Capabilities
- （无）本次不引入新 capability。视觉与块的变更属于现有 report 渲染 / 数据契约的需求修改。

### Modified Capabilities
- `report-document-rendering`: 视觉设计语言由"保守印刷品"改为"现代报告美学"；新增 `callout` / `stat-highlight` / `comparison` / `quote` / `divider` 块的 HTML/PDF/Markdown 渲染需求；`table` 富单元格渲染；`Section primitive 集合定义`扩容；新增基于 theme 调色板的 CSS 变量注入。保留：自包含无外网依赖、`__LEDGER_READY__` 信号、Playwright PDF、字体本地打包、TOC 锚点、cover.author sanitize、`_assets` CORS、滚动条按钮隐藏、PDF 分页正确性。
- `report-data-contract`: `theme` schema 由单 `accent` 扩展为调色板（向后兼容）；`ALLOWED_BLOCK_TYPES` 扩容并在 validator 中校验新块字段；`design-language.md` 需求由"印刷品保守禁用清单"改为"现代报告设计语言（色彩角色系统）"；`section-patterns.md` 由 11 种块扩展为含新原语；`SKILL.md` 新增数据叙事撰写规则与证据链要求。

## Impact

- **资源文件**：`server/data-talk-adapter/src/main/resources/skills/ledger/`：`assets/styles/ledger.css`、`design-language.md`、`section-patterns.md`、`SKILL.md`、`data-contract.md`、`templates/monthly-business-review.md`、`templates/incident-postmortem.md`。
- **应用层**：`ReportRenderer.java`（新块/富表格 HTML + theme 调色板 CSS 变量注入）、`MarkdownRenderer.java`（新块的 Markdown 降级渲染）、`ReportSchemaValidator.java`（`ALLOWED_BLOCK_TYPES` 扩容 + theme 调色板 + 新块字段校验）。
- **测试**：`ReportRendererTest`、`ReportSchemaValidatorTest`、`LedgerReportE2EFixtureGeneratorTest`、`LedgerCssScrollbarContractTest`（滚动条契约必须保留通过）。
- **资源同步契约**：`report-data-contract` 的"ledger skill 同步与目录结构"需求仍要求上述文件存在，文件名不变、内容变更，会触发 `SkillResourceSyncer` 重新同步（SHA-256 marker 失效）。
- **数据源类型兼容性**：本变更不触碰数据库/数据源类型、JDBC、schema discovery、SQL 执行或方言处理 —— 仅改报告渲染与 skill 文档。Data Source Type Compatibility Gate 标记 **N/A**（理由：纯报告视觉/契约层改动，不涉及任何 connection/dialect 行为）。
- **client/ DESIGN 契约**：本变更不改 `client/` 任何 UI；报告 HTML 由服务端渲染、在 Report Viewer iframe 内展示。Frontend Design Contract Gate 标记 **N/A**（理由：无 `client/` 源码改动）。

## Risks / Known Issues

- **回归风险（已修 BUG 区域）**：report 渲染区域近期修过多个 BUG，重写 CSS / renderer 时 MUST 不回归：
  - BUG-0073（字体 CORS）、BUG-0074（滚动条三角按钮隐藏 —— `LedgerCssScrollbarContractTest` 守护，新 CSS 必须保留 `::-webkit-scrollbar-button { display: none }`）、BUG-0075（iframe 重复加载）、BUG-0076（Ctrl+R 空白）、BUG-0077（TOC 锚点 base-href 冲突）。
- **PDF 分页风险**：新增卡片/对比/高亮块需正确设置 `break-inside: avoid`，否则 A4 分页处出现半块割裂。
- **向后兼容风险（功能性）**：历史报告（仅 `theme.accent`、仅旧 11 种块）MUST 仍能校验 + 无错渲染 + 布局连贯；调色板派生与块集合扩容 MUST 不破坏旧 fixture。注意：因共享 `ledger.css`，存量报告外观会随之刷新（有意，见上）——风险点是"破坏渲染/布局"，而非"外观改变"。
- **审美主观性**：色彩角色系统的默认 token 值需在 design.md 收敛确认，避免 apply 阶段反复返工。
