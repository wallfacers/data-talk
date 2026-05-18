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

### Requirement: 图片附件以 OpenCode FilePart 形式批量随消息发送

当用户发送的消息中包含一个或多个图片附件（`mimeType` 以 `image/` 开头）时，前端 SHALL 在调用 `send_message` RPC 时把每张图片以 `FileUploadPart` 形式发送，且 MUST 在该 part 上携带 `url` 字段，其值 SHALL 为形如 `data:<mime>;base64,<...>` 的 base64 data URI（来源为后端 `GET /api/files/{fileId}/content` 拉回的字节）。

后端 `ChannelService.partForWire()` 在处理图片类 `FileUploadPart`（`mimeType` 以 `image/` 开头且 `url` 字段非空）时 SHALL 输出 OpenCode 协议的 `FilePart`：

```json
{ "type": "file", "mime": "<mimeType>", "filename": "<filename>", "url": "<dataUri>" }
```

后端 MUST NOT 在该路径上生成任何提示文本，MUST NOT 引用 `datatalk_file_read` 工具名，MUST NOT 把图片 part 入队 `PendingFileUploadEchoRegistry`。

非图片 `FileUploadPart`（CSV/JSON/SQL/Excel/TEXT/UNKNOWN 等）行为完全不变：仍然降级为 text part 携带 metadata 与 `datatalk_file_read` 引导，仍然入队 echo registry 等待回放。

#### Scenario: 单张图片随文本一次性发出

- **GIVEN** 用户消息含一个 `text` part "这张图什么意思" 与一个 `file_upload` part `{ fileId: "img-001", filename: "screenshot.png", mimeType: "image/png", sizeBytes: 150000, url: "data:image/png;base64,iVBOR..." }`
- **WHEN** `ChannelService.sendMessage` 处理
- **THEN** 转发给 OpenCode 的 body.parts SHALL 含两项：`{type:"text", text:"这张图什么意思"}` 与 `{type:"file", mime:"image/png", filename:"screenshot.png", url:"data:image/png;base64,iVBOR..."}`
- **AND** body.parts 中 MUST NOT 出现任何提及 `datatalk_file_read`、`fileId`、`sizeBytes` 的文本
- **AND** `PendingFileUploadEchoRegistry` MUST NOT 含该 part 的入队记录

#### Scenario: 多张图片同消息批量发送

- **GIVEN** 用户消息含三个图片 `file_upload` part（顺序 A.png → B.jpg → C.webp，均带 `url` 字段）与一个 `text` part "对比一下这三张"
- **WHEN** `ChannelService.sendMessage` 处理
- **THEN** 转发给 OpenCode 的 body.parts SHALL 按 text → A → B → C 顺序排列（保留原始 parts 顺序）
- **AND** 三个图片 part 均 SHALL 为 `type:"file"` 含 `url`
- **AND** 仅调用 OpenCode `promptAsync` 一次，MUST NOT 拆成多次请求

#### Scenario: 图片与非图片混合附件

- **GIVEN** 用户消息含 `text` + image `file_upload` (with url) + csv `file_upload` (without url)
- **WHEN** `ChannelService.sendMessage` 处理
- **THEN** body.parts SHALL 含：原 text、`type:"file"` (image)、`type:"text"`（CSV 降级文本，含 "[Uploaded file: ...]" 与 "Use `datatalk_file_read` ..." 尾句）
- **AND** `PendingFileUploadEchoRegistry` SHALL 仅入队 CSV 这一个 part（图片跳过）
- **AND** 当 OpenCode 后续 echo 回 user message 时，CSV chip 仍通过 echo registry 回放，图片 chip 直接由 OpenCode 回显的 FilePart 渲染

#### Scenario: 图片 part 缺 url 字段时降级到老路径

- **GIVEN** 一个 image `file_upload` part `{ mimeType: "image/png", fileId: "img-002", url: null }`（理论上不会发生，但前端预热失败时可能）
- **WHEN** `ChannelService.sendMessage` 处理
- **THEN** 该 part SHALL 走老路径：降级为 text + 引导 `datatalk_file_read`，并入队 echo registry
- **AND** 后端 SHALL 记录 WARN 日志说明图片缺 dataUri 走降级路径

### Requirement: 前端按需预热并缓存图片 data URI

