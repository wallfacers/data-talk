## ADDED Requirements

### Requirement: `report.json` schema 与服务端原子写入

系统 SHALL 接收 AI 通过 `datatalk_promote_report` 提交的 `report.json`，原子写入 file artifact 并同步派生 HTML、异步派生 PDF + Markdown。`report.json` schema MUST 包含字段：`schemaVersion` (number)、`kind` ("report")、`meta` ({ title, subtitle?, author?, generatedAt, templateId, templateVersion, userPrompt? })、`theme` ({ accent: hex color })、`sections` (array)、`appendix` (array)。`datatalk_promote_report` action input schema MUST 含 `report` (object, required)、`workspaceId` (string, required)、`groupId` (string, optional — 缺省时服务端生成新 group)。

#### Scenario: 合法 report.json promote 成功

- **GIVEN** AI 提交一份合法 `report.json`（含 `schemaVersion=1`, `kind="report"`, `meta.templateId="ledger.monthly-business-review.v1"`, 至少一个 section）
- **WHEN** `ReportArtifactService.promote(json, workspaceId, groupId?)` 被调用
- **THEN** 服务端在 SQLite `report` 表插入一行
- **AND** `pdf_status = 'processing'`、`md_status = 'processing'`
- **AND** 同步派生 `report.html` 到 file artifact 目录
- **AND** 立即返回 `{ reportId, groupId, version, artifactPaths: { json, html }, pdfStatus: "processing", mdStatus: "processing" }`
- **AND** 异步触发 PDF + Markdown 派生流程

#### Scenario: schemaVersion 不匹配拒绝

- **WHEN** AI 提交 `schemaVersion: 2`（当前仅支持 1）
- **THEN** 返回错误 `{ errorCode: "REPORT_SCHEMA_VERSION_UNSUPPORTED", message: "..." }`
- **AND** 不写入数据库
- **AND** 不派生任何文件

#### Scenario: kind 字段必须为 "report"

- **WHEN** AI 提交 `kind: "dashboard"` 或缺失
- **THEN** 返回错误 `{ errorCode: "REPORT_KIND_INVALID" }`

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
- `cover`（封面：title, subtitle?, author?, date?, logoUrl?）
- `executive-summary`（摘要：bullets[]）
- `toc`（目录：自动生成）
- `chapter`（章节容器：heading, blocks[]）
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
- **THEN** 返回错误 `{ errorCode: "REPORT_BLOCK_TYPE_UNKNOWN", details: { type: "video" } }`
