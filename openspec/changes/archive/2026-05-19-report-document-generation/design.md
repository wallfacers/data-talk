## Context

DataTalk 现状的报告/可视化产物谱系：

| 已有产物 | 适用场景 | 与"汇报文档"差异 |
|---|---|---|
| `data-export` MCP action（CSV/JSON/Excel/SQL_INSERT） | 单查询导出 | 只导一张表，无叙事、无图表 |
| bezel dashboard（v2 JSON + 自包含 HTML + iframe + polling） | 实时大屏 | 深色科技风、单源 widget、轮询刷新——不适合"冻结快照 / 邮件 PDF / 打印归档" |
| chat markdown / chart fence | 临时交流 | 不持久化、不导 PDF、不可分享 |
| dashboard 派生 file artifact（kind='dashboard'） | session 内查看 | 跟 session 绑死，无 workspace 维度库 |

汇报文档的关键诉求是 **冻结、跨源、叙事、印刷品** 四件套，没有一项能复用现有产物。

需要复用的是更底层的工程模式：
- **AI 产 JSON + 自包含 HTML** 的双件 artifact promote（dashboard 已跑通）
- **构建期 vendor + 启动期解压** 的 skill 释放机制（`OpenCodeBinaryResolver` 已跑通）
- **`@DataTalkAction` 注册 + Streamable HTTP / SSE** 的 action 协议
- **跨连接 SQL 执行**：`ExecuteSqlAction` 已支持显式 `connectionId` 入参，AI 多次调用即可（[server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java:496](../../../server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java)）

利益相关方：
- 终端用户（业务、产品、技术负责人）：要求文档可邮件转发、可打印、视觉正式
- AI agent（OpenCode）：需要清晰的 skill 指引与稳定的 promote 合约
- 后端：要承接 Playwright + Chromium 依赖、字体资源、Flyway 迁移
- 前端：要新增 workspace 维度持久化的 tab 类型（不与 stage session 切换联动）

## Goals / Non-Goals

**Goals:**

1. AI 能根据用户的"周报 / 月报 / 复盘"诉求，自主选模板、跨连接取数、写 narrative、提交 promote
2. 一份 `report.json` 派生出**视觉一致**的三态产物：HTML（屏上看）/ PDF（邮件、打印）/ Markdown（git 仓库、knowledge base）
3. 报告进入 workspace 级"报告库"，**与会话寿命解耦**——会话归档不带走报告
4. 中文字体在 PDF 中正确显示（不外网依赖）
5. 视觉气质：白底 / 衬线主体 / 黑灰 + 单一品牌色 / 章节编号 / 页眉页脚——企业咨询报告范式
6. v0 提供 2 套模板（业务月报 + 问题复盘），验证模板抽象成立

**Non-Goals:**

1. **不复用 bezel 视觉系**：报告 ≠ 大屏，CSS / 字体 / 调色板完全独立
2. **不做 polling / 实时刷新**：报告是冻结快照，刷新需用户主动"重新生成"
3. **不做"从 dashboard 另存为报告"**：两套体系边界清晰；v1+ 再评估
4. **不做定时调度**（如"每周一 9 点自动生成销售周报"）：v1+ 再评估
5. **不做跨用户共享 / 协同**：单用户单 workspace
6. **不做版本对比 diff**：仅保留版本号字段供未来扩展
7. **不做客户端打印**：PDF 一律服务端 Playwright 渲染，保证一致性
8. **不引入新数据库类型**：跨源能力靠现有 `ExecuteSqlAction`

## Decisions

### D1：报告作为独立 capability，不是 dashboard 子类型

**选项 A（采纳）**：新建 `report-document-rendering` / `report-library` / `report-data-contract` 三个 capability，与 bezel dashboard 完全平行
**选项 B**：扩展 dashboard schema，加 `kind: 'report'` 分支复用 widget 体系
**选项 C**：极简，只做 markdown 模板，不做 HTML/PDF

