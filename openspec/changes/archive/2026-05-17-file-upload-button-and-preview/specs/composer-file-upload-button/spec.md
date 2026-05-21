## ADDED Requirements

### Requirement: 工具栏上传按钮点击触发文件选择

系统 SHALL 在 PromptComposer 工具栏中发送按钮左侧提供一个 Paperclip 图标按钮（📎）。按钮 SHALL 使用 ghost 样式（无背景填充、`text-text-muted` 色值、`size="icon-xs"` + `rounded-full`）。点击按钮 SHALL 触发隐藏的 `<input type="file" multiple>` 文件选择对话框。按钮 MUST 具有 `aria-label="Attach files"` 可访问性标注。

#### Scenario: 用户点击上传按钮打开文件选择器
- **GIVEN** 用户在 PromptComposer 中
- **WHEN** 用户点击 Paperclip 上传按钮
- **THEN** 系统打开操作系统文件选择对话框
- **AND** 文件选择器允许多选

#### Scenario: 用户选择文件后附件出现在输入框顶部
- **GIVEN** 用户通过上传按钮选择了 `report.csv` 和 `schema.json`
- **WHEN** 文件选择完成
- **THEN** 两个文件的附件卡片出现在 InputGroup 顶部水平区域
- **AND** 附件区域可水平滚动

### Requirement: 附件预览区域位于 InputGroup 顶部

系统 SHALL 将附件预览区域放置在 InputGroup 内部、InputGroupTextarea 上方。附件 SHALL 以水平行排列，超出宽度时水平滚动。无附件时该区域 SHALL 不渲染。

#### Scenario: 多个附件水平排列并可滚动
- **GIVEN** 用户上传了 5 个文件
- **WHEN** 附件卡片渲染
- **THEN** 卡片水平排列在输入框顶部
- **AND** 容器可水平滚动查看所有附件

#### Scenario: 无附件时不占用空间
- **GIVEN** 用户未添加任何附件
- **WHEN** PromptComposer 渲染
- **THEN** 附件预览区域不渲染
- **AND** textarea 与工具栏之间无额外间距

### Requirement: 上传按钮支持键盘导航

按钮 SHALL 可通过 Tab 键聚焦，Enter/Space 触发文件选择。SHALL 具有可见的 focus ring（`focus-visible:ring`）。

#### Scenario: 键盘 Tab 聚焦上传按钮
- **GIVEN** 用户通过 Tab 导航到上传按钮
- **WHEN** 按钮获得焦点
- **THEN** 显示 focus ring

#### Scenario: Enter 键触发文件选择
- **GIVEN** 上传按钮已获得焦点
- **WHEN** 用户按 Enter
- **THEN** 打开文件选择对话框
