## Context

DataTalk 当前的文件上传链路覆盖三段责任：

1. **前端**（`useFileUpload.ts`、`prompt-composer.tsx`）：附件入队、上传、删除、与 sendMessage 编排
2. **后端 REST**（`FileUploadController.java`）：multipart 接收、MIME 检测、落盘到 `~/.data-talk/uploads/<fileId>/`、`FileAnalysisService` 解析
3. **后端 MCP tool**（`FileReadActionHandler.java`）：AI 通过 `datatalk.file_read` 按需读取文件内容回传 LLM

`ChannelService.partForWire` 已经把 `FileUploadPart` 在协议层降级为 text part（含元信息 + "Use datatalk_file_read..." 提示），原因是 OpenCode 1.4.7 strict Zod 拒绝自定义字段（BUG-0056 修过）。所以**截图本体不在 message 中流动，AI 是通过 MCP tool 拉文件**。这是 BUG-0058 的现场关键 — `FileReadActionHandler.handle` 第 91-107 行对 `image/*` 直接 `readAllBytes + base64` 无压缩，导致截图 base64 超 LLM 单 tool_result 上限。

前端侧，BUG-0057 修复后已有稳定的 id-based 寻址，但 `uploadAll` 仍保留 lazy 调用语义（仅在 `submitText` 内触发），叠加串行 `for-of await` 导致 BUG-0059 的"按回车 300-600ms 空窗"卡顿感。

本 change 在不修改协议层、不引入新 capability 边界的前提下，对上述两段独立缺陷做最小化但生产可用的改造。

**Stakeholders**：

- 终端用户（最高频付费场景：截图给 AI 提问）
- 模型层（qwen3-VL via OpenCode）：消费方约束直接决定 base64 上限
- 前端/后端工程师：维护者
- BUG-0056/0057 的隐性 owner：本 change MUST NOT 回退其修复

## Goals / Non-Goals

**Goals:**

- 让 ≤2MB 原图常规截图 100% 能流转到 LLM 被消费（消除 BUG-0058）
- 回车到用户气泡上屏延迟从 300-600ms 降到 < 200ms（typical case，eager upload 已完成；消除 BUG-0059）
- 后端图片压缩对调用方可观测（output schema 透传原始/压缩字节、是否压缩、压缩后 mime）
- 前端 abort 语义对用户即时可见（删除 chip = 立即取消 inflight 上传，孤儿文件由后端清理）
- 不引入新的 protocol breaking change；OpenCode 协议、ChannelService.partForWire 不动

**Non-Goals:**

- 不做"客户端上传前压缩"（会丢失原图，影响 chat 内 file preview / download；如未来要做，作为独立 enhancement）
- 不做"image_url 替代 base64"（依赖 OpenCode 进程能 HTTP 直连 backend，部署假设过强；留作"同机部署"专属优化）
- 不做"乐观渲染用户气泡"（与 PendingFileUploadEchoRegistry 的协作复杂、失败回滚边界多、ROI 不如 eager upload）
- 不调整 50MB 上传上限；不改 `FileAnalysisService` 的分析维度
- 不修改非图片类型的 `datatalk.file_read` 路径（text 文件偏移读保持原状）

## Decisions

### D1: 后端压缩库 — Thumbnailator vs ImageIO

**选择**：引入 `net.coobird:thumbnailator:0.4.20`（Apache 2.0）

**理由**：

- JDK ImageIO 能完成 resize + JPEG 编码，但 API 啰嗦（必须自管 `BufferedImage` lifecycle、`ImageWriter` / `IIOImage`、`ImageWriteParam` 三件套）
- Thumbnailator 提供 fluent API：`Thumbnails.of(in).size(2048, 2048).outputQuality(0.85f).outputFormat("jpg").toOutputStream(out)`
- Thumbnailator 默认 progressive bilinear 重采样，比 ImageIO 默认的 `Image.SCALE_AREA_AVERAGING` 在文字 / 边缘上锐度更好
- 包体积 ~150KB、零传递依赖、上一次 release 2021，稳定无 CVE
- 替代品 imgscalr 也可，但 release 更老（2014）

