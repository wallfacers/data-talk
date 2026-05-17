## Why

用户在 prompt-composer 上传文件后，附件信息只在**当条用户气泡内部**以 `FileUploadCard` 形式展示（密度高、占满气泡宽度、与气泡文字混排），与发送前 chip 风格脱节。更关键的是：

1. 历史会话翻看附件时**无法预览**：`FileUploadCard` 只渲染元数据，前端拿不到原文件字节。
2. 视觉断层：发送前是输入框的"横向 chip 列表 + 单击预览 Dialog"，发送后变成气泡内的纵向 metadata 卡片，同一附件在前后两个生命周期里是两种截然不同的视觉表达。
3. 浪费气泡内有限的横向空间：用户文本短（"这是什么"）时，附件块挤占主要视线焦点。

借这次改造让"输入框附件 → 已发送气泡附件 → 历史会话附件"三个生命周期共用同一套视觉与交互，并补齐"历史会话点击预览"这一刚需闭环。

## What Changes

- **气泡上方回显附件**：把 `FileUploadCard` 从用户气泡内部挪到气泡**上方**，作为独立的横向 chip 列表渲染。
- **chip 风格统一**：复用 `FileAttachmentChip` 的视觉风格（中性底 + 主色 focus/hover 点缀），但**去掉 X 删除按钮和上传进度条**（已发送消息不可删/不可重传）。
- **横向布局 + 响应式横滚**：与输入框 chip 容器完全一致 —— `flex flex-row gap-1.5 overflow-x-auto`，右对齐贴气泡边界，宽度上限 `max-w-[85%]` 与气泡同宽。
- **单击 → 复用 `FilePreviewDialog`**：与输入框上的预览弹框 100% 复用同一组件、同一 Dialog UI、同一 maximize/close 交互。
- **后端补 GET 接口**：新增 `GET /api/files/{fileId}/content`，以 stream + 正确 `Content-Type` 返回原文件，支撑历史会话与已发送消息的预览。
- **重构 `FilePreviewDialog` 数据源契约**：从只接受内存 `File` 对象，改为接受 `PreviewSource` 抽象（`local` | `remote` 两种来源），让同一 Dialog 既能服务输入框场景也能服务历史气泡场景。
- 提取 `FileChip` 纯展示组件（无 X、无进度条）作为新的最小复用单元；`FileAttachmentChip` 演化为"`FileChip` + 上传进度条 + 删除按钮"的输入框场景包装。

## Capabilities

### New Capabilities

- `chat-message-attachments`：用户消息附件在气泡上方的回显、横向布局、与输入框风格一致的 chip 视觉、单击复用预览 Dialog、历史会话也可预览的端到端契约。同时包含支撑前端预览的后端 GET 文件内容接口契约。

### Modified Capabilities

- （无）—— 现有 `user-message-markdown` spec 描述的是气泡文字 markdown 渲染、气泡 chrome（copy/timestamp/retry/bang-query icon），不涉及附件展示位置；新需求新增"气泡外部附件列表"的产品行为，与既有 spec 边界互不重叠，不做修改。

## Impact

- **前端代码**：
  - `client/src/features/chat/components/turn/user-bubble.tsx`（移除气泡内 `FileUploadCard` 调用，改为在气泡外上方渲染新的 `BubbleAttachmentList`）
  - `client/src/features/session/components/file-attachment-chip.tsx`（拆出 `FileChip` 纯展示组件）
  - `client/src/features/session/components/file-preview-dialog.tsx`（数据源抽象重构）
  - 新增：`client/src/features/chat/components/turn/bubble-attachment-list.tsx`
  - 新增：`client/src/features/session/components/file-chip.tsx`
  - 删除：`client/src/features/session/components/file-upload-card.tsx`（被 `FileChip` 在气泡上下文中的渲染所取代；输入框场景 `FileAttachmentChip` 仍依赖 `FileChip`）
- **前端 API 层**：`client/src/services/api/file-upload.ts` 新增 `getFileContentUrl(fileId)` 或 `fetchFileContent(fileId): Promise<Blob>`。
- **后端代码**：
  - `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileUploadController.java` 新增 `@GetMapping("/{fileId}/content")` 端点。
  - 复用现有 `UploadedFileRepository.findById(fileId)` 查询（已存在 byId 查询能力，无需新增接口）。
- **持久化**：无 schema 变更。文件物理路径已在 `~/.data-talk/uploads/{fileId}/{filename}`。
- **API 契约**：
  - 新增 `GET /api/files/{fileId}/content` —— 200 stream / 404 not found。
  - 现有 `POST /api/files/upload`、`DELETE /api/files/{fileId}` 不变。
- **不影响**：OpenCode 协议、`FileUploadPart` 协议字段、数据库 schema、会话/连接相关流程。

## Design Inputs

本提案涉及 `client/` UI 改造，已读取并适用 [client/DESIGN.md](../../../client/DESIGN.md) 以下约束：

- **第 279 行 / 第 353 行**：`accent.primary` 仅保留给 focus / selection / primary action，不滥用强调色。→ chip 默认态用中性 `bg-bg-soft`，强调色仅出现在 focus-visible ring 与 active 态的轻量背景着色。
- **第 282–288 行**：`focusRing` / `hover` / `active` / `selected` / `disabled` 五态命名稳定，跨组件统一。→ `FileChip` 五态显式映射五种 token，且在 design.md 中逐一列出。
- **第 314 行**：Messages 用语义区分用户/助手/工具/错误曲面，禁止饱和气泡。→ chip 不使用气泡的 `bg-primary`，沿用消息区域语义中性底。
- **第 308–315 行**：Composer 用 `bg.panel + border.default + interaction.focusRing`；附件 chip 视为 composer 体系的延伸，气泡上方的回显 chip 与之保持视觉一致。
- **第 332–338 行 a11y**：chip 整体作为可点击元素，必须有可访问名（`aria-label` 含文件名 + "预览"动作语义），focus ring 双主题可见，键盘可达，state 不仅依赖颜色（disabled 同时降低不透明度并加 `cursor-not-allowed`）。

## Risks

- **历史 `fileId` 链路**：旧会话的 `FileUploadPart` 若曾在过去版本中无 `fileId`（理论上不存在，因为字段从 day-1 引入），需快速 spot-check 一条最早会话的 message JSON 确认。若发现缺失，chip 优雅降级为不可点击灰态。
- **物理文件被清理**：用户手动删除 `~/.data-talk/uploads/` 后 GET 接口返回 404，前端需在 Dialog 中显示友好错误而非崩溃。
- **图片大文件**：现有 50MB 上限对图片预览算够用，但浏览器解码大图可能卡顿。Dialog 已有 maximize 态，新增预览路径无需额外保护。
- **BUG 索引检查**：已 grep `docs/bugs/index.md`，无与"附件展示 / 附件预览 / 气泡附件"重叠的 open BUG。最相近的 BUG-0044（用户气泡 markdown 不可读）已 verified，且因本次 chip 不放在气泡内部、不使用 `bg-primary` 背景，不会复现该问题。
