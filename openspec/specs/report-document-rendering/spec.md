# report-document-rendering Specification

## Purpose
TBD - created by archiving change report-document-generation. Update Purpose after archive.
## Requirements
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

### Requirement: HTML 派生自包含且无外网依赖

`ReportRenderer.toHtml(reportJson)` SHALL 输出一份自包含 HTML：所有 CSS、字体、ECharts 库、图表数据均内联或引用 ledger skill 解压目录下的本地资源；HTML 不得包含任何 `http://` / `https://` 外网 URL（除 `<a href>` 链接和 SVG `xmlns` namespace 之外）。

#### Scenario: HTML 不含外网资源引用

- **WHEN** 对任一 fixture `report.json` 调用 `toHtml`
- **THEN** 输出 HTML 中 `<link rel="stylesheet" href="...">` 和 `<script src="...">` 的 `href`/`src` MUST 全部以相对路径或 `file://` 开头
- **AND** `@font-face` 的 `src` MUST 指向 ledger skill 的本地字体路径
- **AND** ECharts library MUST 内联 `<script>` 或引用本地路径

#### Scenario: HTML 引用本地中文字体

- **GIVEN** report 含中文标题与正文
- **WHEN** 渲染 HTML
- **THEN** HTML `<style>` 块 MUST 含 `@font-face { font-family: 'NotoSerifSC'; src: url('...') }` 声明
- **AND** body 默认 font-family MUST 含中文字体 fallback 链（如 `'NotoSerifSC', 'PingFang SC', serif`）

### Requirement: ECharts 静态渲染等待 `finished` 信号

ledger HTML 内嵌的 JS SHALL 在所有 ECharts 实例的 `finished` 事件触发后设置 `window.__LEDGER_READY__ = true`；PdfRenderer SHALL 在调用 `page.pdf()` 前用 `page.waitForFunction(() => window.__LEDGER_READY__, { timeout: 30000 })` 同步等待。

#### Scenario: 无 chart 时 LEDGER_READY 立即置位

- **GIVEN** report 仅含 narrative 与 table，无 chart
- **WHEN** HTML 加载到浏览器
- **THEN** `window.__LEDGER_READY__` MUST 在 DOMContentLoaded 后立即 = true

#### Scenario: 多 chart 全部 finished 后置位

- **GIVEN** report 含 3 个 chart
- **WHEN** HTML 加载到浏览器
- **THEN** `window.__LEDGER_READY__` MUST 在 3 个 chart 的 `finished` 事件均触发后才 = true

#### Scenario: chart 渲染超时 PDF 仍生成

- **GIVEN** ECharts 因数据异常导致 `finished` 30 秒未触发
- **WHEN** `PdfRenderer.render()` 处理
- **THEN** waitForFunction 超时后 MUST 继续 `page.pdf()`（保留 fallback）
- **AND** PDF 文件仍输出
- **AND** report 记录 `pdfRenderWarning: "chart_finished_timeout"` 字段

### Requirement: PDF 服务端 Playwright Java SDK 渲染

`PdfRenderer.render(htmlPath, reportId)` SHALL 使用 Playwright Java SDK（`com.microsoft.playwright`）从共享 Chromium 实例新建 page，加载 HTML 后 `page.waitForFunction("() => window.__LEDGER_READY__", new WaitForFunctionOptions().setTimeout(30_000))`，再 `page.pdf(new PagePdfOptions().setFormat("A4").setPrintBackground(true).setMargin(new Margin().setTop("20mm").setBottom("20mm").setLeft("18mm").setRight("18mm")))`；输出 PDF 路径写入 `report.artifact_paths_json.pdf`；状态通过 `report.pdf_status` 字段反映（`processing` / `ready` / `failed`）。

#### Scenario: PDF 渲染成功

- **GIVEN** Chromium 已就绪，HTML 文件存在
- **WHEN** `PdfRenderer.render(htmlPath, reportId)` 被调用
- **THEN** 输出 PDF 文件到 file artifact 目录
- **AND** 更新 `report.pdf_status = 'ready'`
- **AND** 更新 `report.artifact_paths_json.pdf = <path>`

