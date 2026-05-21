## Why

技能端到端测试报告（`~/.data-talk/opencode/skill-test-plan.md`）显示 `datatalk_semantic_lookup` 在无语义模型 YAML 的 connection 上返回 HTTP 500，与契约不符——预期应为空匹配结果。代码 review 在 `SemanticLookupActionHandler` 中发现 3 处潜在 NPE 入口（null query、`Map.of` 不容 null、handle() 无兜底），任一触发都会让上游收到不可解读的 500。同期发现 T32（`archive_artifact` 的 `path_outside_session_dir`）与 T12（`query_editor.set_context` 对 user-source 编辑器返回 noop）的错误响应文案缺乏机器可读的结构化字段与人类可读的提示，AI agent 容易误判为 bug。本提案在不改变外部行为契约前提下做防御加固和错误文案改进。

## What Changes

- **加固 `datatalk.semantic_lookup` action**（行为修正）
  - 对缺失/空 `query` 输入返回结构化空结果而非 NPE
  - 把构造匹配项的 4 处 `Map.of(...)` 替换为允许 null value 的 `LinkedHashMap`
  - `handle()` 用 try-catch 兜底，未捕获异常 `log.error` 后抛 `ActionExecutionException` 并携带 cause，避免裸 500
  - 新增 `SemanticLookupActionHandlerTest`（@TempDir + 真实 `FsSemanticModelRepository`），覆盖：空 query / 不存在的 connection 目录 / 含 null 字段的 entity / null connectionId
- **`datatalk.archive_artifact` 错误响应增强 `hint`**（仅文案，行为不变）
  - 当返回 `path_outside_session_dir` 等 `PathSafetyError` 时，附加 `hint` 字段说明约束（"file must be under current session's working directory"）
  - 错误码/状态码/`ok` 字段保持不变，仅追加可选 `hint` 输出字段
- **`query_editor.set_context` noop 文案结构化**（仅文案，行为不变）
  - 把 `QueryEditorAdapter.ts` 第 530 行 `reason: 'source=user editor cannot follow session'` 改为机器可读的 `reason: 'user_editor_pinned_to_origin'` + 人类可读的 `detail` 字段
  - 返回结构其他字段（`success`、`noop`）不变
- **skill 文档同步**
  - `semantic-model-usage/SKILL.md`：补充 "missing model returns empty matches, never errors"
  - `artifacts-output/SKILL.md`：补充 "archive_artifact requires path under session working dir; check `hint` on error"
  - `query-editor-workflow/SKILL.md`：补充 "user-source editors are pinned to origin session; set_context noop is expected"

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `semantic-model`：在 `datatalk_semantic_lookup` 的契约里追加防御性要求（缺失模型时返回空匹配、不向上抛裸异常、输入校验返回结构化结果），归属于 `semantic-model-foundation` 中已交付的 capability。

## Impact

- **后端代码**（data-talk-adapter 单层）
  - `SemanticLookupActionHandler.java` — 3 项防御加固
  - `ArchiveArtifactAction.java` — `errorResult` 改为 builder，支持附加 `hint`
  - `outputSchema()` 补 `hint` 字段（向后兼容的可选字段）
- **前端代码**（client 单文件，仅字符串字面量）
  - `QueryEditorAdapter.ts:530` — noop 响应 reason 改为结构化值 + 增加 `detail` 字段
- **测试**
  - 新增 `SemanticLookupActionHandlerTest`（4 个 case）
  - 现有 `ArchiveArtifactActionHandlerIT.java` 第 84/95/124 行断言 `error` 字段，hint 字段为新增，无需改动现有断言
- **技能文档**
  - 3 个 SKILL.md 增加 "Known limits" 段落
- **协议契约**
  - 向后兼容：所有变更要么是新增可选字段，要么是行为更安全（不再抛 500）
  - 不变更：action id、输入 schema、`ok`/`error`/`noop`/`success` 字段语义
- **风险**
  - 低。Adapter 层局部 + 前端单行文案，不动 domain/application/infrastructure 接口
  - 不触及数据库或 JDBC，DATA_SOURCE_TYPE_COMPATIBILITY.md 不适用（N/A）
  - 不动 UI/UX/layout/视觉，client/DESIGN.md 设计约束不适用（仅字符串字面量改动，无 visual/interaction 影响，N/A）
- **相关 BUG**
  - `docs/bugs/index.md` 中无 semantic/archive_artifact 模块的 open BUG；query-editor 有 BUG-0042/0043 已 fixed，与本次 noop 文案改进无重叠
  - 本次问题不计入 BUG 库：T29 是代码 review 发现的潜在 NPE（未在 E2E 复现到 stack trace），T32/T12 是已被报告为"设计限制"的预期行为
