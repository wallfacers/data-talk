## Context

当前 PromptComposer 的文件上传触发方式仅有拖拽（`FileDropZone`）和粘贴（`handlePaste`），缺少工具栏按钮入口。附件预览 chips 排列在 textarea 与底部工具栏之间，不符合主流 AI 产品（DeepSeek/ChatGPT）的交互模式。后端文件分析仅覆盖文本类型，图片文件上传后无法被 AI 消费。

Design Inputs（from client/DESIGN.md）：
- 上传按钮：ghost 样式，`text-text-muted`，`size="icon-xs"` + `rounded-full`
- `aria-label` 可访问性标注必须提供
- 附件卡片颜色仅使用 semantic tokens（`bg-bg-soft`、`border-border-default`、`text-text-muted` 等）
- 工具栏 compact density，不引入大尺寸组件

## Goals / Non-Goals

**Goals:**
- 工具栏新增 📎 点击上传按钮，DeepSeek 风格（ghost、无背景、muted 图标）
- 附件预览从 textarea 下方迁移至 InputGroup 顶部，水平滚动行布局
- 图片文件端到端支持：前端缩略图、后端 MIME 检测、二进制读取（base64）
- 多文件上传（前端已支持，无需额外工作）

**Non-Goals:**
- 图片 OCR 或 AI 视觉理解（仅存储 + 元数据提取 + base64 传递给 AI）
- 并发上传优化（当前串行 uploadAll 可接受，后续可独立优化）
- 文件上传进度取消（单个文件上传中断）
- 批量上传后端端点（保持单文件端点 + 前端循环调用）

## Decisions

### D1: 上传按钮使用隐藏 `<input type="file">` + Paperclip 图标

**选择**: `<Paperclip>` 图标 + `ref` 触发隐藏 file input，`ghost` variant + `icon-xs` size

**备选**: 使用 shadcn `Button` 内嵌文件选择器
**理由**: Paperclip 是业界通用上传图标（DeepSeek、ChatGPT 均使用），隐藏 input 方案简单可靠，无需额外依赖。

### D2: 附件区域迁移到 InputGroup 顶部（textarea 上方）

**选择**: 将 `attachments.map(...)` 块从 `InputGroupTextarea` 与 `InputGroupAddon` 之间移到 `InputGroup` 内部最顶部，容器从 `flex-col` 改为 `flex-row overflow-x-auto`

**备选**: 在 `InputGroup` 外部、`FileDropZone` 内部顶部放置附件行
**理由**: 放在 InputGroup 内部保持圆角边框一致性，DeepSeek 也是在输入框内部顶部展示。水平滚动防止附件过多时撑高输入框。

### D3: FileAttachmentChip 适配紧凑横向卡片

**选择**: 保持现有 chip 组件，调整 flex 方向和内边距，新增图片缩略图分支（`URL.createObjectURL` + 小尺寸 `<img>`）

**备选**: 新建独立的 `FileAttachmentCard` 组件
**理由**: 现有 chip 结构已包含文件图标、名称、大小、进度、移除按钮，仅需 CSS 微调 + 图片分支，不值得创建新组件。

### D4: 后端图片支持——扩展 FileAnalysisService + FileReadActionHandler

**选择**:
- `FileAnalysisService`: 新增 `image/*` MIME 检测（png/jpg/jpeg/gif/webp），元数据提取为 `{ width, height, format, sizeBytes }`
- `FileReadActionHandler`: 新增二进制路径，当 MIME 以 `image/` 开头时读取字节并返回 base64 编码字符串

**备选**: 使用图片处理库（Thumbnailator/ImageIO）生成缩略图存为独立文件
**理由**: 当前阶段只需 AI 能消费图片内容（base64 传递给 OpenCode），无需缩略图生成。元数据提取可用 `ImageIO.read` 零依赖获取尺寸。后续如需缩略图可增量添加。

### D5: 前端图片白名单扩展

**选择**: `ALLOWED_EXTENSIONS` 新增 `.png .jpg .jpeg .gif .webp .bmp`

**理由**: 覆盖主流图片格式，与浏览器 `<img>` 原生支持一致。SVG 排除（安全风险——可嵌入脚本）。

## Risks / Trade-offs

- **[图片 base64 传输增大 payload]** → 单张图片 50MB 上限已存在，AI 协议传输大 base64 可能较慢。缓解：可在 SKILL.md 中建议 AI 优先使用元数据而非完整 base64。
- **[FileReadActionHandler 返回 base64 字符串可能很长]** → 现有 `limit` 参数（max 4096 字符）不适用于二进制。需为图片路径绕过 limit，直接返回完整 base64。
- **[SVG 安全风险]** → 白名单排除 SVG，避免 XSS。

## Open Questions

- 图片传给 AI 时，OpenCode 协议是否原生支持 `image_url` content type？如果不支持，base64 文本作为上下文传递是当前唯一路径。
