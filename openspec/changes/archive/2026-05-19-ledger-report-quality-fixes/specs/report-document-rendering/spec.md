## ADDED Requirements

### Requirement: TOC 渲染为可跳转的章节锚点目录

`ReportRenderer.toHtml` SHALL 在遇到 `toc` block 时输出一个含完整章节链接的有序列表：预扫描 `sections[]`，按文档顺序收集所有 `chapter` block，为每个 chapter 分配 1-based 索引 `idx`，TOC 输出 `<section class="ledger-toc"><h2>目录</h2><ol><li><a href="#chap-{idx}">{heading}</a></li>...</ol></section>`；`renderChapter` 同步给每个 chapter 的 `<section>` 标签加 `id="chap-{idx}"` 锚点。`MarkdownRenderer.toMarkdown` 在遇到 `toc` block 时 SHALL 输出 GFM 风格目录：`## 目录\n- [{heading}](#{heading-slug})\n...`。

#### Scenario: HTML TOC 输出章节链接

- **GIVEN** report `sections` 顺序为 `cover`, `executive-summary`, `toc`, `chapter(heading="业务总览")`, `chapter(heading="区域分析")`, `chapter(heading="渠道表现")`
- **WHEN** 渲染 HTML
- **THEN** 输出 HTML MUST 含 `<section class="ledger-toc">`
- **AND** 该 section 内 MUST 含 `<a href="#chap-1">业务总览</a>`、`<a href="#chap-2">区域分析</a>`、`<a href="#chap-3">渠道表现</a>` 三个链接
- **AND** 三个 chapter 的 `<section>` 标签分别带 `id="chap-1"`、`id="chap-2"`、`id="chap-3"`

#### Scenario: 无 chapter 时 TOC 仍渲染但列表为空

- **GIVEN** report `sections` 仅含 `cover`, `executive-summary`, `toc`，无 `chapter`
- **WHEN** 渲染 HTML
- **THEN** 输出 HTML MUST 含 `<section class="ledger-toc"><h2>目录</h2><ol></ol></section>`
- **AND** 不报错

#### Scenario: Markdown TOC 输出 GFM 锚点

- **GIVEN** report 同上含 3 个 chapter
- **WHEN** 渲染 Markdown
- **THEN** 输出 Markdown MUST 含 `## 目录`
- **AND** 后续 MUST 含 `- [业务总览](#业务总览)`、`- [区域分析](#区域分析)`、`- [渠道表现](#渠道表现)`

#### Scenario: TOC 在 PDF 中带可点击锚点

- **GIVEN** report 含 3 个 chapter，渲染为 PDF
- **WHEN** 在 PDF reader 中点击目录条目
- **THEN** PDF MUST 跳转到对应章节首页

### Requirement: cover.author 水印 sanitize

`ReportRenderer.renderCover` 与 `MarkdownRenderer` 渲染 cover block 时 SHALL 对 `author` 字段做 normalize：trim 后若匹配生成器自指水印黑名单（不区分大小写正则：`^(datatalk[\s·-]?(自动生成|生成|auto[\s-]?generated)|auto[\s-]?generated|ai\s?生成|自动生成|系统生成|generated\s+by\s+.*)$`），MUST 视为空字符串处理；命中时 MUST 记 warn 日志含原始 author 值。sanitize 只作用在渲染层，不影响 `meta.author` 入库值，也不在 validator 拒绝。

#### Scenario: cover author 命中黑名单被清空

- **GIVEN** report cover block `{"type":"cover","title":"X","author":"DataTalk 自动生成","date":"2026-05-19"}`
- **WHEN** 渲染 HTML
- **THEN** 输出 HTML 中 `<p class="ledger-cover__meta">` MUST 不含字符串 "DataTalk 自动生成"
- **AND** meta 仅含 date 字符串 "2026-05-19"
- **AND** 服务端日志含一条 warn 包含原始 author 值

#### Scenario: cover author 为合法人名时保留

- **GIVEN** report cover block `{"type":"cover","title":"X","author":"数据分析团队","date":"2026-05-19"}`
- **WHEN** 渲染 HTML
- **THEN** 输出 HTML 中 `<p class="ledger-cover__meta">` MUST 含 "数据分析团队 · 2026-05-19"

#### Scenario: meta.author 入库值不被 sanitize

- **GIVEN** report meta `{"author":"DataTalk 自动生成", ...}`
- **WHEN** promote 成功
- **THEN** SQLite `report` 表对应行的 author 字段 MUST 保留原值 "DataTalk 自动生成"
- **AND** 仅 HTML/Markdown 渲染输出中被清空

#### Scenario: Markdown cover author 同样 sanitize

- **GIVEN** report cover `author="自动生成"`
- **WHEN** 渲染 Markdown
- **THEN** 输出 Markdown meta 行 MUST 不含 "自动生成"

