## Why

`query_editor` 的 `apply_text_edits` 协议要求 AI 同时提供三套互相冗余、且必须彼此一致的定位信息（`baseVersion` 并发闸门、`range` 1-based 行列坐标、`expectedText` 逐字校验）。这把"数对行号""逐字复刻含前导空白的内容"这两件 LLM 最不擅长的事变成了硬前提，导致真实会话中连续 5 次编辑失败（行号错位、缩进 2-vs-4 空格不符、并发误判后不重读）。前一次修复（`f68b0108`）走的是"往 prompt 里补充坐标说明 + 改错误文案"的路子，但模型行为不稳定，未能消除根因。本次从协议侧彻底拿掉对坐标与逐字空白的依赖。

## What Changes

- **BREAKING**：`apply_text_edits` 的 `edits[]` 契约从 `{ range, text, expectedText }` 改为 `{ oldText, newText, hint? }`。`range` 作为权威定位信息被**彻底移除**，改为内容锚定（search/replace）。
- **定位改为内容锚定**：以 `oldText` 在当前内容中的唯一匹配作为替换位置。唯一命中即套用，忽略行号、忽略版本漂移；多命中用可选 `hint` 消歧或报歧义；零命中报锚点缺失。
- **空白容差（W2）**：锚点匹配时把 `oldText` 中的连续空白编译为柔性匹配（`\s+`），非空白 token 序列严格相等且相邻；在原文定位真实区间并精确替换该区间。容忍模型缩进写错，同时不破坏真实缩进。
- **并发自动 rebase**：`baseVersion` 降级为建议值。版本漂移但所有锚点仍唯一命中时自动 rebase 套用，回传新版本；仅当锚点缺失/歧义且无法 rebase 时才报真冲突。
- **错误码改为 AI 友好的明确语义**：`expected_text_mismatch` 拆为 `anchor_not_found`（锚点不存在，建议重读）与 `anchor_ambiguous`（多处匹配，建议补足上下文使锚点唯一）。`version_conflict` 仅保留给无法 rebase 的硬冲突。
- **prompt 瘦身**：删除 `ui-contract/SKILL.md` 中"1-based 数行号 / 逐字复刻前导空白"整段处方，替换为"`oldText` 需带足周边上下文使其在文中唯一"。这是删除脆弱指令而非新增行为引导。
- 整文档替换路径（`ui_patch /content` + `baseVersion`）保留不变，作为大改写兜底。

## Capabilities

### New Capabilities
- `query-editor-text-edits`: query_editor 的锚定式 search/replace 文本编辑契约——`oldText`/`newText`/`hint` 参数语义、唯一/多/零命中的解析规则、空白柔性匹配、并发自动 rebase 行为，以及多编辑同快照解析与不重叠校验。

### Modified Capabilities
- `ui-exec-error-semantics`: 闭合错误码枚举移除 `expected_text_mismatch`，新增 `anchor_not_found` 与 `anchor_ambiguous`；明确 `version_conflict` 仅用于无法 rebase 的硬冲突；规定 `anchor_ambiguous` 的恢复提示（补足上下文）属于"无单一动作可恢复"，不携带 `nextAction`。

## Impact

- **前端**：
  - `client/src/features/stage/stores/sql-workbench-store.ts` — `applyTextEdits`、`resolveTextEdits`/`resolveOffset`（坐标解析整体废弃）、新增锚点解析器（唯一/歧义/缺失 + 空白柔性匹配）、自动 rebase 逻辑、`SqlWorkbenchTextEdit`/`SqlWorkbenchEditResult` 类型。
  - `client/src/features/stage/adapters/QueryEditorAdapter.ts` — `apply_text_edits` 的 `paramsSchema`（去 `range`/`expectedText`，加 `oldText`/`newText`/`hint`）、错误码到 `ExecResult` 的映射。
- **服务端**：
  - `server/data-talk-application/.../stage/EditConflictMarkdownFormatter.java` — 重写 mismatch 文案分支为 `anchor_not_found`/`anchor_ambiguous`，移除 1-based 坐标话术。
  - `server/data-talk-application/.../opencode/McpActionBridge.java` — 错误码识别与字段透传。
- **Skill prompt**：`server/data-talk-adapter/src/main/resources/skills/ui-contract/SKILL.md`、`concurrency-contract/SKILL.md` — 删坐标处方、改并发恢复说明。
- **Spec**：新建 `openspec/specs/query-editor-text-edits/`；修改 `openspec/specs/ui-exec-error-semantics/`。
- **测试**：`sql-workbench-store.test.ts`、`QueryEditorAdapter.test.ts`、`EditConflictMarkdownFormatterTest.java`、`McpActionBridgeTest.java`，以及 e2e `agents-batch4-ui-workspace.spec.ts` / `agents-skills-regression.spec.ts` 中涉及 `apply_text_edits` 的断言。

### Design Inputs

`client/DESIGN.md` 已查阅：本变更为 query_editor 文本编辑**协议/数据流**改动，不引入或修改任何可见 UI、主题、布局或交互组件，无 `client/DESIGN.md` 视觉约束适用（N/A）。编辑后的内容仍通过既有 Monaco 编辑器渲染，呈现层不变。

### Risks / Known Issues

- 现有 open BUG 中无与 `apply_text_edits` 失败模式重叠者（`query-editor` 模块下 BUG-0042/0043/0065 均已 fixed 且属 connection/pre-action 范畴）。
- **空白柔性匹配（W2）误命中风险**：`\s+` 放宽可能在结构相似处贪婪误匹配。缓解：非空白 token 必须严格相等且按序相邻，且整体仍要求唯一命中，否则报 `anchor_ambiguous`。
- **BREAKING 契约**：旧的 `range`/`expectedText` 调用将失效。因该协议仅 query_editor 使用、且消费方为我方 skill/agent，影响面可控；需同步更新 skill prompt 与 e2e 断言。
