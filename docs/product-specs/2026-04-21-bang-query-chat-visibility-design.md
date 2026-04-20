# Bang Query Chat Visibility Design

## 1. 背景

当前用户输入 `!select 1` / `!with ...` 会走 Composer 里的直查分支，直接调用 `/api/query` 并打开 Stage `bang_query` Tab。这个路径不会进入现有消息历史源，因此有两个直接问题：

1. 聊天区没有这次“用户发起了什么”的可见记录，交互割裂。
2. 刷新页面或重新打开会话后，这条输入彻底消失，因为它不属于 `messages` 历史。

同时，输入框在用户进入 `!select` / `!with` 直查模式时没有明显感知，容易让人误以为这仍是普通 AI 对话输入。

## 2. 目标

- `!select` / `!with` 直查输入必须在聊天区可见。
- 该消息必须可持久化，刷新或重新打开会话后仍然存在。
- 消息视觉上仍归属“用户消息”，但要带明确的直查标记。
- Composer 在用户进入直查模式时要给出明显但克制的视觉反馈。
- 历史恢复时顺序必须稳定，不能因为合成消息插入而打乱原有 turn。

## 3. 非目标

- 不把直查请求伪装成 AI assistant 回复。
- 不把直查结果塞进 OpenCode 消息历史。
- 不为 `textarea` 做复杂的局部富文本高亮。
- 不改变现有 `bang_query` Tab / Stage 承载查询结果的主路径。

## 4. 方案选择

### 方案 A：前端临时 user bubble

只在前端 store 插入一条本地 user message，显示后立即执行直查。

优点：
- 实现快

缺点：
- 刷新即丢
- 与服务器历史源脱节
- 顺序只能靠当前 tab 的本地状态，无法稳定恢复

### 方案 B：写入 OpenCode 历史

把 `!select 1` 当作普通 user message 发给 OpenCode，再由前端特殊解释“这条不需要 assistant 回复”。

优点：
- 能进入现有消息历史

缺点：
- 污染 OpenCode 语义
- 会把“直查不走 AI”的边界搞乱
- 未来更难维护消息时序与补偿逻辑

### 方案 C：DataTalk 持久化合成 user message

为当前 session 持久化一条 DataTalk 自己的 synthetic user message，保留原始文本和 metadata；历史接口在返回消息时，把这类消息与 OpenCode 历史统一合并排序；UI 仍按普通 user bubble 渲染，但带“SQL 直查”标记。

优点：
- 刷新后仍在
- 不污染 OpenCode 历史
- 顺序规则可控
- 能明确表达“这是用户发起的直查，不是 AI 会话消息”

缺点：
- 需要后端补一层消息持久化与历史合并

**结论：采用方案 C。**

## 5. 用户体验

### 5.1 聊天气泡

`!select 1` 这类输入在聊天区显示为普通用户气泡：

- 主文本：原始输入，如 `!select 1`
- 右上角或气泡角标：`SQL 直查`
- 语义仍是 user role，不单独新建系统消息样式

这样用户看到的仍然是“我输入了什么”，但能一眼区分这是走直查而非 AI 对话。

### 5.2 输入框直查态

当输入命中以下模式时，Composer 进入直查态：

- 以 `!` 开头
- 去掉 `!` 和首尾空白后，匹配 `select` 或 `with`

直查态视觉：

- 输入框文本颜色和边框进入一套直查态颜色
- 在输入区旁显示轻量标签：`直查模式`
- 不做局部语法高亮，避免 `textarea` 富文本复杂度

当文本不再命中直查模式时，样式立即恢复普通状态。

## 6. 数据模型

新增一类由 DataTalk 自己持久化的 synthetic session message，建议字段至少包含：

- `id`
- `session_id`
- `role`，固定为 `user`
- `text`
- `created_at`
- `kind`，固定为 `bang_query_user`
- `metadata_json`

`metadata_json` 至少包含：

- `displayKind: "bang_query_user"`
- `queryMode: "direct_sql"`

这条记录的职责只有两个：

1. 表达“用户曾发起一次直查”
2. 提供聊天区可恢复的 user bubble 数据源

它不承载查询结果，不替代 Stage artifact / tab。

## 7. 历史合并与顺序保证

当前会话历史主要来自 OpenCode `listMessages`。新方案下，历史接口需要返回：

- OpenCode 消息历史
- DataTalk synthetic user messages

然后在后端统一合并排序，前端继续只消费一个历史结果。

排序规则：

1. 首键：`createdAt` 升序
2. 次键：稳定 source order
   - `synthetic-user`
   - `opencode-user`
   - `opencode-assistant`
3. 末键：稳定 `id`

这样做的原因：

- 同一毫秒内写入时仍然有稳定输出
- 不依赖浏览器本地 Map 插入顺序
- 刷新后不会出现同一 turn 前后翻转

对于 `!select` 这类消息，不要求和 assistant turn 成对。它本质上是“只有 user message 的单臂 turn”，这和当前 `useSessionTurns` 对 orphan turn 的支持是一致的。

## 8. 执行流程

当用户提交 `!select 1` 时，流程改为：

1. 前端识别命中直查模式
2. 若缺活动数据源，先走现有 chooser
3. 前端请求后端创建 synthetic user message
4. 后端返回该 message 的稳定 id / createdAt
5. 前端把这条消息插入当前 session 消息流
6. 前端继续调用现有 `/api/query`
7. 成功后打开 / 更新 `bang_query` Tab
8. 失败时保留这条 user message，不回滚历史

失败不删除消息的原因：

- 这条记录描述的是“用户发起了请求”
- 即使查询失败，这次操作本身也应该可见

失败态可以在后续迭代中考虑给这条气泡补错误标识；首版不强制。

## 9. 前端渲染约定

前端消息模型继续使用现有 `MessageInfo + Part[]` 结构。

对 synthetic `bang_query_user`：

- `role = user`
- part 仍使用 text part
- `metadata.displayKind = "bang_query_user"`

渲染层读取该 metadata：

- `UserBubble` 右上角显示 `SQL 直查` badge
- 文本内容仍原样展示

不新增专门的 query bubble 组件，避免平行 UI 体系。

## 10. 测试策略

后端：

- synthetic user message 持久化测试
- history merge 排序测试，覆盖 `createdAt` 相同的稳定性
- `!select` 重开会话后仍能出现在 `/sessions/{id}/messages`

前端：

- Composer 在 `!select` / `!with` 下进入直查态，输入框变色、标签可见
- synthetic `bang_query_user` 在聊天区显示 user bubble + `SQL 直查` badge
- 历史恢复后顺序不乱
- `!select` 查询失败时消息仍保留

## 11. 风险与约束

- 如果继续让前端自行合并两路历史，顺序很容易再次漂移；因此合并应放在后端。
- 如果把直查消息写进 OpenCode，会让“消息历史”和“直查操作日志”的边界混乱；本方案明确避免这一点。
- 输入框只做整框直查态，不做 token 级富文本渲染，这是为了稳。

## 12. 落地边界

首版只覆盖：

- `!select`
- `!with`

不扩展到其他 `!` 前缀命令。其他 `!` 输入仍维持现有“走 AI 对话”语义。
