## ADDED Requirements

### Requirement: 图片附件压缩策略

`datatalk.file_read` MCP tool 在 `mimeType` 以 `image/` 开头时 SHALL 根据下表执行透明压缩，结果再 base64：

| 原 mime | 压缩动作 | 输出 mime |
|---------|---------|----------|
| `image/png` | resize maxEdge=800 + JPEG q=0.75 重编码 | `image/jpeg` |
| `image/jpeg` | resize maxEdge=800 + JPEG q=0.75 重编码 | `image/jpeg` |
| `image/webp` | resize maxEdge=800 + JPEG q=0.75 重编码 | `image/jpeg` |
| `image/bmp` | resize maxEdge=800 + JPEG q=0.75 重编码 | `image/jpeg` |
| `image/gif` | passthrough（原字节直接 base64） | `image/gif` |

resize SHALL 保持宽高比；若原图任一边 ≤ 800，则 MUST NOT 放大（仅可能缩小或保持原尺寸）。

**实测背书**（两轮迭代）：
- 第一轮 maxEdge=1024 + q=0.75：824×569 PNG (40KB) 压到 30KB JPEG (40KB base64 FITS)，但 1920×1080 PNG (58KB) 压到 48KB JPEG (65KB base64 OVER cap)
- 第二轮 maxEdge=800 + q=0.75：1920×1080 PNG (58KB) → 800×450 → 30KB JPEG (40KB base64 FITS)，824×569 (40KB) → 800×552 → 19KB JPEG (26KB base64 FITS)，两类典型截图同时通过

JPEG 质量保持 q=0.75 — 经实测在 800×450 downscale 后，order 列表 / 表名等典型 UI 文字仍可清晰识别。

#### Scenario: PNG 截图被压缩为 JPEG

- **GIVEN** 上传一张 1920×1080 PNG 截图，原始字节 161024
- **WHEN** AI 调用 `datatalk.file_read` 传入对应 `fileId`
- **THEN** 输出 `content` SHALL 为 `data:image/jpeg;base64,<...>`
- **AND** 输出 `compressedMimeType` SHALL = `"image/jpeg"`
- **AND** 输出 `compressedBytes` SHALL < `originalBytes`
- **AND** 输出 `compressionApplied` SHALL = `true`

#### Scenario: GIF 走原样路径保留动画

- **GIVEN** 上传一张多帧 GIF 动图，`mimeType = "image/gif"`
- **WHEN** AI 调用 `datatalk.file_read` 传入对应 `fileId`
- **THEN** 输出 `content` SHALL 为 `data:image/gif;base64,<原字节 base64>`
- **AND** 输出 `compressedMimeType` SHALL = `"image/gif"`
- **AND** 输出 `compressionApplied` SHALL = `false`
- **AND** 输出 `compressionSkipReason` SHALL = `"animated_passthrough"`

#### Scenario: WebP / BMP 统一压为 JPEG

- **GIVEN** 上传一张 800×600 WebP 图片
- **WHEN** AI 调用 `datatalk.file_read`
- **THEN** 输出 `compressedMimeType` SHALL = `"image/jpeg"`
- **AND** 输出 `compressionApplied` SHALL = `true`

### Requirement: 压缩阈值与安全门

`datatalk.file_read` 在 image 分支内 SHALL 实施以下短路条件：

1. **小图跳过**：若原文件字节数 ≤ 25 × 1024（25KB），MUST 跳过压缩走原字节 base64，`compressionApplied=false` + `compressionSkipReason="below_threshold"`（GIF 仍按上一条 passthrough）。阈值上限由 OpenCode 工具输出的内联 cap（~50KB，超过即外溢到磁盘并替换为模型不可读的 "saved to file" stub）反向推算：raw × 1.333 + JSON wrapper ≤ ~45KB → raw 安全上限 ≈ 33.5KB，向下取 25KB 留裕度
2. **超大解码门**：若图片头部解析得到任一维度 > 16384px 或像素总数 > 8192 × 8192，MUST 跳过压缩走原字节 base64，`compressionApplied=false` + `compressionSkipReason="oversized_source"`；MUST NOT 尝试 decode（防止 OOM）
3. **解码失败回退**：若 `Thumbnails.of(...).toOutputStream(...)` 抛任何异常，MUST 回退到原字节 base64 + `compressionApplied=false` + `compressionSkipReason="decode_failed"`，MUST NOT 让请求整体失败

#### Scenario: 小于 25KB 的图片不压缩

- **GIVEN** 上传一张 10KB 的 PNG 图标
- **WHEN** AI 调用 `datatalk.file_read`
- **THEN** 输出 `content` SHALL 为 `data:image/png;base64,<原字节 base64>`
- **AND** 输出 `compressionApplied` SHALL = `false`
- **AND** 输出 `compressionSkipReason` SHALL = `"below_threshold"`

