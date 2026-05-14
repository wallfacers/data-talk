# 金本位关键词 → 唯一 owning-skill 归属表

> 实施期工件，archive 时随分支历史保留，不进 `openspec/specs/`。
> 用于 §6.1 `SkillRoutingContractTest` 中 "skill 切分边界唯一性" 的输入。
>
> 契约（spec.md "skill 切分边界唯一性（基于关键词清单）"）：对每个 `keyword`，
> 其在 11 个新 SKILL.md 全文中作为大小写敏感子串首次完整定义只能出现在 `owning-skill`；
> 其他 skill 如需引用，**必须**用 `[[<owning-skill>]]` 短引用而非复制原句。

## 关键词清单（at least the 12 seed keywords from spec）

| keyword | owning-skill | 出现语义（用于人工核对） |
|---|---|---|
| `READ-ONLY` | sql-execution | `datatalk_execute_sql` 只读约束 |
| `confirm=true` | connection-management | confirmable mutation 两阶段协议第二阶段 |
| `confirmationToken` | connection-management | confirmable mutation 第二次调用要求字段 |
| `apply_text_edits` | ui-contract | UI exec 编辑动词；带 `baseVersion` + `expectedText` |
| `truncated` | sql-execution | `datatalk_read_schema` 返回 `truncated=true` 的处理策略（schema 截断；不是落盘工件语义） |
| `set_data_context` | connection-management | 会话级数据上下文切换 |
| `er_inspector` | er-tabs | ER Diagram Viewer 类型 |
| `er_designer` | er-tabs | ER Diagram Designer 类型 |
| `expectedVersion` | concurrency-contract | `ui_patch` version 冲突时的版本期望字段（OAC 规范用语；与 `baseVersion`、`expectedText` 同族）|
| `supersedes` | artifacts-output | artifact 链接：`datatalk_render_chart` / `datatalk_supersede_artifact` |
| `information_schema` | sql-execution | "table doesn't exist" 探查的跨数据库 probe SQL |
| `use xxx` | connection-management | `datatalk_resolve_use_target` 入口的用户措辞 |

## 补充关键词（实施期视情况追加）

| keyword | owning-skill | 备注 |
|---|---|---|
| `saved file path` | artifacts-output | 大输出落盘语义；其他 skill 用 `[[artifacts-output]]` |
| `version_conflict` | concurrency-contract | `ui_patch` HTTP 409 错误码 |
| `expected_text_mismatch` | concurrency-contract | `apply_text_edits` 范围文本不匹配错误码 |
| `baseVersion` | concurrency-contract | 乐观锁版本号（虽然 ui-contract 也提及，但完整定义归 concurrency-contract，ui-contract 仅描述工具签名） |
| `dashboardJson` | charts-and-dashboards | dashboard 创建参数 |
| `boundSessionId` | query-editor-workflow | query_editor 的 session 绑定字段 |
| `inWorkset` | tab-management | Library vs Workset 视图 |
| `IngestionConfirmedToken` | data-ingestion | 5-min single-use；既有 skill，本表只声明归属，不再迁移 |

## 使用说明（给 SkillRoutingContractTest）

1. 在 11 个新 SKILL.md 中**大小写敏感**子串搜索每个 keyword。
2. 命中文件集合 `H` MUST 满足 `H ⊆ {owning-skill} ∪ {含 [[owning-skill]] 短引用的其他 skill}`。
3. owning-skill 自身文件中 MUST 至少命中 1 次（即首次完整定义存在）。
4. 短引用合规检查：若 keyword 出现在非 owning-skill 文件中，该出现位置所在的句子（或同行/上下相邻行）MUST 含 `[[owning-skill]]` 形式的短引用。
