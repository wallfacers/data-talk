## Why

当前用户发消息携带图片时，后端 `ChannelService.partForWire()` 会把 `file_upload` part 转换成一段提示文本（"已上传文件 X，请用 `datatalk_file_read` 读取"），AI 必须在助手轮里逐张主动调用 MCP `datatalk_file_read` 才能"看到"图片。这造成三个问题：

1. **多图场景效率低**：N 张图片 → N 次 MCP tool round-trip，AI 上下文被切碎成多轮 tool result。
2. **大图易爆 payload**：`datatalk_file_read` 把图片直接 base64 塞回 tool result（参见已 verified 的 [BUG-0058](../../../docs/bugs/BUG-0058-file-read-image-no-compression-base64-too-large.md)），截图场景频繁触发 "payload too large"。
3. **与上游不一致**：OpenCode 原生协议本就支持一条消息携带多个 `FilePart`（base64 data URI），客户端直接打包发送即可，我们绕了一圈反而引入额外失败点。

OpenCode Web 客户端实测就是这么做的（`packages/app/src/components/prompt-input/attachments.ts:11-26` 的 `dataUrl()` + `build-request-parts.ts:185-193`），服务端按 URL scheme 三路分发（`data:` 解 base64、`file://` 引用本地、其他忽略）。我们沿用同一套，去掉"AI 主动 read_file"中转环节。

## What Changes

- **前端**：上传完成后保留 base64 data URI（来自现有 1024px 压缩链路），构造 prompt parts 时把图片做成 `{ type: "file", mime, filename, url: "data:image/...;base64,..." }` 直接挂在 parts 数组里发送。非图片附件维持现有 `file_upload` 行为。
- **后端 ChannelService**：`partForWire()` 检测到图片附件时，直接转换为 OpenCode `FilePart` 透传，**不再生成提示文本**、**不再引用 `datatalk_file_read`**。非图片附件维持现有逻辑。
- **后端清理**：移除 `PendingFileUploadEchoRegistry` 中专为图片回显设计的分支与相关异步等待逻辑（仅当确认非图片附件不依赖时；详见 design.md）。
- **MCP 工具**：`datatalk_file_read` 工具本身**保留**（AI 仍可主动读取历史会话已上传的文件、CSV、JSON 等），但移除"图片必须用它读"的引导提示。
- **BREAKING**：旧版本客户端发出的消息若仍按"file_upload + 等待 AI 调 file_read"模式，新后端仍然兼容（非图片走原路径）；但**新客户端必须升级到带 data URI 的 payload**。后端通过检测 part 是否带 `url` 字段判断走哪条路径。
- **回归保证**：用户气泡上方 chip 展示、`FilePreviewDialog` 预览、`GET /api/files/{fileId}/content` 端点等现有 `chat-message-attachments` 行为完全不变。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `chat-message-attachments`: 新增 Requirement 描述"前端打包图片为 OpenCode FilePart"与"后端透传图片 FilePart、不再生成 `datatalk_file_read` 引导"的发送链路行为。原有 chip 展示、预览、GET 端点 Requirement 全部保留。

## Impact

**代码**
- 前端 `client/src/features/session/useFileUpload.ts`、`prompt-composer.tsx`、`services/channel/types.ts`
- 后端 `server/data-talk-application/.../channel/ChannelService.java`（`partForWire`、`buildFileUploadContext`）
- 后端 `server/data-talk-application/.../channel/PendingFileUploadEchoRegistry.java`（图片分支清理）
- 后端 `server/data-talk-adapter/.../controller/FileUploadController.java`（上传响应可选增加 dataUri 字段，或前端自行从已下载字节生成）
- MCP `datatalk_file_read` 工具描述字符串（移除"图片"引导，仅保留通用文件读取语义）

**API / 协议**
- 前端 → 后端 `send_message` RPC 的 part schema 新增可选 `url`/`mimeType` 字段（用于图片 data URI）
- 后端 → OpenCode `promptAsync` parts 数组直接包含 OpenCode `FilePart`

**测试**
- 后端 `ChannelServiceTest`、`FakeOpenCodeServer` WireMock 用例增加"图片透传"断言
- 前端 vitest 覆盖 `useFileUpload` 生成 dataUri 的逻辑
- E2E：playwright-cli 跑一遍"贴一张截图 + 文字"端到端，确认 AI 一次就回答、不再触发 `datatalk_file_read`

**关联 BUG**
- 闭环 [BUG-0058](../../../docs/bugs/BUG-0058-file-read-image-no-compression-base64-too-large.md)（图片 base64 过大）—— 改造后图片由 1024px 压缩后的 data URI 一次性发出，不再走 read_file payload。
- 关联 [BUG-0060](../../../docs/bugs/BUG-0060-mcp-image-served-as-text-content.md)（fixed）：MCP 输出端的修复仍保留以防 AI 主动读图。
- 关联 [BUG-0056](../../../docs/bugs/BUG-0056-bubble-attachments-file-upload-part-not-roundtripped.md)（fixed）：roundtrip 的回显行为不能回归。

**风险**
- base64 体积 = 原图 × 1.33；多图叠加 + SSE 单帧需评估，1024px 压缩后大多在 200KB 量级，5 张以内可控。
- 后端转发 OpenCode 时若 OpenCode 服务端对 promptAsync body 有上限，需在 ChannelService 加 size guard（详见 design.md）。
