## ADDED Requirements

### Requirement: Registered Actions 工具描述区分 session-scope 与 global-scope

AGENTS.md 中 `## Registered Actions` 工具表对每一个返回数据的工具描述 SHALL 显式区分其作用域是 **session-scope**（依赖或反映当前会话上下文）还是 **global-scope**（与会话无关、跨 session 共享的全局数据）。对于 global-scope 工具，描述中 MUST 明确出现 "does NOT reflect current session" 或语义等价的中文/英文短语；对于 session-scope 工具，描述中 MUST 明确出现 "current session" / "active session" 字样。

`datatalk_list_connections` 是 global-scope 工具，其描述 MUST 在表格中显式提示读者改用 `datatalk_get_data_context` 来获取当前 session 选中的连接。

#### Scenario: list_connections 描述含全局作用域提示

- **GIVEN** `classpath:/agents/AGENTS.md` 中 `## Registered Actions` 表格行
- **WHEN** 定位 `datatalk_list_connections` 行的 Purpose 列
- **THEN** Purpose 列文本同时满足两个条件：
  - 包含字面子串 "does NOT" 或 "不反映" 或 "不指示" 之一（不区分大小写）
  - 包含 `datatalk_get_data_context` 字面子串

#### Scenario: get_data_context 描述含会话作用域提示

- **GIVEN** `classpath:/agents/AGENTS.md` 中 `## Registered Actions` 表格行
- **WHEN** 定位 `datatalk_get_data_context` 行的 Purpose 列
- **THEN** Purpose 列文本包含字面子串 "current session" 或 "当前会话" 之一

### Requirement: Intent Routing Gate 路由会话归属类问题

AGENTS.md 的 `## Intent Routing Gate` 章节 SHALL 包含一条明确的路由规则：当用户提问当前连接、当前数据库、当前 schema、激活的数据源或任何"现在用的是哪个数据源"语义等价问题（**session-attribution question**，跨语言）时，AI MUST 先调用 `datatalk_get_data_context` 读取激活态，然后再决定是否调用 `datatalk_list_connections` / `datatalk_list_connection_targets`。

该规则 MUST 使用语义分类描述（"session-attribution question"），而非穷举关键词，以覆盖多语言变体。

#### Scenario: Intent Routing Gate 含 session-attribution 规则

- **GIVEN** `classpath:/agents/AGENTS.md` 中 `## Intent Routing Gate` 章节文本
- **WHEN** 扫描该章节
- **THEN** 文本包含字面子串 `datatalk_get_data_context`
- **AND** 文本包含字面子串 "session-attribution" 或语义等价的中文短语（如"会话归属"）
- **AND** 该段落明示"先调用"或 "first" 的顺序约束

### Requirement: 路由规则纳入新增条目不破坏骨架体积

本变更新增的 Registered Actions 描述修订与 Intent Routing Gate 路由规则条目 MUST 不使 `classpath:/agents/AGENTS.md` 去掉空行与 `<!-- -->` 注释后的非空行数突破现有 350 行硬约束。

#### Scenario: 新增条目后体积仍合规

- **GIVEN** 本变更已修改 AGENTS.md
- **WHEN** 读取 `classpath:/agents/AGENTS.md` 并去除空行与 HTML 注释
- **THEN** 非空行数 ≤ 350