前端 `useFileUpload` 在图片上传完成（status === 'done' 且 mimeType 以 `image/` 开头）后 SHALL 异步发起 `GET /api/files/{fileId}/content` 拉取字节，使用 `FileReader.readAsDataURL` 转换为 data URI，并以 fileId 为键缓存到内存。预热的并发数 SHALL 复用现有 `MAX_CONCURRENT = 3` 限制。

用户点击发送按钮时，前端 `partsForSend()` 在为每个图片 `FileUploadPart` 构造请求体时 SHALL 优先从缓存读取 dataUri 填入 `url` 字段。若缓存缺失，前端 SHALL 阻塞等待同步拉取（最多 5 秒），超时则 `url` 留空（由后端走降级路径）。

非图片附件 MUST NOT 触发预热（避免 CSV/JSON 等大文件浪费带宽）。

#### Scenario: 上传完成立即预热

- **GIVEN** 用户在 composer 上传一张 PNG 图片
- **WHEN** 上传响应回包，status 切换到 'done'
- **THEN** 前端 SHALL 在后台异步发起 `GET /api/files/{fileId}/content`
- **AND** 收到字节后 SHALL 转为 data URI 缓存到内存
- **AND** chip UI 状态 MUST NOT 受预热过程影响（chip 立刻 done，不显示额外 loading）

#### Scenario: 发送时缓存命中直接附 url

- **GIVEN** 用户连续上传 3 张图片，预热已全部完成
- **WHEN** 用户点击发送
- **THEN** `partsForSend()` SHALL 同步从缓存取出 3 个 data URI 填入对应 part 的 `url`
- **AND** RPC `send_message` 调用 MUST NOT 等待任何额外网络请求

#### Scenario: 缓存未命中时阻塞同步获取

- **GIVEN** 用户上传图片后立即按发送（预热尚未完成）
- **WHEN** `partsForSend()` 执行
- **THEN** 前端 SHALL 同步等待预热结果（或主动触发 GET），最多 5 秒
- **AND** 超时后 SHALL 把 `url` 留空（null），让后端走降级路径
- **AND** UI SHALL 不阻塞，发送按钮按现有 disabled 状态规则展示

#### Scenario: 非图片附件不触发预热

- **GIVEN** 用户上传一个 CSV 与一张图片
- **WHEN** 两个文件均到达 done 状态
- **THEN** 仅图片 SHALL 触发预热 GET
- **AND** CSV MUST NOT 触发任何额外 GET

### Requirement: UserBubble 渲染图片附件 chip 时识别两类 part 来源

`BubbleAttachmentList` SHALL 同时识别消息 parts 中以下两种附件来源并渲染 chip：

1. `FileUploadPart`（既有）—— 通过 fileId 与 GET 端点获取缩略图字节
2. `FilePart` (`type === 'file'`) 且 `mime` 以 `image/` 开头 —— 直接用 `url` 字段（data URI）作为缩略图源

两类来源在 chip 视觉上 MUST 完全一致（同一 `FileChip` 组件、同一 token 集合、同一 24×24 缩略图尺寸），用户 MUST NOT 能通过外观区分某张图是走老路径还是新路径回显的。

#### Scenario: 新路径回显的图片 chip 直接用 data URI

- **GIVEN** 用户发送一条含图片的消息（走新 FilePart 路径），OpenCode 回 echo 的 user message 含一个 `{ type: "file", mime: "image/png", filename: "x.png", url: "data:image/png;base64,..." }` part
- **WHEN** `BubbleAttachmentList` 渲染该消息
- **THEN** chip SHALL 显示 24×24 缩略图，源为该 part 的 `url`
- **AND** chip 文件名 SHALL 为 "x.png"
- **AND** MUST NOT 触发任何 `GET /api/files/...` 请求

#### Scenario: 老路径回显的 CSV chip 仍通过 fileId 工作

- **GIVEN** 用户发送一条含 CSV 的消息，echo registry 回放出一个 `FileUploadPart`
- **WHEN** `BubbleAttachmentList` 渲染该消息
- **THEN** chip SHALL 显示 CSV 图标（非缩略图），文件名/大小信息来自该 part
- **AND** 点击 chip SHALL 通过 fileId 调 `GET /api/files/{fileId}/content` 打开预览（已有行为）

#### Scenario: 同消息混合两种来源时 chip 顺序保持

