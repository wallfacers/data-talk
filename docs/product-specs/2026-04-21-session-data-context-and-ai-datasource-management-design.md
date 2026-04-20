# Session Data Context & AI Data Source Management 设计

**日期**：2026-04-21  
**范围**：统一 `use xxx` 语义、建立 session 级数据上下文、打通 `!sql` / AI 对话 / Stage Query Editor / schema 读取 / SQL 执行的同一上下文闭环，并补齐 AI 可用的数据源管理能力边界  
**关联现状**：现有 `activeConnectionId` 仅覆盖“活动连接”；`StageTab` 已带 `database/schema` 字段但未形成统一协议；`AGENTS.md` 当前仅提供 `choose_connection`，缺少 database/schema 级上下文工具

---

## 1. 背景与目标

当前 DataTalk 已支持：

- 连接列表与活动连接选择
- `!select` / `!with` 直查
- Stage Tab 绑定 `connectionId`
- AI 通过 `datatalk.ui.exec(workspace, choose_connection)` 请求用户选择连接

但“当前到底在哪个 connection / database / schema 下工作”仍未形成统一真相源，导致以下问题：

1. `! use data_aaa` 无法稳定表达“切换到某个连接 / 数据库 / schema”
2. `! select * from users`、AI 对话式查询、Stage SQL Editor 可能读取不同来源的上下文
3. PostgreSQL 等依赖 schema 的数据库，在未显式选择 schema 时容易出现“表找不到”但提示不明确
4. AI 可请求选择连接，但还不能以边界清晰、无歧义的方式管理数据源与数据上下文
5. 连接被 AI 修改后，session 和 Stage 中缓存的连接上下文缺少一致性规则

本设计的目标是：

1. 建立唯一的 **Session Data Context**，统一保存当前 session 的 `connectionId + database + schema`
2. 统一 `use xxx` 命令式与对话式语义，禁止多套猜测逻辑并存
3. 打通 Composer、AI Tool、Stage Query Editor、`read_schema`、`execute_sql` 的统一上下文解析链路
4. 为 AI 提供闭环的数据源管理能力：新增、测试、选择、修改（修改需二次确认；删除不开放）
5. 明确连接修改后的同步与失效处理规则，保证 session / Stage / UI 展示一致

---

## 2. 设计原则

### 2.1 单一真相源

所有数据库执行前都必须先解析出一个 `ResolvedExecutionContext`。前端 store、Stage tab、AI prompt 都不能各自私有定义“当前库”。

### 2.2 `use xxx` 先自动匹配，但不能静默猜错

系统应尽量自动匹配目标；若未命中或命中多个候选，必须返回明确建议或歧义选项，不能假装已切换成功。

### 2.3 资源层与上下文层分离

- **资源层**：连接本身的增改测选
- **上下文层**：在当前 session / tab 下使用哪个 connection / database / schema

这两层共享同一套元数据与校验器，但状态边界必须分开。

### 2.4 Stage Tab 可临时覆盖，但不反向污染 Session

Stage Query Editor / Bang Query 可带自己的上下文覆盖；除非用户显式选择“设为当前会话上下文”，否则 tab override 不回写 session。

### 2.5 AI 只在工具成功后声明状态变化

AI 不能仅凭 prompt 判断“已经切换成功”或“已经修改成功”；只有当工具返回成功结果后，才允许在回复中确认状态已变更。

---

## 3. 范围与非范围

### 3.1 本期范围

1. `! use xxx` 与对话式 `use xxx`
2. Session 级数据上下文持久化
3. Stage tab 上下文继承与临时覆盖
4. SQL 执行与 schema 读取统一消费解析后的上下文
5. AI 数据源管理：新增、测试、选择、修改
6. `AGENTS.md`、UI Object / 前端工具适配器、错误提示文案更新

### 3.2 非范围

1. AI 删除连接
2. 跨连接批量迁移、跨库 diff 等高级数据库管理功能
3. 自动将 Stage override 回写到 session
4. 依赖 prompt-only 的模糊语义处理

---

## 4. 核心模型

### 4.1 Session Data Context

新增 session 级持久化对象：

```ts
type SessionDataContext = {
  sessionId: string
  connectionId: string | null
  connectionNameSnapshot: string | null
  database: string | null
  schema: string | null
  selectedLevel: 'connection' | 'database' | 'schema' | null
  updatedAt: number
}
```