**Why A**：dashboard 的核心抽象是 widget × endpoint × polling，与冻结快照 + 章节叙事 + 印刷分页的范式根本冲突。强行复用会让 schema、渲染器、AI 心智模型同时背两套契约，长期成本远高于新建。bezel pattern catalog 的视觉资产不可复用（深色 / 玻璃拟态 / 自由 Grid）；ledger 必须独立设计语言。

### D2：PDF 渲染 = 服务端 Playwright headless Chromium

**选项 A（采纳）**：Playwright Java SDK（`com.microsoft.playwright:playwright`）直接在 JVM 内启动 Chromium 进程，`Page.pdf(PdfOptions)` 是 Java API 调用（不经 Node）
**选项 B**：Flying Saucer / iText 纯 Java PDF 库
**选项 C**：wkhtmltopdf 命令行
**选项 D**：客户端浏览器打印对话框

**Why A**：
- 保真度：HTML 怎么显示，PDF 就怎么打印。CSS3、Web Font、SVG、ECharts 全支持
- 中文：和 HTML 共用 `@font-face` 内嵌字体路径，零额外配置
- ECharts：`Page.waitForFunction(() => window.__LEDGER_READY__)` 显式同步等待，避免 PDF 空白图表（BUG-0051 教训）
- 无 Node 运行时依赖：Java SDK 直接管理 Chromium 进程，首次启动通过 `Playwright.create()` 触发本地缓存的浏览器准备

**选项 B 否决**：Flying Saucer 对 Flex / Grid / CSS3 支持有限，企业报告的现代排版做不出；iText 收费且 API 重
**选项 C 否决**：wkhtmltopdf 用旧版 WebKit，中文字体、`break-inside` 行为不稳，社区已停维
**选项 D 否决**：客户端打印产出不可控，邮件转发时收件方再打印结果不一致；汇报场景要求"作者发一份 PDF，下属/上级看到的是同一份"

**代价**：Chromium ≈300MB 服务端依赖。缓解：服务端启动时异步预热（首次调用 `Playwright.create().chromium().launch()` 触发 SDK 缓存的浏览器下载或直接启动），未就绪前 PDF / MD 按钮显示"准备中…"；客户端不受影响。

### D3：数据冻结策略 = 生成时一次性 inline 进 `report.json`

**选项 A（采纳）**：AI 调多次 `datatalk_query_data` 后，把每个数据块的 `rows / columns` 直接写进 JSON
**选项 B**：JSON 只存 `{ connectionId, sql }` 引用，HTML 加载时取数（同 dashboard）
**选项 C**：JSON 存引用，但生成时 server-side 取数缓存到 sidecar 文件

**Why A**：
- "冻结"是汇报文档的核心语义——发出去的 PDF 内容不可变
- PDF / Markdown 派生路径天然要求数据已在手
- 数据来源审计自然落地（每块 inline 旁标 `source: "mysql-prod · sales_summary"`）

**选项 B 否决**：HTML 加载时取数 = 报告"活着"，与冻结语义矛盾；PDF 无法派生
**选项 C 否决**：增加 sidecar 文件治理复杂度，无明显收益

**代价**：大表会让 JSON 变大。缓解：单 table block inline 默认上限 200 行；超过则 inline 前 N 行 + 附录链接到完整 CSV（复用 `data-export` 的临时文件 + 下载端点）。

### D4：报告归属 = workspace 维度独立"报告库"，与 session 解耦

**选项 A（采纳）**：新增 SQLite `report` 表，`workspace_id` 字段；前端 workspace tab 显示报告列表
**选项 B**：报告是 session-scoped file artifact（同 dashboard）

**Why A**：
- 用户的语义模型：报告是"作品 / 资产"，不是"会话过程的副产品"
- 汇报场景里报告要长期保留、可被多个 session 引用
- 重新生成时 AI 可从其他 session 触发