- **GIVEN** 用户发送一条含 1 张图（新路径）+ 1 个 CSV（老路径）的消息
- **WHEN** `BubbleAttachmentList` 渲染回显的 user message
- **THEN** chip 数量 SHALL 为 2
- **AND** chip 顺序 SHALL 与用户上传顺序一致
- **AND** 两种 chip 的视觉样式（边框、背景、文字色、ring、hover）MUST 完全一致

### Requirement: MCP `datatalk_file_read` 工具引导调整 — 不再要求图片必经

`skills/file-upload-routing/SKILL.md` 中针对 IMAGE 类型的引导 MUST 移除 "Use `datatalk_file_read` to retrieve the image content" 一行。IMAGE 节剩余引导 SHALL 仅保留尺寸/格式回报与"承认图片已收到"的语义。

`datatalk_file_read` 工具本身（`FileReadActionHandler` + `@DataTalkAction(id="datatalk.file_read")`）MUST 保留，行为不变，AI 在处理历史会话（旧 FileUploadPart 仍以 fileId 形式存在）时 SHALL 仍能调用它读取图片字节。

`AGENTS.md` 中关于 `datatalk_file_read` 的通用描述 SHALL 保留（适用于非图片文件按需读取）。

`ChannelService.buildFileUploadContext()` 尾句 "Use `datatalk_file_read` with fileId..." SHALL 仅在**非图片** part 走降级路径时出现，图片 part 走 FilePart 路径时 MUST NOT 出现该引导文本。

#### Scenario: SKILL.md 不再要求图片走 read_file

- **WHEN** 读取 `server/data-talk-adapter/src/main/resources/skills/file-upload-routing/SKILL.md`
- **THEN** IMAGE Files 节 MUST NOT 含 "Use `datatalk_file_read` to retrieve the image content" 一行
- **AND** 节内仍 SHALL 含"承认图片已收到"与尺寸/格式回报指引

#### Scenario: 历史会话图片仍可被 AI 主动读取

- **GIVEN** 一个旧会话包含一条 user message 含 `FileUploadPart{fileId: "old-img-001", mimeType: "image/png"}`（无 url 字段，新功能上线前发送）
- **WHEN** AI 在新一轮对话中调用 `datatalk_file_read({fileId: "old-img-001"})`
- **THEN** `FileReadActionHandler` SHALL 正常返回图片 data URI（行为不变）
- **AND** AI SHALL 能据此回答关于该图的问题

#### Scenario: 新路径下不再出现 read_file 引导文本

- **GIVEN** 用户发送一条含图片的消息（图片 part 带 url）
- **WHEN** 抓取转发给 OpenCode 的 body
- **THEN** body.parts 中 MUST NOT 出现任何 "Use `datatalk_file_read`" 文本
- **AND** body.parts 中 MUST NOT 出现 "[Uploaded file: ..." 提示文本

### Requirement: 新路径回显图片 chip 预览复用 FilePreviewDialog

新路径回显的图片 chip（数据来源为 `FilePart` 且 `mime` 以 `image/` 开头）被单击时，SHALL 打开同一个 `FilePreviewDialog` 实例，且预览体验 MUST 与老路径 `FileUploadPart` chip 单击的预览**视觉与交互完全一致**（同一 Dialog 外壳、同一 maximize/restore/close 按钮、同一 header 显示文件名+类型+大小）。

为此 `PreviewSource` 类型 SHALL 扩展第三种 kind：

```ts
type PreviewSource =
  | { kind: 'local';    file: File }
  | { kind: 'remote';   fileId: string; filename: string; mimeType: string; sizeBytes: number }
  | { kind: 'embedded'; dataUri: string; filename: string; mimeType: string; sizeBytes: number }
```

`FilePreviewDialog` 在收到 `kind: 'embedded'` 时 SHALL 直接把 `dataUri` 作为 `<img src>` 渲染，MUST NOT 触发任何网络请求，MUST NOT 调用 `URL.createObjectURL`（dataUri 本身可直接消费）。

`BubbleAttachmentList` 在构造单击事件时 SHALL 按来源 part 类型分发：
- `FileUploadPart` → `{ kind: 'remote', fileId, ... }`（行为不变）
- `FilePart` (image) → `{ kind: 'embedded', dataUri: part.url, filename: part.filename, mimeType: part.mime, sizeBytes: <由 dataUri 字节估算> }`

#### Scenario: 新路径图片 chip 单击打开 embedded 预览

