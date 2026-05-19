## ADDED Requirements

### Requirement: `datatalk_promote_report` 工具注册与 Trigger Gate

`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 的 `## Trigger Gate` 表格 SHALL 新增一行将"汇报 / 周报 / 月报 / 季报 / 复盘 / 总结 / postmortem / weekly report / monthly report / quarterly review"类用户意图路由到 `skill:ledger`；`## Skill Index` SHALL 新增 `skill:ledger` 条目，指向 `skills/ledger/SKILL.md`；`OpenCodeGatewayBeans` SHALL 注册 `PromoteReportActionHandler` 使其在 OpenCode tool 列表中暴露名为 `datatalk_promote_report` 的工具。

#### Scenario: Trigger Gate 含 ledger 行

- **WHEN** 解析 AGENTS.md `## Trigger Gate` 表格
- **THEN** MUST 存在至少 1 行的"target"或"skill"列含字符串 `skill:ledger`
- **AND** 该行的"trigger"列 MUST 含字符串"周报"、"月报"、"复盘"、"汇报"、"report"、"postmortem"之一

#### Scenario: Skill Index 含 ledger 条目

- **WHEN** 解析 AGENTS.md `## Skill Index` 章节
- **THEN** MUST 含 `skill:ledger` 字面字符串
- **AND** 该条目 MUST 引用 `skills/ledger/SKILL.md` 路径

#### Scenario: OpenCode 工具列表含 datatalk_promote_report

- **GIVEN** OpenCode gateway bean 初始化完成
- **WHEN** 检查注册的工具列表
- **THEN** MUST 含名为 `datatalk_promote_report` 的工具描述符
- **AND** 该工具的 input schema MUST 至少含 `report` (object, required) 与 `workspaceId` (string, required) 两个字段

### Requirement: ledger skill classpath 资源与运行时目录闭合

ledger skill 在 classpath `skills/ledger/**` 资源中 vendor 的内容 SHALL 与 `data-talk/.opencode/skills/ledger/` 同步后的内容一致；任何被 AGENTS.md 引用的 `skills/ledger/<filename>` 路径 MUST 在 classpath 资源中真实存在。

#### Scenario: classpath 内含 SKILL.md

- **WHEN** 列出 `classpath*:skills/ledger/**` 资源
- **THEN** MUST 含 `SKILL.md`
- **AND** MUST 含 `templates/monthly-business-review.md`
- **AND** MUST 含 `templates/incident-postmortem.md`
- **AND** MUST 含 `data-contract.md`
- **AND** MUST 含 `design-language.md`
- **AND** MUST 含 `section-patterns.md`
- **AND** MUST 含 `assets/fonts/NotoSerifSC-Regular.otf`
- **AND** MUST 含 `assets/fonts/NotoSansSC-Regular.otf`
- **AND** MUST 含 `assets/styles/ledger.css`

#### Scenario: AGENTS.md 引用的 skill 路径同步后存在

- **GIVEN** 服务端启动完成
- **WHEN** 对 AGENTS.md 中每个 `skills/ledger/<path>` 引用做文件存在性检查
- **THEN** 每个引用路径在 `data-talk/.opencode/skills/ledger/<path>` MUST 真实存在
