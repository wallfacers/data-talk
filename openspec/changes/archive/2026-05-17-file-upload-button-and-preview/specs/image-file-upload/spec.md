## ADDED Requirements

### Requirement: 前端支持图片文件上传

系统 SHALL 在 `useFileUpload` 的 `ALLOWED_EXTENSIONS` 中包含 `.png`、`.jpg`、`.jpeg`、`.gif`、`.webp`、`.bmp`。图片文件 SHALL 受 50MB 大小限制。

#### Scenario: 用户上传 PNG 图片
- **GIVEN** 用户通过拖拽或点击按钮选择 `screenshot.png`（2MB）
- **WHEN** 文件通过扩展名和大小验证
- **THEN** 附件状态为 `pending`
- **AND** 附件卡片显示图片缩略图

#### Scenario: 拒绝 SVG 文件
- **GIVEN** 用户尝试上传 `logo.svg`
- **WHEN** 文件通过扩展名验证
- **THEN** 附件状态为 `error`
- **AND** 错误信息为 "Unsupported file type: .svg"

### Requirement: 附件卡片显示图片缩略图

当附件文件为图片类型（MIME 以 `image/` 开头）时，`FileAttachmentChip` SHALL 显示图片缩略图而非文件类型图标。缩略图 SHALL 使用 `URL.createObjectURL` 生成本地预览 URL，尺寸固定（32×32 或 40×40），`object-fit: cover` 裁剪。

#### Scenario: 图片附件显示缩略图
- **GIVEN** 用户添加了 `photo.jpg` 附件
- **WHEN** 附件卡片渲染
- **THEN** 卡片左侧显示 32×32 图片缩略图
- **AND** 缩略图使用 object-fit cover 裁剪

#### Scenario: 非图片附件显示文件类型图标
- **GIVEN** 用户添加了 `data.csv` 附件
- **WHEN** 附件卡片渲染
- **THEN** 卡片左侧显示 FileSpreadsheet 图标
- **AND** 不渲染 `<img>` 元素

### Requirement: 后端识别图片 MIME 类型

`FileAnalysisService` SHALL 识别 `image/png`、`image/jpeg`、`image/gif`、`image/webp`、`image/bmp` MIME 类型。分析结果 SHALL 包含 `{ width: number, height: number, format: string, sizeBytes: number }` 元数据。

#### Scenario: 上传 JPEG 图片返回尺寸元数据
- **GIVEN** 用户上传一张 1920×1080 的 JPEG 图片（500KB）
- **WHEN** 后端处理文件
- **THEN** 分析结果包含 `{ width: 1920, height: 1080, format: "jpeg", sizeBytes: 512000 }`
- **AND** 文件存储成功，返回 fileId

#### Scenario: 上传超大图片被拒绝
- **GIVEN** 用户上传一张 60MB 的 PNG 图片
- **WHEN** 后端验证文件大小
- **THEN** 返回 422 错误，提示文件超过 50MB 限制

### Requirement: 后端二进制读取图片文件返回 base64

`FileReadActionHandler` SHALL 支持图片文件的二进制读取。当文件 MIME 以 `image/` 开头时，SHALL 读取完整文件字节并返回 base64 编码字符串，格式为 `data:{mimeType};base64,{encoded}`。此路径 SHALL 忽略 `offset` 和 `limit` 参数（图片不分段读取）。

#### Scenario: AI 请求读取上传的图片文件
- **GIVEN** 已上传 `chart.png`（image/png，MIME type 已存储）
- **WHEN** AI 通过 `datatalk.file_read` action 请求读取该文件
- **THEN** 返回 `data:image/png;base64,{base64_encoded_bytes}`
- **AND** 返回内容长度等于完整文件的 base64 编码

### Requirement: 图片文件 AI 路由规则

`file-upload-routing` SKILL.md SHALL 包含图片类型的路由规则。图片文件 SHALL 路由为上下文附件（提供描述 + base64 内容给 AI），不触发数据导入流程。

#### Scenario: 用户上传图片后 AI 收到图片上下文
- **GIVEN** 用户在对话中上传了 `diagram.png`
- **WHEN** AI 处理用户消息
- **THEN** AI 收到图片文件的分析元数据和 base64 内容
- **AND** AI 可基于图片内容回答用户问题