**选项 B 否决**：session 归档时报告也带走 / 难以发现 / 难以重新生成

**代价**：要写 Flyway migration、新表 + repo + REST 端点；workspace 维度持久化策略要与现有 `connection` / `data-source` 一致。

### D5：模板 v0 = 业务月报 + 问题复盘 两套

**Why**：
- 业务月报：覆盖"周期性正向汇报"——KPI 概览、趋势章节、对比表、结论建议
- 问题复盘：覆盖"事件性归因汇报"——事件时间线、影响范围、根因分析、行动项
- 两者结构差异大，能验证模板抽象是否真的成立（如果两个模板都需要的 section 类型，就是核心 section primitive）

**v1+ 候选**（不在本 change）：季度 OKR 复盘、新功能发布报告、用户调研报告、数据治理报告。

### D6：Markdown 中图表 = 派生 PNG 链接到 file artifact

**选项 A（采纳）**：复用 PDF 渲染的同一 Chromium 实例，加载 HTML 后对每个 chart DOM 元素 `Locator.screenshot()` 截图为 PNG，落 file artifact，Markdown 用 `![caption](path/to/chart.png)`
**选项 B**：ECharts SSR + node-canvas（需引入 Node 运行时）
**选项 C**：Java `org.icepear.echarts` + Java 2D（保真度未知）
**选项 D**：SVG inline 嵌入
**选项 E**：纯 ASCII art / 表格代替图表

**Why A（详见 D12）**：

服务端已经为 PDF 引入 Playwright Chromium 依赖，复用同一实例截图 chart DOM 是零额外依赖、零保真损失的路径。Chart 在 HTML 中的渲染结果就是 Markdown PNG 看到的结果——视觉一致性免费。

**选项 B 否决**：引入 Node 运行时是个完全独立的重依赖
**选项 C 否决**：Java 端 ECharts 实现的视觉保真度与浏览器版本差异未知
**选项 D 否决**：复杂 SVG 在很多 viewer 中渲染异常
**选项 E 否决**：丢失视觉信息

### D7：中文字体内嵌到 ledger skill 资源

**选项 A（采纳）**：思源宋体 Regular + 思源黑体 Regular 打包进 ledger skill 的 `assets/fonts/`，HTML 用 `@font-face` 引相对路径，PDF 渲染时字体已就绪
**选项 B**：依赖系统字体
**选项 C**：从 CDN 加载

**Why A**：BUG-0049 已踩过 CDN 不可达导致中文乱码的坑；系统字体在 macOS / Windows / Linux 三平台行为不一致，无法保证 PDF 一致性。

**代价**：字体文件体积（每个 Regular 字重 ≈10-20MB；只装常规字重，不装多字重）。

### D8：ECharts 静态渲染 = 等 `chart.on('finished')` 后通知 Playwright

**Why**：BUG-0051 教训。Playwright 看到 DOMContentLoaded 不等于 chart 已绘完。ledger HTML 在所有 chart `finished` 事件触发后设置 `window.__LEDGER_READY__ = true`，PdfRenderer `await page.waitForFunction(() => window.__LEDGER_READY__)` 再 `page.pdf()`。

### D9：MCP action 单一 promote 入口，不拆分多 step

**选项 A（采纳）**：`datatalk_promote_report` 一次性接收完整 `report.json`，服务端原子写入 + 同步派生 HTML/Markdown + 异步派生 PDF
**选项 B**：拆 `report_create_draft` / `report_add_section` / `report_promote` 多步

**Why A**：
- AI 的输出已经是结构化 JSON，一次性 emit 最经济
- 多步会出现"半成品"中间态，要额外做 GC、超时、状态机
- 失败重试只需重新 emit 完整 JSON，幂等性好

**代价**：单次 JSON payload 可能较大（含 inline 数据）。缓解：D3 的"单 table 上限 200 行"约束保护 payload。

