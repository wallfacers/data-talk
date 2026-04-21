# UI Object Protocol — Registered Objects Reference

> 维护说明：每次新增或修改 `UIObject` Adapter（`client/src/features/stage/adapters/`）时，**必须同步更新本文件**，并同步更新 `OpenCodeGatewayBeans.java` 中的 `AGENTS_MD` 常量。
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
| `state` | `{ tabs: Array<{tabId, type, title, connectionId}>, activeTabId: string \| null }` |
| `schema` | `{ type: 'object', properties: { tabs: array, activeTabId: string\|null } }` |
| `actions` | 见下方 Exec Actions 列表 |
| `full` | `{ state, schema, actions }` 合并 |

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
| `bang_query` | workspace | 用户 `!sql` 直查产生的只读 SQL Tab |
| `query_editor` | workspace | AI 可操作的 SQL 编辑 Tab |
| `er_canvas` | workspace | ER 图画布 |
| `markdown_note` | workspace | Markdown 笔记 |
| 其他（未在 `WORKSPACE_SCOPE_TYPES` 中） | session | 会话级 Tab，关联 `originSessionId` |

---

### 2. `bang_query`

**源文件**：`client/src/features/stage/adapters/BangQueryAdapter.ts`  
**objectId**：`tabId`（动态，格式 `bang_query_<timestamp>_<random>`）  
**说明**：用户通过 `!<sql>` 直查产生的 Tab；结果行**不**暴露给 AI（防止大结果集污染上下文）。

#### `read` 输出

| mode | 返回内容 |
|------|---------|
| `state` | 见下方 State Schema |
| `schema` | `{ type: 'object', properties: { sql: string, pinned: boolean }, patchCapabilities: [...] }` |
| `actions` | 见下方 Exec Actions 列表 |
| `full` | `{ state, schema, actions }` 合并 |

**State Schema**（`mode: 'state'` 返回字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| `sql` | `string` | 当前 Tab 的 SQL 语句 |
| `connectionId` | `string \| undefined` | 所用连接 ID |
| `connectionName` | `string \| undefined` | 所用连接显示名称（供 AI 展示用） |
| `database` | `string \| undefined` | 目标数据库 |
| `schema` | `string \| undefined` | 目标 schema |
| `lastRun` | `{ columns, rowCount, durationMs, truncated } \| undefined` | 最近一次执行元数据 |
| `pinned` | `boolean` | 是否已钉住（默认 `false`） |
| ~~`rows`~~ | — | **故意不暴露**：结果行不进 AI 上下文 |

#### Patch Capabilities

| path | ops | 说明 |
|------|-----|------|
| `/pinned` | `replace` | 钉住 / 取消钉住此 Tab |

#### Exec Actions

| action | 参数 | 效果 |
|--------|------|------|
| `rerun` | — | 重新执行 Tab 内的 SQL（需要 `connectionId`） |
| `focus` | — | 聚焦此 Tab |
| `close` | — | 关闭此 Tab |

---

## 新增 / 修改 Adapter 的 Checklist

新增一个 UIObject Adapter 或修改现有 Adapter 时，**按序完成以下步骤**：

- [ ] 在 `client/src/features/stage/adapters/` 编写 Adapter 类，实现 `UIObject` 接口
- [ ] 在 `useUIObjectRegistry.ts` 或相应初始化逻辑中注册 Adapter 实例
- [ ] 更新本文件（`docs/references/ui-objects-reference.md`）对应章节
- [ ] 更新 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java` 的 `AGENTS_MD` 常量，使 AI 提示词与实现保持一致
- [ ] 若新增了 exec action 的 `type` 值（如新 Tab type），同步更新 `WORKSPACE_SCOPE_TYPES`（`WorkspaceAdapter.ts`）
