## Context

### 现状链路（含历史包袱）

```
[前端 Composer]
  1. 选/粘贴/拖图片 → 立即 eager upload
  2. 服务端 ImageCompressor 把图缩到 1024px、JPEG 重编码、写入 ~/.data-talk/uploads/<fileId>/
  3. 上传响应只回 fileId + analysis（不回 bytes）
  4. 用户点发送 → 构造 parts: [TextPart, FileUploadPart{fileId, filename, mimeType, sizeBytes, analysis}]
  5. POST /api/.../channel { method: 'send_message', params: { parts } }

[后端 ChannelService.sendMessage]
  6. 拆出 FileUploadPart → PendingFileUploadEchoRegistry.enqueue（暂存）
  7. parts → partForWire:
       - TextPart  → { type: 'text', text }
       - FilePart  → { type: 'file', mime, filename, url, source }   ← 已支持，但没人用
       - FileUploadPart → { type: 'text', text: "[Uploaded file: ... | fileId: ... | ...]
                            Use `datatalk_file_read` with fileId `xxx` if you need full content" }
  8. POST OpenCode /session/:ocSid/message  body={ parts: [...], model }

[OpenCode → AI]
  9. AI 看到的是一段文本提示，不是图片
  10. AI 调 datatalk_file_read(fileId, ...) MCP tool
  11. FileReadActionHandler 读盘 → 再次 ImageCompressor 压缩 → 返回 data URI 文本/image content block

[前端回显]
  12. OpenCode 把 user message echo 回来（含 step 9 的 text part，不含 file_upload）
  13. OpenCodeEventLoop 收到 message.updated → 从 PendingFileUploadEchoRegistry.drainNext 取暂存的 FileUploadPart
       → 重新发 message.part.created 事件给前端
  14. 前端 UserBubble 上方渲染 chip
```

### 痛点

| # | 现象 | 根因 |
|---|------|------|
| 1 | 多图场景 N 次 tool round-trip | partForWire 不接 image，AI 必须 read_file |
| 2 | 大截图 payload too large (BUG-0058) | 第二次走 read_file 即使 ImageCompressor 压缩后仍可能超 MCP 单次 limit |
| 3 | image 处理路径过长 | 上传压缩 1 次 + read_file 又压缩 1 次 |
| 4 | chip echo 机制脆弱 | PendingFileUploadEchoRegistry 依赖 message.updated 的时序，BUG-0056 就是它失灵 |

### 上游可借鉴

OpenCode Web 客户端 (`packages/app/src/components/prompt-input/attachments.ts:11-26`)：
```ts
function dataUrl(file: File, mime: string) {
  const reader = new FileReader()
  reader.readAsDataURL(file)
  // → "data:image/png;base64,iVBOR..."
}
```
然后 (`build-request-parts.ts:185-193`)：
```ts
input.images.map(a => ({
  id: Identifier.ascending("part"),
  type: "file",
  mime: a.mime,
  url: a.dataUrl,         // ← base64 data URI
  filename: a.filename,
}))
```
OpenCode 服务端 (`acp/agent.ts:1010-1084`) 按 `url.startsWith("data:")` 解 base64 → ACP image block，直接喂给 Anthropic provider，**不再走 MCP tool**。

### 约束

- **OpenCode 严格 Zod 校验**：`POST /session/:id/message` 的 part 只接受 `type/mime/filename/url/source` 字段，自定义字段（fileId、analysis）会被整请求 reject。这就是当前必须降级到 text 的原因。
- **图片已经在服务端**：浏览器上传时立刻压缩存盘，前端手里只有 fileId，**没有 bytes**。要做 data URI 必须前端从 `GET /api/files/{id}/content` 拉回字节再 base64。
- **多附件类型并存**：CSV/JSON/SQL/Excel 仍需 file_upload + analysis 路径（AI 要根据 analysis 决定调 import_data 还是 query editor）。**只有 IMAGE 类型走新路径**。
- **chip echo 仍要保留**：file_upload 走老路径的部分（CSV 等）依然需要 PendingFileUploadEchoRegistry，不能整体删除，只能让"图片"分支跳过它。

## Goals / Non-Goals

