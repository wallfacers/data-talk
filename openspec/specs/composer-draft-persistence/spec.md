## ADDED Requirements

### Requirement: 草稿写入独立 localStorage key

系统 SHALL 将每个 session 的 composer 草稿存储在独立 localStorage key `dt.draft.<draftKey>` 中，其中 `draftKey` 为 `activeSessionId` 或 `__nosession__`。每次击键时 MUST 同步调用 `localStorage.setItem` 写入当前文本。

#### Scenario: 用户输入文本后草稿写入 localStorage
- **GIVEN** 用户在 session `s1` 的 composer 中输入 "hello"
- **WHEN** keystroke 触发 `setComposerDraft('s1', 'hello')`
- **THEN** `localStorage.getItem('dt.draft.s1')` 返回 `"hello"`
- **AND** Zustand 内存中 `composerDrafts['s1']` 也为 `"hello"`

#### Scenario: 无 session 时草稿写入 __nosession__ key
- **GIVEN** 用户未选中任何 session，在 composer 中输入 "test"
- **WHEN** keystroke 触发 `setComposerDraft('__nosession__', 'test')`
- **THEN** `localStorage.getItem('dt.draft.__nosession__')` 返回 `"test"`

### Requirement: 发送时同步清除 localStorage 草稿

系统 MUST 在消息成功发送时同步调用 `localStorage.removeItem('dt.draft.<draftKey>')` 清除对应草稿。此操作 SHALL 在 `updateText('')` 调用路径中执行，确保在页面卸载前完成。

#### Scenario: 发送消息后 CTRL+R 刷新输入框为空
- **GIVEN** 用户在 session `s1` 的 composer 中输入 "hello" 并按 Enter 发送
- **WHEN** `submitText` 执行 `updateText('')`
- **THEN** `localStorage.getItem('dt.draft.s1')` 返回 `null`
- **WHEN** 用户立刻按 CTRL+R 刷新页面
- **THEN** 页面重新加载后 composer 输入框为空

#### Scenario: 发送失败后草稿恢复
- **GIVEN** 用户在 session `s1` 的 composer 中输入 "hello" 并按 Enter 发送
- **AND** `sendMessage` 返回失败
- **WHEN** `submitText` 执行 `updateText(trimmed)` 恢复文本
- **THEN** `localStorage.getItem('dt.draft.s1')` 返回 `"hello"`
- **AND** composer 输入框显示 "hello"

### Requirement: 页面刷新后从 localStorage hydrate 草稿

系统 SHALL 在 composer 组件初始化时从独立 localStorage key 读取草稿文本。当 session 切换时，MUST 从对应 key hydrate 新 session 的草稿。

#### Scenario: 刷新后恢复正在编辑的草稿
- **GIVEN** 用户在 session `s1` 的 composer 中输入 "hello"（未发送）
- **WHEN** 用户按 CTRL+R 刷新页面
- **AND** 页面重新加载，composer 组件 mount
- **THEN** composer 输入框显示 "hello"

#### Scenario: session 切换后恢复对应草稿
- **GIVEN** session `s1` 的草稿为 "hello"，session `s2` 的草稿为 "world"
- **WHEN** 用户从 `s1` 切换到 `s2`
- **THEN** composer 输入框显示 "world"
- **WHEN** 用户从 `s2` 切换回 `s1`
- **THEN** composer 输入框显示 "hello"

### Requirement: session 删除时清理对应草稿 key

系统 MUST 在删除 session 时同步调用 `localStorage.removeItem('dt.draft.<sessionId>')` 清除对应草稿，防止 localStorage 泄漏。

#### Scenario: 删除 session 后草稿 key 被清理
- **GIVEN** session `s1` 在 localStorage 中有草稿 key `dt.draft.s1`
- **WHEN** 用户删除 session `s1`
- **THEN** `localStorage.getItem('dt.draft.s1')` 返回 `null`

### Requirement: 不通过 Zustand persist 持久化 composerDrafts

系统 MUST NOT 将 `composerDrafts` 包含在 Zustand persist 的 `partialize` 输出中。`data-talk.session` localStorage key 中 SHALL 不出现 `composerDrafts` 字段。

#### Scenario: partialize 不包含 composerDrafts
- **GIVEN** 用户在 session `s1` 中输入 "hello"
- **WHEN** Zustand persist 中间件执行 flush
- **THEN** `data-talk.session` localStorage value 的 `state` 对象中不包含 `composerDrafts` 字段