#### Scenario: Chromium 未就绪时 PDF 延迟

- **GIVEN** 服务端启动期 `ChromiumLifecycle.start()` 尚未完成
- **WHEN** 用户调 `POST /api/reports/{id}/pdf-render`
- **THEN** 返回 `{ status: "system_not_ready", message: "PDF renderer initializing", retryAfterSec: 60 }`
- **AND** report `pdf_status` 保持原值（不变更）

#### Scenario: 渲染超时失败

- **GIVEN** Playwright 60 秒未返回
- **WHEN** `PdfRenderer.render()` 超时
- **THEN** 关闭 page 与 context 释放资源
- **AND** 更新 `report.pdf_status = 'failed'`
- **AND** 记录失败原因 `pdf_fail_reason = "timeout"`

### Requirement: Markdown 中 chart PNG 复用 Chromium 截图

`ChartCaptureRenderer.captureChartsToPng(htmlPath, reportId)` SHALL 复用与 PDF 同一个 Chromium 实例：加载报告 HTML，`waitForFunction(window.__LEDGER_READY__)` 后对每个 chart 容器 DOM (`[data-ledger-chart-id]`) 调用 `Locator.screenshot(new LocatorScreenshotOptions().setOmitBackground(false))` 输出 PNG。PNG 落 file artifact `kind='report-asset'`，文件名 `chart-<blockId>.png`。`MarkdownRenderer` 在收到全部 chart PNG 路径后才生成最终 Markdown 文本并写入 file artifact，更新 `md_status = 'ready'`。截图视口宽度固定 1200px @ 2x device pixel ratio。

#### Scenario: chart 截图与 Markdown 派生成功

- **GIVEN** report 含 2 个 chart block
- **WHEN** 异步派生流程完成
- **THEN** file artifact 目录下 MUST 存在 `chart-<id-1>.png` 与 `chart-<id-2>.png`，文件非零字节
- **AND** Markdown 文本中含 `![<caption-1>](./assets/chart-<id-1>.png)` 与 `![<caption-2>](./assets/chart-<id-2>.png)`
- **AND** `report.md_status = 'ready'`

#### Scenario: 单 chart 截图失败不影响 Markdown 主体

- **GIVEN** report 含 3 个 chart，截图第 2 个时抛异常
- **WHEN** 异步派生流程
- **THEN** 第 1、3 个 chart PNG 正常输出
- **AND** Markdown 中第 2 个 chart 位置写入占位 `> ⚠️ 图表渲染失败：<caption>`
- **AND** `report.md_status = 'ready'`（部分失败仍 ready）
- **AND** `report.md_fail_reason = null`（部分失败不算整体失败）

#### Scenario: Markdown 派生整体超时

- **GIVEN** ChartCaptureRenderer 60 秒未完成
- **WHEN** 异步派生超时
- **THEN** 更新 `report.md_status = 'failed'`
- **AND** 记录 `md_fail_reason = "timeout"`
- **AND** HTML 与 PDF 状态独立判定，不受影响

### Requirement: Markdown 文本块派生规则

`MarkdownRenderer.toMarkdown(reportJson, chartPngPaths)` SHALL 把各 block 类型渲染为对应 Markdown 结构：narrative block → 输出 Markdown 原文；table block → GFM table；KPI strip → 1 行 header + 1 行 value 的 GFM table；chart block → `![caption](./assets/chart-<id>.png)`（PNG 路径来自 `ChartCaptureRenderer` 输出）；timeline block → 有序 list（每项 `- **<at>** — <title>: <description>`）；risk-list block → 无序 list（每项含 severity emoji + description）。

#### Scenario: table block 渲染 GFM 表格

- **GIVEN** table block: `columns=['渠道','GMV','同比'], rows=[['自营','1.2M','+15%'],['抖音','0.8M','+28%']]`
- **WHEN** 渲染 Markdown
- **THEN** 输出含字符串 `| 渠道 | GMV | 同比 |`
- **AND** 含 `| --- | --- | --- |`（GFM 分隔行）
- **AND** 含 `| 自营 | 1.2M | +15% |`

