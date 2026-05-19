## 1. 数据库与 Domain 骨架

- [x] 1.1 编写 Flyway migration `server/data-talk-infrastructure/src/main/resources/db/migration/V4__create_report_table.sql`：创建 `report` 表（字段按 `report-library` spec：id / workspace_id / group_id / version / title / subtitle / template_id / template_version / accent_color / generated_at / generated_by_session_id / user_prompt / artifact_paths_json / pdf_status / md_status / pdf_fail_reason / md_fail_reason）；索引 `idx_report_workspace` + `idx_report_group`
- [x] 1.2 `data-talk-domain` 新增 `Report` record（字段对应 SQL schema）+ `ReportTemplate` enum（v0 两个值：`MONTHLY_BUSINESS_REVIEW` / `INCIDENT_POSTMORTEM`）+ `ReportDerivativeStatus` enum（`PROCESSING` / `READY` / `FAILED`）
- [x] 1.3 `data-talk-domain` 定义 `ReportRepository` port 接口（`findById` / `findByWorkspaceId(wsId, groupId?)` / `findGroupLatest(wsId)` / `save` / `delete` / `updatePdfStatus` / `updateMdStatus`）
- [x] 1.4 检查 domain 改动是否影响其他 sealed interface 的 exhaustive switch（应无 — Report 是独立 record）
- [x] 1.5 验证：`cd server && mvn install -pl data-talk-domain -am -DskipTests`（模块改动需刷新 ~/.m2）

## 2. ledger Skill 资产

- [x] 2.1 创建目录树 `server/data-talk-adapter/src/main/resources/skills/ledger/`（源；与 bezel 同层），无需 tar.gz 打包 — 现成 `SkillResourceSyncer` 走的是 classpath `skills/<name>/**` 目录扫描模式
- [x] 2.2 编写 `SKILL.md`：触发场景、模板索引表、与 bezel 边界、AI 六步流程（含 export CSV → appendixCsvRef → promote 的两步交互；按 `report-data-contract` spec 要求）
- [x] 2.3 编写 `data-contract.md`：跨连接显式 connectionId 强制条款 + 正反例 + `source` 字段格式 + 大表附录 CSV 两步交互正反例
- [x] 2.4 编写 `design-language.md`：白底衬线印刷品规范、字号阶（≥5 级）、color tokens、禁用项（玻璃拟态 / 暗色 / bezel 风格）
- [x] 2.5 编写 `section-patterns.md`：11 种 block 类型分别给出用途 / JSON schema / HTML 示例 / PDF 分页注意（cover / executive-summary / toc / chapter / kpi-strip / narrative / chart / table / risk-list / timeline / appendix）
- [x] 2.6 编写 `templates/monthly-business-review.md`：模板说明 + 完整示例 JSON（`templateId: ledger.monthly-business-review.v1`，含真实示例数据）
- [x] 2.7 编写 `templates/incident-postmortem.md`：模板说明 + 完整示例 JSON（`templateId: ledger.incident-postmortem.v1`，含 timeline + risk-list）
- [x] 2.8 编写 `assets/styles/ledger.css`：商务排版 CSS（`@font-face` 引本地字体、`break-inside: avoid` 规则、`.ledger-table--paged` 强制分页、`.ledger-block-source` 数据来源脚注样式、`[data-ledger-chart-id]` 容器样式以便 ChartCaptureRenderer 定位）
- [x] 2.9 引入字体资源 `assets/fonts/NotoSerifSC-Regular.otf` 与 `NotoSansSC-Regular.otf`（思源宋体/黑体 OFL 许可证；体积控制：每个 ≤ 20MB；如超出考虑子集化）
- [x] 2.10 (removed) — 无需 Maven assembly tar.gz，`SkillResourceSyncer` 直接扫 classpath 目录
- [x] 2.11 验证：`mvn package` 后解压 jar 确认 `classpath:/skills/ledger/SKILL.md` 与 9 个必备文件全部就位

## 3. 后端渲染服务（application 层）

