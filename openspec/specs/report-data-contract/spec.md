# report-data-contract Specification

## Purpose
TBD - created by archiving change report-document-generation. Update Purpose after archive.
## Requirements
### Requirement: ledger skill 同步与目录结构

服务端启动时 SHALL 通过现有的 `SkillResourceSyncer.syncSkill("ledger", opencodeCwd)` 将 classpath `skills/ledger/**` 资源目录同步到 `data-talk/.opencode/skills/ledger/`；同步后目录 MUST 包含：`SKILL.md`、`templates/monthly-business-review.md`、`templates/incident-postmortem.md`、`design-language.md`、`data-contract.md`、`section-patterns.md`、`assets/fonts/NotoSerifSC-Regular.otf`、`assets/fonts/NotoSansSC-Regular.otf`、`assets/styles/ledger.css`。

#### Scenario: 启动后目录就绪

- **GIVEN** 服务端首次启动
- **WHEN** Spring context 完成 ready
- **THEN** `data-talk/.opencode/skills/ledger/SKILL.md` MUST 存在
- **AND** `templates/` 下 MUST 含 2 个模板文件
- **AND** `assets/fonts/` 下 MUST 含 2 个 otf 文件
- **AND** `assets/styles/ledger.css` MUST 存在

#### Scenario: 已同步时幂等跳过

- **GIVEN** `data-talk/.opencode/skills/ledger/` 已存在且 SHA-256 marker 与 classpath 内容一致
- **WHEN** 服务端二次启动
- **THEN** 不重新拷贝（`SkillResourceSyncer` 的 marker 比对路径短路）
- **AND** 启动时间 MUST NOT 显著增加

#### Scenario: classpath 内容变更触发重新同步

- **GIVEN** classpath `skills/ledger/` 任一文件内容变更（导致 SHA-256 marker 失效）
- **WHEN** 服务端启动
- **THEN** 旧目录被删除后整体重写
- **AND** 新版本 SKILL.md 生效

### Requirement: `data-contract.md` 强制跨连接显式 `connectionId`

ledger skill `data-contract.md` SHALL 明确要求 AI 在每次调用 `datatalk_query_data` 时**必传** `connectionId` 参数，不依赖 session 默认 connection；任何在 ledger 生成报告流程中省略 `connectionId` 的 query MUST 被 AI 自己识别为错误并修正后重试。`data-contract.md` SHALL 同时记载 `datatalk_promote_report` 返回的错误结构格式（`{error, errorCode, errorCodes[], violations[], recoveryHints{}}`）与每个字段的语义，便于 AI 在 promote 失败时正确解析。

#### Scenario: data-contract.md 含强制条款

- **WHEN** 读取 ledger `data-contract.md`
- **THEN** MUST 含字符串 "每次 query 必传 `connectionId`" 或语义等价表达
- **AND** MUST 给出至少 1 个正面例子（含 `connectionId` 的 query 调用）
- **AND** MUST 给出至少 1 个反面例子（省略 `connectionId` 的反模式 + ✗ 标记）

#### Scenario: data-contract.md 含 promote 错误结构说明

- **WHEN** 读取 ledger `data-contract.md`
- **THEN** MUST 含 `errorCodes` 与 `violations` 与 `recoveryHints` 三个字段的说明
- **AND** MUST 给出至少 1 个错误返回的 JSON 示例

### Requirement: `data-contract.md` 数据来源标注约束

ledger skill `data-contract.md` SHALL 要求 AI 在每个 `table` / `chart` / `kpi-strip` block 旁的 `source` 字段标注数据来源：格式 `<connection-name> · <table-or-summary-name>`，可选附 `as of <ISO-date>`；该字段在 HTML 渲染时呈现于 block 下方小字脚注。

#### Scenario: data-contract.md 含 source 格式要求

- **WHEN** 读取 ledger `data-contract.md`
- **THEN** MUST 给出 `source` 字段格式说明（含 `<connection-name>` 与 `<table-name>` 占位符）
- **AND** MUST 含正面例子 `mysql-prod · sales_summary as of 2026-04-30`

#### Scenario: HTML 渲染显示 source

- **GIVEN** report 中 chart block `source: "mysql-prod · sales_summary as of 2026-04-30"`
- **WHEN** 渲染 HTML
- **THEN** chart 容器下方 DOM 节点 MUST 含 CSS class `ledger-block-source`
- **AND** 该节点文本 MUST 含字符串 `mysql-prod · sales_summary`

### Requirement: `SKILL.md` 入口与模板索引

