## MODIFIED Requirements

### Requirement: 页面刷新后从 localStorage hydrate 草稿

系统 SHALL 在 composer 组件初始化时从独立 localStorage key 读取草稿文本。当 session 切换时，MUST 从对应 key hydrate 新 session 的草稿。附件预览区域 SHALL 位于 InputGroup 内部顶部（InputGroupTextarea 上方），不影响草稿文本的存储和 hydrate 逻辑。

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

#### Scenario: 有附件时草稿文本仍可正常 hydrate
- **GIVEN** 用户在 session `s1` 中添加了 2 个附件并输入 "analyze this"
- **WHEN** 用户按 CTRL+R 刷新页面
- **THEN** composer 输入框显示 "analyze this"（文本草稿恢复）
- **AND** 附件不恢复（附件为临时状态，不持久化）
