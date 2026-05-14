## ADDED Requirements

### Requirement: Error markdown includes tab source

当 Tab 标题存在时，`buildErrorMarkdown` 生成的 markdown SHALL 在消息开头包含消息来源标识。

消息来源 SHALL 使用 blockquote 引用格式：
```
> 消息来源：查询编辑器 Tab「{tabTitle}」
```

当 `tabTitle` 为 null 或空字符串时，该块 SHALL NOT 渲染。

#### Scenario: Error with tab title includes source block

- **GIVEN** ErrorContext with `tabTitle = '用户查询'`
- **WHEN** `buildErrorMarkdown(ctx)` is called
- **THEN** the output SHALL contain `> 消息来源：查询编辑器 Tab「用户查询」`
- **AND** it SHALL appear before the connection info section

#### Scenario: Error without tab title omits source block

- **GIVEN** ErrorContext with `tabTitle = null`
- **WHEN** `buildErrorMarkdown(ctx)` is called
- **THEN** the output SHALL NOT contain a "消息来源" block
- **AND** the output SHALL still contain connection, database, schema, and error info sections

### Requirement: Error markdown lists available databases and schemas

当 database 为 null 且 `availableDatabases` 非空数组时，`buildErrorMarkdown` SHALL 渲染可用数据库列表。当 schema 为 null 且 `availableSchemas` 非空数组时，SHALL 渲染可用 schema 列表。

列表 SHALL 使用 markdown 列表格式：
```
- **该连接可用的数据库**: analytics, test, production
```

#### Scenario: Database not set with available databases

- **GIVEN** ErrorContext with `database = null`, `availableDatabases = ['analytics', 'test', 'production']`
- **AND** `schema = 'public'` (already set)
- **WHEN** `buildErrorMarkdown(ctx)` is called
- **THEN** the output SHALL contain `该连接可用的数据库: analytics, test, production`
- **AND** the output SHALL NOT contain a "该连接可用的 Schema" line

#### Scenario: Schema not set with available schemas

- **GIVEN** ErrorContext with `schema = null`, `availableSchemas = ['public', 'private', 'audit']`
- **AND** `database = 'analytics'` (already set)
- **WHEN** `buildErrorMarkdown(ctx)` is called
- **THEN** the output SHALL contain `该连接可用的 Schema: public, private, audit`

#### Scenario: Both database and schema not set with both available

- **GIVEN** ErrorContext with `database = null`, `schema = null`
- **AND** `availableDatabases = ['db1', 'db2']`, `availableSchemas = ['s1', 's2']`
- **WHEN** `buildErrorMarkdown(ctx)` is called
- **THEN** both available databases and available schemas lists SHALL appear

#### Scenario: Available list is empty or null

- **GIVEN** ErrorContext with `database = null`, `availableDatabases = []`
- **WHEN** `buildErrorMarkdown(ctx)` is called
- **THEN** the output SHALL NOT contain an available databases line

### Requirement: Error markdown includes actionable guidance

当 database 或 schema 为 null 时，`buildErrorMarkdown` SHALL 在消息末尾渲染操作指引，引导用户在对应 Tab 的工具栏中进行设置。

操作指引 SHALL 使用 blockquote 格式：
```
> 请在「{tabTitle}」Tab 的工具栏中设置 database 和 schema。点击连接名称右侧的下拉框即可选择。
```

当 `tabTitle` 为 null 时，指引中的 Tab 名称 SHALL 回退为 "对应" Tab。

#### Scenario: Database not set renders guidance with tab title

- **GIVEN** ErrorContext with `database = null`, `tabTitle = '用户查询'`
- **WHEN** `buildErrorMarkdown(ctx)` is called
- **THEN** the output SHALL contain `> 请在「用户查询」Tab 的工具栏中设置 database 和 schema`

#### Scenario: Database not set without tab title renders generic guidance

- **GIVEN** ErrorContext with `database = null`, `tabTitle = null`
- **WHEN** `buildErrorMarkdown(ctx)` is called
- **THEN** the output SHALL contain `> 请在对应该 Tab 的工具栏中设置 database 和 schema`

#### Scenario: Both database and schema are set omits guidance

- **GIVEN** ErrorContext with `database = 'analytics'`, `schema = 'public'`
- **WHEN** `buildErrorMarkdown(ctx)` is called
- **THEN** the output SHALL NOT contain the actionable guidance block