说明：

- `connectionId` 是唯一稳定引用；session 不绑定连接名
- `connectionNameSnapshot` 仅用于 UI 快照展示，连接改名时允许刷新
- `database` 与 `schema` 独立建模，不互相覆盖
- `selectedLevel` 用于表达用户最近一次显式选到了哪一层，便于 AI 与 UI 文案回显

### 4.2 Stage Tab Context Override

`StageTab` 延续现有字段：

- `connectionId`
- `connectionName`
- `database`
- `schema`

但统一定义为：

- 这是 tab 自身的执行上下文快照
- 执行顺序优先级：`tab override > session context > connection default`
- 只对该 tab 生效，不自动改 session context

### 4.3 ResolvedExecutionContext

所有读 schema / 执行 SQL / 自动补全 / 表名定位在执行前统一转换为：

```ts
type ResolvedExecutionContext = {
  connectionId: string
  connectionName: string
  database: string | null
  schema: string | null
  source: 'composer' | 'ai' | 'stage' | 'bang_query'
}
```

执行器只接受 `ResolvedExecutionContext`，不直接消费用户原始输入。

---

## 5. `use xxx` 统一语义

### 5.1 目标层级

`use xxx` 是统一语义入口，但 `xxx` 可以表示：

1. 已配置连接名
2. 当前连接下某个 database
3. 当前连接下某个 schema

前提限制：

- 只能切换到“当前可解析到的真实目标”
- 不允许虚构目标
- 若当前连接是 `aaa`，不能仅因用户输入 `use bbb` 就越权切到不存在或不可解析的目标

### 5.2 自动匹配规则

解析顺序：

1. 若当前 session 已有 `connectionId`，先在该连接可见范围内匹配：
   - `database`
   - `schema`
2. 若当前连接内未命中，再尝试匹配连接名
3. 若仍未命中，返回建议列表，不执行切换
4. 若命中多个候选，返回歧义结果，不执行切换

### 5.3 未命中与歧义

`use xxx` 不能只返回“失败”，必须附带下一步建议：

- 未命中：
  - “当前数据源下未找到 `aaa`。可选项：`bbb`、`ccc`”
- 歧义：
  - “`aaa` 可指向多个目标，请明确选择：连接 `aaa` / schema `aaa`”

AI 收到歧义或未命中结果时，必须继续澄清或给建议，不能静默挑一个继续。

### 5.4 命令式与对话式统一

以下入口都必须调用同一个 resolver：

- `! use xxx`
- 对话输入 `use xxx`
- “切到 xxx”
- “使用 xxx 库”
- “切到 public schema”

前端命令拦截与 AI 自然语言理解不得各自实现一套规则。

---

## 6. 跨数据库兼容策略

### 6.1 PostgreSQL

- connection 一般已固定 database
- `use xxx` 常见目标是 schema
- 未选对 schema 时，`select * from users` 可能找不到表

因此 PG 路径必须：

1. 保留 `database` 与 `schema` 分层
2. 支持 schema 能力探测与候选列举
3. 在执行前显式应用 schema 上下文，而不是完全依赖默认 search path

### 6.2 MySQL

- 更常见的是切 database
- schema 可视为与 database 同义或忽略

### 6.3 H2 / 其他数据库

- 通过能力探测决定支持到哪一层
- 仅支持 connection 的驱动，不暴露 database/schema 级切换

结论：

resolver 不能假设 `xxx` 一定是 database，也不能把 PG schema 语义塞成“数据库名”。

---

## 7. 表名推断与自动补全策略

对于用户输入：

```sql
select * from users
```

处理规则如下：

1. 若当前上下文完整，直接执行
2. 若仅缺 database/schema，则先在当前 connection 下做轻量元数据定位
3. 若能唯一定位到某个 database/schema，自动补全上下文并执行，同时回显：
   - “已自动使用 `public` schema”
4. 若不能唯一定位，停止执行并提示用户选择目标层级

目标是满足“尽量自动推断，但不盲猜”的产品要求。

---

## 8. 后端接口设计

### 8.1 Session Data Context API

新增会话上下文接口：

```http
GET /api/sessions/{id}/data-context
PUT /api/sessions/{id}/data-context
POST /api/sessions/{id}/data-context/resolve-use
POST /api/sessions/{id}/data-context/validate
```

职责：