### D10：Skill 命名 = `ledger`

候选：`ledger` / `briefing` / `dispatch` / `memo` / `chronicle`

**Why `ledger`**：会计/财务/汇报语义，跟 bezel（"边框/装饰条"）形成对位——bezel 是面向观众的展示面，ledger 是面向管理层的账本/报表，语义清晰。

### D11：跨源 SQL 编排 = 复用 `ExecuteSqlAction`，不新增 action

`ExecuteSqlAction.execute` 已经按 `request.connectionId → sessionContext.connectionId → ctx.connectionId` 优先级解析（[server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java:496](../../../server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java)）。AI 在生成报告时按需多次调用，每次显式传 `connectionId`。ledger skill 的 `data-contract.md` 必须强制要求"每次 query 必传 `connectionId`，不依赖 session 默认"。

### D12：派生产物状态机拆分 + Chromium 异步流程

派生路径整理：

```
report.json (AI emit)
    │
    ▼
[同步] HTML 派生
    └─→ HTML 自包含，浏览器端 ECharts 渲染，无需 Chromium
[异步] PDF + Markdown 派生（共享 Chromium 实例）
    ├─→ Chromium 加载 HTML
    ├─→ waitForFunction(window.__LEDGER_READY__)
    ├─→ 对每个 chart DOM 截图 → PNG → file artifact （Markdown 用到）
    ├─→ page.pdf() → PDF 文件
    └─→ Markdown 串行：narrative + table + kpi-strip + PNG link
```

状态字段 SQLite `report` 表拆分为：
- `pdf_status TEXT` (`processing` / `ready` / `failed`)
- `md_status TEXT` (`processing` / `ready` / `failed`)
- `pdf_fail_reason TEXT NULLABLE`
- `md_fail_reason TEXT NULLABLE`

**Why 拆分而非合并**：用户可能只需要 Markdown（贴 Wiki），不需要 PDF；或反之。前端各按钮独立判断状态。失败时一个 derivative 失败不影响另一个。

前端通过 TanStack Query 轮询 `GET /api/reports/{id}`（interval 3s，配合 `query.refetchInterval` + `select` 终止条件），当 `pdf_status` 或 `md_status` 变 `ready` / `failed` 时按钮状态更新。轮询在两个 status 都终止时停止。

### D13：重新生成的 `group_id` 关联

`report` 表新增 `group_id TEXT NOT NULL`（同一份报告的所有重新生成版本共享）+ `version INTEGER NOT NULL DEFAULT 1`（在 group 内单调递增）。

- 第一次 promote：`id = r-N, group_id = g-M, version = 1`
- 重新生成：`id = r-N+1, group_id = g-M, version = 2`（同 group_id）

报告库列表 UI **按 group_id 折叠**：默认显示每个 group 的最新版本；点击展开可看到该 group 的历史版本。

**Why 不是 `previous_version_id` 链表**：链表查询要递归，列表 UI 想"按 group 折叠"得递归到根。`group_id` 是扁平的，单 query + group by 就能拿到列表。

### D14：`user_prompt` 字段保留以支持重新生成（OQ1 决定）