**风险**：第三方依赖。Mitigation：vendored 单一 JAR、版本钉死、定期 dependabot 巡检

### D2: 压缩策略 — 阈值 + 格式映射

**选择**：

| 原 mime | 行为 | 压缩后 mime |
|---------|------|-----------|
| image/png | resize maxEdge=1024 + JPEG q=0.75 | image/jpeg |
| image/jpeg | resize maxEdge=1024 + JPEG q=0.75 重编码；若原图边均 ≤1024 且字节 ≤25KB 跳过 | image/jpeg |
| image/webp | resize maxEdge=1024 + JPEG q=0.75 | image/jpeg |
| image/bmp | resize maxEdge=1024 + JPEG q=0.75 | image/jpeg |
| image/gif | **passthrough（原样 base64）** | image/gif |

- 阈值：原图字节 ≤ 25KB → skip（已经够小，CPU 不值）
- 阈值：原图任一维度 > 16384 或像素总数 > 8192×8192 → skip（safety guard，避免 ImageIO OOM）；passthrough 时 `compressionApplied=false` + 增加 `compressionSkipReason: "oversized_source"`
- 选 JPEG 而非 WebP 作为统一输出：JPEG 在所有 LLM 视觉模型上兼容性最稳；WebP 在 qwen3-VL 早期版本曾有 decode 兼容性问题
- maxEdge=1024 + q=0.75：经过 30+ 实测矩阵采样后的"既能压缩、又 OCR 可读、又 fits OpenCode cap"的甜蜜点（详见下文「参数反算」）

**理由**：GIF 走原样是因为帧序列丢失会破坏动图语义；BMP / WebP 用户场景罕见但保持完整覆盖

**原 fix（maxEdge=2048 + q=0.85）失效根因**（2026-05-18 follow-up 才暴露）：

| 用户实际场景 | 原 fix 行为 | 输出 |
|------------|-----------|------|
| 1920×1080 PNG 161KB（BUG-0058 主报告） | 不 resize（边 ≤ 2048）→ JPEG q=0.85 | ~150KB → base64 200KB → **STILL over cap** |
| 824×569 PNG 40KB（用户 2026-05-18 复现） | 不 resize（边 ≤ 2048）→ JPEG q=0.85 | **92KB → base64 122KB → OVER cap** |

UI 截图（文字 + 色块）的 JPEG q=0.85 经常**比 PNG 大 1.5–2.5×**，因为：
1. 锐利文字边缘 → 高频 DCT 系数无法压缩
2. UI 色块的硬边 → JPEG 块效应需要更多比特保留
3. PNG 的 deflate + 滤波器对此类内容更友好

**实测参数矩阵**（用户那张 824×569 / 40KB PNG，2026-05-18）：

```
maxEdge q     JPEG     base64    fits cap?
2048   0.85  91,971   122,628    OVER  ← 原 fix
2048   0.50  69,727    92,972    OVER
1280   0.75  40,962    54,616    OVER
1024   0.85  35,803    47,740    OVER（贴边）
1024   0.75  30,095    40,128    FITS  ← 选定
1024   0.65  26,639    35,520    FITS（更保守）
 800   0.85  23,301    31,068    FITS（更保守）
```

选 maxEdge=1024 + q=0.75 是 **OCR 文字可读性 × 输出大小**的甜蜜点。再激进的话 OCR 在小字号 UI 上开始模糊，再保守就贴 cap 边缘没裕度

**阈值反算（25KB 取值依据）**：

