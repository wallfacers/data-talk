## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: `SKILL.md` 数据叙事撰写规则（有理有据）

ledger `SKILL.md` SHALL 含一段"数据叙事撰写规则"，要求 AI 在组织报告时遵循证据驱动与视觉层次原则：

1. **论点→证据→来源链条**：每个关键结论 MUST 由具体数据支撑，并经 `source` 字段挂到数据来源/口径。
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
- **THEN** MUST 含"关键结论需有数据/来源支撑"或语义等价表达
- **AND** MUST 关联 `source` 字段的使用

#### Scenario: SKILL.md 引导使用新富视觉原语

- **WHEN** 读取 ledger `SKILL.md`
- **THEN** MUST 提及 `callout` / `stat-highlight` / `comparison` 等新原语的适用场景
- **AND** MUST 说明它们用于强调结论而非装饰
