# Semantic Model — Delta

## ADDED Requirements

### Requirement: datatalk_semantic_lookup 防御性输入处理

`datatalk_semantic_lookup` Action MUST 在面对边界输入与空模型状态时保持稳定，向调用方返回结构化结果而非未捕获异常或 HTTP 5xx。

#### Scenario: 缺失或空 query 返回空匹配

- **WHEN** `datatalk_semantic_lookup` 收到 input 中 `query` 字段为 `null`、缺失、或空白字符串
- **THEN** Action MUST 返回成功结果 `{matches: [], total: 0, warning: "empty_query"}`
- **AND** MUST NOT 抛出 `NullPointerException` 或返回 HTTP 5xx

#### Scenario: connection 无语义模型目录返回空匹配

- **GIVEN** 当前 active connection 的 `~/.data-talk/semantic/<connectionId>/` 目录不存在或为空（尚未创建任何 `.model.yaml`）
- **WHEN** 任意合法 `query` 调用 `datatalk_semantic_lookup`
- **THEN** Action MUST 返回 `{matches: [], total: 0}`
- **AND** MUST NOT 抛出异常

#### Scenario: entity 含 null 可选字段不导致 NPE

- **GIVEN** 某 `<domain>.model.yaml` 中 entity 的 `description` 字段为 null（当前 record 契约下唯一真正可空的字段；其他字段如 `type`/`physical.table` 在 record 层即被 requireNonNull）
- **WHEN** 调用 `datatalk_semantic_lookup` 且 query 命中该 entity（按 name 或非 null 字段匹配）
- **THEN** Action MUST 在匹配项构造时使用允许 null value 的 Map（如 `LinkedHashMap`）
- **AND** MUST NOT 因响应 Map 构造逻辑（旧版 `Map.of(...)` 拒绝 null）而抛出 `NullPointerException`
- **AND** 即便未来 record 放宽更多字段为可空，响应构造路径仍 MUST 兼容

#### Scenario: 未捕获异常翻译为结构化错误

- **WHEN** `datatalk_semantic_lookup` 的 handle() 内任何意外 `RuntimeException`（如 IO 错误、YAML 反序列化失败、null 字段超出本规范覆盖）发生
- **THEN** 后端 MUST 通过 `log.error` 记录完整 cause chain，包含 `connectionId` 与 query
- **AND** MUST 抛出 ActionExecutionException（或仓库现有等价异常），携带原始 cause 与简短 error code `semantic_lookup_failed`
- **AND** 调用方收到的响应 MUST 是协议层的 action_result.error 字段而非裸 HTTP 5xx

### Requirement: datatalk_semantic_lookup 在 null connectionId 上不崩溃

`datatalk_semantic_lookup` 标注了 `requiresConnection = true`，但 handler MUST 不假设上游已经强制过 connectionId 非空——必须在调用底层 `SemanticModelRepository` 之前自行短路。底层 `FsSemanticModelRepository.connectionDir(null)` 会因 `java.nio.file.Path.resolve(null)` 抛 NPE（生产 stack trace 验证），所以防御必须在 handler 层完成。

#### Scenario: ctx.connectionId() 为 null

- **GIVEN** ActionContext 的 `connectionId()` 返回 `null`（session 未绑定 connection，是 lookup 场景下最常见的失败模式）
- **WHEN** 调用 `datatalk_semantic_lookup`
- **THEN** Action MUST 在调用 `SemanticModelRepository.listDomains(...)` 之前短路
- **AND** MUST 返回 `{matches: [], total: 0, warning: "no_active_connection"}`
- **AND** MUST NOT 抛出 `NullPointerException`

#### Scenario: ctx.connectionId() 为空白字符串

- **GIVEN** ActionContext 的 `connectionId()` 返回 `""` 或 `"   "`
- **WHEN** 调用 `datatalk_semantic_lookup`
- **THEN** 行为同上 — 短路、返回 `warning: "no_active_connection"`
