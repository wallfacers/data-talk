## MODIFIED Requirements

### Requirement: Error context markdown template

The system SHALL generate error-to-AI messages using a standardized markdown template.

The template SHALL follow this structure:
```
**{title}**

> 消息来源：查询编辑器 Tab「{tabTitle}」

- **连接**: {connectionName} ({connectionKind})
- **数据库**: {database}
- **Schema**: {schema}

- **该连接可用的数据库**: {comma-separated list}
- **该连接可用的 Schema**: {comma-separated list}

- **执行的 SQL**:
```sql
{statementText}
```
- **错误信息**:
```
{errorMessage}
```

> 请在「{tabTitle}」Tab 的工具栏中设置 database 和 schema。点击连接名称右侧的下拉框即可选择。
```

Fields that are null or empty SHALL be rendered as "未设置" (or the localized equivalent). The `statementText` section SHALL be omitted when `statementText` is empty. The `connectionKind`, `database`, and `schema` fields SHALL be omitted when their values are null. The `tabTitle` block SHALL be omitted when `tabTitle` is null or empty. The available databases/schemas lists SHALL be omitted when the corresponding context field is already set, or when the available list is null or empty. The actionable guidance block SHALL be omitted when both `database` and `schema` are already set.

#### Scenario: Full context generates complete markdown

- **GIVEN** all fields in `ErrorContext` are populated including `tabTitle = '用户查询'`, `availableDatabases = ['analytics']`, `availableSchemas = ['public']`
- **WHEN** the markdown template is generated
- **THEN** all sections (消息来源, 连接, 数据库, Schema, SQL, 错误信息) SHALL appear in the output
- **AND** the actionable guidance block SHALL NOT appear (because database and schema are both set)

#### Scenario: Partial context includes available options and guidance

- **GIVEN** `ErrorContext` with `database = null`, `schema = null`, `tabTitle = '用户查询'`, `availableDatabases = ['analytics', 'test']`, `availableSchemas = ['public']`
- **WHEN** the markdown template is generated
- **THEN** the 数据库 and Schema sections SHALL NOT appear
- **AND** the 消息来源 block SHALL appear
- **AND** the available databases and schemas SHALL appear
- **AND** the actionable guidance block SHALL appear
- **AND** the 连接 and 错误信息 sections SHALL still appear