#### Scenario: 25KB–50KB 中段图片 MUST 压缩以避开 OpenCode 内联截断

- **GIVEN** 上传一张 40KB 的 PNG 截图（原始字节在 25KB 与 50KB 之间）
- **WHEN** AI 调用 `datatalk.file_read`
- **THEN** 输出 `compressionApplied` SHALL = `true`
- **AND** 输出 `compressedMimeType` SHALL = `"image/jpeg"`
- **AND** 输出 `content` 解码后的 base64 字节数 SHALL < 40 × 1024（保留 OpenCode ~50KB 内联 cap 的安全裕度）

#### Scenario: 超大原图拒绝解码

- **GIVEN** 上传一张 20000×20000 像素的 PNG（极端用例）
- **WHEN** AI 调用 `datatalk.file_read`
- **THEN** 服务端 MUST NOT 触发 `BufferedImage` 全量 decode（不消耗 ≥256MB heap）
- **AND** 输出 `compressionApplied` SHALL = `false`
- **AND** 输出 `compressionSkipReason` SHALL = `"oversized_source"`
- **AND** 请求 SHALL 在 1 秒内返回（仅 header 解析 + base64）

#### Scenario: Thumbnailator 抛异常时回退原字节

- **GIVEN** 上传一张损坏的 PNG（header 合法但 IDAT 不完整）
- **WHEN** AI 调用 `datatalk.file_read`，Thumbnailator 抛 IOException
- **THEN** 请求 MUST NOT 返回 5xx / error 结构
- **AND** 输出 `content` SHALL = 原字节 base64 data URI（用原 mime）
- **AND** 输出 `compressionApplied` SHALL = `false`
- **AND** 输出 `compressionSkipReason` SHALL = `"decode_failed"`

### Requirement: 输出 schema 可观测性字段

`FileReadActionHandler.outputSchema()` SHALL 在原有字段（`fileId`、`offset`、`content`、`bytesRead`）基础上声明以下新字段（全部 optional，additive，旧 client 不读不崩）：

- `originalBytes`: integer — 原文件字节数
- `compressedBytes`: integer — 压缩后字节数（未压缩时 = originalBytes）
- `compressionApplied`: boolean — 是否实际执行了 resize + 重编码
- `compressedMimeType`: string — 输出 `content` data URI 的真实 mime
- `compressionSkipReason`: string — 仅 `compressionApplied=false` 时出现；取值集合 `"below_threshold" | "oversized_source" | "decode_failed" | "animated_passthrough"`

`bytesRead` 字段 SHALL 表示**输出 base64 解码后的字节数**（= `compressedBytes`），保持与历史语义一致（"AI 实际读到的内容字节数"）。

#### Scenario: 输出 schema 包含可观测字段

- **GIVEN** AI 调用 `datatalk.file_read(fileId=xxx)`，xxx 为 PNG 图片
- **WHEN** handler 处理完成
- **THEN** 返回 map SHALL 包含 keys：`fileId`, `offset`, `content`, `bytesRead`, `originalBytes`, `compressedBytes`, `compressionApplied`, `compressedMimeType`
- **AND** `compressionApplied=true` 时 SHALL NOT 包含 `compressionSkipReason` 字段（缺席即默认含义）

### Requirement: 非图片文件路径不受影响

`datatalk.file_read` 对 `mimeType` 不以 `image/` 开头的文件 MUST 保持现有行为：

- `RandomAccessFile` 按 offset / limit 读取
- 输出 `content` 为字节流的 UTF-8 字符串表示
- 不出现 `originalBytes` / `compressedBytes` / `compressionApplied` / `compressedMimeType` / `compressionSkipReason` 字段

#### Scenario: text 文件读取不变

- **GIVEN** 上传一份 CSV 文件
- **WHEN** AI 调用 `datatalk.file_read(fileId, offset=0, limit=4096)`
- **THEN** 输出 `content` SHALL 为 CSV 前 4096 字节的 UTF-8 字符串
- **AND** 输出 map MUST NOT 包含 `compressionApplied` 字段
- **AND** 输出 `bytesRead` SHALL = 实际读到的字节数（≤ 4096）

### Requirement: 性能与可观测性

`FileReadActionHandler.handle` SHALL 在每次 image 路径调用时记录结构化日志，格式 SHALL 包含：

- `fileId`
- `originalBytes`
- `compressedBytes`
- `compressionApplied`
- `compressionSkipReason`（如有）
- `compressionDurationMs`（resize + 重编码 wall-clock 时间）

日志级别 SHALL 为 `INFO`（用于命中率统计），但在 `compressionSkipReason="decode_failed"` 时升级为 `WARN`。

