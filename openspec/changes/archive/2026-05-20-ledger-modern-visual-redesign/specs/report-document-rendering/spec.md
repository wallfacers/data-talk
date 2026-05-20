## MODIFIED Requirements

### Requirement: `report.json` schema 与服务端原子写入

系统 SHALL 接收 AI 通过 `datatalk_promote_report` 提交的 `report.json`，原子写入 file artifact 并同步派生 HTML、异步派生 PDF + Markdown。`report.json` schema MUST 包含字段：`schemaVersion` (number)、`kind` ("report")、`meta` ({ title, subtitle?, author?, generatedAt, templateId, templateVersion, userPrompt? })、`theme`、`sections` (array)、`appendix` (array)。

`theme` 字段 SHALL 支持两种形态且**向后兼容**：
- **旧形态（单强调色）**：`{ accent: hex }` —— 历史报告仅含此字段时 MUST 照常渲染。
- **新形态（色彩角色系统）**：`{ primary?: hex, accent?: hex, surface?: hex, tints?: hex[] }` —— 各字段均可选；缺省字段 SHALL 由已提供字段按确定性规则派生（派生规则见 design.md）。`accent` 在两种形态下含义一致，保证旧客户端与旧 fixture 不破。

`datatalk_promote_report` action input schema MUST 含 `report` (object, required)、`workspaceId` (string, required)、`groupId` (string, optional — 缺省时服务端生成新 group）。校验失败时 SHALL 返回 `{ error, errorCode, errorCodes, violations, recoveryHints }` 结构，其中 `errorCode` 取 `errorCodes[0]` 用于向后兼容，`errorCodes` 列出全部违规 code，`violations` 含每条违规的 `{code, path, message}`，`recoveryHints` 是已知 code → 修复提示的映射。校验流程 SHALL collect-all 违规一次性返回，不再 fail-fast。

#### Scenario: 合法 report.json promote 成功

- **GIVEN** AI 提交一份合法 `report.json`（含 `schemaVersion=1`, `kind="report"`, `meta.templateId="ledger.monthly-business-review.v1"`, 至少一个 section）
- **WHEN** `ReportArtifactService.promote(json, workspaceId, groupId?)` 被调用
- **THEN** 服务端在 SQLite `report` 表插入一行
- **AND** `pdf_status = 'processing'`、`md_status = 'processing'`
- **AND** 同步派生 `report.html` 到 file artifact 目录
- **AND** 立即返回 `{ reportId, groupId, version, artifactPaths: { json, html }, pdfStatus: "processing", mdStatus: "processing" }`
- **AND** 异步触发 PDF + Markdown 派生流程

#### Scenario: 旧形态 theme（仅 accent）向后兼容

- **GIVEN** 历史 report.json `theme: { "accent": "#1f4e79" }`（无 primary/surface/tints）
- **WHEN** promote 校验与 HTML 渲染
- **THEN** MUST 接受，不报 theme 相关违规
- **AND** 渲染输出的 CSS 变量中 `--ledger-accent` = `#1f4e79`
- **AND** 缺省的 `--ledger-primary` / `--ledger-surface` 等角色色 MUST 由 accent 按派生规则补齐，渲染不缺色

#### Scenario: 新形态 theme（色彩角色系统）

- **GIVEN** report.json `theme: { "primary": "#0F2A4A", "accent": "#2F6FBF", "surface": "#F4F7FB" }`
- **WHEN** HTML 渲染
- **THEN** 输出 CSS 变量 `--ledger-primary` / `--ledger-accent` / `--ledger-surface` MUST 分别等于上述 hex
- **AND** 未提供的 `tints` MUST 由 `accent` 派生出同色阶梯度

#### Scenario: schemaVersion 不匹配返回 collect-all 违规

- **WHEN** AI 提交 `schemaVersion: 2`（当前仅支持 1），同时 `sections` 为空数组
- **THEN** 返回错误 `{ error, errorCode: "REPORT_SCHEMA_VERSION_UNSUPPORTED", errorCodes: ["REPORT_SCHEMA_VERSION_UNSUPPORTED", "REPORT_SECTIONS_MISSING"], violations: [...], recoveryHints: {...} }`
- **AND** `errorCode` 字段值等于 `errorCodes[0]`（兼容旧客户端）
- **AND** 不写入数据库
- **AND** 不派生任何文件

#### Scenario: kind 字段必须为 "report"

- **WHEN** AI 提交 `kind: "dashboard"` 或缺失
- **THEN** `errorCodes` MUST 含 `"REPORT_KIND_INVALID"`
- **AND** `violations` 中对应条目 `path = "kind"`、`message` 含期望值说明

#### Scenario: 多处违规一次性全部返回

- **WHEN** AI 提交的 report.json 同时缺 `meta.title`、`meta.templateId`、`sections`
- **THEN** `errorCodes` MUST 含全部 3 个 code（`REPORT_META_MISSING` 出现 1-2 次，`REPORT_SECTIONS_MISSING` 1 次）
- **AND** `violations` 数组长度 ≥ 3
- **AND** AI 一轮修齐所有违规后重试 promote MUST 成功

#### Scenario: recoveryHints 含所有 violation code

- **WHEN** promote 失败返回
- **THEN** `recoveryHints` map MUST 对 `errorCodes` 中出现的每个 code 都包含一条修复提示文案
- **AND** 提示文案为非空中文字符串

#### Scenario: 原子写入失败回滚

- **GIVEN** HTML 派生抛 IO 异常（同步路径，发生在响应返回之前）
- **WHEN** `ReportArtifactService.promote(json, ...)` 处理
- **THEN** 系统 MUST 删除已写入的任何中间文件
- **AND** SQLite `report` 表不插入任何行
- **AND** 返回错误 `{ errorCode: "REPORT_RENDER_FAILED" }`

#### Scenario: 异步 PDF 派生失败不影响 HTML 与 Markdown

- **GIVEN** HTML 派生成功，SQLite row 已写入，Markdown 派生成功，PDF 派生超时失败
- **WHEN** 异步任务完成
- **THEN** `pdf_status = 'failed'`、`pdf_fail_reason` 含失败原因
- **AND** `md_status = 'ready'`、HTML/MD 均可下载
- **AND** report 列表查询仍能返回此报告

### Requirement: Section primitive 集合定义

ledger SHALL 定义且仅支持以下 section block 类型，每种类型有固定 schema：
- `cover`（封面：title, subtitle?, author?, date?, logoUrl?；`author` 字段在渲染时 SHALL 经 sanitize 黑名单过滤，命中生成器自指水印的值视为空）
- `executive-summary`（摘要：bullets[]）
- `toc`（目录：渲染时 SHALL 预扫描 sections 收集 chapter heading 输出锚点列表）
- `chapter`（章节容器：heading, blocks[]；渲染时 SHALL 带 `id="chap-{idx}"` 锚点）
- `kpi-strip`（KPI 条带：items[]，3-6 项）
- `narrative`（叙事段落：markdown 文本）
- `chart`（图表：echartsOption, caption, source）
- `table`（表格：columns[], rows[], caption?, source?, appendixCsvRef?；列可选 `cellFormats[]` 声明富单元格修饰）
- `risk-list`（风险/行动项列表：items[{severity, description, owner?, dueDate?}]）
- `timeline`（事件时间线：events[{at, title, description}]）
- `appendix`（附录容器：subType: 'sql-listing' | 'csv-link' | 'glossary'）
- `callout`（高亮/洞察块：variant: 'insight' | 'warning' | 'note' | 'success'，title?, markdown）
- `stat-highlight`（hero 关键指标：value, label, context?, delta?, source?）
- `comparison`（并列对比卡：items[{label, value, caption?}]，2-4 项）
- `quote`（pull-quote：text, attribution?）
- `divider`（章节视觉分隔：label?）

任何不在此集合内的 block 类型 SHALL 在 promote 时被拒绝。新增块（`callout` / `stat-highlight` / `comparison` / `quote` / `divider`）可嵌套在 `chapter.blocks[]` 内，与既有块同级。

#### Scenario: 已知 block 类型接受（含新原语）

- **GIVEN** report 含 cover + executive-summary + chapter(stat-highlight, callout, comparison, quote, divider, kpi-strip, narrative, chart, table)
- **WHEN** promote
- **THEN** 接受

#### Scenario: 旧报告（仅 11 种块）仍接受

- **GIVEN** 历史 report 仅含原 11 种 block 类型，无任何新原语
- **WHEN** promote
- **THEN** 接受，渲染不报错

#### Scenario: 混合形态（旧 theme + 旧块 + 一个新块）

- **GIVEN** report `theme` 仅含旧 `{accent}`，sections 含旧 11 种块**外加**一个 `callout`
- **WHEN** promote 与渲染 HTML
- **THEN** 接受
- **AND** 旧块的渲染路径不受新块存在影响（旧块 DOM 与旧版一致）
- **AND** 新增的 callout 也正确渲染（含 `ledger-callout` class）

#### Scenario: 未知 block 类型拒绝

- **GIVEN** report 某 block `type="video"`（不在集合内）
- **WHEN** promote
- **THEN** `errorCodes` MUST 含 `"REPORT_BLOCK_TYPE_UNKNOWN"`
- **AND** `violations` 中对应条目 `path` 指向该 block 路径（如 `sections[2].blocks[1]`）

#### Scenario: cover author 字段渲染时按黑名单清洗

- **GIVEN** cover block `author="DataTalk 自动生成"`
- **WHEN** 渲染 HTML
- **THEN** 输出 HTML 不含字符串 "DataTalk 自动生成"
- **AND** promote 本身仍接受（不在 validator 拒绝）

#### Scenario: chapter 渲染带锚点 id

- **GIVEN** report 含 3 个 chapter
- **WHEN** 渲染 HTML
- **THEN** 三个 `<section class="ledger-chapter">` 分别带 `id="chap-1"`、`id="chap-2"`、`id="chap-3"`

### Requirement: PDF 分页规则

ledger CSS SHALL 对 `.ledger-chart`, `.ledger-table`, `.ledger-kpi-strip`, `.ledger-callout`, `.ledger-stat-highlight`, `.ledger-comparison`, `.ledger-quote` 类应用 `break-inside: avoid`（卡片/高亮/对比/引用块不得在 A4 分页处被割裂）；包含 30 行以上的 table block SHALL 应用 `break-before: page`（强制起新页）；章节级 `.ledger-chapter` SHALL 在标题与首段间应用 `break-after: avoid`（标题孤行保护）。

#### Scenario: chart 不跨页

- **WHEN** 检查 ledger CSS
- **THEN** 含 `.ledger-chart { break-inside: avoid; }` 规则

#### Scenario: 新增卡片类块不跨页

- **WHEN** 检查 ledger CSS
- **THEN** `.ledger-callout`、`.ledger-stat-highlight`、`.ledger-comparison`、`.ledger-quote` MUST 均含 `break-inside: avoid`

#### Scenario: 长表强制分页

- **GIVEN** 一个 table block rows.length = 50
- **WHEN** 渲染 HTML
- **THEN** 该 table block DOM 节点 MUST 含 CSS class `ledger-table--paged`
- **AND** CSS 规则 `.ledger-table--paged { break-before: page; }` 存在

## ADDED Requirements

### Requirement: theme 色彩角色系统注入 CSS 变量

`ReportRenderer.toHtml` SHALL 把 `theme` 解析为完整色彩角色 token 并以 inline `<style>:root{...}</style>` 注入 CSS 变量，至少包含 `--ledger-primary`、`--ledger-accent`、`--ledger-surface`，以及由 `accent` 派生的同色阶 tint 梯度变量（4 档离散：`--ledger-tint-1` … `--ledger-tint-4`）与语义数据色 `--ledger-positive` / `--ledger-negative` / `--ledger-neutral`。当 `theme` 仅提供部分角色色时，缺省色 SHALL 按确定性派生规则补齐，保证任意合法 theme 都产出完整变量集合，渲染不缺色。所有派生 MUST 是纯函数（同输入同输出），便于测试。

#### Scenario: 完整 theme 注入全部角色变量

- **GIVEN** report.json `theme: { primary, accent, surface }` 三色齐全
- **WHEN** 渲染 HTML
- **THEN** 输出 `<style>` 中 `:root` MUST 同时定义 `--ledger-primary` / `--ledger-accent` / `--ledger-surface`
- **AND** MUST 定义至少一档 tint 变量与三个语义数据色变量

#### Scenario: 仅 accent 时派生其余角色色

- **GIVEN** report.json `theme: { accent: "#1f4e79" }`
- **WHEN** 渲染 HTML
- **THEN** `--ledger-accent` = `#1f4e79`
- **AND** `--ledger-primary` 与 `--ledger-surface` MUST 被派生填充（非空、合法 hex）

#### Scenario: 派生纯函数稳定

- **GIVEN** 同一份 theme
- **WHEN** 两次调用 `toHtml`
- **THEN** 两次输出的 `:root` CSS 变量值 MUST 完全一致

#### Scenario: 用户 tints 超 4 个只注入前 4

- **GIVEN** report.json `theme.tints` 含 6 个 hex
- **WHEN** 渲染 HTML
- **THEN** `:root` MUST 只注入 `--ledger-tint-1` … `--ledger-tint-4`（取前 4 个）
- **AND** 不报错（tints 超 4 不 reject）

#### Scenario: chart 默认调色板来自 theme

- **GIVEN** report 含 chart block，其 `echartsOption` 未显式指定 series `color`
- **WHEN** HTML 在浏览器渲染该 chart
- **THEN** 图表 series 着色 MUST 来自 theme 解析出的调色板（accent 打头），而非 ECharts 内置默认板

#### Scenario: chart 显式 series 色优先于 theme 调色板

- **GIVEN** report 某 chart 的 `echartsOption.series[0].itemStyle.color = "#123456"`
- **WHEN** 渲染该 chart
- **THEN** 该 series MUST 着 `#123456`（报告显式色胜出，theme 调色板不覆盖）

### Requirement: 新增富视觉 block 的 HTML/Markdown 渲染

`ReportRenderer.toHtml` 与 `MarkdownRenderer.toMarkdown` SHALL 渲染 `callout` / `stat-highlight` / `comparison` / `quote` / `divider` 五种新块：HTML 输出对应语义化结构与 CSS class（`ledger-callout` / `ledger-stat-highlight` / `ledger-comparison` / `ledger-quote` / `ledger-divider`），色彩仅引用 theme 注入的 CSS 变量（不得硬编码高饱和霓虹色）；Markdown 输出 SHALL 降级为合理的 GFM 等价物（callout → blockquote 带前缀图标；stat-highlight → 加粗大数字 + 说明行；comparison → GFM 表格；quote → blockquote；divider → `---`）。

#### Scenario: callout 渲染语义结构

- **GIVEN** chapter 含 block `{ type: "callout", variant: "insight", title: "关键洞察", markdown: "GMV 增长主要来自抖音渠道" }`
- **WHEN** 渲染 HTML
- **THEN** 输出 MUST 含 `<... class="ledger-callout ledger-callout--insight">`
- **AND** 块内文字色/底色 MUST 引用 theme CSS 变量（如 `var(--ledger-accent)` / `var(--ledger-surface)`）

#### Scenario: stat-highlight 渲染 hero 数字

- **GIVEN** block `{ type: "stat-highlight", value: "¥3.2M", label: "总 GMV", delta: "+18%", context: "同比" }`
- **WHEN** 渲染 HTML
- **THEN** 输出 MUST 含 class `ledger-stat-highlight`，value 以最大字号呈现
- **AND** delta 为正值时着 `--ledger-positive` 色

#### Scenario: comparison 降级为 Markdown 表格

- **GIVEN** block `{ type: "comparison", items: [{label:"自营",value:"1.2M"},{label:"抖音",value:"0.8M"}] }`
- **WHEN** 渲染 Markdown
- **THEN** 输出 MUST 含 GFM 表格表示两个对比项

#### Scenario: divider 渲染分隔

- **GIVEN** block `{ type: "divider", label: "下半年展望" }`
- **WHEN** 渲染 HTML 与 Markdown
- **THEN** HTML 含 class `ledger-divider`；Markdown 含 `---` 分隔线

### Requirement: table 富单元格格式渲染

`table` block 的列 SHALL 支持可选 `cellFormats[]`（**若存在**，长度 MUST 等于 `columns.length`，每项 MUST ∈ {`text`(默认), `bar`(内嵌迷你条形), `delta`(±箭头着色), `heat`(热力底色)}；违反 SHALL 在 promote 时被拒绝，违规 code `REPORT_TABLE_CELLFORMAT_INVALID`）。`ReportRenderer` 渲染 HTML 时 SHALL 按列 cellFormat 修饰单元格：`bar` 在单元格内叠加宽度正比于数值的条形（色用 theme tint）；`delta` 对含 +/- 的值着 `--ledger-positive` / `--ledger-negative`；`heat` 按数值大小映射底色深浅（同色阶）。未声明 `cellFormats` 时 SHALL 退化为纯文本表格（与旧行为完全一致）。Markdown 渲染 SHALL 忽略富修饰、输出纯文本 GFM 表格。

#### Scenario: 未声明 cellFormats 退化为纯文本表格

- **GIVEN** table block 无 `cellFormats` 字段
- **WHEN** 渲染 HTML
- **THEN** 输出与旧版纯文本表格一致，单元格不含 bar/heat 修饰 DOM

#### Scenario: cellFormats 长度或取值非法被拒绝

- **GIVEN** table block `columns` 长度为 4，但 `cellFormats` 长度为 3（或含非法值 `"sparkline"`）
- **WHEN** promote 校验
- **THEN** `errorCodes` MUST 含 `"REPORT_TABLE_CELLFORMAT_INVALID"`
- **AND** `violations` 对应条目 `path` 指向该 table block 的 `cellFormats`

#### Scenario: delta 列着色

- **GIVEN** table block 某列 `cellFormats[i]="delta"`，该列值含 `"+18%"` 与 `"-5%"`
- **WHEN** 渲染 HTML
- **THEN** `+18%` 单元格着 `--ledger-positive` 色，`-5%` 着 `--ledger-negative` 色

#### Scenario: Markdown 忽略富修饰

- **GIVEN** table block 含 `cellFormats` 含 `bar` / `heat`
- **WHEN** 渲染 Markdown
- **THEN** 输出为纯文本 GFM 表格，不含任何条形/热力标记