- [x] 3.1 `data-talk-application` 实现 `ReportArtifactService.promote(reportJson, workspaceId, groupId?, sessionId)`：schema 校验（schemaVersion=1、kind=report、block 类型白名单、大表 appendixCsvRef 强校验、groupId 存在性校验）→ 计算 group_id 与 version → 落 SQLite（pdf_status=processing, md_status=processing）→ 同步派生 HTML → 异步触发 PDF + Markdown 派生
- [x] 3.2 实现 `ReportRenderer.toHtml(reportJson, ledgerSkillRoot)`：生成自包含 HTML（内联 ledger.css、`@font-face` 本地路径、ECharts UMD 内联或本地引用、`window.__LEDGER_READY__` 信号 JS、为每个 chart 容器加 `data-ledger-chart-id` 属性以便后续截图定位）
- [x] 3.3 实现 `MarkdownRenderer.toMarkdown(reportJson, chartPngPaths)`：narrative → 原文、table → GFM、kpi-strip → 单行 GFM table、chart → 用 `chartPngPaths` 映射查 PNG 路径生成 `![caption](./assets/...)`、timeline → 有序 list、risk-list → 无序 list 含 severity emoji
- [x] 3.4 实现异步派生编排（virtual thread）：HTML 同步派生后，dispatch 异步任务调 `ChartCaptureRenderer.captureChartsToPng` → 拿到 chartPngPaths → 调 `MarkdownRenderer.toMarkdown` → 调 `PdfRenderer.render` → 各自更新 pdf_status / md_status；任一失败独立记录 fail_reason
- [x] 3.5 单元测试：fixture JSON → 期望 HTML（含 4 个 finished signal 场景：0 chart / 1 chart / 3 chart / chart 超时）
- [x] 3.6 单元测试：fixture JSON + mock chartPngPaths → 期望 Markdown（table GFM、kpi-strip、chart PNG 路径、timeline、risk-list）
- [x] 3.7 单元测试：promote schema 校验（拒绝 schemaVersion=2、拒绝 kind=dashboard、拒绝未知 block type、拒绝大表无 appendixCsvRef、拒绝非法 groupId）
- [x] 3.8 验证：`cd server && mvn install -pl data-talk-application -am -DskipTests`

## 4. 后端 PDF / Chart 截图渲染（infrastructure 层）

- [x] 4.1 添加 `com.microsoft.playwright:playwright` 依赖到 `server/data-talk-infrastructure/pom.xml`
- [x] 4.2 实现 `ChromiumLifecycle`（infrastructure, Spring `@Component`）：启动期异步 `Playwright.create().chromium().launch(LaunchOptions.headless(true))`，提供共享 `Browser` 实例；状态写入 `ReportSystemStatus` bean（`chromiumReady` 字段）；`@PreDestroy` 关闭 Browser + Playwright
- [x] 4.3 实现 `PdfRenderer`：从 `ChromiumLifecycle` 拿 Browser → `newContext().newPage()` → `page.navigate("file://" + htmlPath)` → `page.waitForFunction("() => window.__LEDGER_READY__", 30_000)` → `page.pdf(A4 + 边距 PdfOptions)` → 写出 PDF；60s 整体超时 + 失败时更新 `report.pdf_status = failed` + `pdf_fail_reason`；释放 page / context
- [x] 4.4 实现 `ChartCaptureRenderer`：从同一 Browser 拿 page，加载 HTML 后等 LEDGER_READY，对所有 `[data-ledger-chart-id]` 元素 `Locator.screenshot(omitBackground=false, scale=2)`；每张 PNG 落 file artifact `kind='report-asset'`，文件名 `chart-<blockId>.png`；返回 `Map<blockId, pngPath>` 供 MarkdownRenderer 使用
- [x] 4.5 实现 `JdbcReportRepository`（implements domain `ReportRepository`）：findGroupLatest 按 group_id 折叠返回每 group 最新版本、findByWorkspaceId 含 groupId 过滤、updatePdfStatus / updateMdStatus 独立更新、delete 时级联清理 file artifact（含 `report-data-csv` 附录）
- [ ] 4.6 单元测试：`JdbcReportRepository` CRUD + 跨 workspace 隔离 + group 折叠查询 + group 展开查询
- [ ] 4.7 集成测试（CI 跳过 / 本地手动）：`PdfRenderer` + `ChartCaptureRenderer` 端到端 — fixture HTML → 真实 Chromium → PDF 文件 + chart PNG 文件存在且 size > 0
- [x] 4.8 验证：`cd server && mvn install -pl data-talk-infrastructure -am -DskipTests`