#### Scenario: 成功压缩记录 INFO 日志

- **GIVEN** AI 调用 `datatalk.file_read` 处理一张 200KB PNG
- **WHEN** 压缩成功完成（耗时 80ms，输出 30KB）
- **THEN** 应用日志 SHALL 出现一条 INFO 级别记录
- **AND** 记录 SHALL 包含 `fileId`, `originalBytes=204800`, `compressedBytes=30720`, `compressionApplied=true`, `compressionDurationMs=80`

#### Scenario: decode 失败记录 WARN 日志

- **GIVEN** AI 调用 `datatalk.file_read` 处理一张损坏 PNG
- **WHEN** Thumbnailator 抛 IOException
- **THEN** 应用日志 SHALL 出现一条 WARN 级别记录
- **AND** 记录 SHALL 包含 `compressionSkipReason="decode_failed"` 与异常 message

### Requirement: MCP image content block emission

`DataTalkMcpService.toToolResult` 在收到 action 输出包含 image data URI 时，MUST 将 MCP `content` 数组拆为 **两个** content block，以符合 MCP spec 的 `ImageContent` 约定，让 OpenCode / AI SDK 能把图片转发为 LLM provider 的 vision message part。

**Detection 规则**（同时满足才走 image 路径）：
- outcome 为 success（`isError=false`）
- output 是 `Map<String, Object>`
- output 含 `content` 字段且为 `data:image/<sub>;base64,<raw>` 字符串

**输出结构**（image 路径）：
```json
{
  "content": [
    {"type": "text", "text": "<output JSON with `content` field stripped>"},
    {"type": "image", "data": "<raw base64, NO data URI prefix>", "mimeType": "<compressedMimeType || mime from URI>"}
  ],
  "structuredContent": { /* 完整原始 output，含 content data URI */ }
}
```

**关键约束**：
1. text content 的 metadata JSON MUST NOT 包含原 `content` 字段 — 避免 base64 在 text + image 两处重复，让 prompt token 翻倍。
2. image content 的 `data` MUST 是 raw base64（**剥离** `data:image/...;base64,` 前缀），MCP spec 与 AI SDK 校验器均拒绝带前缀的形式。
3. `mimeType` 优先取 output 的 `compressedMimeType`，缺失时 fallback 到 `mimeType` 字段，再缺失则从 data URI header 解析。
4. `structuredContent` MUST 保留完整原始 output（包含 content data URI），供程序化客户端无损消费。
5. 错误响应（`isError=true`）即使含 image data URI 也 MUST NOT 拆分（防御性，避免将错误 payload 误塞到 vision 通道）。
6. 非 image 输出 MUST 保持原行为：单个 `{type:"text", text:"<serialized>"}` content block。

#### Scenario: image data URI 输出被拆为 text + image 两个 content block

- **GIVEN** AI 调用 `datatalk.file_read(fileId=xxx)`，xxx 为 PNG 图片
- **WHEN** handler 返回 `{fileId, content:"data:image/jpeg;base64,<B>", compressedMimeType:"image/jpeg", compressionApplied:true, ...}`
- **THEN** MCP `result.content` SHALL 含 2 个 element
- **AND** `content[0]` SHALL = `{type:"text", text:<JSON 含 fileId / compressionApplied / compressedMimeType，不含 `content` 字段>}`
- **AND** `content[1]` SHALL = `{type:"image", data:"<B>", mimeType:"image/jpeg"}`（`data` 不含 `data:` 前缀）
- **AND** `result.structuredContent.content` SHALL 仍然以 `data:image/jpeg;base64,` 开头（完整原始）

#### Scenario: 缺 compressedMimeType 时从 data URI 头推断 mime

- **GIVEN** output 是 `{fileId, content:"data:image/png;base64,<B>"}`（仅旧字段）
- **WHEN** MCP 包装
- **THEN** `content[1].mimeType` SHALL = `"image/png"`（从 URI header 解析）
- **AND** `content[1].data` SHALL = `<B>`

#### Scenario: 非 image action 输出保持单 text content

- **GIVEN** `datatalk.file_read` 读取 CSV，output `content` 是 `"id,name\n1,foo\n"`
- **WHEN** MCP 包装
- **THEN** `result.content` SHALL 仅含 1 个 `{type:"text"}` element
- **AND** `result.content[0].text` SHALL 包含 `"id,name"` 子串

#### Scenario: 错误响应不触发 image content 拆分

- **GIVEN** outcome 是 error，payload 含 `content:"data:image/jpeg;base64,..."`（譬如校验失败时回显输入）
- **WHEN** MCP 包装
- **THEN** `result.content` SHALL 仅含 1 个 `{type:"text"}` element
- **AND** `result.isError` SHALL = `true`
