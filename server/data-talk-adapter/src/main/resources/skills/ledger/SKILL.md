---
name: ledger
description: |
  企业级汇报文档生成（业务月报、问题复盘、季度总结、事件postmortem）。
  与 bezel skill 的边界：bezel 做实时大屏 dashboard（深色科技风、单源 widget、轮询刷新）；
  ledger 做冻结快照报告（白底衬线印刷品风、跨源叙事、PDF 邮件转发与打印归档）。
  二者**不可互相替代**：用户说"做大屏"用 bezel，用户说"做汇报/周报/月报/复盘"用 ledger。
triggers:
  - 周报
  - 月报
  - 季报
  - 季度复盘
  - 复盘
  - 汇报
  - 总结
  - 报告
  - postmortem
  - weekly report
  - monthly report
  - quarterly review
  - 事故复盘
  - 业务月报
---

# Ledger — 企业级汇报文档生成

## 触发场景

当用户表达以下意图，**必须**使用本 skill 而不是 bezel：

- "做一份 2026 年 4 月的销售月报"
- "Q1 数据库性能事件复盘"
- "汇报一下上周 GMV 数据"
- "给老板看的季度总结"

如果用户说"做个大屏" / "实时看板" / "dashboard"，使用 bezel skill，**不要**使用本 skill。

## 模板索引

| templateId | 适用场景 | 必备 section | 说明 |
|---|---|---|---|
| `ledger.monthly-business-review.v1` | 业务月报 / 周报 / 季报 | `cover`, `executive-summary`, `toc`, `chapter(业务总览)`, `chapter(渠道表现)`, `chapter(区域分析)`, `chapter(风险与建议)`, `appendix(sql-listing)` | KPI 概览 + 趋势章节 + 对比表 + 结论建议；正向汇报 |
| `ledger.incident-postmortem.v1` | 问题复盘 / 事故 postmortem | `cover`, `executive-summary`, `chapter(事件概况)`, `chapter(影响范围)`, `chapter(根因分析)`, `chapter(行动项)`, `appendix(glossary)` | 时间线 + 影响范围 + 根因 + 行动项；事件性归因汇报 |

模板详情见 `templates/monthly-business-review.md` 与 `templates/incident-postmortem.md`。

## 报告生成六步流程

严格按此顺序执行，**禁止边写边 promote、禁止分多次 promote 同一报告**。

1. **确认报告目的与时间窗口**：跟用户对齐"是月报还是季报？时间窗口是几月几日到几月几日？给谁看？关注哪个业务线？"。
2. **根据用户意图选定 templateId**：从模板索引表选择最贴合的 templateId；不确定时优先选 `ledger.monthly-business-review.v1`。
3. **列出该模板所需的全部数据查询清单**：把整份报告需要的所有 SQL 一次性列出，每条标注 `connectionId` + SQL 草稿 + 预期返回的列。不要边写章节边发现需要新数据。
4. **跨连接逐项 `datatalk_query_data` 取数并把结果 inline 写进 report.json**：每个查询调用 `datatalk_query_data` 时**必须显式传 `connectionId`**，不依赖 session 默认 connection。把返回的 rows / columns 直接写进对应 block 的 inline 数据字段。
5. **对超过 200 行的 table block：先 export CSV，再 inline 前 N 行 + 引用 appendixCsvRef**：
   - 先调用 `datatalk_export_data(format='csv', sql=<完整 SQL>)` 得到 `{ fileArtifactId }`
   - 写 table block 时设 `appendixCsvRef: <fileArtifactId>`，inline `rows` 只保留前 N 行（N ≤ 200，建议 50-100 行做预览）
   - **不可**直接 inline 超过 200 行的数据，否则 promote 会被拒（`REPORT_TABLE_OVERSIZE_NO_APPENDIX`）
6. **一次性 `datatalk_promote_report`（含完整 report.json，包括各 appendixCsvRef 引用）**：在所有数据取齐、所有 narrative 写完之后，**一次性 single call** 提交完整 `report.json`。服务端原子写入、同步派生 HTML、异步派生 PDF + Markdown。
   - **promote 前逐 block 自检字段名**（写错会被校验拒绝、且渲染成空白）：`narrative`→`markdown`（不是 content/text）、`executive-summary`→`bullets`（不是 blocks）、`kpi-strip`/`risk-list`→`items`（risk-list 每项用 `severity`+`description`，不是 risks/level）、`chart`→必有 `echartsOption`（`id` 可省略，服务端自动补）、`table`→`columns` 为 `string[]`、`rows` 为 `string[][]`（不是对象数组）。完整清单见 `data-contract.md` §3。
   - **禁止**"先 promote 一个空壳，再分多次 add section / append data"——服务端不支持，会让 status 状态机失控。
   - **禁止**分多次 promote 同一份报告。每次 promote 都是一个**新版本**（version + 1，同 group_id）。

## 数据叙事撰写规则（有理有据）

ledger 报告的价值在于**有理有据、视觉服务于结论**，而非把查询结果整表丢给读者。组织报告时**必须**遵循：

1. **论点 → 证据链条**：每个关键结论 **MUST** 由具体数据支撑。没有数据支撑的结论性数字视为未完成。
2. **一图一观点**：单个 `chart` **MUST** 只表达一个核心观点。禁止一张图叠加多个互不相关的对比 / callout；需要多观点就拆成多张图，每张配一句结论 narrative。
3. **视觉服务于结论，禁数据倾倒**：富视觉原语（`callout` / `stat-highlight` / `comparison`）**MUST** 用于**强调结论性洞察**，禁止纯装饰。
   - 用 `stat-highlight` 锚定本章最重要的一个数字；用 `callout(insight)` 点出"这意味着什么"；用 `comparison` 做同维度横向对比；用 `quote` 引用关键论断。
   - **禁止"数据倾倒"**：不要把原始查询结果整表丢给读者而不给解读。大表放附录 CSV，正文只留前 N 行预览 + 一段 narrative 解读。