原先实现的阈值 50KB 在二轮 dogfood 中被验出**漏报边界 case**：一张 40KB 的 PNG 截图未走压缩分支，base64 后 ~54KB，触发 OpenCode 工具调用层的**内联输出 cap**（实测 ~50KB），输出被外溢到 `~/.local/share/opencode/tool-output/<id>` 并替换为 `Output too large. Saved to file. Use Task tool to process...` 的 stub。qwen3-VL 等无 Task tool 的下游模型直接回报"无法查看二进制数据"，看不到原图任何字节（BUG-0058 回归暴露）。

| 约束链 | 数值 |
|--------|------|
| OpenCode 内联工具输出 cap（实测） | ~50,000 byte |
| FileReadActionHandler JSON wrapper（fileId/bytesRead/metadata） | ~250 byte |
| base64 编码膨胀 | × 1.333（4/3） |
| 安全裕度 | 10%（模型/版本差异） |

求解：`raw_max = (50000 − 250) × 0.9 / 1.333 ≈ 33,580 byte` → 向下取整 25KB（`25 × 1024 = 25600`），确保任何刚好低于阈值的 raw 图最终 base64 + JSON wrapper < 38KB，留 ~12KB 给跨模型/跨版本 cap 差异

**Rejected alternatives**：

- **删除 below_threshold 短路（无条件压缩）**：5KB 的 PNG icon（含 alpha 通道）重编码为 JPEG 反而可能涨到 8-15KB（JPEG header overhead + 失去透明度），且对此类小图来说几十 ms CPU 是浪费
- **按 base64 输出长度二次校验**：实现复杂（要 trial-and-error 压缩参数），收益小（25KB 阈值已能覆盖 99% 案例），引入的尾延迟反而劣化 BUG-0059 的目标

### D3: 输出 schema 兼容性 — additive

**选择**：`FileReadActionHandler.outputSchema` 新增 4 个字段，全部 optional：

```java
{
  "fileId": "string",
  "offset": "integer",
  "content": "string",
  "bytesRead": "integer",
  // ADDED:
  "originalBytes": "integer",         // 原图字节
  "compressedBytes": "integer",       // 压缩后字节
  "compressionApplied": "boolean",    // 是否实际压了
  "compressedMimeType": "string",     // 压缩后真实 mime（GIF / passthrough 时 = 原 mime）
  "compressionSkipReason": "string"   // optional, only when compressionApplied=false && image
}
```

**理由**：

- 旧 client（含已部署 OpenCode） 不读新字段 → forward compatible
- LLM prompt 不需要改 — 模型只关心 `content`
- 服务端日志可以聚合 `compressionApplied` 统计命中率

### D4: 前端 eager upload 时机

**选择**：`useFileUpload.addFiles` 内对每个新 attachment 立刻 schedule `uploadAll`（concurrency-aware）；不再依赖 `submitText` 触发

**理由**：

- 用户从 drop / paste 到按 Enter 通常 > 500ms，这段时间足够吃掉单图上传
- "立即" = 在 `setAttachments(...)` 之后的 microtask 内通过 `queueMicrotask(...)` 启动，避免在 React render 周期内发起 fetch（不会触发 effect ordering 问题）
- `uploadFile(file, sessionId, signal)` 签名加入 `AbortSignal`；前端 `fetch` 直接传入

**Alternative considered & rejected**：在 `addFiles` 内直接 `void uploadAll()` — 简单但会与"删除立即生效"的 abort 产生未明确的 ordering 风险。`queueMicrotask` 保证 setState 后再上传

### D5: 并发控制 — chunk-based Promise.all

**选择**：max-concurrent = 3，简单 chunk 实现而非引入 p-limit

**理由**：

- 多文件场景：3 张图 + 1 个 CSV 同时上传，3 并发已足够、不至于浏览器 socket 风暴
- chunk 实现 = `for (chunk of chunks(pending, 3)) await Promise.all(chunk.map(uploadOne))` — 10 行代码，无新依赖
- p-limit / p-queue 引入外部包，对 3 并发场景过度工程

