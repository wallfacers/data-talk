## Why

DataTalk 已能产出实时大屏（bezel dashboard）和单查询导出（data-export），但企业用户最常见的"下属向上级汇报"场景仍是缺口：业务月报、季度复盘、问题事故复盘等需要**叙事化文档**——含封面、摘要、章节、跨多个数据源的数据表格与图表、解读段落、结论与建议，并能输出为 PDF 邮件转发或打印归档。bezel 的深色科技大屏视觉、iframe 轮询、单源 widget 模型，**与汇报文档的范式不兼容**，必须新建独立 capability。

## What Changes

- **新增 MCP action `datatalk_promote_report`**：AI 提交一份冻结好的 `report.json`，服务端原子写入 file artifact，并派生 HTML / PDF / Markdown 三态产物
- **新增 ledger AI skill（构建期 vendor + 启动期解压）**：含 `SKILL.md`、`templates/`（业务月报 + 问题复盘 v0 两套）、`design-language.md`（正式商务排版规范）、`data-contract.md`（AI 跨连接 `datatalk_query_data` 后把结果 inline 进 JSON 的契约）、`section-patterns.md`（KPI 条 / 趋势章节 / 对比表 / 风险列表 / 附录可复用块）
- **新增后端 `ReportArtifactService` + `ReportRenderer` + `PdfRenderer`**：JSON → HTML（自包含、内嵌 ECharts 浏览器端渲染、内嵌中文字体，同步派生）→ PDF（Playwright Java SDK 启动 headless Chromium → `page.pdf()`，异步派生）→ Markdown（复用同一 Chromium 实例对 chart DOM 截图为 PNG 落 file artifact，正文 GFM，异步派生）
- **新增前端"报告库"workspace tab**：workspace 维度的报告列表、查看（iframe 内嵌 HTML）、导出 PDF / Markdown 按钮、重新生成入口；报告与 session 解耦，可在任意会话中被发现与复用
- **新增 SQLite `report` 表 + Flyway migration**（`V4__create_report_table.sql`，落在 `data-talk-infrastructure/src/main/resources/db/migration/`）：workspace 维度持久化 `id / workspace_id / group_id / version / title / templateId / templateVersion / generatedAt / generatedBySessionId / userPrompt / accentColor / artifactPaths(json/html/pdf/md) / pdfStatus / mdStatus / pdfFailReason / mdFailReason`
- **新增 Playwright Java SDK 依赖**：`com.microsoft.playwright:playwright` Maven 依赖；服务端启动时异步 ensure Chromium 可用（首次需 `Playwright.create().chromium()` 触发下载，与 `OpenCodeBinaryResolver` 同形态的本地缓存策略）
- **修改 `agent-skill-routing`**：注册 `datatalk_promote_report` 路由与 ledger skill 触发条件
- **v0 不做**：从 dashboard "另存为报告"、报告定时调度、跨用户共享、版本对比 diff

## Capabilities

### New Capabilities
- `report-document-rendering`: 后端 JSON → HTML/PDF/Markdown 的渲染合约，含正式商务排版规则、ECharts 静态渲染等待 `finished` 事件、中文字体内嵌、PDF 分页规则、Markdown 图表派生 PNG 链接
- `report-library`: workspace 维度的报告持久化与查看，含 SQLite schema、`/api/reports` REST 端点、前端 tab 类型注册（global stage）、查看器与重新生成入口
- `report-data-contract`: AI 端 `report.json` schema 与"先取数后 inline 冻结"的数据契约，含跨连接 `datatalk_query_data` 编排约束、模板版本字段、数据来源（connection + SQL）必须随每个数据块显式标注

### Modified Capabilities
- `agent-skill-routing`: 新增 `datatalk_promote_report` 工具的注册与触发路由；ledger skill 在用户表达"周报 / 月报 / 复盘 / 汇报"等意图时被命中

## Impact

### Backend
- **新 Action Handler**: `PromoteReportActionHandler`（adapter 层，`@DataTalkAction`）
- **新 Service**: `ReportArtifactService`（application）, `ReportRenderer`（application，纯 Java，HTML 同步派生）, `ChartCaptureRenderer`（infrastructure，复用 Playwright Chromium 实例对 chart DOM 截图为 PNG）, `MarkdownRenderer`（application，依赖 ChartCaptureRenderer 输出后填 PNG link）, `PdfRenderer`（infrastructure，Playwright Java SDK 启动 Chromium → `page.pdf()`）
- **新 Controller**: `ReportController` — 列表、详情、artifact 下载、状态轮询、重新生成入口
- **新 Repository**: `JdbcReportRepository`（infrastructure）+ `ReportRepository`（domain port）
- **新 Domain**: `Report` record + `ReportTemplate` 枚举（`monthly-business-review` / `incident-postmortem`）+ `ReportDerivativeStatus` 枚举（`PROCESSING` / `READY` / `FAILED`）
- **新 Migration**: `V4__create_report_table.sql`（落 `server/data-talk-infrastructure/src/main/resources/db/migration/`）
- **新依赖**: `com.microsoft.playwright:playwright` Maven 依赖；服务端启动时异步 ensure Chromium；中文字体（思源宋体 / 黑体）打包进 ledger skill `assets/fonts/`
- **Skill 解包**: 复用 `OpenCodeBinaryResolver` 模式，源在 `server/data-talk-adapter/src/main/resources/skills/ledger/`，构建期打包为 jar 内 tar.gz，启动期由 adapter 层 `LedgerSkillResolver` 解压到 `data-talk/.opencode/skills/ledger/`（与 bezel skill 同层）