- **GIVEN** 用户刚发送一条含图片的消息（走新 FilePart 路径），OpenCode echo 回的 user message 含一个 `{ type:"file", mime:"image/png", filename:"x.png", url:"data:image/png;base64,iVBOR..." }` part
- **WHEN** 用户单击该 chip
- **THEN** `FilePreviewDialog` SHALL 打开，`source.kind === 'embedded'`
- **AND** Dialog `<img>` 元素的 `src` SHALL 直接为该 part 的 `url`
- **AND** 整个交互过程 MUST NOT 出现任何 `GET /api/files/...` 请求
- **AND** Dialog header SHALL 显示 "x.png" 与正确的类型/大小

#### Scenario: 老路径 chip 与新路径 chip 视觉与交互一致

- **GIVEN** 同一消息含 1 张新路径图片 + 1 个老路径 CSV
- **WHEN** 用户分别单击两个 chip
- **THEN** 两次打开的 Dialog 外壳 SHALL 完全相同（同一组件实例，同一 maximize/restore/close 按钮位置与样式）
- **AND** 两次 Dialog 的 header 都 SHALL 正常显示文件名+类型+大小
- **AND** 用户 MUST NOT 能通过外观判断哪个 chip 走了哪条路径

#### Scenario: 刷新会话后历史新路径图片仍可预览

- **GIVEN** 用户在新功能启用后发送一条含图片的消息，关闭浏览器
- **WHEN** 用户重新打开会话，前端从后端 replay 历史消息事件
- **AND** OpenCode 回放的 user message 仍含原 FilePart 且 `url` 字段为 data URI（OpenCode 已持久化）
- **THEN** chip 渲染与单击预览行为 SHALL 与"刚发送"场景完全一致
- **AND** 不依赖任何前端内存缓存
- **AND** 若 OpenCode 因任何原因丢失 url 字段（保留 filename/mime 但 url 为空），chip SHALL 渲染失效占位（灰底+"图片已失效"文案），单击 MUST NOT 崩溃

### Requirement: 发送前图片 payload 总量护栏

前端在 `partsForSend()` 构造完所有 image FilePart 后 SHALL 计算 base64 字符串总字节量。当总量满足以下条件时 SHALL 采取对应行为：

| 总量 | 行为 |
|------|------|
| ≤ 3 MB | 正常发送 |
| > 3 MB 且 ≤ 5 MB | 发送前显示 warning toast，仍允许发送 |
| > 5 MB | 阻止发送，显示 error toast 提示用户减少图片数量或换更小的图，发送按钮 MUST 暂时禁用 |

阈值常量 SHALL 定义在前端 i18n / config 文件中，便于按 OpenCode 实测调整。

非图片附件（CSV/JSON 等）MUST NOT 计入该护栏（其走降级路径，body 体积本就很小）。

#### Scenario: 单图小于阈值正常发送

- **GIVEN** 用户上传 1 张 200KB 图片（base64 后约 270KB）
- **WHEN** 用户点发送
- **THEN** 无 warning / error toast
- **AND** 消息正常发出

#### Scenario: 多图超 warning 阈值

- **GIVEN** 用户上传 5 张图，base64 总量 3.5 MB
- **WHEN** 用户点发送
- **THEN** warning toast SHALL 出现（文案告知体积较大可能影响响应）
- **AND** 消息仍正常发出

#### Scenario: 超 hard 上限阻止发送

- **GIVEN** 用户上传 8 张图，base64 总量 6 MB
- **WHEN** 用户点发送
- **THEN** error toast SHALL 出现
- **AND** RPC `send_message` MUST NOT 被调用
- **AND** 发送按钮 SHALL 暂时禁用直到用户移除图片至总量 ≤ 5 MB

### Requirement: user_message_attachments 表通过独立 Flyway 迁移创建

`user_message_attachments` 表的 schema 定义 SHALL 由 `db/migration/V2__user_message_attachments.sql` 单一文件提供，不再由 `V1__init.sql` 包含。

V2 SHALL 使用 `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS`，对以下三类环境幂等：

1. **Fresh DB**：从无到有创建（V1 仅含其他表）
2. **历史已应用 V1 含建表语句的环境**：跳过建表（表已存在）
3. **手工 workaround 已建表的环境**（含 BUG-0061 临时修复用户）：跳过建表

#### Scenario: Fresh DB 启动 user_message_attachments 表可达