ledger `SKILL.md` SHALL 含：1 段触发场景描述、模板索引表（含 templateId / 适用场景 / 必备 section 列表）、报告写作 5 步流程（确认目的 → 选模板 → 列查询清单 → 跨连接逐项取数 → 一次性 promote）、与 bezel skill 的边界说明（dashboard ≠ report）。

#### Scenario: SKILL.md 含模板索引表

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 含 Markdown 表格，至少 2 行
- **AND** 行 1 含 `monthly-business-review` 或 `ledger.monthly-business-review.v1`
- **AND** 行 2 含 `incident-postmortem` 或 `ledger.incident-postmortem.v1`

#### Scenario: SKILL.md 含与 bezel 边界说明

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 含字符串 "bezel" 或 "dashboard"
- **AND** MUST 明示二者用途差异（如"dashboard 实时大屏 vs report 冻结汇报"）

### Requirement: 业务月报模板（`ledger.monthly-business-review.v1`）

`templates/monthly-business-review.md` SHALL 定义业务月报的标准结构与 section 顺序：`cover` → `executive-summary` → `toc` → `chapter(业务总览：kpi-strip + narrative + chart)` → `chapter(渠道表现：table + narrative)` → `chapter(区域分析：chart + table + narrative)` → `chapter(风险与建议：risk-list + narrative)` → `appendix(sql-listing)`。模板 MUST 含至少 1 份完整示例 JSON（含真实数据样例）。

#### Scenario: 模板含完整示例 JSON

- **WHEN** 读取 `templates/monthly-business-review.md`
- **THEN** MUST 含至少 1 个 ```` ```json ```` code fence
- **AND** 该 JSON 解析后 MUST 含 `meta.templateId = "ledger.monthly-business-review.v1"`
- **AND** 包含上述全部章节结构

#### Scenario: 模板含必备 section 清单

- **WHEN** 读取模板文件
- **THEN** MUST 含"必备 section"或"required sections"列表
- **AND** 列表包含 `cover`、`executive-summary`、`kpi-strip`、`risk-list`

### Requirement: 问题复盘模板（`ledger.incident-postmortem.v1`）

`templates/incident-postmortem.md` SHALL 定义问题复盘的标准结构：`cover` → `executive-summary` → `chapter(事件概况：timeline + narrative)` → `chapter(影响范围：kpi-strip + table)` → `chapter(根因分析：narrative + chart)` → `chapter(行动项：risk-list)` → `appendix(glossary)`。模板 MUST 含至少 1 份完整示例 JSON。

#### Scenario: 模板含完整示例 JSON

- **WHEN** 读取 `templates/incident-postmortem.md`
- **THEN** MUST 含至少 1 个 `json` code fence
- **AND** JSON `meta.templateId = "ledger.incident-postmortem.v1"`
- **AND** 含 `timeline` 与 `risk-list` 两种 block

### Requirement: `design-language.md` 印刷品排版规范

`design-language.md` SHALL 定义企业咨询报告的视觉契约：白色背景、衬线中文（思源宋体）+ 半衬线西文（与衬线协调的 sans-serif）正文、黑灰文字（`#1A1A19` 主标 / `#34322D` 正文 / `#5E5E5B` 次要 / `#858481` 弱化）、单一品牌强调色 `accent`（用户在 report.json `theme.accent` 字段指定，默认 `#1f4e79` 经典深蓝）；规范 MUST 显式禁用：玻璃拟态、暗色背景、霓虹色、装饰动画、bezel 风格元素。

#### Scenario: design-language.md 含禁用项

- **WHEN** 读取 `design-language.md`
- **THEN** MUST 含 "禁用" 或 "Don't" 章节
- **AND** 该章节 MUST 含字符串 "玻璃拟态" 或 "glassmorphism" 或 "bezel"

#### Scenario: design-language.md 含字号阶

- **WHEN** 读取 `design-language.md`
- **THEN** MUST 含至少 5 级字号定义（h1 / h2 / h3 / body / caption 或等价 6 级阶梯）
- **AND** 每级 MUST 标 font-size 与 line-height

### Requirement: `section-patterns.md` 可复用 section 块说明

`section-patterns.md` SHALL 为 `report-document-rendering` 定义的 11 种 block 类型分别给出：用途、JSON schema 示例、HTML 渲染示例、PDF 分页注意事项；每种 block MUST 含至少 1 个正面 JSON 示例。

#### Scenario: 每种 block 类型有示例

- **WHEN** 读取 `section-patterns.md`
- **THEN** MUST 含 11 种 block 类型对应的 `## <type>` 章节
- **AND** 每个章节 MUST 含至少 1 个 `json` code fence

### Requirement: AI 报告生成六步流程

ledger skill SHALL 在 `SKILL.md` 中明确 AI 生成报告时遵循的六步流程：

