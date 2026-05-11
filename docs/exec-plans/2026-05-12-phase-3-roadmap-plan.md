# Phase 3 Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 DataTalk 从"智能数据库协作平台"升级为"AI 驱动的数据分析与报告工作台"。三期聚焦 **数据流通**（文件→DB、DB→文件、外部→DB）与 **智能报告生成**（PDF/HTML/Markdown，全平台数据源聚合），让 AI 自然语言完成导入、导出、分析、报告全链路。

**Architecture:** 每个非平凡能力需独立 product spec + execution plan。本 roadmap 确定优先级、依赖顺序、排除方案和验收门禁。

**Tech Stack:** Spring Boot 3.5, Java 21, SQLite metadata store, dynamic JDBC, Tauri v2, React 19, TypeScript, Zustand, TanStack Query, Vitest, JUnit 5, Maven, Playwright (E2E).

---

## Status

- **Created:** 2026-05-12
- **State:** Active
- **Owner intent:** 二期数据源矩阵（17 first-class kind）与 Dashboard/bezel 渲染管线收尾后，进入三期：以"文件上传→智能识别→数据流通→报告生成"为主线，砍掉传统 DBA 运维功能（权限/备份/迁移/存储过程）。
- **Explicit removals from original Phase 3 scope:** 权限管理（GRANT/REVOKE/用户创建）、备份恢复（mysqldump/pg_dump/定时任务）、跨库迁移（结构/数据迁移/diff）、存储过程/函数/触发器 DDL 管理。这些是传统 DBA 工具的核心能力，但与 DataTalk"AI 对话驱动分析"的产品定位偏离——它们面向运维而非分析，且每个都需要大量方言适配和安全管理，投入产出比低。

---

## Context

- 二期已 ship：跨 session 工作台 + `ui_find`、Intelligent Operations（EXPLAIN/索引推荐）、ER Inspector/Designer、Dashboard P1（bezel + iframe sandbox）、Guarded DDL/DML、SQL 编辑器全功能、File Artifact 系统 5 Part 全链路。
- 17 种 first-class 数据源：Wave A 4/4 + Wave B 7/7 + Wave C 6/6（含 GaussDB）。Wave D（云数仓）和 Wave E（非 SQL）延后到后续独立评估。
- Task 10（外部数据采集）已在二期启动，三期继续推进为正式能力。
- 产品规格：[docs/product-specs/index.md](../product-specs/index.md) §3.5（可视化）、§3.7（导入导出）、§3.12（外部数据采集）。

## Design Inputs

Frontend work in this roadmap must follow [client/DESIGN.md](../../client/DESIGN.md):

- 文件上传控件：使用 `bg.subtle` 容器 + `border.strong` 虚线边框拖拽区 + `accent.primary` 上传进度条；键盘可达，焦点环 `interaction.focusRing`。
- 报告预览：Stage Tab 内嵌 HTML/Markdown 渲染器，复用现有 `file_preview` Tab type 的 Monaco/Markdown 渲染链路，加 PDF 下载按钮。
- 导入映射预览：表格使用稳定表头 + 低强调 hover + 显式选中态，技术值用 mono 字体。
- 所有新控件按 `client/DESIGN.md` 五态 token 映射（idle/hover/active/focus/disabled）。
- Motion 仅用于状态确认，非装饰。

## Product Direction

### 三期定位

**从"查数据"到"数据流转 + 智能报告"**。二期的用户对数据库的连接、查询、ER 图、Dashboard 已经可用。三期解决两端：**数据怎么进来**（文件上传/外部采集）和**分析怎么出去**（报告生成/数据导出）。

### Recommended Order

1. **Task 12: AI 文件上传与智能识别** — 核心入口。AI 输入框支持拖拽/粘贴上传 CSV/Excel/JSON/SQL/TXT，AI 分析内容后决定：直接导入建表？分析数据内容？生成报告？这是三期价值感知最强的功能。
2. **Task 13: 对话式数据导入导出** — 数据流通闭环。"把 orders 表导出为 CSV"、"把这个 CSV 导入到当前库"——全部通过自然语言完成。导入端复用 Task 12 的文件入口，导出端新建流式导出后端。
3. **Task 10 (续): 外部数据采集（Skill 驱动）** — 从二期延续。通用 HTTP/REST/GraphQL skill 脚手架 + 电商平台等垂直 skill。依赖 Task 12 的文件识别和 Task 13 的导入链路。
4. **Task 14: 智能报告生成** — 分析产出。基于对话内容、查询结果、Dashboard 数据生成 PDF/HTML/Markdown 报告。依赖 Task 8 的 Dashboard 底座和 Task 13 的导出管线。

