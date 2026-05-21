## Requirements

### Requirement: `datatalk_ui_exec` 失败 SHALL 返回结构化错误

`datatalk_ui_exec` 在客户端执行失败时 SHALL 返回包含 `error.code` 枚举的结构化错误对象，代替自由文本错误信息。`error.code` 的取值 SHALL 来自闭合枚举：`no_active_query_editor`, `no_active_er_inspector`, `no_active_er_designer`, `no_active_dashboard`, `version_conflict`, `anchor_not_found`, `anchor_ambiguous`, `unknown_object`, `unknown_action`, `invalid_params`。`expected_text_mismatch` 已移除，由 `anchor_not_found`（锚点零命中）与 `anchor_ambiguous`（锚点多命中无法消歧）替代。

#### Scenario: 无活跃 query_editor tab 时 run_sql 返回 no_active_query_editor

- **GIVEN** 当前 stage 没有任何 `query_editor` 类型的 tab 处于 active 状态
- **WHEN** AI 调用 `datatalk_ui_exec({ object: "query_editor", action: "run_sql" })`
- **THEN** 返回 `{ error: { code: "no_active_query_editor", message: <human-readable>, nextAction: { object: "workspace", action: "open", params: { type: "query_editor", title: "Untitled SQL" } } } }`

#### Scenario: 无活跃 er_inspector tab 时返回 no_active_er_inspector

- **GIVEN** 当前 stage 没有 `er_inspector` tab active
- **WHEN** AI 调用 `datatalk_ui_exec({ object: "er_inspector", action: "refresh" })`
- **THEN** 返回 `error.code = "no_active_er_inspector"`

#### Scenario: apply_text_edits 锚点零命中返回 anchor_not_found

- **GIVEN** `apply_text_edits` 的某 edit 的 `oldText` 在当前内容中零命中
- **WHEN** 客户端执行该 edit
- **THEN** 返回 `error.code = "anchor_not_found"`
- **AND** 不再返回已移除的 `expected_text_mismatch`

#### Scenario: apply_text_edits 锚点多命中返回 anchor_ambiguous

- **GIVEN** `apply_text_edits` 的某 edit 的 `oldText` 多命中且无法用 `hint` 消歧
- **WHEN** 客户端执行该 edit
- **THEN** 返回 `error.code = "anchor_ambiguous"`

### Requirement: 失败错误 SHALL 在适用场景下携带 nextAction 自愈提示

当失败的根因可通过单一后续 `datatalk_ui_exec` 调用恢复时，错误对象 SHALL 包含 `nextAction` 字段建议下一步动作。`nextAction` 是可选字段；不适用场景（如 `version_conflict` / `anchor_not_found` / `anchor_ambiguous`）可省略，因为正确恢复需要 AI 重读最新内容并重新构造锚点（`anchor_not_found`）或加入更多上下文使锚点唯一（`anchor_ambiguous`），没有单一动作能完成。

#### Scenario: no_active_query_editor SHALL 携带 workspace.open nextAction

- **GIVEN** 错误码为 `no_active_query_editor`
- **WHEN** 错误对象被构造
- **THEN** `error.nextAction.object === "workspace"`
- **AND** `error.nextAction.action === "open"`
- **AND** `error.nextAction.params.type === "query_editor"`

#### Scenario: anchor_not_found SHALL NOT 携带 nextAction 且回传当前内容

- **GIVEN** `apply_text_edits` 因锚点零命中失败
- **WHEN** 返回 `anchor_not_found` 错误
- **THEN** `error.nextAction` SHALL 缺省或为 `null`（恢复需 AI 重读并基于最新内容重定锚点）
- **AND** `error.currentState` SHALL 包含 `{ tabId, version, content }`

#### Scenario: anchor_ambiguous SHALL NOT 携带 nextAction 且提示补足上下文

- **GIVEN** `apply_text_edits` 因锚点多命中失败
- **WHEN** 返回 `anchor_ambiguous` 错误
- **THEN** `error.nextAction` SHALL 缺省或为 `null`
- **AND** `error.message` SHALL 提示在 `oldText` 中加入更多周边上下文使锚点唯一

### Requirement: 错误结构 SHALL 向下兼容旧客户端

旧客户端忽略未识别字段。`error.code` 与 `error.message` SHALL 总是存在；`error.nextAction` 是新增可选字段。

#### Scenario: 旧客户端只读 error.message 也能正常显示

- **GIVEN** 调用方仅读取 `error.message` 字段
- **WHEN** 收到任意 ui_exec 错误
- **THEN** `error.message` 是非空人类可读字符串
