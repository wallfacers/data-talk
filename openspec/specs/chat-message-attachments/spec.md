# chat-message-attachments Specification

## Purpose
TBD - created by archiving change user-bubble-attachments. Update Purpose after archive.
## Requirements
### Requirement: 用户气泡上方回显附件 chip 列表

当用户消息（`MessageInfo.role === 'user'`）携带一个或多个 `FileUploadPart` 时，`UserBubble` SHALL 在气泡 `<div>` 的**外部上方**渲染一个独立的 `BubbleAttachmentList` 容器。该容器 MUST NOT 出现在气泡内部，气泡内 MUST NOT 再渲染任何附件元素。

附件列表容器 SHALL 满足以下布局约束：

- 横向单行排列，`flex flex-row gap-1.5 overflow-x-auto`；
- 右对齐，`ml-auto` + `justify-end`；
- 宽度上限 `max-w-[85%]`，与气泡宽度上限完全一致；
- 单个 chip 宽度上限 `max-w-[180px]`，与输入框 `FileAttachmentChip` 一致；
- chip 顺序遵循 `parts` 数组中 `file_upload` part 的出现顺序（即上传顺序）；
- chip 数量为 0 时整个容器 MUST NOT 渲染（不留空白占位）。

#### Scenario: 单文件用户消息在气泡上方显示 chip

- **GIVEN** 一条用户消息含一个 `file_upload` part：`{ filename: "效果图.png", mimeType: "image/png", sizeBytes: 333097 }` 与一个 `text` part "这是什么"
- **WHEN** `UserBubble` 渲染
- **THEN** 气泡上方 SHALL 出现一个 `BubbleAttachmentList` 容器
- **AND** 容器内 SHALL 出现一个 `FileChip`，显示文件名 "效果图.png"、类型/大小 "PNG 325.3 KB"、图片缩略图
- **AND** 气泡内部 SHALL 只渲染 "这是什么" 文本，不含任何附件元素

#### Scenario: 多文件按上传顺序水平排列

- **GIVEN** 一条用户消息含三个 `file_upload` part（顺序：A.csv → B.png → C.json）
- **WHEN** `UserBubble` 渲染
- **THEN** chip 列表 SHALL 按 A → B → C 顺序水平排列
- **AND** 容器 SHALL 右对齐贴气泡边界

#### Scenario: 附件数量超过容器宽度时横向滚动

- **GIVEN** 一条用户消息含 10 个 `file_upload` part，总宽度超过 `max-w-[85%]`
- **WHEN** `UserBubble` 渲染
- **THEN** 容器 SHALL 出现横向滚动条
- **AND** chip 顺序保持上传顺序不变
- **AND** 容器宽度仍受 `max-w-[85%]` 限制，不溢出气泡宽度

#### Scenario: 无附件用户消息不渲染容器

- **GIVEN** 一条用户消息只含 `text` part，无 `file_upload` part
- **WHEN** `UserBubble` 渲染
- **THEN** `BubbleAttachmentList` 容器 MUST NOT 出现在 DOM 中

### Requirement: FileChip 纯展示组件与五态语义 token

新增 `FileChip` 组件 SHALL 是不持有 `File` 对象、不带删除按钮、不带上传进度条的纯展示组件。其 props 接口 SHALL 为：

```ts
{
  filename: string
  sizeBytes: number
  mimeType: string
  thumbnailUrl?: string        // 图片场景，调用方提供
  onClick?: () => void
  disabled?: boolean
  ariaLabel?: string
}
```

`FileChip` SHALL 按以下 token 映射五态，且 disabled 态 MUST NOT 仅依赖颜色（须同时降低 opacity 并设置 `cursor-not-allowed` 与 `aria-disabled="true"`）：

| 状态 | Token |
|---|---|
| 默认 | `border-border-default bg-bg-soft text-text-base` |
| hover | `bg-bg-subtle border-border-strong` |
| focus-visible | `outline-none ring-2 ring-accent-primary/40 ring-offset-1 ring-offset-bg-canvas` |
| active | `bg-accent-primary/8 border-accent-primary/40` |
| disabled | `opacity-60 cursor-not-allowed` + `aria-disabled="true"` |

#### Scenario: 默认态使用中性 token

- **GIVEN** 任意 `FileChip` 实例
- **WHEN** 既未 hover、未 focus、未 active、未 disabled
- **THEN** 元素 className SHALL 包含 `border-border-default`、`bg-bg-soft`、`text-text-base`
- **AND** 元素 className MUST NOT 包含任何 `accent-primary` 相关 token

#### Scenario: focus-visible 出现可见 ring 且使用 accent.primary

- **GIVEN** 用户使用键盘 Tab 聚焦到一个 `FileChip`
- **WHEN** 元素进入 focus-visible 状态
- **THEN** 元素 SHALL 出现 `ring-2 ring-accent-primary/40 ring-offset-1` 的视觉环
- **AND** ring 在 light 与 dark 主题下 SHALL 均可见