## 5. 后端 Adapter & REST 端点

- [x] 5.1 实现 `PromoteReportActionHandler`（adapter，`@DataTalkAction(name = "datatalk_promote_report")`）：input schema = `{ report: object (required), workspaceId: string (required), groupId: string (optional) }`；调 `ReportArtifactService.promote` → 返回 `{ reportId, groupId, version, artifactPaths, pdfStatus, mdStatus }`
- [x] 5.2 在 `OpenCodeGatewayBeans.startEmbedded()` 启动钩子注册 ledger：在现有的 `skillSyncer.syncSkill(...)` 串行调用列表中加入 `skillSyncer.syncSkill("ledger", opencodeCwd);`（与 bezel 同层；`SkillResourceSyncer` 自带 SHA-256 marker 幂等）
- [x] 5.3 实现 `ReportController`：`GET /api/reports`、`GET /api/reports/{id}`、`GET /api/reports/{id}/download/{format}`、`POST /api/reports/{id}/pdf-render`、`POST /api/reports/{id}/md-render`、`DELETE /api/reports/{id}`、`GET /api/reports/system-status`
- [x] 5.4 下载端点处理：`html` 直接流式返回；`pdf`/`md` 在 `processing` 时返回 409 + `{ status: processing, retryAfterSec: 3 }`；`failed` 时返回 502 含 fail_reason
- [x] 5.5 修改 `AGENTS.md`：`Trigger Gate` 表新增 ledger 行（trigger 关键词：周报/月报/复盘/汇报/postmortem/report/weekly/monthly/quarterly），`Skill Index` 加 `skill:ledger` 条目
- [ ] 5.6 集成测试：复用 `SkillResourceSyncerIT` 模式，加 ledger fixture 校验首次同步 + 二次幂等 + classpath 变更触发重写
- [ ] 5.7 WireMock 集成测试：`PromoteReportActionHandler` 完整流程（promote → 落 DB → 返回 artifactPaths）
- [ ] 5.8 集成测试：`ReportController` 各端点 happy path + 错误码（schemaVersion 不匹配、未知 block、跨 workspace 越权、PDF/MD 未就绪 409、非法 groupId）
- [ ] 5.9 集成测试：AGENTS.md Trigger Gate 与 Skill Index 闭合性（既有的 trigger-gate test 套件加新断言：ledger 行存在 + classpath ledger.tar.gz 解压后含 SKILL.md）
- [ ] 5.10 验证：`cd server && mvn verify`（全量测试通过）

## 6. 前端 Feature 模块

