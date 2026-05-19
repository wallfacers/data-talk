### Requirement: 资源目录概览卡片

存储治理页面 SHALL 在 storage overview 区域增加 5 个资源目录的磁盘占用卡片。

#### Scenario: 资源目录卡片展示

- **GIVEN** 存储治理页面加载完成
- **WHEN** storage-overview 数据返回
- **THEN** 在现有卡片（OpenCode、Sessions、Workspaces、Trash）下方显示 5 个资源目录卡片：Dashboards、Reports、Exports、Semantic、Uploads
- **AND** 每个卡片显示资源数量（如 "3 files"）和磁盘占用（如 "245 KB"）
- **AND** 卡片使用 `bg.panel` token，遵循现有卡片样式

### Requirement: 资源类型 Tab 切换

存储治理页面 SHALL 提供资源类型 Tab 切换，选中 Tab 后显示对应类型的资源列表表格。

#### Scenario: Tab 初始状态

- **WHEN** 用户首次进入资源目录区域
- **THEN** 默认选中第一个有数据的资源类型 Tab，若全部为空则选中 Dashboards

#### Scenario: Tab 切换

- **WHEN** 用户点击 Reports Tab
- **THEN** 表格切换到 Reports 资源列表，显示 report 专用列（标题、可用格式、大小、创建时间）
- **AND** Tab 切换不触发额外动画，使用 `motion.fast`（120ms）切换内容

### Requirement: 资源列表表格 —— Dashboard

Dashboard 列表 SHALL 显示以下列：checkbox（多选）、名称（title，若无则 fallback 到 filename）、Widget 数量、大小、创建时间、更新时间、操作（预览、删除）。

#### Scenario: Dashboard 表格列

- **WHEN** Dashboards Tab 选中
- **THEN** 表格列 header 为：☐ | 名称 | Widgets | 大小 | 创建时间 | 更新时间 | 操作
- **AND** 名称列显示 `title`（从 dashboard.json 提取），无 title 时显示 `filename`
- **AND** 大小列使用 `ui-sm` + mono 字体，显示 KB/MB
- **AND** 操作列包含 👁 预览按钮（cobalt 色）和 🗑 删除按钮（`status.danger` 红色）

### Requirement: 资源列表表格 —— Report

Report 列表 SHALL 显示以下列：checkbox、标题、可用格式（HTML/PDF/MD badge）、大小、创建时间、更新时间、操作。

#### Scenario: Report 表格格式列

- **WHEN** Reports Tab 选中
- **THEN** 格式列显示小 badge：`HTML` `PDF` `MD`（每个格式一个 badge）
- **AND** badge 使用 `bg.subtle` 背景 + `text.muted` 文字 + `radius.sm`（8px）圆角

### Requirement: 资源列表表格 —— Export

Export 列表 SHALL 显示以下列：checkbox、文件名、格式（CSV/JSON/XLSX/SQL）、大小、行数、创建时间、过期时间、操作。

#### Scenario: Export 过期时间展示

- **WHEN** Exports Tab 选中
- **THEN** 过期时间列显示绝对时间（如 "2026-05-19 15:30"）
- **AND** 若距过期 < 10 分钟，时间显示为 `status.warning` 琥珀色
- **AND** 若已过期，显示 "已过期" 标签（`status.danger` 红色）

### Requirement: 资源列表表格 —— Semantic

Semantic 列表 SHALL 显示以下列：checkbox、Domain 名称、关联连接、状态（active badge / pending badge）、大小、更新时间、操作。

#### Scenario: Semantic 状态 badge

- **WHEN** Semantic Tab 选中
- **THEN** active 状态使用 `status.success` 绿色 badge
- **AND** pending 状态使用 `status.warning` 琥珀色 badge

### Requirement: 资源列表表格 —— Upload

Upload 列表 SHALL 显示以下列：checkbox、文件名、MIME 类型、大小、上传时间、过期时间、操作。

#### Scenario: Upload MIME 类型展示

- **WHEN** Uploads Tab 选中
- **THEN** MIME 列显示简化的类型描述（如 "CSV", "PNG Image", "JSON", "PDF"）
- **AND** 若 MIME 为 `image/*`，文件名前显示缩略图（32x32）

### Requirement: 批量删除

系统 SHALL 支持勾选多行后执行批量删除。

#### Scenario: 批量删除确认

- **WHEN** 用户勾选 ≥1 行并点击"批量删除"按钮
- **THEN** 弹出确认对话框，列出将要删除的资源文件名
- **AND** 对话框使用 `status.danger` 红色强调"此操作不可撤销"
- **AND** 确认后发送 DELETE 请求到对应端点，成功后刷新列表

### Requirement: 预览抽屉

系统 SHALL 在点击预览按钮时打开右侧抽屉，根据资源类型渲染预览内容。

#### Scenario: Dashboard/Report HTML 预览

- **WHEN** 用户点击 dashboard 或 report 的预览按钮
- **THEN** 打开 720px 宽右侧抽屉
- **AND** 抽屉内使用 `<iframe sandbox>` 渲染 HTML 内容
- **AND** iframe 高亮显示，宽度 100%

#### Scenario: Semantic YAML 预览

- **WHEN** 用户点击 semantic model 的预览按钮
- **THEN** 打开 480px 宽右侧抽屉
- **AND** 抽屉内使用 Shiki 语法高亮显示 YAML 内容
- **AND** 使用 `mono-sm` 字体

#### Scenario: Export 数据预览

- **WHEN** 用户点击 export 的预览按钮
- **THEN** 打开 720px 宽右侧抽屉
- **AND** 抽屉内显示虚拟滚动表格（前 100 行，列头 + 数据行）
- **AND** 表格上方显示格式 badge 和总行数信息

#### Scenario: Upload 文件预览

- **WHEN** 用户点击 upload 的预览按钮
- **THEN** 根据 MIME：文本类显示代码高亮、图片类显示 `<img>` 标签、其他显示文件元信息
- **AND** 抽屉宽度根据内容类型自适应（文本 480px，图片 720px）

### Requirement: 设计约束遵循

资源目录 UI SHALL 遵循 client/DESIGN.md 的设计 token 和组件规则。

#### Scenario: 表格组件设计

- **WHEN** 资源列表表格渲染
- **THEN** 表头使用 `bg.subtle` 背景 + `ui-xs` 字体
- **AND** 数据行使用 `ui-sm` 字体，hover 时使用 `interaction.hover` 背景
- **AND** 选中行使用 `interaction.selected` 背景（`cobalt.50` light / `rgba(37, 99, 235, 0.18)` dark）
- **AND** 数字列（大小、行数）使用 mono 字体

#### Scenario: 空状态

- **WHEN** 某资源目录没有文件
- **THEN** 表格显示空状态：`bg.canvas` 背景上居中显示描述文字（`text.muted`）
- **AND** 文字如 "No dashboards yet. Dashboards created via AI chat will appear here."

### Requirement: 来源会话信息展示

资源列表 SHALL 对每个资源显示其来源会话信息（如果 `originSessionId` 不为 null）。

#### Scenario: 来源会话存在

- **WHEN** 资源的 `originSessionId` 对应的 session 仍然存在
- **THEN** 名称列下方显示 "来源: 会话名称"（`text.muted` + `ui-xs`）

#### Scenario: 来源会话已删除

- **WHEN** 资源的 `originSessionId` 不为 null 但对应 session 已被删除
- **THEN** 名称列下方显示 "来源: (已删除)"（`text.soft` + `ui-xs`）

#### Scenario: 无来源会话

- **WHEN** 资源的 `originSessionId` 为 null
- **THEN** 不显示来源信息