1. 确认报告目的与时间窗口
2. 根据用户意图选定 templateId
3. 列出该模板所需的全部数据查询清单（每条含 connectionId + SQL 草稿）
4. 跨连接逐项 `datatalk_query_data` 取数并把结果 inline 写进 report.json
5. **对超过 200 行的 table block：先调用 `datatalk_export_data`（format=csv）导出完整数据，拿到返回的 file artifact ID，写入该 block 的 `appendixCsvRef` 字段；block inline 数据只保留前 N 行（N ≤ 200）作为预览**
6. 一次性 `datatalk_promote_report`（含完整 report.json，包括各 appendixCsvRef 引用）

流程描述 MUST 显式禁止"边写边 promote"或"分多次 promote 同一报告"。

#### Scenario: 流程六步清晰列出

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 含编号 1-6 的六步流程
- **AND** 第 4 步 MUST 含字符串 "datatalk_query_data" 与 "inline"
- **AND** 第 5 步 MUST 含字符串 "datatalk_export_data" 与 "appendixCsvRef"
- **AND** 第 6 步 MUST 含字符串 "datatalk_promote_report" 与"一次性"或"single call"

#### Scenario: 流程禁止多次 promote

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 含禁止"分多次 promote"的明确表达（如"不要 / 不得 / MUST NOT 多次 promote 同一份报告"）

#### Scenario: 大表附录的两步交互正例

- **WHEN** 读取 ledger `SKILL.md` 或 `data-contract.md`
- **THEN** MUST 含至少一个完整正例：先 `datatalk_export_data(format='csv', sql=...)` 拿到 `{ fileArtifactId }`，再把该 ID 作为 `appendixCsvRef` 写入 table block，最后 `datatalk_promote_report`
- **AND** MUST 含反例：直接在 report.json 中 inline 1000 行 table 并 promote → ✗ 被服务端拒绝

### Requirement: `section-patterns.md` 对 cover.author 的禁用词约束

`section-patterns.md` 中 cover block 字段说明 SHALL 明确：`author` 字段 MUST NOT 填写生成器自指文案，禁用词包括但不限于 `DataTalk`、`DataTalk 自动生成`、`AI 生成`、`自动生成`、`系统生成`、`Auto-generated`、`Generated by …`；若无明确作者/团队信息，author MUST 留空。文档 SHALL 给出至少 1 个反例（被禁用词命中的写法）和 1 个正例（合法作者写法或留空）。

#### Scenario: section-patterns.md cover 字段说明含禁用词

- **WHEN** 读取 ledger `section-patterns.md` 的 cover block 章节
- **THEN** MUST 含"禁用"或"禁止"或"MUST NOT"等明示否定的关键词
- **AND** MUST 至少列出 4 个禁用词样本（含 "DataTalk"、"AI 生成"、"自动生成"、"Generated by"）
- **AND** MUST 含 ✗ 反例（如 `"author": "DataTalk 自动生成"`）
- **AND** MUST 含 ✓ 正例（如 `"author": "数据分析团队"` 或 author 字段被省略）

#### Scenario: SKILL.md 流程提醒 author 留空

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 含一句明示：cover 的 author 不确定来源时留空，不要写"DataTalk 自动生成"等生成器自指词

### Requirement: AI promote 失败的兜底重试流程

`SKILL.md` SHALL 在报告生成流程末尾明确 promote 失败处理规则：
1. 服务端返回 `error` 字段时，读取 `errorCodes[]` 与 `violations[]`，一轮内修齐所有违规后重试。
2. 连续失败 2 次（含）后 MUST 停止 retry，把完整 `violations` 列表汇报给用户，由用户决策修数据/改模板/放弃。
3. 校验失败的 retry MUST NOT 带 `groupId`（首次 promote 失败时服务端没有产生 record，沿用旧 groupId 会被拒绝为孤立 version）。
4. 仅"重新生成"场景（用户明示"按上次样式重做"）才带 `groupId` 沿用历史 group。

#### Scenario: SKILL.md 含失败兜底段落

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 含"promote 失败"或"promote 出错"或"validation failed"段落
- **AND** MUST 含数字 "2"（连续失败次数上限）
- **AND** MUST 含字符串 "errorCodes" 与 "violations"
- **AND** MUST 含 retry 不带 groupId 的明示

#### Scenario: SKILL.md 区分两种 retry 语义

- **WHEN** 读取 SKILL.md "重新生成" 与 "promote 失败重试" 两个段落
- **THEN** 两段 MUST 明确区分：重新生成（用户驱动）必带 groupId 沿用旧 group；校验失败 retry（系统驱动）不带 groupId