### D6: 按钮多态 — 复用 disabled token，新增 loading 视觉

**选择**：前端按钮 state machine 扩展：

```
idle      → 默认态（accent.primary 背景 + ArrowUp icon）
uploading → 等价 disabled 视觉 + Loader2Icon spinner（按 DESIGN.md interaction.disabled token，不依赖颜色）
sending   → 同 uploading（按钮整体生命周期连续，用户不需区分）
streaming → 现有 destructive variant + Loader2Icon spinner（保留 abort 能力）
```

**理由**：

- DESIGN.md interaction tokens（line 282-288）只显式定义 `focusRing/hover/active/selected/disabled` 五态，没有 `loading` 命名 token
- "Motion as Confirmation"（principle line 263）→ 用 spinner 动效区分 disabled 与 loading
- `uploading`/`sending` 在用户看来都是"消息正在发"，无需 UI 区分；仅日志 / Devtools 区分

### D7: chip abort 态

**选择**：`FileAttachment` 新增 status `aborting`（短暂过渡态，AbortController.abort() 调用后到 fetch reject 之间）→ 立刻从 `attachments` 移除（不显示 aborting chip）

**理由**：

- 用户体验：点 X = 立即消失，无任何过渡
- 实现：`removeAttachment(id)` 先 `controller.abort()`，再从数组中 splice；后续 fetch reject 走 silent path（不上报 setAttachments，因为目标 id 已不在数组）
- 已知"幽灵态" — fetch 已 abort 但服务端写盘已完成 → 后端孤儿清理

### D8: 后端孤儿清理

**选择**：`FileUploadController.upload` 在 `catch (IOException e)` 与 `IOException` 的 wrap path 内删除 temp + permanent；客户端中断（典型 EOF / connection reset）被 Spring multipart parser 抛 `MultipartException`，同样走清理

**理由**：

- 客户端 abort fetch → 浏览器关闭 connection → Spring 抛 `MultipartException` 或 `IOException`
- 现有 catch 路径已删 temp，但 permanent 写完后再失败的窗口未清理。补一层 try-with-resources 或 explicit cleanup
- 不引入 background sweeper（YAGNI）— 单次清理足够

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Thumbnailator decode 大图 OOM | 解码前 read header（`ImageIO.getImageReaders` peek dimensions）拒绝 > 8192×8192；JVM heap 配置 ≥ 512MB（生产 default OK） |
| JPEG q=0.85 损伤 PNG 截图文字锐度 | A/B 比对：内部 dogfood 1 周；如反馈差则升到 q=0.9（仅 30% 体积代价） |
| 动画 GIF 漏判为静态图 → 丢动画 | mime 严格 = `image/gif` 一律 passthrough；额外回归测试覆盖 |
| eager upload + 用户立即删除的 race | `removeAttachment` 同步 `controller.abort()` + splice；fetch reject path silent；后端孤儿清理 |
| 并发上传放大短时网络风暴 | max-concurrent=3 chunk；多文件用户场景罕见 |
| 旧 client 不识别新 schema 字段 | additive only；旧 client `JsonNode.get(...)` 返 null 不崩 |
| 回退 BUG-0056/0057 | tasks.md 显式要求保留这两个 BUG 的回归测试通过 |
| ImageIO 在 Linux/macOS/Windows 行为差异 | Thumbnailator 抽象掉，单元测试用 PNG/JPEG/GIF/BMP 各 1 张 fixture 覆盖三平台 CI |
| 前端 Tauri webview 与 Chrome devtools 行为差异 | E2E 在 Tauri / web 双跑（已有的双跑 pipeline） |
| 用户上传 SVG（mime: image/svg+xml） | SVG 不走 image 分支（mime 已不在白名单），保持原 text 路径 — 与现状一致 |

## Migration Plan

**Phase 1 — Backend 压缩（独立合并）**：

