## ADDED Requirements

### Requirement: sql-execution skill 必须含 DO NOT 段禁止批量 SQL 走 execute_sql

`skills/sql-execution/SKILL.md` SHALL 包含 `## ❗ DO NOT` 段或等价小节，明确禁止 AI 把大批量 SQL（>4KB / >20 INSERT / 源自文件）通过 `datatalk_execute_sql` 执行，并指向 `datatalk_import_data` 替代路径。

#### Scenario: SKILL.md 含 DO NOT 段字面短语

- **GIVEN** classpath 资源 `skills/sql-execution/SKILL.md`
- **THEN** 文件 SHALL 包含 `❗ DO NOT` 段标题或等价 Markdown 标记
- **AND** SHALL 包含字面短语 `datatalk_import_data` 与 `use_import_data`
- **AND** SHALL 包含字面短语 `4096` 与 `20 INSERT`
- **AND** SHALL 包含说明用户 query_editor 路径不适用本约束的陈述

### Requirement: SkillRoutingContractTest 必须断言 sql-execution DO NOT 段

`SkillRoutingContractTest` SHALL 新增测试用例断言 `sql-execution` SKILL.md 包含 DO NOT 段的所有关键短语。

#### Scenario: 测试通过

- **GIVEN** SKILL.md 含完整 DO NOT 段
- **WHEN** `SkillRoutingContractTest.sqlExecutionSkill_hasDoNotSectionForBulkSql` 运行
- **THEN** 测试 SHALL 通过

#### Scenario: SKILL.md 缺失关键短语时测试失败

- **GIVEN** 有人编辑 SKILL.md 删掉 `use_import_data` 字样
- **WHEN** 合同测试运行
- **THEN** 测试 SHALL 失败并指出缺失的关键短语