**Goals:**
- 图片附件由客户端打包成 OpenCode `FilePart` 一次性发送，AI 同一条 user message 直接看到全部图片，不再触发 `datatalk_file_read`。
- 闭环 [BUG-0058](../../../docs/bugs/BUG-0058-file-read-image-no-compression-base64-too-large.md)：图片只在上传时压缩一次，发送时直接用 1024px 压缩结果，不再二次压缩。
- 保留 `FileUploadPart` + `PendingFileUploadEchoRegistry` 用于 CSV/JSON/SQL 等结构化附件（行为不变）。
- AI 仍能通过 `datatalk_file_read` 主动按需读取**历史会话的非图片文件**（语义保留，引导语调整）。
- 前端 chip 展示、`FilePreviewDialog` 预览、`GET /api/files/{id}/content` 端点行为完全不变。

**Non-Goals:**
- 不引入新的图片压缩策略（沿用 ImageCompressor 1024px / 80% quality）。
- 不重构 `useFileUpload` 整体架构、不动 eager upload 并发模型。
- 不改 OpenCode/Anthropic provider 适配（直接走 OpenCode 现成路径）。
- 不删除 `FileReadActionHandler` 图片读取分支（向后兼容旧会话存档；只调引导语）。
- 不把"批量发送"扩展到非图片附件。

## Decisions

### Decision 1：图片承载格式 — base64 data URI（与 OpenCode 一致）

**选择**：图片用 `data:image/<mime>;base64,<...>` 形式塞 `FilePart.url`。

**理由**：
- OpenCode 上游 Web 客户端就是 data URI，服务端按 scheme 三路分发已稳定。沿用同一套零额外适配。
- 本地 HTTP URL（`http://localhost:8080/api/files/...`）需要 OpenCode 进程能反向访问后端：单机 Tauri 场景 OK，但 OpenCode 跑在容器/远端机时直接断网，可移植性差。
- `file://` 路径假设 OpenCode 与后端共享文件系统，DataTalk 后端把上传放在 `~/.data-talk/uploads/`，OpenCode 进程未必 mount，且引入额外的路径白名单/SSRF 风险。

**取舍**：payload 体积 = 原图 × 1.33；1024px 压缩后单图通常 80–300 KB，base64 后 110–400 KB；5 张以内单次 promptAsync body 在 2 MB 量级，HTTP/SSE 可承受。**不需要 chunk upload**。

### Decision 2：前端从 GET 端点拉字节再转 base64（不让后端额外发 dataUri）

**选择**：上传响应保持现状（只回 fileId）。发送时前端按需 `GET /api/files/{fileId}/content` → `FileReader.readAsDataURL` → 拼 `FilePart`。

**理由**：
- 上传响应里塞 dataUri 会让响应体翻几百 KB，损害 UI 响应（用户上传后立刻看到 chip 不应等大 body）。
- 浏览器 `URL.createObjectURL` 已经在 `FilePreviewDialog` 走通了一遍 GET → blob 的流程，复用同一套。
- 客户端可以缓存 dataUri 到内存（按 fileId keyed），避免同一图片多次发送重复拉取。

**备选**：在上传响应里就返回 dataUri —— 否决，理由如上。

### Decision 3：partForWire 分支细化 — 仅图片走新路径

**选择**：`partForWire(FileUploadPart u)` 增加一个前置判断：
- 若 `u.mimeType().startsWith("image/")` 且 `u.url() != null`（即前端已经填充了 data URI）→ 输出 `{ type:"file", mime, filename, url }`。
- 否则保持现状（生成提示文本 + 引导 read_file）。

**理由**：CSV/JSON/SQL 附件路径完全不动，零回归风险。图片新走 FilePart，老 echo registry 对图片不再 enqueue。

**实现细节**：
- 给 `FileUploadPart` (domain record) 增加可选字段 `String url`（默认 null）。
- 前端发送时图片 part 携带 url；CSV 等保持不带 url。
- `ChannelService.sendMessage` 中 `uploadParts` 过滤 echo enqueue 时只收集 **非图片**（或更准：`url == null` 的）part。

### Decision 4：MCP `datatalk_file_read` 工具保留，仅调整引导语

**选择**：不删除 tool，不改 handler 实现；只：
- 删除 `skills/file-upload-routing/SKILL.md` 中 Image Files 一节里 "Use `datatalk_file_read` to retrieve image content" 的引导。
- 删除 `partForWire` 给 FileUploadPart 拼的 "Use `datatalk_file_read` with fileId..." 尾句（图片分支已经走 FilePart 不会到这里；CSV 等可以**保留**——非图片确实需要 AI 按需读全文）。
- `AGENTS.md` 里 read_file 的通用描述保持。

