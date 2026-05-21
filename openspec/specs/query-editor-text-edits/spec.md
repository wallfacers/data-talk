## Requirements

### Requirement: apply_text_edits SHALL 以内容锚定而非坐标定位

`query_editor` 的 `apply_text_edits` action 的每个 edit SHALL 由 `{ oldText, newText, hint? }` 构成，其中 `oldText` 是要被替换的当前内容片段（锚点），`newText` 是替换后的文本，`hint`（可选）为 `{ line: number }`（1-based）仅用于多命中消歧。系统 SHALL NOT 接受 `range`（行列坐标）或 `expectedText` 字段作为定位依据；定位完全由 `oldText` 在当前内容中的匹配决定。

#### Scenario: 唯一命中时按锚点位置替换并忽略行号

- **GIVEN** 当前内容中 `oldText` 归一化后恰好出现一次
- **WHEN** AI 调用 `apply_text_edits` 提交该 edit
- **THEN** 系统 SHALL 在该唯一命中区间用 `newText` 替换，无需任何行列坐标
- **AND** 即使 AI 提供的 `hint.line` 不正确，替换结果 SHALL 不受影响

#### Scenario: 缺省坐标字段不影响成功

- **GIVEN** edit 仅含 `oldText` 与 `newText`，无 `hint`
- **AND** `oldText` 在当前内容中唯一命中
- **THEN** 编辑 SHALL 成功套用

### Requirement: 多命中 SHALL 用 hint 消歧，无法消歧时报 anchor_ambiguous

当 `oldText` 在当前内容中出现多于一次时，系统 SHALL 优先用 `hint.line` 选择起始行最接近的那一处命中；若无 `hint` 或多处仍并列无法唯一确定，系统 SHALL 返回 `anchor_ambiguous` 错误，并提示在 `oldText` 中加入更多周边上下文使其唯一。

#### Scenario: hint.line 唯一确定一处命中

- **GIVEN** `oldText` 出现两次，分别起始于第 3 行与第 12 行
- **WHEN** edit 提供 `hint.line = 12`
- **THEN** 系统 SHALL 替换第 12 行附近的那一处命中

#### Scenario: 无 hint 的多命中返回 anchor_ambiguous

- **GIVEN** `oldText` 出现两次且 edit 未提供 `hint`
- **WHEN** 提交 edit
- **THEN** 系统 SHALL 返回 `error.code = "anchor_ambiguous"`
- **AND** `error.details` SHALL 包含 `editIndex` 与命中数 `matchCount`
- **AND** `error.message` SHALL 提示加入更多上下文使锚点唯一

### Requirement: 零命中 SHALL 报 anchor_not_found

当 `oldText` 在当前内容中归一化后零命中时，系统 SHALL 返回 `anchor_not_found` 错误，并附带当前内容供 AI 重读重定位。

#### Scenario: 锚点不存在时返回 anchor_not_found 并回传当前内容

- **GIVEN** `oldText` 在当前内容中不存在（内容已变或文本被编造）
- **WHEN** 提交 edit
- **THEN** 系统 SHALL 返回 `error.code = "anchor_not_found"`
- **AND** `error.currentState` SHALL 包含 `{ tabId, version, content }`
- **AND** `error.details` SHALL 包含 `editIndex` 与提交的 `oldText`

### Requirement: 锚点匹配 SHALL 容忍空白差异但保持非空白 token 严格相等

锚点匹配 SHALL 把 `oldText` 中的连续空白（含换行）视为柔性匹配——匹配当前内容中相应位置任意非空的空白序列；而 `oldText` 中的非空白 token SHALL 严格相等且按原序相邻匹配。系统 SHALL 在替换前把换行统一归一化（`\r\n` 与 `\r` 视同 `\n`）进行搜索，但替换 SHALL 落在原始内容的精确区间，不改变文件既有换行风格。

#### Scenario: 缩进空格数不同仍能命中

- **GIVEN** 当前内容某行为 `    COUNT(*)`（4 空格缩进）
- **WHEN** edit 的 `oldText` 对应行写作 `  COUNT(*)`（2 空格缩进）
- **THEN** 系统 SHALL 视为命中并完成替换

#### Scenario: 非空白 token 不同则不命中

- **GIVEN** 当前内容为 `SELECT COUNT(*)`
- **WHEN** edit 的 `oldText` 为 `SELECT SUM(*)`
- **THEN** 系统 SHALL NOT 命中该处，按命中数走 anchor_not_found 或 anchor_ambiguous

#### Scenario: 替换不改变文件既有换行风格

- **GIVEN** 当前内容使用 `\r\n` 换行
- **WHEN** 锚点跨多行命中并被替换
- **THEN** 替换区间之外的内容 SHALL 保持原有 `\r\n` 不被改写为 `\n`

### Requirement: 多 edit SHALL 同快照解析并校验不重叠

当一次 `apply_text_edits` 含多个 edit 时，系统 SHALL 先针对同一份原始内容快照解析出每个 edit 的命中区间，再校验各区间两两不重叠，最后按起始位置降序套用。若任意两个 edit 的命中区间重叠，系统 SHALL 返回 `invalid_params` 并指明冲突的 editIndex。

#### Scenario: 多个不重叠 edit 同时套用

- **GIVEN** 两个 edit 的锚点分别唯一命中且区间不重叠
- **WHEN** 提交批量 edit
- **THEN** 两处 SHALL 同时被替换，结果与对原始快照逐一套用一致

#### Scenario: 区间重叠的多 edit 被拒绝

- **GIVEN** 两个 edit 的命中区间存在重叠
- **WHEN** 提交批量 edit
- **THEN** 系统 SHALL 返回 `error.code = "invalid_params"` 并在 message/details 指明冲突 editIndex
- **AND** 系统 SHALL NOT 套用任何一个 edit

### Requirement: baseVersion SHALL 为建议值并支持锚点唯一时自动 rebase

`apply_text_edits` 的 `baseVersion` SHALL 为可选建议值而非硬闸门。当 `baseVersion` 落后于当前版本（发生漂移）但所有 edit 的锚点在当前内容中仍唯一命中时，系统 SHALL 自动 rebase 并套用编辑，返回最新 `version`、`content` 与 `rebased: true`。仅当漂移且任一锚点已不可定位时，系统 SHALL 拒绝并按 `anchor_not_found` / `anchor_ambiguous` 报错。

#### Scenario: 版本漂移但锚点唯一时自动 rebase

- **GIVEN** AI 提交 `baseVersion = 3` 而当前 `version = 4`（他处发生过编辑）
- **AND** 所有 edit 的锚点在当前内容中仍唯一命中
- **WHEN** 提交 edit
- **THEN** 系统 SHALL 套用编辑并返回 `{ version: 5, content, rebased: true }`
- **AND** 系统 SHALL NOT 返回 version_conflict

#### Scenario: 版本漂移且锚点失效时拒绝

- **GIVEN** `baseVersion` 落后且某 edit 的锚点在当前内容零命中
- **WHEN** 提交 edit
- **THEN** 系统 SHALL 返回 `anchor_not_found` 并回传当前内容
- **AND** `error.message` SHALL 注明版本已变、需基于最新内容重定锚点