`report` 表新增 `user_prompt TEXT NULLABLE`。promote 时 AI 在 `report.json.meta` 中带上原始 user prompt（"做一份 2026 年 4 月销售月报"），服务端存入此字段。v0 仅存储不消费；v1+ 重新生成时可读取并自动注入新 session 作为 user message。成本极低，留接口给未来。

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Chromium 依赖 ≈300MB，首次启动慢 | 服务端启动时异步 `Playwright.create().chromium().launch()` 触发 SDK 浏览器缓存就绪；PDF / MD 按钮在 ready 前显示"准备中…"；ledger skill 文档明示"PDF / Markdown 导出需要服务端首次准备 ~1 分钟" |
| PDF 中文乱码（BUG-0049 同源风险） | 字体打包进 ledger skill assets，`@font-face` 引本地路径；启动 Playwright 时确认字体路径可访问 |
| iframe 报告查看器白屏（BUG-0051 同源风险） | 报告 HTML 完全自包含（CSS / JS / 字体均内嵌或本地），无外网依赖；前端 iframe `onload` 后才隐藏 loader |
| ECharts 在 PDF 中渲染未完成 → 空白图 | `window.__LEDGER_READY__` 信号 + Playwright `waitForFunction` 同步 |
| 大表 inline 让 JSON 爆炸 | 单 table block ≤ 200 行硬上限；超出走附录 CSV 下载 |
| PDF 分页时图表/表格被切断 | ledger CSS 全局 `.chart, .table-block { break-inside: avoid; }`；表格行 > 30 时强制分页 |
| Markdown 中 PNG 路径在不同 viewer 失效 | 同时输出 absolute + relative 两个 Markdown 变体（默认 relative；导出绝对路径选项给"我要发邮件 / 上传到 wiki"场景） |
| AI 多次 query_data 累积时延 | ledger skill 明示"先列查询清单，逐个 query，最后一次性 promote"；每个 query 用 streaming SSE 反馈进度 |
| 模板 v0 抽象过早 | v0 完成后留 1-2 周观察期，第 3 套模板再加时若发现共性 section 不够才重构抽象 |
| Playwright Chromium 崩溃 / 卡死 | PdfRenderer + ChartCaptureRenderer 加 60s 超时；失败时 promote 仍返回 success（HTML 已派生），PDF / MD 用各自 status 字段异步轮询；Chromium 实例池化，避免每次重启 |
| workspace 报告库的"重新生成"如何复现旧上下文 | v0 重新生成 = 在当前会话以 templateId + 原始用户诉求重新走一次 AI；不强保证 byte-for-byte 一致（数据可能已变） |

## Migration Plan

**Step 1：后端骨架**

1. 新 Flyway migration `V4__create_report_table.sql`（位于 `server/data-talk-infrastructure/src/main/resources/db/migration/`）；字段：`id` / `workspace_id` / `group_id` / `version` / `title` / `subtitle` / `template_id` / `template_version` / `accent_color` / `generated_at` / `generated_by_session_id` / `user_prompt` / `artifact_paths_json` / `pdf_status` / `md_status` / `pdf_fail_reason` / `md_fail_reason`
2. domain：`Report` record + `ReportTemplate` 枚举 + `ReportDerivativeStatus` 枚举
3. application：`ReportArtifactService`、`ReportRenderer`（HTML 同步派生）、`MarkdownRenderer`（依赖 chart PNG 输出后回填）
4. infrastructure：`JdbcReportRepository`、`PdfRenderer`（Playwright Java SDK）、`ChartCaptureRenderer`（Playwright 截图 chart DOM），`ChromiumLifecycle`（启动期异步预热 + 池化 + shutdown 清理）
5. adapter：`PromoteReportActionHandler`（`@DataTalkAction`）、`ReportController`（REST）、`LedgerSkillResolver`（与 bezel 同层，源 `server/data-talk-adapter/src/main/resources/skills/ledger/`）

**Step 2：Skill 打包**

1. 创建 `server/data-talk-adapter/src/main/resources/skills/ledger/` 目录树
2. 写 `SKILL.md` / `templates/monthly-business-review.md` / `templates/incident-postmortem.md` / `design-language.md` / `data-contract.md` / `section-patterns.md`
3. `assets/fonts/`：思源宋体 Regular + 思源黑体 Regular
4. `assets/styles/ledger.css`：商务排版 CSS
5. 构建期 vendor 到 infrastructure 资源；启动期 `OpenCodeBinaryResolver` 复用模式解压到 `data-talk/.opencode/skills/ledger/`

**Step 3：前端**