### Frontend
- **新 tab type**: `report-library`（workspace scope，注册到 `client/src/features/stage/registry/tab-type-registry.ts`），打开右栏报告库
- **新组件**: `report-library-tab.tsx`（列表，按 group_id 折叠展示版本）, `report-viewer-tab.tsx`（iframe sandbox 内嵌 HTML + 顶栏导出按钮）
- **iframe 沙箱**: 复用 `client/src/features/dashboard/iframe-shell.tsx` 模式，`sandbox="allow-scripts"`（与 dashboard 一致，允许 ECharts 浏览器端渲染；本地字体通过 `srcDoc` + `@font-face` 不需要 `allow-same-origin`）
- **新 feature 模块**: `client/src/features/report/`（schema、store、API hooks、components）
- **新 chat 卡片**: report promote 完成后 AI 消息内显示「报告已生成 · 打开」卡片，点击打开 `report-viewer-tab`
- **i18n**: 报告库列表、查看器顶栏、按钮、空状态、错误提示文案注册到 `client/src/i18n/locales/zh-CN/` 与 `en-US/`

### Skills
- **新**: `server/data-talk-adapter/src/main/resources/skills/ledger/` — 构建期 vendor 进 infrastructure 资源
- **修改**: `agent-skill-routing` 注册新 MCP 工具

### Database / Data Source Compatibility
- 报告生成本身**不引入新的数据库类型**，跨源 SQL 编排复用现有 `ExecuteSqlAction`（已支持显式 `connectionId` 参数，见 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java:496`）
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../../../docs/DATA_SOURCE_TYPE_COMPATIBILITY.md) 大部分章节 N/A：本 change 不新增数据源、不改 JDBC 连接、不改 schema 发现、不改 SQL 执行 / 切分 / 风险分析、不改诊断；仅复用 `datatalk_query_data` 取数后落到 `report.json`

### Design Inputs（Frontend Plan Gate）
本 change 在 client 端引入"报告库" workspace tab 与"报告查看器" tab。依据 [client/DESIGN.md](../../../client/DESIGN.md) 的约束：

- **Stage state global**: 报告库 tab 与查看器 tab 均为 workspace scope，注册到 `client/src/features/stage/registry/tab-type-registry.ts`，instance 不带 `scope` 字段；切换 session 时不改变 stage 状态
- **Layout mode**: 报告库 = Instrument Panel（列表型），报告查看器 = Conversation Workspace 中的 instrument lane（iframe 内容区）
- **Color tokens**: 列表 / 顶栏 / 按钮严格映射 semantic token（`bg.canvas` / `bg.subtle` / `text.strong` / `accent.primary` / `border.default`），禁止 raw primitive color
- **报告 HTML 内部样式独立于 client DESIGN.md**：报告是给上级汇报的正式文档，视觉气质为白底衬线、企业咨询报告风（参考麦肯锡 / 德勤月报），与 client 的 dual-theme research workbench 视觉**有意区分**——这是 ledger skill `design-language.md` 单独定义的"印刷品"排版语言，不复用 DataTalk client tokens；只有"报告库 / 查看器"的**外壳 UI** 才走 client DESIGN.md
- **iframe 沙箱**: 复用 [client/src/features/dashboard/iframe-shell.tsx](../../../client/src/features/dashboard/iframe-shell.tsx) 的 `sandbox="allow-scripts"` 模式，加载冻结快照 HTML，无 polling

### Risks
- **PDF 中文乱码**: BUG-0049 在 bezel 上已踩过，根因是外网字体 CDN 不可达。本 change 必须**将中文字体（思源宋体常规 / 黑体常规）打包进 ledger skill 的 CSS 资源**，HTML 用 `@font-face` 引本地字体，Playwright print 时字体已 ready
- **iframe 白屏**: BUG-0051 根因 loader 早卸 + 外网 CDN。本 change 的报告 HTML 是自包含静态文件（无 polling、无外网依赖），Playwright print 必须等待 ECharts `chart.on('finished')` 后再触发，避免 PDF 出空白图表
- **Chromium 依赖体积** ≈300MB（仅服务端）：用户首次启动可能需要等待下载；策略：服务端启动时异步预热，未就绪前 PDF 导出按钮显示"准备中"
- **chart 在 PDF 跨页**: section-patterns 强制 chart block 用 `break-inside: avoid`；表格行 > N 时强制分页
- **报告 JSON 体积**: 数据 inline 冻结意味着大表会让 JSON 变大；策略：每个 table block 默认上限 200 行，超过则只 inline 前 N 行 + 标注"完整数据见附录 CSV 链接"（复用 data-export 临时文件）
- **模板 v0 抽象的稳定性**: 两套模板可能不足以验证抽象是否成立；策略：v0 完成后留观察期，第 3 套模板再加时如发现抽象不够再迭代
