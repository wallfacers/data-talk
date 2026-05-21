## ADDED Requirements

### Requirement: 附件 eager upload 时机

`useFileUpload.addFiles` 在把新附件 push 入 `attachments` 数组后 SHALL 立即（通过 `queueMicrotask` 或等价排队）触发对应 attachment 的 `uploadFile`，MUST NOT 等待 `submitText` / 用户按 Enter。eager upload SHALL 仅对 `status === 'pending'` 的附件生效；已 `done` / `error` / `uploading` 的不重复上传。

最大并发上传数 SHALL = 3。当 pending 数 > 3 时，多余的附件 SHALL 排队等待已开始的上传完成后再启动（chunk 实现）。

#### Scenario: 单文件 drop 立即开始上传

- **GIVEN** 用户向 `FileDropZone` 拖入一个 200KB PNG
- **WHEN** `addFiles` 完成 state 更新（同一 React tick 内）
- **THEN** 下一个 microtask 内 SHALL 调用 `uploadFile(file, sessionId, signal)`
- **AND** chip 状态 SHALL 在 100ms 内由 `pending` 切到 `uploading`
- **AND** 用户不需要按 Enter 即可看到上传进度

#### Scenario: 5 个文件同时拖入触发并发 3

- **GIVEN** 用户一次性拖入 5 个文件（均 pending）
- **WHEN** `addFiles` 完成
- **THEN** 同一时刻活跃的 `uploadFile` Promise SHALL 不超过 3 个
- **AND** 前 3 个完成后，剩余 2 个 SHALL 自动启动
- **AND** 总耗时 SHALL ≤ ceil(5/3) × 单文件平均上传时间

#### Scenario: 按 Enter 时所有 pending 已 done 则零等待 sendMessage

- **GIVEN** 用户上传 1 张 200KB 图片，等待 ~500ms 后所有 chip 已显示 `done`
- **WHEN** 用户输入文本并按 Enter
- **THEN** `submitText` 内 `uploadAll()` SHALL 立即返回（pending 数 = 0）
- **AND** `sendMessage` SHALL 在按 Enter 后 < 100ms 内被调用
- **AND** 用户气泡 SHALL 在 < 200ms 内上屏

### Requirement: 附件删除取消进行中上传

`FileAttachment` 接口 SHALL 新增 optional 字段 `controller?: AbortController`，由 `addFiles` 在创建 attachment 时实例化。`uploadFile` 客户端 API SHALL 接受 optional 第三参数 `signal?: AbortSignal`，并将其透传给底层 `fetch`。

`removeAttachment(id)` SHALL 同步执行以下序列：

1. 查找匹配 id 的 attachment
2. 若该 attachment 的 `controller` 存在且 `signal.aborted === false`，调用 `controller.abort()`
3. 从 `attachments` 数组中过滤掉该 attachment（splice）

被 abort 的 `uploadFile` Promise reject 后，其后续 `setAttachments(...)` 回调 SHALL 检测目标 id 已不在数组中并 silent 退出（不报错、不弹 toast）。

#### Scenario: 上传中删除立即取消请求

- **GIVEN** 用户拖入一个 5MB 文件，chip 显示 `uploading 35%`
- **WHEN** 用户单击 chip 右侧 X 按钮
- **THEN** chip SHALL 立即从 DOM 移除
- **AND** 对应的 `fetch` SHALL 收到 abort 信号
- **AND** 浏览器 Network 面板 SHALL 显示该请求状态为 `(canceled)`
- **AND** 控制台 MUST NOT 出现未捕获错误

#### Scenario: 上传完成后删除不影响已上传内容

- **GIVEN** 一个附件 status 已 `done`，对应 `response.fileId` 已持久
- **WHEN** 用户单击 X 删除
- **THEN** chip 从 DOM 移除
- **AND** 已上传的文件 fileId 不再出现在后续 `submitText` 构造的 parts 中
- **AND** 后端孤儿文件由 cleanup 路径处理（不在本 requirement 范围）

#### Scenario: removeAttachment 调用顺序保证

- **GIVEN** `removeAttachment(id)` 被调用
- **WHEN** 执行序列
- **THEN** `controller.abort()` 调用 SHALL 发生在 `setAttachments(filter)` 之前（同步顺序）
- **AND** abort 与 splice 之间 MUST NOT 出现 await

### Requirement: 发送按钮多态视觉反馈

`PromptComposer` 的发送/中止按钮 SHALL 根据下列状态机渲染：