### Explicit Exclusion

- **不引入传统 DBA 运维功能**：权限管理、备份恢复、跨库迁移、存储过程/函数/触发器管理不在三期范围。这些功能每条都需要大量方言适配、安全审计和灾难恢复机制，与 DataTalk"AI 对话驱动分析"的定位偏离。
- **不引入 Wave D/E 数据源**：云数仓（Snowflake/BigQuery/Redshift/Databricks）和非 SQL（MongoDB/Elasticsearch）不在三期范围，按需独立评估。
- **不做实时协同**：报告/导入/导出均为单人操作，不引入 CRDT 或多人协同编辑。

---

## Spec Mapping

- High-level product roadmap: [docs/product-specs/index.md](../product-specs/index.md), sections 3.5, 3.7, 3.12, and 4.
- Current implementation tracking: [docs/exec-plans/index.md](./index.md).
- Design-system gate: [client/DESIGN.md](../../client/DESIGN.md).
- Data-source compatibility gate: [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
- Quality gates: [docs/QUALITY.md](../QUALITY.md).
- Plan workflow: [docs/PLANS.md](../PLANS.md).

---

## File Structure Map

### Coordination Documents

| File | Responsibility |
|------|----------------|
| `docs/exec-plans/2026-05-12-phase-3-roadmap-plan.md` | This Phase 3 roadmap and ordering source |
| `docs/exec-plans/index.md` | Active / Completed registration |
| `docs/product-specs/index.md` | Product roadmap source and index for future feature specs |

### Existing Runtime Areas Phase 3 Plans Will Touch

| Area | Files |
|------|-------|
| AI input / composer | `client/src/features/chat/components/PromptComposer.tsx`, `client/src/features/chat/components/composer/` |
| Chat message pipeline | `client/src/features/chat/components/markdown/`, `client/src/features/chat/stores/chat-parts-store.ts` |
| File upload backend | `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/` (new), `server/data-talk-application/` (new service) |
| Import/export service | `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java` |
| Stage tabs | `client/src/features/stage/tabs/`, `client/src/features/stage/components/sql-result-panel.tsx` |
| File artifact system | `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/FileArtifactService.java` |
| Dashboard | `client/src/features/stage/components/dashboard-tab.tsx`, bezel HTML rendering pipeline |
| Report generation | `server/data-talk-application/` (new report service), `client/src/features/stage/tabs/` (new report tab) |
| Skill system | `server/data-talk-adapter/src/main/resources/opencode/skills-src/` |

---

## Batch Plan

### Task 12: AI 文件上传与智能识别

**定位**：三期入口功能。AI 输入框支持文件上传，AI 分析内容后智能路由——导入建表 / 数据分析 / 生成报告。

**Files:**
- Create: `docs/product-specs/YYYY-MM-DD-file-upload-intelligent-analysis-design.md`
- Create: `docs/exec-plans/YYYY-MM-DD-file-upload-intelligent-analysis-plan.md`
- Modify: `client/src/features/chat/components/PromptComposer.tsx` (file drop zone + paste handler + attachment chip)
- Modify: `client/src/features/chat/components/composer/` (file attachment UI)
- New: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileUploadController.java` (multipart upload endpoint)
- New: `server/data-talk-application/src/main/java/com/datatalk/application/fileanalysis/FileAnalysisService.java` (MIME detection + content sampling + AI prompt assembly)
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` (file analysis prompt rules)

- [ ] **Step 12.1: Composer 文件入口**
  - 拖拽区：Composer 底部常驻虚线边框拖拽区（`border.strong` 虚线 + `bg.subtle`），拖入高亮 `accent.primary` 边框。
  - 粘贴：Ctrl+V 粘贴文件自动识别并上载。
  - 文件类型：CSV（.csv）、Excel（.xlsx/.xls）、JSON（.json/.jsonl）、SQL（.sql）、纯文本（.txt/.md/.log）。单文件 ≤ 50MB。
  - 附件 Chip：上传后显示文件名 + 大小 + 类型 icon + 删除按钮（×）。支持多文件。
  - 上传进度：`accent.primary` 进度条 + 百分比。

- [ ] **Step 12.2: 后端文件接收与分析**
  - `POST /api/files/upload` multipart 端点，返回 `fileId` + MIME 类型 + 前 100 行预览 + AI 分析建议。
  - `FileAnalysisService`：MIME 检测（Apache Tika 或魔数检测）+ CSV/Excel/JSON 自动解析前 N 行 + SQL 文件语句类型扫描。
  - 文件暂存：上传文件写入 `~/.data-talk/uploads/` 临时目录，会话结束时清理（由 HousekeepingScheduler 兜底，24h TTL）。
  - 安全约束：拒绝可执行文件、二进制文件（非 CSV/Excel/JSON/SQL/TXT 的统一拒绝）、0 字节文件。

- [ ] **Step 12.3: AI 智能路由**
  - 上传完成后，文件信息（文件名/MIME/大小/前 100 行预览）注入当前对话 as system context。
  - AGENTS.md 新增 `## File Upload & Analysis` 节，定义 AI 决策树：
    1. CSV/Excel/JSON 且内容为结构化数据 → 建议导入数据库，给出 `CREATE TABLE` + 目标数据库/schema。
    2. SQL 文件 → 逐条解析语句类型，L2/L3 语句走确认流。
    3. TXT/MD/LOG → 分析文本内容，提取关键信息，不导入。
    4. 非结构化或无法识别 → 如实告知用户，询问意图。
  - AI 给出分析建议后，用户可在聊天中确认执行或调整。

- [ ] **Step 12.4: 定义验收**
  - Backend: `mvn compile -q` + FileUploadController IT + FileAnalysisService 单元测试。
  - Frontend: `npx tsc --noEmit` + Composer 拖拽/粘贴 vitest + FileAttachmentChip 组件测试。
  - E2E: Playwright 拖拽 CSV/SQL 文件 → 验证 AI 正确建议导入/分析。

### Task 13: 对话式数据导入导出

**定位**：二期数据查询的出口和入口。自然语言驱动的导入导出——"导出 orders 表到 CSV"、"把这个 JSON 导入到生产库"——AI 自动完成。

**Files:**
- Create: `docs/product-specs/YYYY-MM-DD-conversational-data-import-export-design.md`
- Create: `docs/exec-plans/YYYY-MM-DD-conversational-data-import-export-plan.md`
- New: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/DataExportController.java` (流式导出端点)
- New: `server/data-talk-application/src/main/java/com/datatalk/application/dataio/DataExportService.java` (流式 CSV/JSON/SQL dump)
- New: `server/data-talk-application/src/main/java/com/datatalk/application/dataio/DataImportService.java` (CSV/Excel/JSON → 建表 + INSERT)
- New: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExportDataAction.java` (MCP tool: `datatalk_export_data`)
- New: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ImportDataAction.java` (MCP tool: `datatalk_import_data`)
- Modify: `client/src/features/stage/components/sql-result-panel.tsx` (导出按钮升级)
- Modify: `client/src/features/stage/components/sql-result-table.tsx` (导出范围选择)
- New: `client/src/features/stage/components/import-preview-panel.tsx` (导入预览 + schema 映射)

- [ ] **Step 13.1: 对话式导出**
  - MCP `datatalk_export_data` tool：AI 根据对话上下文确定导出目标（表名/查询结果/全库），用户一句话确认即可。
  - 格式：CSV（默认）、JSON、SQL INSERT dump、Excel（.xlsx）。
  - 流式导出：`GET /api/export/table/{connectionId}/{schema}/{table}?format=csv` 流式写入 HTTP response（`Transfer-Encoding: chunked`），不占内存。
  - 导出范围：整表 / WHERE 条件 / 当前查询结果集 / 当前页。
  - 前端：导出进度弹窗 → 下载完成通知（Toast）+ 自动触发浏览器下载。
  - 与 Task 4（SQL Result Export）的关系：Task 4 是前端-only 的当前结果导出；Task 13 是后端驱动的全量流式导出，打通 MCP 让 AI 可直接调用。

- [ ] **Step 13.2: 对话式导入**
  - 入口：Task 12 文件上传后 AI 建议导入 + 用户直接自然语言 "把这个 CSV 导入到当前库"。
  - MCP `datatalk_import_data` tool：AI 接收文件引用（来自 Task 12 的 `fileId`）+ 目标连接/数据库/schema。
  - `DataImportService` 流程：
    1. 解析文件（CSV/Excel/JSON），推断 header + 每列类型。
    2. 生成 `CREATE TABLE` DDL（表名由 AI 建议 / 用户确认）。
    3. 前端展示导入预览面板：前 10 行 + 推断的 schema + 目标表名（可编辑）。
    4. 用户确认后走 L2 风险流程（`CREATE TABLE` + 批量 `INSERT`）。
    5. 批量 INSERT 复用 Task 5 的 batch DML + `INSERT ... VALUES` rewrite 优化。
  - 支持增量导入：如果目标表已存在，`INSERT INTO` 追加（走 L2 确认）。
  - 安全护栏：导入前 `SELECT COUNT(*)` 预估、文件行数超限（默认 100 万行）截断提示。

- [ ] **Step 13.3: 前端导入导出 UI**
  - 导出：SQL 结果面板工具栏升级——新增格式下拉（CSV/JSON/SQL/Excel）+ "导出全部" / "导出选中列" 选项。
  - 导入预览面板：Stage Tab 内嵌 `import_preview` Tab type，展示目标表名 + schema 映射 + 前 10 行预览 + 导入进度。
  - Chat 内联卡片：AI 建议导入时，渲染 `ImportPreviewCard`（含表名、行数、schema 摘要 + 确认/取消按钮）。
  - 所有文案接入 i18n（en/zh）。

- [ ] **Step 13.4: 定义验收**
  - Backend: `mvn compile -q` + DataExportService 流式测试 + DataImportService schema 推断测试 + ExportDataAction/ImportDataAction IT。
  - Frontend: `npx tsc --noEmit` + ImportPreviewPanel vitest + 导出按钮升级测试。
  - E2E: Playwright "导出 orders 表为 CSV" 对话 → 验证下载文件内容正确。导入 CSV → 验证建表 + 数据行数一致。

### Task 10: 外部数据采集（Skill 驱动）

**定位**：从二期延续的增值能力。通过 skill 系统对接外部数据源（电商平台、通用 HTTP API），AI 自然语言驱动"采集 → 落库 → 分析"全流程。

**Files:**
- Create: `docs/product-specs/YYYY-MM-DD-external-data-ingestion-skills-design.md`
- Create: `docs/exec-plans/YYYY-MM-DD-external-data-ingestion-skills-plan.md`
- New: `server/data-talk-adapter/src/main/resources/opencode/skills-src/data-ingestion/` (generic HTTP skill)
- New: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionTaskService.java` (采集任务调度)
- New: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/IngestionController.java` (采集任务 REST)
- New: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/IngestionAction.java` (MCP tool)
- New: `client/src/features/stage/components/ingestion-task-view.tsx` (采集任务进度 Tab)

- [ ] **Step 10.1: 通用 HTTP/REST/GraphQL 采集 Skill 脚手架**
  - skill 包结构：`skills/data-ingestion/SKILL.md` + `scripts/fetch.py`（或 TypeScript）+ `schemas/` 目录。
  - SKILL.md 定义契约：URL → 分页策略（offset/cursor/page）→ 字段映射建议 → 目标连接写入。
  - OpenCode MCP 与 skill 协议互通：AI 读 SKILL.md → 调 skill 脚本 → 拿结果 → 调 `datatalk_import_data` 落库。
  - 凭据托管：OAuth / API key 通过 OpenCode config 注入，DataTalk 核心不存第三方凭据。

- [ ] **Step 10.2: 采集任务调度与进度**
  - `IngestionTaskService`：异步采集任务（Java 21 virtual threads），进度回调写 `ingestion_task` 表。
  - 前端采集进度 Tab：复用 Stage Tab 持久化能力（Task 6），展示采集源 URL、已拉取行数、速率、预计剩余时间。
  - 采集完成后自动触发 `datatalk_import_data` 落库（复用 Task 13 导入管线）。

- [ ] **Step 10.3: 垂直平台 Skill（后续按需）**
  - 电商平台（淘宝/京东/拼多多/抖音电商）各自独立 child plan，不捆在一起。
  - 每个平台 skill 必须通过合规审查（平台 ToS、API 许可、数据使用协议）。
  - 优先顺序由用户需求和 API 可达性决定。

- [ ] **Step 10.4: 架构硬约束（继承原 roadmap §Step 10.2）**
  - DataTalk 核心不捆绑任何平台 SDK。凭据、采集脚本、字段映射全在 skill 包里。
  - 自动建表走 Task 13 的 L2 导入流程，不走私有 bypass。
  - 采集源 URL、执行时间、原始 payload 引用写入审计日志。
  - 采集进度/字段映射预览/目标表 DDL 预览作为持久化 Tab（Task 6 底座）。

- [ ] **Step 10.5: 定义验收**
  - 通用 HTTP skill：对公开 JSON API（如 JSONPlaceholder）完成"AI 一句话采集 → 建表 → 查询验证"全链路。
  - Backend: `mvn compile -q` + IngestionTaskService 测试。
  - E2E: Playwright "采集 https://jsonplaceholder.typicode.com/posts 并存到当前库" → 验证表存在 + 行数正确。

### Task 14: 智能报告生成

**定位**：三期统一产出层。将 DataTalk 中**所有**数据来源和分析结果转化为正式报告——PDF / HTML / Markdown 三种格式全支持。这不是"导入数据的附属功能"，而是对整个平台分析成果的完整包装与分发能力。

**核心原则**：用户在任何上下文中产出的分析成果——对话、查询、Dashboard、ER 图、导入数据、外部采集数据——都可以一句话生成报告。报告是 DataTalk 分析能力的"最终一公里"。

**Files:**
- Create: `docs/product-specs/YYYY-MM-DD-intelligent-report-generation-design.md`
- Create: `docs/exec-plans/YYYY-MM-DD-intelligent-report-generation-plan.md`
- New: `server/data-talk-application/src/main/java/com/datatalk/application/report/ReportGenerationService.java` (报告模板引擎 + 渲染 + 多源数据收集)
- New: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/ReportController.java` (报告生成 + 下载端点)
- New: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/GenerateReportAction.java` (MCP tool: `datatalk_generate_report`)
- New: `client/src/features/stage/components/report-preview-tab.tsx` (报告预览 Tab)
- Modify: `client/src/features/chat/components/` (report artifact 内联卡片)
- New: `server/data-talk-adapter/src/main/resources/report-templates/` (默认 HTML/Markdown 模板)

- [ ] **Step 14.1: 报告内容来源（全平台数据源）**

  报告聚合以下**所有** DataTalk 中可用的数据和分析成果：

  | 来源 | 说明 |
  |------|------|
  | **对话分析** | 当前 session 中 AI 与用户的完整分析过程、关键发现、结论、推理链 |
  | **SQL 查询结果** | 任意查询结果集（表格 + AI 解读），支持多结果集组合 |
  | **Dashboard 大屏** | Dashboard 图表数据 + ECharts 渲染截图 + 业务指标摘要 |
  | **ER 图** | ER Designer/Inspector 中的表结构关系图（PNG 截图 + DDL） |
  | **导入的数据 (Task 13)** | 用户上传并导入的结构化数据及其分析结果 |
  | **外部采集数据 (Task 10)** | Skill 采集的电商/HTTP API 数据及其分析结果 |
  | **文件分析 (Task 12)** | 上传文件（CSV/Excel/JSON）的内容摘要和分析结论 |
  | **用户自定义** | 附加文字说明、标题、章节结构、公司 Logo/水印（自然语言描述即可） |

  AI 根据对话上下文自动判断哪些来源应纳入报告，用户可一句话调整——"加上昨天的订单表对比"、"把 ER 图去掉"。

- [ ] **Step 14.2: 报告格式——三种格式全支持**

  - **Markdown**（.md）：最轻量。直接输出 Markdown 文件，支持代码块/表格/图片链接/任务列表。走 File Artifact 系统归档，可在 Files Library 和 file_preview Tab 中直接预览。适合技术文档、分析笔记。
  - **HTML**（.html）：自包含单文件 HTML（内联 CSS + 图表渲染），可直接在浏览器打开、打印为 PDF、或嵌入邮件。复用 bezel letterpress 自包含 HTML 模式——所有资源内联，零外部依赖。适合正式报告、业务汇报。
  - **PDF**（.pdf）：后端 HTML→PDF 转换（OpenHTMLToPDF 或 Playwright headless print），输出可分发的最终文档。适合存档、打印、对外交付。
  - **用户一句话切换格式**：AI 默认生成 Markdown，用户说"导出为 PDF"即切到 PDF，"生成 HTML 报告发给我"即出 HTML。

- [ ] **Step 14.3: 报告模板与风格**

  - 默认模板：`report-templates/` 目录存放中文/英文各一套默认模板（含封面页、目录、页眉页脚、字体层级）。
  - 自然语言换肤：用户无需理解模板语法——"用深色主题"、"图表放前面、表格放附录"、"加公司 Logo"——AI 自行调整模板参数。
  - 模板可扩展：后续可沉淀行业模板（电商运营周报、财务月报、安全审计报告），作为 skill 或预设分发。
  - 图表渲染：报告中的图表复用 bezel ECharts → SVG/PNG 渲染管线，保证与 Dashboard 视觉一致。

- [ ] **Step 14.4: AI 报告生成流程**

  - MCP `datatalk_generate_report` tool 参数：
    - `title` — 报告标题（必填，AI 从对话推断默认值）
    - `sources` — 内容来源列表（sessionId / queryResultIds / dashboardId / erDesignerId / fileAnalysisId / ingestionTaskId），AI 自动选择默认勾选
    - `format` — `"md"` | `"html"` | `"pdf"`（默认 `"md"`）
    - `style` — 风格描述（可选，自然语言）
    - `sections` — 章节结构（可选，AI 自动生成默认结构）

  - `ReportGenerationService` 流程：
    1. **数据收集**：根据 `sources` 并发拉取所有源数据（对话摘要、查询结果、Dashboard 渲染截图/数据、ER 图、导入数据摘要、采集数据摘要等）。
    2. **Prompt 组装**：源数据 + 模板 + 风格要求 → 结构化 prompt（分章节，每章绑定数据源）。
    3. **AI 生成正文**：通过当前 session 的 OpenCode 通道逐章生成报告正文。
    4. **渲染**：Markdown 直出 / HTML 模板填充 + ECharts 图表内联 / HTML→PDF 转换。
    5. **归档**：产物写入 File Artifact 系统（kind=`report`，scope=`workspace`），进入 Files Library。
  - 大报告（>10 页）自动分章节生成，避免上下文溢出。

- [ ] **Step 14.5: 前端报告体验**

  - Chat 内联卡片：报告生成完成后，聊天中出现 `ReportCard`——标题 + 格式 icon（Md/Html/Pdf）+ 页数/大小摘要 + 预览 + 下载按钮。
  - 报告预览 Tab：`report_preview` Tab type——
    - Markdown → 复用现有 Monaco/Markdown 渲染器
    - HTML → iframe sandbox（与 Dashboard Tab 同模，`sandbox="allow-scripts"`）
    - PDF → 浏览器内置 PDF viewer（`<iframe src="blob:...">` 或 `object` 标签）
  - 一键下载：对应格式文件直接触发浏览器下载。
  - 报告历史：Files Library 按 `kind=report` 筛选，查看所有历史报告。
  - 重新生成：用户可在报告预览 Tab 中改格式/改风格/加来源，触发 AI 重新生成（保留旧版本，走 File Artifact 版本递增）。

- [ ] **Step 14.6: 与 Dashboard / ER / 各模块的关系**

  - **Dashboard → Report**：Dashboard Tab 工具栏"导出为报告"按钮，一键将当前 Dashboard 数据 + 图表渲染为 HTML/PDF。
  - **ER → Report**：ER Designer Tab "导出到报告"，输出表结构关系图 + DDL 脚本 + 字段说明。
  - **查询结果 → Report**：SQL 结果面板"添加到报告"，将当前结果集加入报告数据源列表。
  - **对话 → Report**：Chat 面板"生成本次分析报告"，将当前 session 的完整分析过程打包为报告。
  - Report 是消费方，不是替代方——它不替代 Dashboard 的实时交互，不替代 ER Designer 的编辑能力，不替代 SQL 编辑器的查询功能。

- [ ] **Step 14.7: 定义验收**
  - Backend: `mvn compile -q` + ReportGenerationService 多源数据收集测试 + GenerateReportAction IT + ReportController MockMvc + HTML→PDF 转换测试。
  - Frontend: `npx tsc --noEmit` + ReportPreviewTab 三格式渲染 vitest + ReportCard 组件测试。
  - E2E: Playwright 覆盖 5 条关键路径——
    1. "根据当前对话生成 PDF 报告" → 验证下载
    2. "把这个 Dashboard 导出为 HTML 报告" → 验证自包含 HTML
    3. "生成包含查询结果和 ER 图的 Markdown 报告" → 验证多源聚合
    4. 导入 CSV → 分析 → 生成报告 → 验证导入数据出现在报告中
    5. 外部采集 → 分析 → 生成报告 → 验证采集数据出现在报告中

---

## Ordering And Parallelism

```
Task 12 (文件上传) ──┬──> Task 13 (导入导出) ──┐
                    │                          │
                    └──> Task 10 (外部采集) ──┤
                                              │
                    Task 8 (Dashboard) ───────┼──> Task 14 (报告生成)
                                              │
                    Task 6 (ER Designer) ─────┤
                                              │
                    对话/查询结果 ─────────────┘
```

- **Task 12 必须最先**：文件上传是所有"数据进 DataTalk"的统一入口，Task 13 的导入和 Task 10 的采集结果落库都依赖它。
- **Task 13 第二**：导入导出是数据流通闭环的核心，被 Task 10 和 Task 14 依赖。
- **Task 10 与 Task 13 可部分并行**：通用 HTTP skill 脚手架可在 Task 13 导入管线开发期间并行设计；但落库集成必须等 Task 13 `DataImportService` 就绪。
- **Task 14 最后**：报告生成是**所有分析成果的统一出口**——消费全平台数据源（对话、查询、Dashboard、ER 图、导入数据、外部采集数据），聚合为正式报告。依赖 Task 13 的导出管线（HTML/PDF 渲染）+ Task 8 的 Dashboard 底座 + Task 6 的 ER 图数据 + Task 10/12 的数据入口。

## Verification Gates

Every child implementation plan created from this roadmap must include:

- Backend compile gate: `cd server && mvn compile -q`
- Backend full gate when server behavior changes: `cd server && mvn clean verify`
- Frontend type gate when client behavior changes: `cd client && npx tsc --noEmit`
- Focused frontend tests for changed components
- E2E smoke: Playwright 覆盖关键用户路径（上传→导入、对话→导出、对话→报告生成）
- Documentation housekeeping: plan checkbox status, `docs/exec-plans/index.md`, `docs/product-specs/index.md`

## Exit Criteria

- [ ] Task 12 shipped: AI 输入框支持 CSV/Excel/JSON/SQL/TXT 拖拽/粘贴上传，AI 正确识别结构化/非结构化并给出导入/分析/报告建议。
- [ ] Task 13 shipped: 自然语言导入导出全链路打通——一句话导出表/查询结果到 CSV/JSON/SQL/Excel，上传文件导入建表 + L2 确认。
- [ ] Task 10 shipped: 通用 HTTP/REST skill 脚手架可用，至少一个示范采集→落库→查询链路跑通。
- [ ] Task 14 shipped: 基于对话/查询结果/Dashboard 生成 Markdown/HTML/PDF 报告，产物进入 File Artifact 系统。
- [ ] All child plans registered in `docs/exec-plans/index.md` and `docs/product-specs/index.md`.
- [ ] Phase 2 roadmap (`2026-04-25-next-implementation-roadmap-plan.md`) updated: Task 10 moved from placeholder to active + Phase 3 roadmap reference added.
