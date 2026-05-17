## Why

当前文件上传仅支持拖拽和粘贴触发，缺少直观的点击入口。附件预览以 chip 形式排列在 textarea 与工具栏之间，不符合主流 AI 对话产品（DeepSeek、ChatGPT）的交互惯例——附件应展示在输入框顶部，点击上传按钮应放置在工具栏中。此外，当前不支持图片文件的预览和上传。

## What Changes

- 在 PromptComposer 工具栏发送按钮左侧新增 📎（Paperclip）点击上传按钮，ghost 样式，点击触发 `<input type="file" multiple>` 文件选择器
- 将附件 chips 从 textarea 与工具栏之间迁移到 InputGroup 顶部（textarea 上方），改为水平滚动行布局
- `FileAttachmentChip` 适配为更紧凑的横向卡片样式
- 新增图片文件扩展名支持（`.png .jpg .jpeg .gif .webp`）
- `FileAttachmentChip` 新增图片缩略图预览（`URL.createObjectURL` + `<img>`）
- 后端 `FileAnalysisService` 新增 image MIME 类型识别，提取尺寸/格式元数据
- 后端 `FileReadActionHandler` 新增二进制读取模式，图片文件返回 base64 编码

## Capabilities

### New Capabilities

- `composer-file-upload-button`: PromptComposer 工具栏中的点击上传按钮，触发文件选择对话框
- `image-file-upload`: 图片文件的端到端上传支持——前端缩略图预览、后端 MIME 检测与二进制读取、AI 路由

### Modified Capabilities

- `composer-draft-persistence`: 附件预览区域从 textarea 下方迁移至顶部，需同步更新 spec 中描述的 composer 布局结构

## Impact

**Frontend**:
- `client/src/features/session/prompt-composer.tsx` — 新增上传按钮、布局重排
- `client/src/features/session/components/file-attachment-chip.tsx` — 横向卡片样式 + 图片缩略图
- `client/src/features/session/useFileUpload.ts` — 扩展白名单加入图片类型

**Backend**:
- `server/data-talk-application/.../FileAnalysisService.java` — 新增 image MIME 检测与元数据提取
- `server/data-talk-adapter/.../FileReadActionHandler.java` — 新增二进制读取路径（base64）
- `server/.../skills/file-upload-routing/SKILL.md` — 新增图片类型路由规则

**Design Constraints** (from client/DESIGN.md):
- 上传按钮使用 ghost 样式，`text-text-muted` 语义色
- 图标按钮需 `aria-label` 可访问性标注
- 工具栏保持 compact density，`size="icon-xs"` + `rounded-full`
- 颜色仅使用 semantic tokens，不使用 primitive 色值

**BUGs**: 无已知 open BUG 与此区域重叠。