### Requirement: `_assets/**` 端点 CORS 头

`ReportController.serveAsset` 处理 `GET /api/reports/_assets/**` 时 SHALL 在响应头中包含 `Access-Control-Allow-Origin: *` 与 `Vary: Origin`，使得 Report Viewer iframe（`sandbox=allow-scripts srcdoc`，origin=`null`）能正常加载字体、样式、脚本资源。此 CORS 头 SHALL 仅作用于 `/api/reports/_assets/**` 路径，不影响其他 API 端点。

#### Scenario: 字体响应含 CORS 头

- **WHEN** `GET /api/reports/_assets/fonts/NotoSerifSC-Regular.otf`
- **THEN** 响应头 MUST 含 `Access-Control-Allow-Origin: *`
- **AND** 响应头 MUST 含 `Vary: Origin`
- **AND** Content-Type 仍为 `font/otf`

#### Scenario: CSS 响应含 CORS 头

- **WHEN** `GET /api/reports/_assets/styles/ledger.css`
- **THEN** 响应头 MUST 含 `Access-Control-Allow-Origin: *`
- **AND** 响应头 MUST 含 `Vary: Origin`
- **AND** Content-Type 仍为 `text/css`

#### Scenario: ECharts JS 响应含 CORS 头

- **WHEN** `GET /api/reports/_assets/scripts/echarts.min.js`
- **THEN** 响应头 MUST 含 `Access-Control-Allow-Origin: *`
- **AND** Content-Type 仍为 `application/javascript`

#### Scenario: iframe srcdoc 加载字体无 CORS 错误

- **GIVEN** Report Viewer 加载已渲染报告 HTML 到 `<iframe sandbox="allow-scripts" srcdoc="...">` 中
- **WHEN** iframe DOM 触发 `@font-face` 加载 `/api/reports/_assets/fonts/NotoSerifSC-Regular.otf`
- **THEN** 浏览器 console MUST NOT 含 CORS error
- **AND** 报告正文 MUST 使用思源宋体渲染（非系统默认字体回退）

#### Scenario: 其他 API 端点不受影响

- **WHEN** `GET /api/reports/{id}/download/html`
- **THEN** 响应头 MUST NOT 自动新增 `Access-Control-Allow-Origin: *`（CORS 仅限 _assets 路径）

## MODIFIED Requirements

### Requirement: `report.json` schema 与服务端原子写入

系统 SHALL 接收 AI 通过 `datatalk_promote_report` 提交的 `report.json`，原子写入 file artifact 并同步派生 HTML、异步派生 PDF + Markdown。`report.json` schema MUST 包含字段：`schemaVersion` (number)、`kind` ("report")、`meta` ({ title, subtitle?, author?, generatedAt, templateId, templateVersion, userPrompt? })、`theme` ({ accent: hex color })、`sections` (array)、`appendix` (array)。`datatalk_promote_report` action input schema MUST 含 `report` (object, required)、`workspaceId` (string, required)、`groupId` (string, optional — 缺省时服务端生成新 group）。校验失败时 SHALL 返回 `{ error, errorCode, errorCodes, violations, recoveryHints }` 结构，其中 `errorCode` 取 `errorCodes[0]` 用于向后兼容，`errorCodes` 列出全部违规 code，`violations` 含每条违规的 `{code, path, message}`，`recoveryHints` 是已知 code → 修复提示的映射。校验流程 SHALL collect-all 违规一次性返回，不再 fail-fast。

#### Scenario: 合法 report.json promote 成功

- **GIVEN** AI 提交一份合法 `report.json`（含 `schemaVersion=1`, `kind="report"`, `meta.templateId="ledger.monthly-business-review.v1"`, 至少一个 section）
- **WHEN** `ReportArtifactService.promote(json, workspaceId, groupId?)` 被调用
- **THEN** 服务端在 SQLite `report` 表插入一行
- **AND** `pdf_status = 'processing'`、`md_status = 'processing'`
- **AND** 同步派生 `report.html` 到 file artifact 目录
- **AND** 立即返回 `{ reportId, groupId, version, artifactPaths: { json, html }, pdfStatus: "processing", mdStatus: "processing" }`
- **AND** 异步触发 PDF + Markdown 派生流程

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
- `table`（表格：columns[], rows[], caption?, source?, appendixCsvRef?）
- `risk-list`（风险/行动项列表：items[{severity, description, owner?, dueDate?}]）
- `timeline`（事件时间线：events[{at, title, description}]）
- `appendix`（附录容器：subType: 'sql-listing' | 'csv-link' | 'glossary'）

任何不在此集合内的 block 类型 SHALL 在 promote 时被拒绝。

#### Scenario: 已知 block 类型接受

- **GIVEN** report 含 cover + executive-summary + chapter(kpi-strip, narrative, chart, table)
- **WHEN** promote
- **THEN** 接受

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