#### Scenario: disabled 态不仅依赖颜色

- **GIVEN** 一个 `FileChip` 设置 `disabled={true}`
- **WHEN** 渲染
- **THEN** 元素 SHALL 同时具备 `opacity-60`、`cursor-not-allowed` className 与 `aria-disabled="true"` 属性
- **AND** onClick 不触发

#### Scenario: 图片附件显示 24×24 缩略图

- **GIVEN** `FileChip` 接收 `mimeType: "image/png"` 与有效 `thumbnailUrl`
- **WHEN** 渲染
- **THEN** chip 左侧 SHALL 显示 `<img>` 元素，尺寸 24×24，`object-cover` `rounded`
- **AND** 图标占位 MUST NOT 同时出现

#### Scenario: 非图片附件显示类型图标

- **GIVEN** `FileChip` 接收 `mimeType: "text/csv"` 或 `mimeType: "application/json"`
- **WHEN** 渲染
- **THEN** chip 左侧 SHALL 显示对应类型图标（`FileSpreadsheet` / `FileJson` / `FileText`）
- **AND** `<img>` 缩略图 MUST NOT 出现

### Requirement: FileAttachmentChip 与 BubbleAttachmentList 共用 FileChip

输入框 `FileAttachmentChip` SHALL 重构为基于 `FileChip` 的包装组件，叠加上传进度条、删除按钮、错误态背景。其外部 prop 接口（`attachment`、`onRemove`）SHALL 保持不变，原有调用方（`prompt-composer.tsx`）无须修改。

`BubbleAttachmentList` SHALL 是基于 `FileChip` 的另一包装组件，专用于用户气泡上方场景，MUST NOT 包含删除按钮、MUST NOT 包含上传进度条、MUST NOT 接受 `File` 对象。

#### Scenario: 输入框 chip 行为保持回归

- **GIVEN** 用户在 `prompt-composer` 选择一个文件
- **WHEN** chip 渲染在输入框内
- **THEN** chip SHALL 显示上传进度条（status === 'uploading' 时）
- **AND** chip SHALL 显示右侧 X 删除按钮，点击触发 `onRemove`
- **AND** chip 错误态背景 SHALL 显示 `border-status-danger/40 bg-status-dangerSurface text-status-danger`
- **AND** 单击 chip 主体（非 X 按钮）SHALL 触发 `FilePreviewDialog` 打开

#### Scenario: 气泡上方 chip 不含删除与进度

- **GIVEN** 一条已发送的用户消息含 `file_upload` part
- **WHEN** `BubbleAttachmentList` 渲染
- **THEN** chip 内部 MUST NOT 出现 X 删除按钮
- **AND** chip 内部 MUST NOT 出现上传进度条
- **AND** chip 内部 MUST NOT 出现错误态背景

### Requirement: 单击 chip 复用 FilePreviewDialog

`FilePreviewDialog` 组件 SHALL 重构为接受 `source: PreviewSource | null` prop。`PreviewSource` 类型 SHALL 为：

```ts
type PreviewSource =
  | { kind: 'local';  file: File }
  | { kind: 'remote'; fileId: string; filename: string; mimeType: string; sizeBytes: number }
```

Dialog SHALL 根据 `source.kind` 自动切换字节来源，外部 UI、maximize/restore 按钮、close 按钮、header 文件名/类型/大小展示 MUST 在两种来源下完全一致。`FileAttachmentChip` 与 `BubbleAttachmentList` 单击均 SHALL 打开同一个 `FilePreviewDialog` 实例（虽然实际渲染为不同 React 实例，但视觉与交互 MUST 不可区分）。

#### Scenario: 输入框 chip 单击打开 local 预览

- **GIVEN** 用户在 `prompt-composer` 上传一个图片文件，已变为 `done` 状态
- **WHEN** 用户单击 chip 主体
- **THEN** `FilePreviewDialog` SHALL 打开，`source.kind === 'local'`
- **AND** Dialog SHALL 通过 `URL.createObjectURL(file)` 渲染图片
- **AND** Dialog 关闭时 SHALL 调用 `URL.revokeObjectURL` 释放

#### Scenario: 气泡 chip 单击打开 remote 预览

- **GIVEN** 一条已发送的用户消息含图片 `file_upload` part `{ fileId: "abc-123", filename: "效果图.png", mimeType: "image/png", sizeBytes: 333097 }`
- **WHEN** 用户单击该 chip
- **THEN** `FilePreviewDialog` SHALL 打开，`source.kind === 'remote'`
- **AND** Dialog SHALL 通过 `GET /api/files/abc-123/content` 拉取字节
- **AND** Dialog header SHALL 显示 "效果图.png" 与 "PNG · 325.3 KB"
- **AND** Dialog 内部 SHALL 渲染图片
- **AND** maximize / restore / close 按钮 SHALL 与 local 场景行为一致

