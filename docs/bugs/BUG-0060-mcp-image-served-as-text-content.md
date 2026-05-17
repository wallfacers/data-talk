---
id: BUG-0060
title: MCP file_read 把 image data URI 塞进 text content，模型只看到 base64 字符串
status: fixed
priority: P0
source: manual-report
modules: [adapter, opencode, file-upload]
discovered: 2026-05-18
discoveredBy: agent
testRunId: null
fixCommit: pending
fixPlanRef: openspec/changes/optimize-file-upload-image-and-latency/specs/file-read-image-pipeline/spec.md
duplicateOf: null
regression: false
---

## Summary

`DataTalkMcpService.toToolResult` 把所有 action 输出（包括 `FileReadActionHandler` 在 image 分支返回的 `content: "data:image/jpeg;base64,..."`）整体 JSON 序列化为单条 `{"type":"text","text":"..."}` MCP content block。
LLM 收到的不是 vision input，而是一长串 base64 字符串，自然无法 OCR — 只能按 mime 头和上下文凭空猜出"数据管理平台界面"之类的笼统描述。

## Reproduction Steps

1. DataTalk 上传一张含中英文混排的截图（如 824×569 PNG，40KB）。
2. 在会话中让 AI 调用 `datatalk_file_read` 读取该图。
3. AI 回复一段与图内容毫不相关的泛泛描述（如"数据管理或分析平台的 Web 应用界面"）。
4. 同样的图在 OpenCode `POST /session/{id}/message` 里以 `{type:"file", mime:"image/jpeg", url:"data:..."}` 直发 qwen3.6-plus，模型可逐行识别全部文字。

## Expected vs Actual

- **Expected**: LLM 在 file_read 后能逐行识别图中所有文字（表名 / 按钮 / 列头）。
- **Actual**: LLM 把图当成"看不见的二进制"，回笼统的界面类型描述。

## Environment

- Backend commit: `d2072bed` (before fix)
- Frontend commit: same
- Model: qwen3.6-plus / alibaba-coding-plan-cn（**是**视觉模型 — 之前的"模型没有视觉能力"误判已纠正）
- OpenCode: v1.14.41
- 数据源: N/A（与数据库无关）

## Evidence

后端日志（修复前路径正常执行，证明问题不在压缩）：
```
[file-read] image compression: fileId=d3ac9e74... originalBytes=40678 compressedBytes=19242 applied=true compressionDurationMs=63
```

直接 OpenCode API 测试（绕过 MCP，证明模型 + 压缩都 OK）：
```
POST /session/.../message  with {type:"file", mime:"image/jpeg", url:"data:image/jpeg;base64,<19KB>"}
→ qwen3.6-plus 返回：表格 / 复制表格 / CSV / 更多 / 表名 / addresses / categories / order_items / orders / payments / products / refund_records / shipments / shopping_carts / users
```

DataTalk MCP 包装后（修复前）：
- `result.content = [{type:"text", text:"{\"fileId\":...,\"content\":\"data:image/jpeg;base64,/9j/4AAQ...\"}"}]`
- 模型看到的是 JSON 字符串里嵌入的 base64，无法解码为图像。

## Root Cause

MCP spec 对 tool result 的 image content 有专门类型：
```json
{ "type": "image", "data": "<raw base64, NO data URI prefix>", "mimeType": "image/jpeg" }
```

OpenCode 拿到该 content block 后，AI SDK 会自动转换为 LLM provider 的 vision message part（qwen3.6-plus 的多模态接口）。

DataTalk 的 `DataTalkMcpService.toToolResult` 不区分 action 输出形态，总是把整个 output JSON 序列化后塞进 `{type:"text"}` content。image 路径下，`content` 字段存的 data URI 就成了纯文本，永远到不了 vision pipeline。

## Fix

修改文件：`server/data-talk-application/src/main/java/com/datatalk/application/opencode/DataTalkMcpService.java`

在 `toToolResult` 里检测 output map 是否包含 image data URI（`content` 字段以 `data:image/...;base64,` 开头）：
- 若是 image：拆成 2 个 MCP content block —
  - `{type:"text", text:"<metadata JSON with `content` field stripped>"}` — 保留 fileId / compressedBytes / compressionApplied 等可观测字段，剔除 base64 避免重复占 token
  - `{type:"image", data:"<raw base64>", mimeType:"<compressedMimeType || mime from URI>"}`
- 否则保持单条 text content（与所有非 image action 旧行为完全一致）

错误响应（`outcome.isError()`）即使含 `content` 字段也不会被拆，避免误触发。

`structuredContent` 仍包含完整原始 output，供程序化客户端无损消费。

## Verification

1. 单元测试 `DataTalkMcpServiceTest`（5 → 9 个用例）：
   - `imageDataUriOutputIsSplitIntoTextAndImageContentBlocks` — 拆分正确、metadata 不带 base64、`data` 为 raw base64
   - `imageBranchToleratesMissingCompressedMimeTypeViaDataUriHeader` — 缺 `compressedMimeType` 时从 URI 推断 mime
   - `nonImageContentFieldStaysAsSingleTextBlock` — text/CSV 输出不被误拆
   - `errorOutputWithImageDataUriDoesNotEmitImageContent` — 错误路径不拆

2. E2E（绕 DataTalk channel 真实链路）：
   ```
   POST /api/sessions/{sid}/channel  send_message[file_upload + text]
   → OpenCode 调 datatalk_file_read
   → MCP 拆出 image content block
   → qwen3.6-plus 完整识别 15 条文本（按钮 + 表头 + 10 张表名一字不差）
   ```

## Notes

- 与 BUG-0058 互补：BUG-0058 解决了 base64 体积超过 OpenCode ~50KB inline cap 的截断问题；BUG-0060 解决了即使体积达标，image 依然无法进入 LLM 视觉通道的问题。两者叠加才让 image 真正可被识别。
- `compressedMimeType` 字段成为 MCP 层 image 探测的 strong signal，未来其它产出 image 的 action（chart screenshot、ER 图导出等）只要遵循 `{content: data URI, compressedMimeType: image/...}` 即可自动获得正确的 MCP image content block 包装。
- MCP spec 参考：https://modelcontextprotocol.io/specification —— `ImageContent` 要求 `data` 为 raw base64（**无** `data:` 前缀），`mimeType` 必填。