1. 新增 Thumbnailator 依赖 + `ImageCompressor` 工具类（adapter 模块，单元测试就近）
2. 改 `FileReadActionHandler.handle` 图片分支为：原图字节 → ImageCompressor.maybeCompress → base64
3. 扩展 outputSchema + 实际 output map
4. 单元测试：PNG/JPEG/WebP/BMP/GIF/oversized/under-threshold 各 1 用例
5. Backend 全量 `mvn verify` 通过 → merge

**Phase 2 — Frontend eager upload（独立合并）**：

1. `uploadFile` 签名加 `AbortSignal` 参数
2. `useFileUpload`：FileAttachment.controller 字段、addFiles 触发 queueMicrotask schedule、removeAttachment abort、uploadAll concurrent chunk
3. `prompt-composer`：按钮多态、submitText 流程简化
4. vitest 覆盖：eager scheduling / abort / 并发 / 与 BUG-0057 回归共存
5. Frontend `npx tsc --noEmit` + `npx vitest` 全绿 → merge

**Phase 3 — 真实交互验证**：

1. Tauri / web 双跑：选 1920×1080 截图 → 回车 → 测量回车-气泡延迟
2. 真实 LLM：上传截图 → 确认 qwen3-VL 不再 "payload too large"，并能回答截图内容
3. 多文件上传：3 张图同时拖入 → 看并发命中 + 总耗时 < 串行

**Rollback strategy**：

- Phase 1 / 2 独立 commit；任何一段出问题单独 revert
- 后端压缩出问题：revert FileReadActionHandler.java 单文件即可（依赖保留无害）
- 前端 eager upload 出问题：revert useFileUpload + prompt-composer + file-upload.ts 三文件
- Feature flag 不引入（YAGNI，change scope 小，revert 成本低）

## Open Questions

无。所有方向用户已明确 approve "按推荐"。

## Design Inputs (Frontend Gate)

**MUST**：本 change 的前端部分严格遵守 `client/DESIGN.md` 以下约束（line ranges referencing current DESIGN.md as of `d2072bed`）：

- **Interaction tokens (DESIGN.md L282-288)**：
  - 按钮 `idle` 态：使用 `accent.primary` 背景（cobalt.700 light / 对应 dark mapping）+ `text.inverse`
  - 按钮 `uploading` / `sending` 态：等同 `disabled` 视觉（`opacity-40`、`cursor-not-allowed`），并 **MUST** 叠加 spinner 动效以满足 DESIGN.md L335 "state cannot be communicated by color alone"
  - 按钮 `streaming` 态：destructive variant（status.danger），保留 abort 入口
  - chip `pending` / `done` 态：FileChip 现有视觉无修改（已通过 BUG-0044 / BUG-0057 走过 review）
  - chip `error` 态：现有 `ring-1 ring-status-danger/40` 不动
  - chip `aborting` 态：**不渲染**（直接从 DOM 移除，无过渡动画 — 符合 DESIGN.md L263 "Motion as Confirmation"，无状态变化无需动效）
- **Accessibility (DESIGN.md L330-338)**：
  - 按钮 `uploading` / `sending` 态：`aria-disabled="true"` + 文本可访问名 SHOULD 改为 i18n `chat.composer.sendingLabel`（含 "sending" 语义，screen reader 可感知）
  - spinner 必须配合 `aria-busy="true"` 以满足"状态不靠颜色"
- **Motion (DESIGN.md L263)**：spinner 是状态确认动效，不是装饰。`prefers-reduced-motion` 用户走 dot pulse 替代（DESIGN.md L337）
- **禁用项 (DESIGN.md L349-354)**：
  - 不使用原始 primitive 颜色（如直接写 `#3B82F6`），一律走 semantic token
  - 不引入 glassmorphism 或装饰动画
  - chip 删除按钮不引入 saturated 红色（保持 `text.muted` hover → `text.base`）