| 状态 | 触发条件 | 视觉 |
|------|---------|------|
| `idle` | 既无 streaming、无 pending/uploading 上传、无 inflight sendMessage | 默认 primary 按钮 + `ArrowUpIcon` |
| `uploading` | `attachments.some(a => a.status ∈ {'pending','uploading'})` 且 sendMessage 未发起 | disabled 视觉 + `Loader2Icon` spinner + `aria-busy="true"` |
| `sending` | sendMessage 已发起、尚未收到 server echo | disabled 视觉 + `Loader2Icon` spinner + `aria-busy="true"` |
| `streaming` | `isStreaming === true` | destructive variant + `Loader2Icon` spinner + abort 可点击 |

`uploading` 与 `sending` 在用户视觉上 MAY 不区分（两者共用相同 className），但 `aria-label` SHALL 体现当前语义：

- `uploading` → i18n key `chat.composer.uploadingLabel`（如 "正在上传文件..."）
- `sending` → i18n key `chat.composer.sendingLabel`（如 "正在发送..."）
- `streaming` → 现有 i18n key（不变）

#### Scenario: pending 文件存在时按钮显示 spinner

- **GIVEN** 用户刚拖入一个 1MB 文件，chip 显示 `uploading 10%`
- **WHEN** PromptComposer 渲染
- **THEN** 发送按钮 SHALL 显示 `Loader2Icon` 自旋
- **AND** 按钮 className SHALL 包含 `opacity-40` (disabled 视觉)
- **AND** 按钮 `aria-disabled` SHALL = `"true"`
- **AND** 按钮 `aria-busy` SHALL = `"true"`
- **AND** 按钮 `aria-label` SHALL 等同 `chat.composer.uploadingLabel` 的翻译结果

#### Scenario: 所有上传 done 后按钮恢复 idle

- **GIVEN** 上传完成，attachments 都已 `done`
- **WHEN** 文本框非空且无 streaming
- **THEN** 按钮 SHALL 显示 `ArrowUpIcon`
- **AND** 按钮 className MUST NOT 包含 `opacity-40`
- **AND** 按钮 `aria-disabled` SHALL = `"false"` 或不出现该属性
- **AND** 按钮可点击触发 sendMessage

#### Scenario: streaming 中按钮可中止

- **GIVEN** AI 正在 streaming 回复（`isStreaming=true`）
- **WHEN** PromptComposer 渲染
- **THEN** 按钮 SHALL 渲染 destructive variant + `Loader2Icon`
- **AND** 单击按钮 SHALL 调用 `abort()` 而非 sendMessage

#### Scenario: prefers-reduced-motion 用户的 spinner 替代

- **GIVEN** 用户操作系统设置 `prefers-reduced-motion: reduce`
- **WHEN** 按钮处于 `uploading` / `sending` / `streaming` 态
- **THEN** spinner 动画 SHALL 切换为静态 dot 或 pulse 替代（不自旋）
- **AND** `aria-busy="true"` 仍 SHALL 设置

### Requirement: BUG-0057 / BUG-0056 回归保护

本次 eager upload 改造 MUST NOT 回退以下两条既有保证（已通过 BUG-0057 / BUG-0056 修复落地）：

- `FileAttachment.id` 字段 SHALL 仍为 attachment 的唯一稳定标识，所有跨 setState 的寻址 MUST 通过 `a.id === targetId` 而非 `a === target` 引用比较
- `uploadAll` 完成后 hasUploads SHALL 收敛到 `false`（不再卡 `uploading` 0%）
- `file_upload` part SHALL 通过 `PendingFileUploadEchoRegistry` 在用户 message echo 时被前端 chat-parts-store 接收，并在 user bubble 上方/内部正确渲染

#### Scenario: BUG-0057 卡 uploading 不复现

- **GIVEN** 用户上传 1 张图、立即按 Enter 发送、消息成功送达
- **WHEN** sendMessage 成功完成
- **THEN** 输入框内 chip SHALL 全部消失（`clearDone()` 生效）
- **AND** `hasUploads` SHALL = `false`
- **AND** 发送按钮 SHALL 恢复 `idle` 态可点击

#### Scenario: BUG-0056 用户气泡 chip 仍可见

- **GIVEN** 用户发送一条含 1 张图的消息
- **WHEN** OpenCode echo 回 user message
- **THEN** 用户气泡上方 SHALL 渲染 `BubbleAttachmentList` 容器
- **AND** 容器内 SHALL 出现对应的 `FileChip`
- **AND** chip 单击 SHALL 通过 `GET /api/files/{fileId}/content` 拉取 remote 字节并打开预览