- `GET`：读取当前 session 的上下文
- `PUT`：显式设置上下文
- `resolve-use`：解析 `use xxx`，返回匹配 / 未命中建议 / 歧义候选
- `validate`：在连接修改、重连、切换后校验上下文是否仍有效

### 8.2 解析响应模型

```ts
type ResolveUseResult =
  | {
      status: 'matched'
      context: SessionDataContext
      matchedTarget: {
        level: 'connection' | 'database' | 'schema'
        label: string
      }
    }
  | {
      status: 'ambiguous'
      candidates: Array<{
        level: 'connection' | 'database' | 'schema'
        connectionId?: string
        connectionName?: string
        database?: string
        schema?: string
        label: string
      }>
    }
  | {
      status: 'not_found'
      suggestions: Array<{
        level: 'connection' | 'database' | 'schema'
        label: string
      }>
      message: string
    }
```

### 8.3 数据源管理 API

后端沿用现有连接 CRUD / test 接口，但对 AI 能力暴露做边界约束：

- 可读：列表、详情、可选目标
- 可执行：新增、测试、选择、修改
- 不可执行：删除

连接修改需提供确认 token 或确认步骤，避免 AI 直接改写连接信息。

---

## 9. AI Tool 与 Prompt 设计

### 9.1 新增工具闭环

推荐补齐以下 AI 工具：

1. `datatalk.get_data_context`
2. `datatalk.set_data_context`
3. `datatalk.resolve_use_target`
4. `datatalk.list_connection_targets`
5. `datatalk.list_connections`
6. `datatalk.create_connection`
7. `datatalk.test_connection`
8. `datatalk.select_connection`
9. `datatalk.update_connection_confirmable`

说明：

- `update_connection_confirmable` 必须走二次确认
- 不提供 `delete_connection`
- `resolve_use_target` 是统一入口；AI 不应直接跳过它去猜测 `use xxx`

### 9.2 `AGENTS.md` 规则更新

需明确写入以下约束：

1. 用户说 `use xxx` 时，先调用 `datatalk.resolve_use_target`
2. 只有 resolver 返回 `matched` 后，才能调用 `datatalk.set_data_context`
3. 返回 `ambiguous` 或 `not_found` 时，必须先澄清或给建议
4. AI 如需让用户手动选择连接，仍可使用 `datatalk.ui.exec(workspace, choose_connection)`
5. 若未来补 `choose_database` / `choose_schema`，也必须通过同一套上下文协议落地

---

## 10. 前端交互设计

### 10.1 Composer

- 支持 `! use xxx`
- 支持 `! select ...`
- 对话式 `use xxx` 交由 AI，但 AI 最终仍通过工具设置上下文
- 若缺连接或上下文不足，先引导修复上下文，再继续原动作

### 10.2 Stage Query Editor

- 打开时默认继承 session data context
- 用户可在 tab 内临时覆盖 connection/database/schema
- 执行 SQL 时优先用 tab override
- tab override 不自动写回 session context

### 10.3 Bang Query

- `!select` / `!with` 命中的直查结果应固化执行时上下文快照
- 历史 bang query tab 在回放 / rerun 时继续使用自身快照
- 若连接仍有效但名称变化，仅刷新展示

### 10.4 数据源管理界面与 AI 联动

- AI 可新增、测试、选择、修改连接
- 修改连接前必须弹确认
- 修改成功后前端需同步刷新连接列表、session context、Stage tab 展示快照

---

## 11. 一致性与失效处理

### 11.1 连接修改后的同步规则

session 永远绑定 `connectionId`，不绑定连接名。

连接被修改后：

1. 若只是名称变化：
   - 保留 `connectionId`
   - 刷新 `connectionNameSnapshot`
   - 刷新 Stage / UI 展示名称
2. 若连接参数变化但目标仍有效：
   - 保留 session context
3. 若原 `database` / `schema` 已失效：
   - 清空失效字段
   - 保留 `connectionId`
   - 给用户明确提示重新选择

### 11.2 不允许的行为

- 不允许在连接修改后静默切换到另一个 database/schema
- 不允许 Stage override 自动回写 session
- 不允许 AI 在工具未确认前声称“已切换成功”或“已修改成功”

---

## 12. 错误处理与文案原则

### 12.1 错误分类

数据库相关错误至少分为：

