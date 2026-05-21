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

### Requirement: block `source` 字段不渲染

ledger 报告渲染 SHALL NOT 输出数据来源脚注。即便 `report.json` 的 block 含 `source` 字段，HTML 与 Markdown 渲染均 MUST 忽略它，不产生任何可见节点。

#### Scenario: HTML 渲染忽略 source

- **GIVEN** report 中 chart block `source: "mysql-prod · sales_summary as of 2026-04-30"`
- **WHEN** 渲染 HTML
- **THEN** 输出 MUST NOT 含 CSS class `ledger-block-source`
- **AND** 输出 MUST NOT 含字符串 `mysql-prod · sales_summary`

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

### Requirement: `design-language.md` 现代报告设计语言

`design-language.md` SHALL 定义 ledger 报告的现代报告设计语言（替换原"印刷品保守禁用清单"契约），核心是**色彩角色系统**而非单一强调色：

- **色彩角色系统**：定义 `primary`（专业主色）/ `accent`（创新强调）/ `surface`（干净背景层）三个角色，外加由其派生的同色阶 tint 梯度与语义数据色（正向/负向/中性）；规范 MUST 说明丰富感来自"同色阶层级 + 留白节奏"，而非高饱和霓虹堆砌。
- **theme 来源**：用户在 `report.json` `theme` 字段指定（支持仅 `accent` 的旧形态与含 `primary/accent/surface/tints` 的新形态，向后兼容）；MUST 给出一组默认角色色 token（hex）。
- **排版**：白色背景仍为默认（保证打印可读），衬线中文（思源宋体）+ 协调 sans-serif 西文；MUST 含至少 6 级字号阶（每级标 font-size + line-height）。
- **富视觉原语规范**：MUST 描述 `callout` / `stat-highlight` / `comparison` / `quote` / `divider` 的视觉规则与色彩用法。
- **保留克制边界**：MUST 明示仍禁止滑向 bezel 风格（暗色大屏 / 玻璃拟态 / 自由 grid 拖拽），以维持与 bezel skill 的视觉边界；现代化≠暗色科技风。

#### Scenario: design-language.md 含色彩角色系统

- **WHEN** 读取 `design-language.md`
- **THEN** MUST 含 `primary` / `accent` / `surface` 三个色彩角色的定义
- **AND** MUST 给出每个角色的默认 hex token
- **AND** MUST 说明语义数据色（正向/负向/中性）

#### Scenario: design-language.md 含字号阶

- **WHEN** 读取 `design-language.md`
- **THEN** MUST 含至少 6 级字号定义（h1 / h2 / h3 / body / caption / micro 或等价阶梯）
- **AND** 每级 MUST 标 font-size 与 line-height

#### Scenario: design-language.md 保留 bezel 边界

- **WHEN** 读取 `design-language.md`
- **THEN** MUST 含与 bezel 视觉边界的说明（明示禁止暗色大屏 / 玻璃拟态 / 自由 grid 拖拽）

#### Scenario: design-language.md 含富视觉原语规范

- **WHEN** 读取 `design-language.md`
- **THEN** MUST 含 `callout`、`stat-highlight`、`comparison`、`quote`、`divider` 的视觉用法说明
- **AND** 色彩用法 MUST 引用色彩角色系统而非裸 hex 霓虹色

### Requirement: `section-patterns.md` 可复用 section 块说明

`section-patterns.md` SHALL 为 `report-document-rendering` 定义的全部 block 类型分别给出：用途、JSON schema 示例、HTML 渲染示例、PDF 分页注意事项；每种 block MUST 含至少 1 个正面 JSON 示例。文档 MUST 覆盖原 11 种块**以及**新增的 `callout` / `stat-highlight` / `comparison` / `quote` / `divider` 五种富视觉原语，并为 `table` 的富单元格 `cellFormats`（`text` / `bar` / `delta` / `heat`）给出 schema 与示例。

#### Scenario: 每种 block 类型有示例

- **WHEN** 读取 `section-patterns.md`
- **THEN** MUST 含原 11 种 + 新 5 种共 16 种 block 类型对应的 `## <type>` 章节
- **AND** 每个章节 MUST 含至少 1 个 `json` code fence

#### Scenario: table cellFormats 有 schema 与示例

- **WHEN** 读取 `section-patterns.md` 的 table 章节
- **THEN** MUST 含 `cellFormats` 字段说明，列出 `text` / `bar` / `delta` / `heat` 四种取值
- **AND** MUST 含至少 1 个含 `cellFormats` 的 JSON 示例

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

### Requirement: `SKILL.md` 数据叙事撰写规则（有理有据）

ledger `SKILL.md` SHALL 含一段"数据叙事撰写规则"，要求 AI 在组织报告时遵循证据驱动与视觉层次原则：

1. **论点→证据链条**：每个关键结论 MUST 由具体数据支撑。
2. **一图一观点**：单个 `chart` MUST 只表达一个核心观点，禁止一张图叠加多个互不相关的 callout/对比。
3. **视觉服务于结论**：富视觉原语（`callout` / `stat-highlight` / `comparison`）MUST 用于强调结论性洞察，禁止纯装饰；禁止"数据倾倒"（把原始查询结果整表丢给读者而不给解读）。
4. **渐进披露**：复杂分析 MUST 按"概述 → 分项 → 结论 → 建议"顺序组织，先给基线再给关键对比。

#### Scenario: SKILL.md 含数据叙事规则

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 含"数据叙事"或"撰写规则"或等价标题段落
- **AND** MUST 含"一图一观点"或语义等价表达
- **AND** MUST 含禁止"数据倾倒"或纯装饰富视觉块的明示

#### Scenario: SKILL.md 要求论点挂证据

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 含"关键结论需有数据支撑"或语义等价表达

#### Scenario: SKILL.md 引导使用新富视觉原语

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 提及 `callout` / `stat-highlight` / `comparison` 等新原语的适用场景
- **AND** MUST 说明它们用于强调结论而非装饰