#### Scenario: KPI strip 渲染为单行 table

- **GIVEN** kpi-strip block: `items=[{label:'GMV',value:'1.2M',delta:'+12%'},{label:'订单',value:'12,345',delta:'+8%'}]`
- **WHEN** 渲染 Markdown
- **THEN** 输出 1 行 header + 1 行 value 的 GFM table
- **AND** value 行含 delta 标注（如 `1.2M ↑12%`）

#### Scenario: chart block 引用 PNG 路径

- **GIVEN** chart block id="ch-1"，已有 `chart-ch-1.png` 文件
- **WHEN** `MarkdownRenderer.toMarkdown(json, { "ch-1": "./assets/chart-ch-1.png" })`
- **THEN** 输出 Markdown 含 `![<caption>](./assets/chart-ch-1.png)`

### Requirement: 大表 inline 上限与附录 CSV 派生

单个 `table` block 的 `rows` 数组 SHALL 不超过 200 行；超过时 AI MUST 在 `report.json` 中只 inline 前 N 行（N ≤ 200），并在该 block 旁标注 `appendixCsvRef: "<csvId>"`，对应的完整数据 CSV 文件 SHALL 通过 file artifact 持久化（`kind='report-data-csv'`，与 report 主 artifact 同生命周期）。

#### Scenario: 200 行以内不需要附录

- **GIVEN** table block rows.length = 150
- **WHEN** AI promote report
- **THEN** rows 全量 inline
- **AND** 不要求 `appendixCsvRef` 字段

#### Scenario: 超过 200 行强制 inline 截断 + 附录

- **GIVEN** AI 提交 table block rows.length = 500
- **WHEN** `ReportArtifactService.promote()` 校验
- **THEN** 若缺少 `appendixCsvRef` 字段 MUST 拒绝并返回 `{ errorCode: "REPORT_TABLE_OVERSIZE_NO_APPENDIX" }`
- **AND** 若含 `appendixCsvRef`，且 file artifact 中存在对应 CSV，则接受
- **AND** HTML 渲染时 table 下方 MUST 出现 "完整数据见附录 CSV" 文本与下载链接

### Requirement: PDF 分页规则

ledger CSS SHALL 对 `.ledger-chart`, `.ledger-table`, `.ledger-kpi-strip` 类应用 `break-inside: avoid`；包含 30 行以上的 table block SHALL 应用 `break-before: page`（强制起新页）；章节级 `.ledger-section` SHALL 在标题与首段间应用 `break-after: avoid`（标题孤行保护）。

#### Scenario: chart 不跨页

- **WHEN** 检查 ledger CSS
- **THEN** 含 `.ledger-chart { break-inside: avoid; }` 规则

#### Scenario: 长表强制分页

- **GIVEN** 一个 table block rows.length = 50
- **WHEN** 渲染 HTML
- **THEN** 该 table block DOM 节点 MUST 含 CSS class `ledger-table--paged`
- **AND** CSS 规则 `.ledger-table--paged { break-before: page; }` 存在

### Requirement: 中文字体打包到 ledger skill 资源

ledger skill 解压后 `assets/fonts/` 目录 SHALL 包含至少 `NotoSerifSC-Regular.otf`（思源宋体常规）与 `NotoSansSC-Regular.otf`（思源黑体常规）两个字体文件；HTML 与 PDF 渲染时 `@font-face` 路径 MUST 指向这两个文件。

#### Scenario: 字体文件随 skill 解压

- **GIVEN** 服务端启动完成
- **WHEN** 检查 `data-talk/.opencode/skills/ledger/assets/fonts/` 目录
- **THEN** MUST 含 `NotoSerifSC-Regular.otf`
- **AND** MUST 含 `NotoSansSC-Regular.otf`

#### Scenario: HTML 引用本地字体路径

- **WHEN** 渲染任意 fixture report 的 HTML
- **THEN** `@font-face` 的 `src` MUST 指向 `./fonts/NotoSerifSC-Regular.otf` 或类似本地路径

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