1. 未选择连接
2. 已选连接但缺 database/schema
3. `use xxx` 未找到目标
4. `use xxx` 命中多个候选
5. 连接更新导致上下文失效
6. SQL 执行时报 schema/table 不存在

### 12.2 文案方向

- 未选连接：
  - “当前还没有选择数据源，请先选择连接”
- 当前连接下未匹配到 `aaa`：
  - “当前数据源下未找到 `aaa`。可选项：`bbb`、`ccc`”
- 匹配歧义：
  - “`aaa` 可指向多个目标，请明确选择：连接 `aaa` / schema `aaa`”
- PG 下未选 schema：
  - “当前连接下未定位到 `users`。该库可能需要先选择 schema，请先执行 `use public` 或明确 schema”
- 连接更新后上下文失效：
  - “当前会话绑定的数据源已更新，原 database/schema 不再有效，请重新选择”

---

## 13. 测试矩阵

### 13.1 后端

1. `resolve-use`
   - 命中 connection
   - 命中 database
   - 命中 schema
   - 当前连接内优先匹配
   - 未找到返回建议
   - 歧义返回候选
2. `validate`
   - 连接更新后仍有效
   - schema/database 失效后被清空
3. 执行链路
   - session context 执行 SQL
   - Stage tab override 执行 SQL
   - PG schema 场景下查表成功 / 失败提示正确
4. 数据源管理
   - AI 新增连接
   - AI 测试连接
   - AI 修改连接要求确认
   - AI 无法删除连接

### 13.2 前端

1. Composer 输入 `! use aaa`
2. Composer 输入 `! select * from users`
3. 无 connection 时的引导
4. 未匹配到时的建议展示
5. Stage 打开继承 session context
6. Stage override 不污染 session
7. 连接修改后 UI 上下文刷新
8. AI 调 `ui_exec` / data-context 工具后界面状态同步

### 13.3 手工冒烟

1. MySQL：切 database 后 `!select`
2. PostgreSQL：切 schema 后 `!select`
3. 对话式 `use aaa` 后 AI 正确走工具闭环
4. 修改连接导致 schema 失效后提示重选
5. AI 新增连接并立即切换使用

---

## 14. 实施拆分与并行边界

为便于多智能体并行执行，建议拆为四个子系统：

### Batch A：后端 Session Context 与 Resolver

- session data context 持久化
- resolve / validate API
- 能力探测与 PG schema 支持

### Batch B：前端 Composer / Stage Context 消费

- `! use`
- `! select`
- Stage Query Editor 继承与 override

### Batch C：AI Tool / Prompt / UI Adapter

- `AGENTS.md`
- 新 data-context tools
- `choose_connection` 与上下文协议对齐

### Batch D：AI 数据源管理一致性

- 新增 / 测试 / 选择 / 修改连接
- 修改前确认
- 修改后 session / Stage / UI 刷新与失效清理

依赖关系：

- Batch A 是执行基线
- B 与 C 可在 A 的接口草案稳定后并行
- D 依赖 A 的 validate 与失效规则

---

## 15. 实施顺序建议

1. 先做后端 `SessionDataContext + resolve-use + validate`
2. 再接 Composer / Stage 的上下文消费
3. 再补 AI tools、`AGENTS.md`、前端适配器
4. 最后接入 AI 数据源管理与连接修改后的刷新逻辑
5. 联调阶段重点回归 PostgreSQL schema 场景

---

## 16. 验收标准

满足以下条件才视为完成：

1. 用户可通过 `! use xxx` 稳定切换到可解析的 connection/database/schema
2. 用户直接输入 `use xxx` 时，AI 能明确调用工具完成切换，而非仅口头确认
3. `! select`、AI 查询、Stage SQL Editor 使用同一套上下文解析规则
4. PostgreSQL 未选 schema 时，系统能给明确引导，而不是模糊报错
5. AI 可新增、测试、选择、修改连接；修改需确认；删除不可用
6. 连接修改后，session / Stage / UI 状态一致，失效上下文被清空并提示

---

## 17. 结论

本设计将“活动连接”升级为“可验证、可持久化、可被 AI 与 UI 共用的 session 数据上下文”，把命令式、对话式、Stage 执行与数据源管理收敛到同一套协议之下。它的重点不是新增更多命令，而是消除多套状态、多套猜测、多套提示并存的歧义源，确保用户输入后系统能明确知道要做什么、在哪个库上做、失败时该怎么继续。