**理由**：
- 历史会话里的图片消息仍然以 fileId 形式存在（旧的 FileUploadPart）；AI 翻历史时仍要能读到图。
- AI 主动需要"再读一次某图细节"的场景虽然罕见但合理，保留工具能力是好事。
- 改动最小化、可回退。

### Decision 5：PendingFileUploadEchoRegistry 不删除，调整入队规则

**选择**：保留 registry；`enqueue` 时过滤掉图片 part（仅入队非图片）。

**理由**：
- 图片走 FilePart 后，OpenCode 会自然 echo 回 `message.part.created({type:'file'})`，前端的 chip 渲染逻辑要扩展为接受 `FilePart` 也能渲染（参见 chat-message-attachments 现有 spec，chip 接受 `mimeType` + 缩略图）。
- CSV/JSON 仍走 file_upload → text 降级 → echo registry 回放的老路径，保持 BUG-0056 修复成果。

**回放路径调整**：
- `OpenCodeEventLoop` 收到 user message.updated 后 `drainNext`：若拿到的列表为空（图片场景），跳过；否则按原逻辑回放为 `message.part.created`。

### Decision 6：前端 chip 来源统一 — FilePart 也能渲染 chip

**选择**：`UserBubble` 上方的 `BubbleAttachmentList` 扩展为同时识别两种 part：
- `FileUploadPart` (已有)：用 fileId + GET 端点拉缩略图
- `FilePart` (新)：直接用 `url`（data URI）作缩略图，无需 GET

**理由**：图片走新路径回显时 OpenCode 发回的是 FilePart，UI 必须能渲染。其他附件类型继续走 FileUploadPart 路径。

**Design Inputs (client/DESIGN.md)**：
- 本变更不引入新的视觉 token；chip 仍用现有 `border-border-default bg-bg-soft text-text-base` 五态映射。
- 缩略图尺寸 24×24、`object-cover rounded` 保持不变。
- 无新 UI 组件、无视觉重排，仅扩展数据来源——`client/DESIGN.md` 视觉约束完全沿用，无冲突点。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
|------|------|------|
| **base64 膨胀** | 单消息 body 可能从几 KB 涨到 1–3 MB | 1024px + JPEG 80% 压缩已在；前端发送前做 size guard（>3MB 总量给警告，>5MB 拒发），用户可在 UI 减少图数 |
| **OpenCode promptAsync body 上限未知** | 大 body 可能被 OpenCode 拒收 | 在 `OpenCodeGateway.forwardUserMessage` 前预估 body size，超阈值降级到老路径（write a single warning to event bus） |
| **前端 GET 拉字节延迟** | 用户按发送 → 等 GET → 再发，感知卡顿 | 上传成功后**预热**：useFileUpload 在 chip done 时就开始后台 GET + base64，缓存到内存；发送时直接取缓存 |
| **历史会话兼容** | 旧 FileUploadPart 仍走 read_file 路径 | 不动 FileReadActionHandler，AI 仍能读；新消息走新路径，互不干扰 |
| **多图 token 成本** | LLM token 计费按图算 | 用户行为不变（本来就是手动添图），不引入新风险；UI 在 chip 数 ≥3 时显示提示语已经存在 |
| **PendingFileUploadEchoRegistry 边界** | 改动 enqueue 过滤逻辑可能影响 CSV 路径 | 单元测试覆盖 enqueue(图片only/混合/非图片) 三场景；E2E 跑 CSV chip 回归 |
| **BubbleAttachmentList 双 part 类型分支** | 渲染逻辑复杂度增加 | 抽 `pickAttachments(message.parts)` helper：把 FileUploadPart 与 image FilePart 统一映射成 `{ filename, mimeType, thumbnailSrc }` |

## Migration Plan

1. **后端先行（向后兼容）**
   - `FileUploadPart` domain record 加 `url` 可选字段（不破老调用方）。
   - `partForWire` 加图片分支（url 非空 → FilePart）。
   - `PendingFileUploadEchoRegistry.enqueue` 过滤图片。
   - 新增 `ChannelServiceTest` 覆盖图片/非图片/混合三场景。
   - `mvn install -pl data-talk-application -am -DskipTests` 推 jar。

2. **前端打通**
   - `useFileUpload`：上传完成后异步预热 base64（按 fileId 缓存）。
   - `prompt-composer` 构造 part 时图片附 `url=cachedDataUri`，非图片不变。
   - 类型扩展 `FileUploadPart.url?: string`。
   - `BubbleAttachmentList` 增加从 FilePart 渲染 chip 的分支（image only）。

