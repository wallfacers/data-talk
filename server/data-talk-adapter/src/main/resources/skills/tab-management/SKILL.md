---
name: tab-management
description: Tab 生命周期、复用、持久化与搜索策略。触发词：复用 tab / 新开 tab / 当前编辑器 / 上一条 SQL / 继续编辑 / library vs workset / tab reuse / continuation / search tabs / locate text / inWorkset / archive tab / focus tab。指导何时复用 query_editor、何时新开 tab、library 与 workset 的区别、跨重启持久化、以及 ui_find 三动词（list/search/read）组合策略。
---

## When to use

- 用户提到"当前 SQL 编辑器""刚才那条 SQL""继续编辑"等可能复用已有 tab 的措辞。
- 需要在 workbench 中打开新工作面板（尤其是 `query_editor`）之前判断意图。
- 需要在所有持久化 tab 中检索内容或定位文本片段。
- 需要在 library（持久全集）和 workset（顶部 tab 栏当前打开集合）之间移动 tab。

## When NOT to use

- 仅修改 `query_editor` 内容、版本、上下文等编辑细节 — 见 `[[query-editor-workflow]]`。
- ER tab（`er_inspector` / `er_designer`）的内部结构、视图与编辑动作 — 见 `[[er-tabs]]`。
- `datatalk_ui_find` / `datatalk_ui_read` 工具签名、参数 schema、错误码等协议细节 — 见 `[[ui-contract]]`。本 skill 仅讲业务策略（何时用哪种 mode）。

## Library vs Workset

并存的两种 tab 视图：

- **Library**：全部未归档 tab 的持久集合。由 `datatalk_ui_find` 与左栏暴露。包含当前并未在顶部 tab 栏中打开的 tab。
- **Workset**：当前在顶部 tab 栏中打开的 tab。按应用实例追踪，不在服务端持久化。由 `datatalk_ui_read` 的 `state.inWorkset` 字段反映。

用户措辞映射："current SQL editor" / "当前 SQL 编辑器" 通常指 workset tab；"the SQL I wrote yesterday" / "我昨天写的那条 SQL" 指可能不在 workset 中的 library tab。

用于在两种视图间移动 tab 的 workspace 动词（`focus` / `detach` / `archive` / `trash`）的工具签名见 `[[ui-contract]]`。本视图特有语义：`archive(archived=true)` 与 `trash` 都会级联将 tab 从 workset 中分离；已归档的 tab 必须先反归档才能被 focus。

## Tab reuse vs new tab

在打开任何 workbench tab（尤其是 `query_editor`）之前，**必须**先把用户意图分类为 **new task** 或 **continuation**。

### New task

用户发起全新请求，没有引用任何先前的 SQL、编辑器或 assistant 行为。示例：

- "show the users table"、"query orders"、"view customers"
- "show 10 rows from products"、"open SQL for customers"、"write a query for recent orders"
- "查一下用户表"、"看下订单"、"写一条最近订单的 SQL"

对 new task，遵循 "Open or Reuse a SQL Workspace" 流程：优先复用已存在的空白或内容已匹配的 `query_editor`；否则通过 workspace open 动作创建新 tab（具体调用语义见 `[[ui-contract]]`）。

### Continuation

用户在修复、调整、扩展、迭代最近一条产出或执行过的 SQL。示例：

- "the SQL is wrong, keep editing"、"continue editing"、"add a where clause to that SQL"
- "change it to limit 100"、"change the date range to the last 7 days"、"add a group by"
- "the SQL is wrong, fix it"、"add a where clause"、"change limit to 50"
- "刚才那条 SQL 写错了，继续改"、"加一个 where"、"把 limit 改成 50"

continuation 场景下，**必须**复用已有的 `query_editor`。新开 tab 会丢弃用户已有工作并制造重复 tab —— 这是路由违例，不是安全兜底。判定后的执行步骤：