1. `client/src/features/report/` 新模块：schema、API hooks、components
2. `tab-type-registry.ts` 注册 `report-library` / `report-viewer` 两种 workspace-scope tab type
3. 报告库 tab UI（列表 / 搜索 / 打开）
4. 报告查看器 tab UI（iframe sandbox 加载 HTML + 顶栏按钮：导出 PDF / 导出 MD / 重新生成）
5. chat 内 promote 完成卡片组件

**Step 4：Playwright 依赖**

1. Maven 加入 `com.microsoft.playwright:playwright` 依赖到 `data-talk-infrastructure/pom.xml`
2. 后端启动时异步 `ChromiumLifecycle.start()` ensure（首次自动下载 SDK 缓存的浏览器），状态记入 `ReportSystemStatus` bean
3. 提供 `GET /api/reports/system-status` 端点供前端 PDF / MD 按钮判定
4. shutdown hook 关闭 Chromium 进程 + 清理 Playwright 实例

**Step 5：MCP 注册**

1. `agent-skill-routing` 注册 `datatalk_promote_report` 工具
2. ledger skill 触发条件加入意图列表（周报 / 月报 / 季报 / 复盘 / 汇报 / 总结 / 报告 / report / weekly / monthly / postmortem）

**Step 6：测试**

1. `JdbcReportRepository` + `ReportArtifactService` 单测
2. `PromoteReportActionHandler` WireMock 集成测试
3. `ReportRenderer` HTML/MD 输出黄金测试（fixture JSON → 期望 HTML/MD）
4. `PdfRenderer` 集成测试（CI 跳过本地跑，按需运行）
5. 前端 vitest：report tab 注册、查看器加载、按钮 enable/disable
6. E2E（Playwright client 端，区别于 server 端 PDF 用 Playwright）：用户对话 → AI 生成报告 → 列表 → 查看 → 导出 PDF

**Rollback：**

- Flyway 迁移可写对应 down migration；
- ledger skill 解压在每次启动期幂等，删除目录或回滚 jar 即恢复；
- `datatalk_promote_report` action 删除后老报告仍可在报告库查看，但无法新建——这是优雅降级。

## Open Questions（已解决）

1. **重新生成保留 user prompt**：✅ 已决。`report` 表加 `user_prompt TEXT NULLABLE` 字段；promote 时 AI 从 `report.json.meta.userPrompt` 传入。v0 仅存储不消费；v1+ 用来复现重新生成。详见 D14。
2. **模板的本地化**：✅ 已决。v0 中文 only。模板是 SKILL.md 级别内容，后续加英文模板只需新增 `templates/monthly-business-review.en.md`，无 schema 变更。
3. **PDF 水印 / 抬头 logo**：✅ 已决。v0 不做。spec 已正确留了 `cover.logoUrl?` 可选字段。v1+ 加 workspace 配置 UI。
4. **报告库容量上限**：✅ 已决。v0 不设硬上限。单 workspace 报告量自然受限，SQLite 量级远不是瓶颈。v1+ 如出现滥用再按 LRU。
5. **附录 CSV 落 file artifact**：✅ 已决。落 `kind='report-data-csv'`，与 report 同生命周期，delete 时级联清理。详见 spec `report-document-rendering` 的"大表 inline 上限与附录 CSV 派生" requirement。

## 新增 Open Questions

1. **Chromium 实例池化大小**：单实例顺序处理（FIFO 队列）vs 多实例并行？倾向单实例 v0，简化生命周期；并发报告生成压测后再决定是否池化。
2. **chart 截图视口尺寸**：PNG 宽度固定 1200px（高 DPI 适配 retina）vs 跟随 HTML 渲染时的 chart 容器宽度？倾向固定 1200px @ 2x DPI，保证 Markdown 视觉一致。
3. **`user_prompt` 长度上限**：是否需要截断？倾向 4KB 软上限，超出时只存截断版本 + 标志位。