- [x] 6.1 创建 `client/src/features/report/` 目录树：`schema.ts` / `api.ts` / `store.ts` / `components/`
- [x] 6.2 `schema.ts`：Zod schemas — `Report`（含 group_id / version / pdf_status / md_status）、`ReportListItem`（含 groupSize）、`ReportSystemStatus`（与后端 DTO 对齐）
- [x] 6.3 `api.ts`：TanStack Query hooks — `useReportList(workspaceId, groupId?)` / `useReport(id, { refetchInterval })`（轮询条件：pdf_status 或 md_status = processing 时 3s，否则 false）/ `useSystemStatus()` / `useRenderPdfMutation()` / `useRenderMdMutation()` / `useDeleteReportMutation()`
- [x] 6.4 `store.ts`：Zustand store — 本地选中状态（report 列表当前选中行、按 groupId 展开状态、viewer 顶栏按钮 loading 状态等）
- [x] 6.5 `components/report-library-tab.tsx`：报告列表 UI（按 client/DESIGN.md tokens — `bg.canvas` 主体、`bg.subtle` 行 hover、`accent.primary` 选中 left-border、`text.muted` 元数据）；group 折叠展示（每行显示最新 version，groupSize > 1 时显示展开图标）
- [x] 6.6 `components/report-viewer-tab.tsx`：顶栏（标题/时间/3 按钮：导出 PDF / 导出 MD / 重新生成 + 状态 spinner）+ iframe sandbox `allow-scripts`（复用 `iframe-shell.tsx` 模式）+ onLoad-gated `<Skeleton>` loader
- [x] 6.7 `components/report-card.tsx`：chat 内 promote 完成卡片（标题 / 相对时间 / 打开按钮）
- [x] 6.8 注册 tab types 到 `client/src/features/stage/registry/tab-type-registry.ts`：`report-library`（scope: 'workspace'）+ `report-viewer`（scope: 'workspace'）
- [x] 6.9 集成到 chat 消息渲染：识别 `datatalk_promote_report` action 结果后渲染 `report-card`
- [x] 6.10 `重新生成` 按钮逻辑：当前 session 注入 user message "请基于 {templateId} 模板和上次的需求重新生成报告（groupId={groupId}）"，触发 AI 走流程；无 active session 时 toast 提示
- [x] 6.11 PDF/MD 按钮状态机：(a) `useSystemStatus().chromiumReady === false` → disabled + tooltip "PDF/Markdown 准备中…"；(b) `pdf_status='processing'` → disabled + spinner；(c) `pdf_status='failed'` → 显示警告图标 + tooltip 含 fail_reason；(d) `ready` → 可点击下载
- [x] 6.12 i18n 注册：在 `client/src/i18n/locales/zh-CN/` 与 `en-US/` 添加 report 相关文案 key（列表标题、空状态、按钮、tooltip、错误提示、确认对话框）；按既有 i18n 文件分布与命名规则放置
- [x] 6.13 vitest 单元测试：列表渲染（含 group 折叠）、tab 注册、PDF 按钮各状态、卡片打开 viewer 行为、切换 session 不变 stage 状态、轮询启停时机
- [x] 6.14 验证：`cd client && npx tsc --noEmit`

## 7. 端到端与文档

- [ ] 7.1 启动 backend + client，用 playwright-cli skill 跑端到端：用户对话 "生成 2026 年 4 月销售月报" → AI 触发 ledger skill → 多次 query_data → promote → chat 显示卡片 → 打开 viewer → 导出 PDF → 导出 Markdown
- [ ] 7.2 端到端走查：问题复盘场景 "Q1 数据库性能事件复盘报告" → 验证 timeline + risk-list block 渲染正确
- [ ] 7.3 中文字体验证：PDF / HTML 中文显示正确无乱码（重点回归 BUG-0049 同源风险）
- [ ] 7.4 iframe 白屏验证：viewer 加载体验流畅，loader 等到 onLoad 才隐藏（回归 BUG-0051 同源风险）
- [ ] 7.5 大表附录验证：故意构造 300 行 table，无 appendixCsvRef 时被拒；有 appendixCsvRef 时正确派生附录 CSV
- [ ] 7.6 PDF 分页验证：长表强制分页 / 图表不跨页
- [x] 7.7 任何端到端发现的产品行为偏差，按 `docs/bugs/README.md` 模板新建 BUG 文件并登记到 `docs/bugs/index.md`
- [x] 7.8 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`：在变更日志或附录小节标记 "report-document-generation N/A — 复用现有 ExecuteSqlAction，不引入新数据源类型"
- [x] 7.9 在 `docs/bugs/index.md` 写本次端到端结果："Found N BUGs in this run, registered at …"（即便 N=0）
- [x] 7.10 更新 `CLAUDE.md` 知识库导航表：新增 "Report skill (ledger) — server/.../skills/ledger/SKILL.md, templates/" 行