3. **MCP 提示清理**
   - 编辑 `file-upload-routing/SKILL.md` 删 Image 节的 read_file 引导。
   - 编辑 `partForWire` 的 buildFileUploadContext 尾句保留（仅非图片到这里）。

4. **验证**
   - 后端单测 + WireMock 集成测试。
   - 前端 vitest（useFileUpload 预热缓存、partsForSend 图片分支）。
   - playwright-cli E2E：粘贴 2 张截图 + 文本一次发送 → 断言 OpenCode 收到的 parts 含 2 个 type:file + 1 个 type:text，AI 回复无 `datatalk_file_read` tool 调用。
   - 回归：上传 CSV → 发送 → AI 调 read_file 走老路径。
   - 关联 BUG-0058 标记 `verified` → `fixed`（如果 E2E 验证通过）。

5. **回滚策略**
   - 全部改动隔离在 `partForWire` 的一个 if 分支 + 前端的 `partsForSend` 一个 map 步骤。
   - 紧急情况：把 `partForWire` 图片分支注释掉、前端不填 url，即回到老路径。
   - 不需要数据迁移，无 Flyway 改动。

### Decision 7：FilePreviewDialog 扩展 embedded kind 而非复用 remote

**选择**：`PreviewSource` 增加第三种 kind `'embedded'`（含 dataUri），新路径回显的 image FilePart chip 单击映射到这种 kind。

**理由**：
- 新路径的 FilePart 没有 fileId（OpenCode 协议不带），强行复用 `remote` kind 会引入"假 fileId"或要求后端额外建立 dataUri → fileId 映射，污染数据模型。
- `embedded` 语义最直白：dataUri 即图源，无需任何 IO。
- Dialog 内部增加一个分支：`source.kind === 'embedded'` → 直接 `<img src={source.dataUri}>`，零额外组件。
- 老路径 `local` / `remote` 行为完全不动，零回归。

**取舍**：dataUri 估算 sizeBytes 需要 `Math.floor(base64Length * 3 / 4)` 简单计算（精度足够 chip header 显示）。

## Risks / Trade-offs（补充）

| 风险 | 影响 | 缓解 |
|------|------|------|
| **OpenCode 历史消息 url 字段丢失** | 刷新后历史新路径图片 chip 无图源、单击预览空白 | (a) E2E 场景明确验证 OpenCode echo 是否保留 url；(b) chip 在 url 为空时渲染"图片已失效"占位，预览 Dialog 在 embedded.dataUri 为空时显示友好错误，不崩溃 |
| **dataUri 体积让 OpenCode 持久化膨胀** | OpenCode 消息存储磁盘占用增大 | OpenCode 自己的 Web 客户端就这么用，属于上游已接受的成本；我们仅承担传输 |

## Open Questions

1. **预热时机**：图片很多时（10+）并发 GET 可能爆带宽。是否需要给"预热"加并发上限（如 max 3）？
   → 建议复用 useFileUpload 现有的 MAX_CONCURRENT=3。

2. **dataUri 缓存生命周期**：单 session 内有效还是常驻？是否需要在 chip remove / send 完成后清理？
   → 建议 send 成功后清理（一次性使用），失败重试时若缓存命中直接复用。

3. **size guard 阈值**：单消息总 base64 大小硬上限定多少？
   → 草案：3 MB warning（toast 提示）、5 MB hard reject。先按草案做，E2E 期间根据 OpenCode 实测调整。

4. **OpenCode `source` 字段**：FilePart 的 `source` 在 OpenCode 里是给 @mention 用的（代码引用范围）。我们图片场景不填 source 是否会被 Zod 拒？
   → 已经在 `partForWire(FilePart)` 里观察到现网未传 source 也能跑，应该 OK，但 design.md 写完后建议在 FakeOpenCodeServer 用例里加 schema 校验断言确认。

5. **OpenCode 是否长期保留 FilePart.url（data URI）于历史消息**：上游 Web 客户端能正常显示历史图片，说明 OpenCode 至少在当前版本保留 url；但是否会在压缩/归档/总结环节剥离 dataUri 未确认。
   → E2E 场景 8.6 + 新加 8.7 必须覆盖"发送后关闭再打开会话"验证；若发现丢失，需要后端在收到 OpenCode echo 的 FilePart 时辅以本地副本兜底（额外 Task，不阻塞本次改造主路径）。
