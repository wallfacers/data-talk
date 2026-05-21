## ADDED Requirements

### Requirement: script_editor tab 类型

系统 SHALL 支持 `script_editor` tab 类型，提供 Monaco 代码编辑器 + xterm.js 控制台面板的 IDE 风格界面。

#### Scenario: tab 注册

- **WHEN** 应用启动
- **THEN** `tab-type-registry.ts` 包含 `script_editor` 类型定义：type=`script_editor`, persistent=true, scope=`workspace`, icon=TerminalIcon, labelKey=`tabType.scriptEditor`
- **AND** 该 tab 类型出现在 stage tab bar 中可被打开

#### Scenario: 打开新脚本 tab

- **WHEN** 用户或 AI 请求打开新的 script_editor tab
- **THEN** 创建新 tab，Monaco 编辑器区域初始为空（或由 AI 填入模板代码）
- **AND** 语言选择器默认选择 Python（如果检测到环境）
- **AND** 连接选择器显示当前 session 的数据源连接
- **AND** 控制台面板折叠，显示空状态

### Requirement: Monaco 代码编辑器

script_editor tab SHALL 内嵌 Monaco 编辑器，支持 Python 和 JavaScript 语法高亮、代码补全和格式化。

#### Scenario: Python 语法高亮

- **WHEN** 语言选择器选中 Python
- **THEN** Monaco 编辑器使用 Python language mode，提供关键字高亮、缩进辅助、括号匹配

#### Scenario: JavaScript 语法高亮

- **WHEN** 语言选择器选中 JavaScript
- **THEN** Monaco 编辑器使用 JavaScript language mode，提供关键字高亮、ES6+ 语法支持

#### Scenario: AI 通过 apply_text_edits 修改代码

- **GIVEN** 一个已打开的 script_editor tab
- **WHEN** AI 调用 `datatalk_ui_exec` object=`script_editor` action=`apply_text_edits`
- **THEN** Monaco 编辑器应用文本编辑（支持 insert/replace/delete），遵循 baseVersion 冲突检测
- **AND** 用户看到代码实时更新

### Requirement: xterm.js 控制台面板

script_editor tab SHALL 内嵌 xterm.js 控制台面板，显示脚本运行输出。

#### Scenario: 控制台布局

- **WHEN** script_editor tab 被渲染
- **THEN** 上半部分为 Monaco 编辑器，下半部分为可折叠的 xterm.js 控制台面板
- **AND** 面板高度可通过拖拽分隔条调整
- **AND** 控制台背景使用 `bg.canvas` token，与 Monaco 编辑器背景一致

#### Scenario: 主题切换

- **WHEN** app 在 dark/light 主题间切换
- **THEN** xterm.js theme 自动更新：
  - Dark: 背景 `bg.canvas` (neutral.950)，前景 `text.primary` (neutral.100)，ANSI 色彩映射到 dark 变体
  - Light: 背景 `bg.canvas` (neutral.25)，前景 `text.primary` (neutral.900)，ANSI 色彩映射到 light 变体
- **AND** 切换无闪烁

#### Scenario: 控制台交互

- **WHEN** 脚本运行中输出内容
- **THEN** xterm.js 自动滚动到底部
- **AND** 用户可向上滚动查看历史输出（scrollback ≥ 10000 行）
- **AND** 用户可选中文字并复制

### Requirement: 运行控制工具栏

script_editor tab SHALL 提供工具栏，包含 Run/Stop 按钮、语言选择器、连接选择器。

#### Scenario: Run 按钮

- **GIVEN** 脚本未在运行中，且检测到对应语言环境
- **WHEN** 用户点击 Run 按钮
- **THEN** Run 按钮变为 disabled，Stop 按钮变为 enabled
- **AND** 控制台面板自动展开（如果折叠）
- **AND** 控制台清空上次输出，显示运行启动信息

#### Scenario: Run 按钮 disabled 状态

- **WHEN** 未检测到对应语言环境，或脚本正在运行中
- **THEN** Run 按钮显示为 disabled 状态（opacity 降低 + cursor not-allowed），不响应点击

#### Scenario: Stop 按钮

- **GIVEN** 脚本正在运行中
- **WHEN** 用户点击 Stop 按钮
- **THEN** 发送终止信号到子进程
- **AND** Stop 按钮变为 disabled，Run 按钮恢复 enabled

### Requirement: script-workbench-store 状态管理

系统 SHALL 使用 Zustand store 管理 script_editor tab 的状态。

#### Scenario: per-tab 状态隔离

- **WHEN** 存在多个 script_editor tab
- **THEN** 每个 tab 拥有独立的：scriptText, version, language, executeStatus, consoleOutput, connectionId
- **AND** 操作 tab A 的状态不影响 tab B

#### Scenario: 执行状态机

- **WHEN** 脚本运行状态变化
- **THEN** executeStatus 按 `idle` → `running` → `success` | `error` 流转
- **AND** `running` 状态下 consoleOutput 可持续追加
- **AND** 到达终态后 consoleOutput 冻结

### Requirement: AI 多轮对话代码编辑

AI SHALL 能通过 OpenCode protocol 与用户多轮对话，编写和修改脚本内容。

#### Scenario: AI 生成脚本

- **WHEN** 用户在聊天中说"帮我写个 Python 脚本抓取 xxx"
- **THEN** AI 调用 `ui_exec` 打开 script_editor tab（如果没有打开的）
- **AND** AI 调用 `apply_text_edits` 写入生成的 Python 代码
- **AND** 用户在 Monaco 编辑器中看到代码

#### Scenario: AI 修改脚本

- **WHEN** 用户在聊天中说"加上分页逻辑"
- **THEN** AI 调用 `apply_text_edits` 修改当前 script_editor tab 的代码
- **AND** Monaco 编辑器显示修改后的代码