4. **渐进披露**：复杂分析 **MUST** 按"概述 → 分项 → 结论 → 建议"顺序组织——先给基线（kpi-strip / stat-highlight），再给关键对比（comparison / chart），最后给结论与行动（callout / narrative / risk-list）。

> 反问自己：读者读完这一章，能不能用一句话说清楚"发生了什么、为什么、接下来怎么办"？如果不能，说明叙事还没做完。

## 重新生成（用户驱动）

用户说"按上次的样式重新做一份" / "把数据更新到 5 月" / "重新生成"时：
- 必须在 `datatalk_promote_report` 调用中显式传 `groupId`（沿用原报告的 groupId，让服务端把新版本归到同一 group）
- 服务端会自动 `version = max(版本号) + 1`，旧版本保留

## promote 失败的兜底重试（系统驱动）

`datatalk_promote_report` 返回 `{ error, errorCode, errorCodes[], violations[], recoveryHints{} }` 结构时（含 `error` 字段即视为失败）：

1. **一轮修齐**：读 `errorCodes[]` 与 `violations[]` 一次性看到所有违规；按 `violations[i].path` 定位到 report.json 中的具体节点；参考 `recoveryHints[code]` 的中文修复提示，**一轮内**修齐所有违规后重试。
2. **连续失败 2 次必须停止**：如果第二次重试仍然失败，**MUST NOT** 继续 retry —— 停下，把完整 `violations` 列表汇报给用户，请用户决策（修数据 / 改模板 / 放弃）。盲目重试会浪费 token 并掩盖根因。
3. **校验失败重试 MUST NOT 带 `groupId`**：首次 promote 失败时服务端没有产生任何 record，沿用旧 groupId 会被服务端拒绝为 `REPORT_GROUP_NOT_FOUND`（孤立 version）。这与"重新生成"语义不同：
   - **重新生成（用户驱动）**：必带 `groupId` 沿用历史 group。
   - **promote 失败 retry（系统驱动）**：不带 `groupId`，按全新提交。

错误结构字段含义（详见 `data-contract.md`）：
- `errorCode`（string）：第一个违规的 code，保留与单错误码 contract 的向后兼容。
- `errorCodes`（string[]）：全部违规的 code 列表。
- `violations`（array of `{ code, path, message }`）：每条违规的 JSON path 与说明。
- `recoveryHints`（map of code → 中文修复提示）：仅对当前响应中出现的 code 给出提示。

## 模板字段速查

详细 schema 见 `section-patterns.md`。常用 block 类型：
- `cover`（封面：title, subtitle?, author?, date?, logoUrl?；**author 不确定时留空**，禁止写 "DataTalk 自动生成" / "AI 生成" / "自动生成" 等生成器自指词）
- `executive-summary`（摘要：bullets[]）
- `toc`（目录：自动生成）
- `chapter`（章节容器：heading, blocks[]）
- `kpi-strip`（KPI 条带，3-6 项）
- `narrative`（叙事段落，markdown 文本）
- `chart`（图表：echartsOption, caption）
- `table`（表格：columns[], rows[], caption?, appendixCsvRef?）
- `risk-list`（风险/行动项列表）
- `timeline`（事件时间线，复盘专用）
- `appendix`（附录容器）

富视觉原语（用于强调结论，**非装饰**——见"数据叙事撰写规则"）：
- `callout`（key-insight 高亮块：variant ∈ insight/warning/note/success, title?, markdown）—— 点出"这意味着什么"
- `stat-highlight`（hero 关键指标：value, label?, context?, delta?）—— 锚定本章最重要的一个数字
- `comparison`（并列对比卡：items[2-4]{label,value,caption?}）—— 同维度横向对比
- `quote`（pull-quote：text, attribution?）—— 引用关键论断 / 方法论
- `divider`（章节视觉分隔：label?）

`table` 支持可选列级 `cellFormats`（`text`/`bar`/`delta`/`heat`），长度须等于 columns。

任何不在此集合内的 block 类型在 promote 时会被服务端拒绝（`REPORT_BLOCK_TYPE_UNKNOWN`）。

## 与 bezel 的边界（再次强调）

| 维度 | bezel（dashboard） | ledger（report） |
|---|---|---|
| 视觉气质 | 深色科技大屏、玻璃拟态、霓虹 | 白底衬线、现代色彩角色系统（primary/accent/surface + tint 梯度）、富视觉原语 |
| 数据冻结 | 实时 polling，活的 | 一次性 inline 冻结，死的 |
| 跨数据源 | 单 widget 单 endpoint | 一份报告跨多个 connection 取数 |
| 派生产物 | 仅 HTML（iframe 内自渲染） | HTML + PDF + Markdown |
| 归属维度 | session-scoped file artifact | workspace-scoped report 库 |
| 重新生成 | 直接重跑 widget query | promote 新版本，旧版本保留 |
| 适用场景 | 实时监控、大屏展示 | 冻结快照、邮件转发、打印归档 |

**不要**让 ledger 借用 bezel 的视觉资产（深色、玻璃拟态等），见 `design-language.md`。

## 详细参考

- `data-contract.md` — promote 数据契约（跨连接 connectionId 强制、大表附录 CSV）
- `design-language.md` — 现代报告设计语言（色彩角色系统、字号阶、富视觉原语、bezel 边界）
- `section-patterns.md` — 16 种 block 类型详细 schema + HTML 示例 + PDF 分页注意
- `templates/monthly-business-review.md` — 业务月报模板含完整示例 JSON
- `templates/incident-postmortem.md` — 问题复盘模板含完整示例 JSON