#### Scenario: 历史会话 chip 也可预览

- **GIVEN** 用户刷新页面后回到一个含历史 `file_upload` 消息的会话
- **WHEN** 历史消息渲染并用户单击其中一个 chip
- **THEN** `FilePreviewDialog` SHALL 打开
- **AND** 该 chip 的预览行为 SHALL 与"刚发送的消息"的 chip 行为完全一致
- **AND** 不依赖任何前端内存缓存（即使页面是首次加载该会话）

#### Scenario: remote source 加载失败显示友好态

- **GIVEN** 用户单击一个 chip，其底层 `fileId` 对应的物理文件已被清理
- **WHEN** Dialog 发起 `GET /api/files/{fileId}/content` 收到 404
- **THEN** Dialog SHALL 渲染友好错误文案（复用 `chat.filePreview.readError` 文案分支）
- **AND** Dialog MUST NOT 崩溃或抛出未捕获异常
- **AND** Dialog header（文件名、大小）SHALL 仍正常显示

### Requirement: 后端 GET 文件内容端点

后端 SHALL 暴露 `GET /api/files/{fileId}/content` 端点，挂在现有 `FileUploadController` (`@RequestMapping("/api/files")`) 下。该端点 SHALL 根据 `fileId` 从 `UploadedFileRepository.findById(fileId)` 查询元数据，从 `UploadedFile.physicalPath` 读取文件内容，以 stream 形式返回。

响应 SHALL 设置以下 HTTP header：

- `Content-Type: {uploadedFile.mimeType}` —— 严格使用 DB 中持久化的 mimeType，MUST NOT 由请求参数覆盖；
- `Content-Length: {uploadedFile.sizeBytes}`；
- `Content-Disposition: inline; filename*=UTF-8''{percentEncode(uploadedFile.filename)}` —— 支持中文文件名；
- `Cache-Control: private, max-age=300`。

端点 SHALL 实施以下安全防御：

- `fileId` MUST 仅作为数据库查询键，MUST NOT 与文件系统路径拼接；
- 读文件前 MUST 断言 `physicalPath.normalize().startsWith(uploadBase.normalize())`，其中 `uploadBase = ~/.data-talk/uploads`；
- 任何不通过白名单的路径 MUST 返回 404，MUST NOT 暴露路径细节。

#### Scenario: 成功获取已上传文件

- **GIVEN** 数据库中存在 `UploadedFile { id: "abc-123", filename: "data.csv", mimeType: "text/csv", sizeBytes: 1024, physicalPath: "/home/user/.data-talk/uploads/abc-123/data.csv" }`
- **AND** 物理文件存在且可读
- **WHEN** 客户端发起 `GET /api/files/abc-123/content`
- **THEN** 响应 SHALL 为 200
- **AND** `Content-Type` SHALL 为 `text/csv`
- **AND** `Content-Length` SHALL 为 `1024`
- **AND** `Content-Disposition` SHALL 包含 `inline; filename*=UTF-8''data.csv`
- **AND** Body SHALL 为文件原始字节流

#### Scenario: fileId 不存在返回 404

- **GIVEN** 数据库中不存在 `fileId = "nonexistent"`
- **WHEN** 客户端发起 `GET /api/files/nonexistent/content`
- **THEN** 响应 SHALL 为 404
- **AND** 响应 body MUST NOT 泄漏文件系统路径

#### Scenario: 物理文件已被清理返回 404

- **GIVEN** 数据库中存在 `UploadedFile { id: "abc-123", physicalPath: "/home/user/.data-talk/uploads/abc-123/data.csv" }`
- **AND** 物理文件已被用户手动删除
- **WHEN** 客户端发起 `GET /api/files/abc-123/content`
- **THEN** 响应 SHALL 为 404

#### Scenario: 中文文件名正确编码

- **GIVEN** 数据库中存在 `UploadedFile { filename: "效果图.png" }`
- **WHEN** 客户端发起 `GET /api/files/{id}/content`
- **THEN** `Content-Disposition` SHALL 为 `inline; filename*=UTF-8''%E6%95%88%E6%9E%9C%E5%9B%BE.png`
- **AND** 浏览器接收后 SHALL 解码为 "效果图.png"

#### Scenario: 防御路径穿越攻击（数据被篡改场景）

- **GIVEN** 数据库中存在 `UploadedFile { physicalPath: "/etc/passwd" }`（理论上不可能，但防御性测试）
- **WHEN** 客户端发起 `GET /api/files/{id}/content`
- **THEN** 端点 SHALL 检测到 `physicalPath` 不在 `~/.data-talk/uploads/` 白名单内
- **AND** 响应 SHALL 为 404
- **AND** MUST NOT 返回 `/etc/passwd` 内容