1. 若本会话工具调用历史中仍有最近操作的 `query_editor` 的 `tabId`，直接定位它。
2. 否则用 `datatalk_ui_find` + `filter.type=query_editor`，按最近活跃排序（或 `lastTouchedAfter`）挑出最近触达的 tab；必要时用 `workspace.activeTabId` 打破平局。
3. 用 `datatalk_ui_read` 取该 tab 当前内容与版本（具体 `mode` 选择见 `[[ui-contract]]`）。
4. 编辑路径与版本协议见 `[[query-editor-workflow]]`。
5. 若目标 tab 不在 workset，先用 workspace `focus` 动作把它拉入 workset 再编辑。

### Continuation signals

| 类别 | 触发短语 |
|---|---|
| 中文 | 继续、继续改、接着改、刚才那条、上面那条、那条 SQL、这条 SQL、上面的、改一下、再加、再加一个 where、再加一个 limit、改成 X、把 X 改成 Y、写错了 |
| English | continue, keep editing, the SQL, that query, the editor, the one above, fix it, adjust, change X to Y, add a where, add a limit, previous query, this SQL, that SQL, the SQL above, edit again, add another clause |

### Ambiguous cases

若一句话既可能是 new task 也可能是 continuation，**必须**先询问用户是改当前 SQL 编辑器还是新开一个，而不是默默猜。把用户仍打开的 SQL 丢掉，比多问一句澄清问题严重得多。

## UI navigation rules

- 当用户问到"当前"或"已打开"的 SQL 编辑器时，先用 `datatalk_ui_find` + `filter.type=query_editor`。
- 若存在多个 `query_editor` tab，优先用 `datatalk_ui_find` 返回的精确 `tabId`。
- 多编辑器且意图不明时，读 workspace（`object=workspace`），检查 `state.activeTabId`。
- 若当前活跃 workspace tab 不是 `query_editor`，对 `object=query_editor` 使用 `target=active` 会失败。
- 仅当活跃对象已经明确时才使用 `target=active` 或省略 `target`；其他场合明确传 `tabId`。
- tab 复用 vs 新开决策遵循上面的"Tab reuse vs new tab"小节。
- 优先用 library/workset 区分：用 `datatalk_ui_find` 发现持久化 tab，再用 workspace `focus` 把目标 tab 拉入 workset。
- 若用户只是在问"有没有打开的 SQL 编辑器"或"看一下当前的"，而实际并不存在 `query_editor`，**必须**如实说没有，不要假装有一个打开着。若任务本身需要 query editor，再走 workspace open 流程。

## Tab persistence and search

Tab 跨应用重启持久化；相同 `tabId` 标识相同的逻辑工作对象。

`datatalk_ui_find` 有三个可组合动词：

- **list**：只用 `filter` —— 仅返回元数据。
- **search**：`filter` + `query`（`query.mode` 为 `fts` / `regex` / `substring`）。
- **read**：`read.tabIds` —— 拉取内容，可选行范围。

组合策略：

- `filter + query + output.mode=tabs_only` ≈ `grep -l`：只要命中 tab 的列表。
- `filter + query + read`：先窄化再读内容。
- 仅元数据浏览：单 `filter`。

输出预算：默认 `headLimit=100`、`maxTabs=50`。存在性检查用 `output.mode=count` 或 `tabs_only`；只有真的需要匹配行时才用 `mode=matches`；避免一次对很多 tab 做全文读取。

工具参数 schema 与错误处理细节见 `[[ui-contract]]`。

## Recommended workflow

### Locate Text Inside an Existing Tab

1. `datatalk_ui_find` 以 `query.mode=fts`、`query.pattern=<text>`、`output.mode=tabs_only` 检索：先拿到候选 tab 列表。
2. 若需要确认是哪一条命中行，重新以 `output.mode=matches` 跑一次。
3. 把范围窄化到目标 tab 后，再用 `read.tabIds` 配合行范围或 `"full"` 拉取内容。

该工作流体现"先 list / 再 search / 最后 read"的预算友好顺序：先用 metadata 与命中数过滤，再花字节读完整内容。
