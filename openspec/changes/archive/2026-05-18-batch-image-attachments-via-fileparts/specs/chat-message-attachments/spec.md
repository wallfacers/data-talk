## ADDED Requirements

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
