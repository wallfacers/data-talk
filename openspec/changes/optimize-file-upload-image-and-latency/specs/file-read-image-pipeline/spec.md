## ADDED Requirements

### Requirement: 图片附件压缩策略

`datatalk.file_read` MCP tool 在 `mimeType` 以 `image/` 开头时 SHALL 根据下表执行透明压缩，结果再 base64：

| 原 mime | 压缩动作 | 输出 mime |
|---------|---------|----------|
| `image/png` | resize maxEdge=1024 + JPEG q=0.75 重编码 | `image/jpeg` |
| `image/jpeg` | resize maxEdge=1024 + JPEG q=0.75 重编码 | `image/jpeg` |
| `image/webp` | resize maxEdge=1024 + JPEG q=0.75 重编码 | `image/jpeg` |
| `image/bmp` | resize maxEdge=1024 + JPEG q=0.75 重编码 | `image/jpeg` |
| `image/gif` | passthrough（原字节直接 base64） | `image/gif` |

resize SHALL 保持宽高比；若原图任一边 ≤ 1024，则 MUST NOT 放大（仅可能缩小或保持原尺寸）。

**实测背书**：典型 UI 截图（含密集文字 + 色块）在源宽 824–1280px 时，若不强制 downscale，JPEG q=0.85 输出常 1.5–2.5× 大于源 PNG（实测 824×569 PNG 40,678 byte → JPEG q=0.85 91,971 byte）。maxEdge=1024 + q=0.75 是经过 30+ 实测矩阵采样后确定的"既能压缩，又 OCR 可读，又 fits OpenCode cap"的参数组合。

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
