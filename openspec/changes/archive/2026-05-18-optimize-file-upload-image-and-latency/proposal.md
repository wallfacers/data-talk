## Why

DataTalk 当前的文件上传链路存在两条独立缺陷，都已在 docs/bugs/ 中以 BUG 形式登记：

1. **BUG-0058（P1，功能不可用）**：`datatalk.file_read` MCP tool 对图片直接 `Files.readAllBytes` + `Base64.getEncoder().encodeToString`，零压缩、零 resize。一张 161KB 的桌面截图在 base64 后膨胀到 ~215KB，单条 `tool_result.content` 即超出 qwen3-VL（经 OpenCode 转发）的单 message 上限，触发"payload 太大"被拒，AI 无法消费截图内容。截图是 chat 场景最高频的图片附件类型。

2. **BUG-0059（P2，UX 感知性能）**：前端 `submitText` 采用 lazy upload — 文件进 `attachments` 后保持 `pending`，只在按 Enter 时才串行 `await uploadAll() → await sendMessage()`。整段 300-600ms 空窗期间输入框已清空、按钮无 spinner、用户气泡未上屏，用户体感是"按了回车屏幕卡了一下"。

两个 BUG 都在 file upload 链路上、都涉及 `useFileUpload.ts`，且互相耦合（压图缩小上行体积也会缓解卡顿），合并为单一 change 集中治理。

## What Changes

**Backend (BUG-0058)**：

- `FileReadActionHandler.handle` 的 image 分支新增透明压图：检测 `image/png|jpeg|webp|bmp` → resize 到 max-edge 2048px → 重编码 JPEG q=0.85 → base64。`image/gif` 走原样路径以保留动画
- 输出 schema 新增字段：`originalBytes`、`compressedBytes`、`compressionApplied: boolean`、`compressedMimeType`（压缩后真实 mime；GIF/passthrough 时 = 原 mime）
- 阈值：原图字节数 ≤ 50KB 时跳过压缩（避免无谓 CPU 开销）
- 解码安全门：拒绝单边 > 16384px 或像素总数 > 8192×8192 的图（防止 ImageIO OOM / DoS）；超限时按原样 base64 + `compressionApplied=false` + `reason="oversized_source"`
- **BREAKING（行为）**：现有调用方拿到的 `content` data-URI 的 mime 可能与原图不同（PNG → JPEG）。`compressedMimeType` 字段是真相

**Frontend (BUG-0059)**：

- `useFileUpload.addFiles` 在入队后立即触发 `uploadAll`，不再等 Enter
- `FileAttachment` 新增 `controller: AbortController`；`removeAttachment` 同步 `controller.abort()`，对应的 `uploadFile` 调用 cancel
- `uploadAll` 由 `for-of await` 改为 `Promise.all`（per-file try/catch，max-concurrent=3 通过简单 chunk 实现）
- `submitText`：保留"若仍有 pending/uploading 则等 done 再 sendMessage"语义（兜底）；正常路径下 done 已就绪，直接进 sendMessage
- 发送按钮状态机扩展为 `idle | uploading | sending | streaming`；`uploading` / `sending` 也切 spinner（不仅 `isStreaming`）

**Backend supporting changes**：

- `FileUploadController.upload` 接受 `AbortController.abort()` 触发的客户端中断作为非 5xx 路径（已写入磁盘的 temp / permanent 文件在请求中止时清理孤儿）

## Capabilities

### New Capabilities

- `file-read-image-pipeline`: `datatalk.file_read` MCP tool 的图片消费契约 — 压缩策略、mime 协商、安全门、可观测性字段

### Modified Capabilities

- `chat-message-attachments`: 新增"前端文件上传时机与取消语义"requirements（eager upload、abort 行为、发送按钮多态反馈）

## Impact

**Backend**：

- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/FileReadActionHandler.java`（核心改动点）
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileUploadController.java`（中断清理）
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/FileReadActionHandlerTest.java`（新增压缩验证用例）
- 依赖：新增 `net.coobird:thumbnailator:0.4.20`（Apache 2.0，~150KB，无传递依赖） — JDK 内置 ImageIO 也能完成，但 Thumbnailator API 更简洁、resize 算法（progressive bilinear）质量更好
- 不涉及 domain / application 层；不涉及数据库 schema
- 不涉及 ingestion 链路

**Frontend**：

- `client/src/features/session/useFileUpload.ts`（eager upload + abort + 并发）
- `client/src/features/session/useFileUpload.test.ts`（新增 6+ 用例）
- `client/src/features/session/prompt-composer.tsx`（按钮多态、submitText 流程简化）
- `client/src/features/session/components/file-attachment-chip.tsx`（abort 时 chip 视觉提示）
- `client/src/services/api/file-upload.ts`（`uploadFile` 接受 `AbortSignal`）
- `client/DESIGN.md`：必读 gate；按钮多态、chip abort 态需要从 DESIGN.md 引用 token

**External / Protocol**：

- OpenCode 协议本身不变（`datatalk.file_read` 的 input schema 不变）
- 增加的 output 字段（`compressedBytes` 等）对 LLM 透明 — schema additive，旧 client 兼容
- LLM prompt（AGENTS.md / skill 说明）需要在适当位置补一句"压缩仅影响图片消费体积，不影响其他文件类型"，避免 AI 误解

**Data Source Type Compatibility**：N/A（本 change 不涉及任何 JDBC / 数据源类型）

**Risks**：

- JPEG q=0.85 对 PNG 截图文字锐度的影响 — 经验上对 1440p 以下 UI 截图无感知；如需更激进可改 q=0.9（体积换质量）
- 动画 GIF 必须走原样路径；漏判会丢动画帧
- eager upload + 用户立即删除时的 race：abort 必须在 `removeAttachment` 同步触发；后端孤儿文件由 `FileUploadController` 在 IOException / 客户端 close 时清理
- Thumbnailator decode 全量进堆：单图 max 8192×8192 × 4B ≈ 256MB，配合 50MB 上传限制其实安全，但仍需测压
- 并发上传 max-concurrent=3：避免短时多文件场景下浏览器并发 socket 风暴；多于 3 的批量上传走 chunk 排队

**Open BUGs in 邻近模块**（已在 docs/bugs/index.md 检索）：

- BUG-0049（dashboard 中文乱码）— 与本 change 无交集
- BUG-0050（dashboard JSON widget skeleton）— 与本 change 无交集
- 同模块刚 fixed：BUG-0056（file_upload part 协议降级）、BUG-0057（chip 卡 uploading）— 本 change **MUST NOT** 回退这两个修复，eager upload 改动需在测试中复用 BUG-0057 的 id-based addressing 守护用例

**Frontend Design Inputs（Gate）**：

本 change 的前端部分必须在 design.md 的 "Design Inputs" 章节引用 client/DESIGN.md，明确列出：

- 按钮在 `idle / hover / active / disabled / loading` 五态的 token（特别是 `loading` 态如何与现有 `streaming` 态视觉区分或统一）
- chip 在 `pending / uploading / done / error / aborting` 各态的视觉规范
- 进度反馈（chip 内进度条、按钮 spinner）的颜色 / 动画 token
