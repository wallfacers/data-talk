# UI Object Protocol — Registered Objects Reference

> 维护说明：每次新增或修改 `UIObject` Adapter（`client/src/features/stage/adapters/`）时，**必须同步更新本文件**，并同步更新 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`。运行时提示词由 `OpenCodeGatewayBeans.writeAgentsMd()` 从该资源文件写入 OpenCode 工作目录。
>
> 范围：前端已注册到 `UIRouter` 的所有 UIObject 类型，含 state schema、patch capabilities、exec actions。

---

## 协议摘要

每个 UIObject 通过 `type + objectId` 标识，对外暴露三个动词：

| 动词 | 后端 Action | 含义 |
|------|------------|------|
| `read(mode)` | `datatalk.ui.read` | 读对象状态 / schema / actions / full |
| `patch(ops)` | `datatalk.ui.patch` | JSON Patch 修改对象属性 |
| `exec(action, params)` | `datatalk.ui.exec` | 执行具名动作 |

发现入口：`datatalk.ui.list`（可按 `type` 过滤）。

---

## 已注册对象

### 1. `workspace`

**源文件**：`client/src/features/stage/adapters/WorkspaceAdapter.ts`  
**objectId**：固定为 `"workspace"`  
**说明**：Tab 容器，管理整个 Stage 内的所有标签页。

#### `read` 输出

| mode | 返回内容 |
|------|---------|
| `state` | `{ tabs: Array<{tabId, type, title, connectionId, contextOverride?}>, activeTabId: string \| null }` |
| `schema` | `{ type: 'object', properties: { tabs: array, activeTabId: string\|null } }` |
| `actions` | 见下方 Exec Actions 列表 |
| `full` | `{ state, schema, actions }` 合并 |

其中 `query_editor` 行的 `connectionId` 反映**当前生效的上下文**，`contextOverride` 单独暴露覆盖态元数据。

#### `patch`

不支持（返回 `status: 'error'`，workspace 为只读，需通过 `exec` 操作）。

#### Exec Actions

| action | 必填参数 | 可选参数 | 效果 |
|--------|---------|---------|------|
| `open` | `type: string` | `title`, `connection_id`, `database`, `schema`, `payload` | 开一个新 Tab；`type` 决定 Tab 类型和 scope（见下表） |
| `close` | `target: tabId` | — | 关闭指定 Tab |
| `focus` | `target: tabId` | — | 聚焦指定 Tab |
| `choose_connection` | — | `preferredConnectionId: string` | 弹出数据源选择器，等待用户选择后返回结果 |

**`open` 的 Tab type → scope 映射**

| type | scope | 说明 |
|------|-------|------|
| `query_editor` | session | 统一 SQL 工作页；用于空白 SQL、资源树 SQL、AI 预填 SQL 与 direct SQL |
| `er_canvas` | workspace | ER 图画布 |
| `markdown_note` | workspace | Markdown 笔记 |
| `report` | workspace | 报表页 |
| `dashboard` | workspace | 仪表盘页 |
| 其他（未在 `WORKSPACE_SCOPE_TYPES` 中） | session | 会话级 Tab，关联 `originSessionId` |

---

### 2. `query_editor`

**源文件**：`client/src/features/stage/adapters/QueryEditorAdapter.ts`  
**objectId**：`tabId`  
**说明**：统一 SQL 工作页，承接手动 SQL、资源树 SQL、AI 预填 SQL 与 direct SQL。

#### `read` 输出

| mode | 返回内容 |
|------|---------|
| `state` | `{ tabId, title, scope, content, language: 'sql', version, dirty, cursor, selection, connectionId, connectionName, database, schema, contextOverride, entryMode, autoRun, executeStatus, results, activeResultId, limit }` |
| `schema` | `{ type: 'object', properties: { tabId, title, scope, content, language, version, dirty, cursor, selection, connectionId, connectionName, database, schema, contextOverride, entryMode, autoRun, executeStatus, results, activeResultId, limit } }` |
| `actions` | 见下方 Exec Actions 列表 |
| `full` | `{ state, schema, actions, capabilities }` 合并 |

#### `capabilities`

```ts
{
  editableContent: true,
  acceptsTextEdits: true,
  runnable: true,
  formattable: true,
  supportsContextBinding: true,
  supportsResults: true,
}
```

#### `state` 字段说明

- `content`：当前 SQL 文本
- `version`：SQL 文本版本号；配合 `apply_text_edits.baseVersion` 使用
- `dirty`：是否存在未保存的文档改动
- `cursor` / `selection`：编辑器光标与选区
- `connectionId / connectionName / database / schema`：**当前生效的执行上下文**，会反映基础 tab/payload、继承的 session context，以及任何 override
- `contextOverride`：覆盖态元数据 / 来源信息；与上面的生效上下文字段分开暴露
- `entryMode / autoRun`：打开来源与是否自动执行
- `executeStatus / results / activeResultId / limit`：运行时状态
- `results`：**仅摘要，不包含 `rows`**；每项仅暴露 `{ resultId, statementIndex, columns, rowCount, durationMs, truncated, error? }`

#### `patch`

支持以下白名单路径：

| path | ops | 作用 |
|------|-----|------|
| `/content` | `replace` | 整段覆盖 SQL 文本 |
| `/connectionId` | `replace` | 修改连接 |
| `/database` | `replace` | 修改数据库 |
| `/schema` | `replace` | 修改 schema |

#### Exec Actions

| action | 参数 | 效果 |
|--------|------|------|
| `apply_text_edits` | `{ baseVersion, edits: [{ range, text }] }` | 按 range 精确编辑 SQL，带版本冲突保护 |
| `set_context` | `{ connectionId?, database?, schema? }` | 一次性设置执行上下文；至少传一个字段 |
| `run_sql` | `{ limit? }` | 执行当前 SQL，结果写回 query editor runtime state |
| `format_sql` | — | 格式化当前 SQL，并更新 `content/version` |
| `focus` | — | 聚焦此 Tab |
| `close` | — | 关闭此 Tab |

---

## 新增 / 修改 Adapter 的 Checklist

新增一个 UIObject Adapter 或修改现有 Adapter 时，**按序完成以下步骤**：

- [ ] 在 `client/src/features/stage/adapters/` 编写 Adapter 类，实现 `UIObject` 接口
- [ ] 在 `useUIObjectRegistry.ts` 或相应初始化逻辑中注册 Adapter 实例
- [ ] 更新本文件（`docs/references/ui-objects-reference.md`）对应章节
- [ ] 更新 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`，确保运行时提示词与实现保持一致
- [ ] 若新增了 exec action 的 `type` 值（如新 Tab type），同步更新 `WORKSPACE_SCOPE_TYPES`（`WorkspaceAdapter.ts`）