- **GIVEN** `~/.data-talk/datatalk.db` 不存在
- **WHEN** 后端首次启动并执行 Flyway migrate
- **THEN** `flyway_schema_history` SHALL 含 version=1 和 version=2 两行
- **AND** `sqlite_master` SHALL 含 name=`user_message_attachments` 的 table
- **AND** `sqlite_master` SHALL 含 name=`idx_user_message_attachments_session_message` 的 index
- **AND** `GET /api/sessions/{id}/messages` 对任意 session id 都 SHALL NOT 因缺表返回 500

#### Scenario: 老环境升级到含 V2 的版本

- **GIVEN** 一个曾经应用过含建表语句的 V1 但缺 `user_message_attachments` 表的 DB
- **WHEN** 后端启动并执行 Flyway migrate
- **THEN** V2 SHALL 被应用并把 `user_message_attachments` 表 + 索引创建出来
- **AND** `flyway_schema_history` SHALL 新增 version=2 行
- **AND** `GET /api/sessions/{id}/messages` 对历史 session SHALL 返回 200 并正常回放消息

#### Scenario: 手工已建表的环境保持幂等

- **GIVEN** 一个开发者运行过 BUG-0061 文档的 sqlite3 workaround 手工建过表的 DB
- **WHEN** 后端启动并执行 Flyway migrate
- **THEN** V2 SHALL 因 `IF NOT EXISTS` 跳过实际 CREATE 但仍登记到 `flyway_schema_history`
- **AND** 不 SHALL 因表已存在抛 `SQLITE_ERROR`

### Requirement: V1__init.sql 不再包含 user_message_attachments 定义

`V1__init.sql` SHALL NOT 包含 `user_message_attachments` 表或对应索引的 DDL。这一职责完全转移到 V2。

#### Scenario: 静态校验 V1 不含 user_message_attachments

- **GIVEN** 仓库 develop 分支 HEAD
- **WHEN** 检查 `server/data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql` 内容
- **THEN** 文件 SHALL NOT 包含字面字符串 `user_message_attachments`

### Requirement: multipart 上传上限与 Controller 业务上限对齐

`spring.servlet.multipart.max-file-size` 和 `spring.servlet.multipart.max-request-size` SHALL 在所有 profile 中 ≥ `FileUploadController.MAX_SIZE_BYTES`（当前 50MB）。当代码常量调整时，配置数值 MUST 同步调整。

实际表现：用户上传 ≤ 50MB 单文件时 SHALL NOT 因 Spring multipart 默认 1MB 上限被提前拒（在 multipart 解析层抛 `MaxUploadSizeExceededException`），SHALL 进到 Controller 层走业务校验路径。

#### Scenario: 上传 5MB 图片成功进 Controller

- **GIVEN** 后端使用 default profile 启动，`FileUploadController.MAX_SIZE_BYTES = 50MB`
- **WHEN** 用户上传一张 5MB 的真实截图
- **THEN** Spring multipart 解析层 SHALL NOT 抛 `MaxUploadSizeExceededException`
- **AND** 请求 SHALL 进入 `FileUploadController.upload` 方法
- **AND** Controller 业务校验 SHALL 通过（5MB < 50MB）
- **AND** 文件 SHALL 落盘至 `~/.data-talk/uploads/<fileId>/`
- **AND** 前端 chip SHALL 显示 `done` 状态

#### Scenario: 上传 60MB 文件按业务上限被拒

- **GIVEN** 后端使用 default profile 启动
- **WHEN** 用户上传一个 60MB 文件
- **THEN** 请求 SHALL 在 multipart 解析层（max-file-size=50MB）或 Controller 业务校验层被拒
- **AND** 响应 SHALL 是结构化错误，而非 500 内部错误
- **AND** 临时文件 SHALL NOT 残留在磁盘上

#### Scenario: batch-image-attachments-via-fileparts 的 5MB hard limit 可达

- **GIVEN** 前端按 `IMAGE_PAYLOAD_HARD_LIMIT_BYTES = 5MB` 阻止大图 payload
- **AND** 用户上传两张各 3MB 的图片
- **WHEN** 前端尝试发送
- **THEN** 单图上传 SHALL 都成功（multipart 上限 50MB ≥ 3MB）
- **AND** 前端 SHALL 在 send 时按合计 6MB 触发 `chat.image.payloadTooLarge` toast 阻止发送
- **AND** 该 toast SHALL 来自前端 `classifyImagePayload`，而非 multipart 1MB 提前拒

