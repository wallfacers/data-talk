---
id: BUG-0058
title: datatalk_file_read 对图片直接 readAllBytes+base64，截图 ~161KB 即触发"payload 太大"，超出 OpenCode / qwen3-VL 单 tool_result 上限
status: verified
priority: P1
source: manual-report
modules: [file-upload, opencode, chat]
discovered: 2026-05-17
discoveredBy: human
testRunId: null
fixCommit: pending
fixPlanRef: openspec/changes/optimize-file-upload-image-and-latency/
duplicateOf: null
regression: false
---

## Summary

`FileReadActionHandler`（`datatalk.file_read` MCP tool）对 `mime` 以 `image/` 开头的上传文件直接 `Files.readAllBytes` 后 `Base64.getEncoder().encodeToString(...)`，无任何 resize / 重编码 / 压缩。一张 161KB 的 PNG 截图编码后约 215KB（base64 固定 +33% 开销），加上 `data:image/png;base64,` 前缀，整体写入 tool_result 的 `content` 字段返回给 OpenCode → LLM。在 qwen3-VL 走 OpenCode 转发的路径上，这个单次 tool_result 体积已经触发"payload 太大"被拒，AI 无法消费截图内容。

## Reproduction Steps

1. 启动后端 + 前端
2. 任一 session 上传一张 PNG 截图（≥100KB；典型分辨率 1920×1080 的桌面截图）
3. 文本输入 "这是什么" → 回车
4. 用户气泡渲染含图片 chip（OK，前端不受影响）
5. AI 决定调用 `datatalk_file_read(fileId=xxx)`
6. 后端返回 `content` = `data:image/png;base64,<~215KB 字符串>`
7. OpenCode / qwen3-VL 拒绝该 tool_result（"payload too large" 或同义错误），AI 无法看图

## Expected vs Actual

- **Expected**：常见尺寸的截图（≤2MB 原图）SHALL 能完整流转到 LLM，AI 能基于截图内容作答
- **Actual**：~161KB PNG 截图即被拒，base64 后体积 ~215KB 即超出单 tool_result 上限

## Environment

- Backend commit: `d2072bed` (develop)
- Frontend commit: `d2072bed` (develop)
- OS / Browser: WSL Ubuntu / Tauri webview
- LLM: Qwen3 VL（具体 modelID 由用户 ModelPicker 选择）经 OpenCode 转发
- Data source: N/A

## Evidence

用户报告原文（2026-05-17）：

> 这张截图文件仍然太大（PNG 约 161KB，base64 编码后超过 215KB）

代码现场 — `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/FileReadActionHandler.java:91-107`：

```java
// Binary read path for image files — return base64 data URI, ignore offset/limit
String mimeType = uploaded.mimeType();
if (mimeType != null && mimeType.startsWith("image/")) {
    try {
        byte[] allBytes = Files.readAllBytes(path);
        String encoded = Base64.getEncoder().encodeToString(allBytes);
        String dataUri = "data:" + mimeType + ";base64," + encoded;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", fileId);
        out.put("offset", 0);
        out.put("content", dataUri);
        out.put("bytesRead", allBytes.length);
        return out;
    } catch (Exception e) {
        return errorResult("Failed to read image file: " + e.getMessage());
    }
}
```

零压缩、零 resize、零格式优化 — 任何 mime 以 `image/` 开头的文件都按原始字节走 base64。

## Root Cause

`FileReadActionHandler.handle` 对图片走一条专门的"binary base64"分支，直接读全量原始字节再 base64。该实现的隐含假设是"上传图片体积一定可控"，但实际：

1. **PNG 是无损格式**，桌面截图（4K / Retina）单图常驻 1–5MB；即使是普通 1080p 截图也常 100–500KB
2. **Base64 固定膨胀 +33%**（每 3 字节编 4 字符），161KB → ~215KB
3. **OpenCode 协议层** 单条 tool_result 的 `content` 字段虽无显式上限，但下游 LLM API（qwen3-VL DashScope、OpenAI 兼容端点等）对单 message / 单 tool_result 有体积上限（典型 ~200KB-1MB 量级），且 token 占用按 base64 字符串计费，1KB 图片 ≈ 350 tokens，215KB ≈ 75K tokens，超出常规 context window
4. **截图场景对画质不敏感**：UI 截图主要是文字 / 图标 / 配色，JPEG q=85 + max-edge 2048 的有损压缩对识别精度几乎无影响

下游连锁：`datatalk_file_read` 返回的 tool_result 被 OpenCode 转发 → qwen3-VL 拒绝 → AI 无法基于截图回答，用户体感是"AI 看不见我发的图"。

注意：上传链路本身（`/api/files/upload`）有 50MB 限制，原图能成功落盘；问题完全发生在 AI 消费阶段。

## Fix (applied 2026-05-17, status=fixed)

通过 OpenSpec change `optimize-file-upload-image-and-latency` 落地方案 A — 在 `FileReadActionHandler` 内引入透明压图：

### 代码改动清单

- `server/data-talk-adapter/pom.xml` — 新增依赖 `net.coobird:thumbnailator:0.4.20`（Apache 2.0，~150KB，零传递依赖）
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/util/ImageCompressor.java` — 新建工具类。`CompressionResult maybeCompress(Path, mimeType)` 返回 record，含 `bytes / mimeType / applied / skipReason / durationMs`。策略：
  - PNG / JPEG / WebP / BMP → `Thumbnails.of(...).size(2048, 2048).outputQuality(0.85f).outputFormat("jpg")` 重编码为 JPEG
  - GIF → passthrough，保留动画帧（`skipReason="animated_passthrough"`）
  - 字节 ≤ 50KB → passthrough（`skipReason="below_threshold"`），避免无谓 CPU
  - header peek 维度 > 16384 边 或 > 8192×8192 像素 → passthrough（`skipReason="oversized_source"`），防 ImageIO OOM
  - 解码异常 → 回退原字节（`skipReason="decode_failed"`），不让请求整体失败
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/FileReadActionHandler.java`:
  - image 分支由 `Files.readAllBytes + Base64.encodeToString` 改为 `ImageCompressor.maybeCompress(path, mimeType)` → base64
  - `outputSchema()` additive 扩展 5 个 optional 字段：`originalBytes / compressedBytes / compressionApplied / compressedMimeType / compressionSkipReason`
  - 接入 SLF4J 结构化日志：INFO 记录命中率指标，WARN only `decode_failed`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileUploadController.java`:
  - `Files.move` 失败 catch 路径补 cleanup（删 fileDir 整体，避免 permanent 半写入孤儿）
  - `FileAnalysisService.analyze` 失败路径加注释说明 permanent 保留原因（DB row + UNKNOWN-type fallback）

### 测试改动

- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/util/ImageCompressorTest.java` — 新增 8 个用例（PNG/JPEG/WebP routing/BMP/GIF/corrupt/below-threshold/oversized；含 < 1s 安全门延迟断言）。fixtures 由 ImageIO 程序生成到 `@TempDir`（不写 `src/test/resources/`），保证 hermetic + 跨平台
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/FileReadActionHandlerTest.java` — 新增 3 个 image 场景（大 PNG 压缩成 JPEG / GIF passthrough / 小 PNG below_threshold），并对现有 2 个 image 用例补元字段断言；保留所有 text 用例不变；新增 `doesNotContainKey` 断言守护 text 路径未受影响

### 跳过的 task

- task 3.3（FileUploadControllerIT multipart IOException 用例）— 平台特定权限操作（POSIX chmod vs Windows ACL vs WSL）会让 IT flaky 且维护成本高。改为建议手测验证

## Verification

```bash
# Backend 全模块编译 + 全测试
cd server && mvn install -pl data-talk-adapter -am -DskipTests
cd server && mvn verify
# 结果：BUILD SUCCESS, 5/5 modules (domain / application / infrastructure / adapter), 总 4m53s

# 焦点测试单独跑（确认压缩路径行为正确）
cd server && mvn -pl data-talk-adapter test -Dtest='ImageCompressorTest,FileReadActionHandlerTest'
# 结果：Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
```

BUG-0056 / BUG-0057 守护测试 grep 验证：
- `ChannelServiceTest:148`（BUG-0056 reproduces）、`OpenCodeEventLoopTest:276` 全 pass
- 上述 mvn verify 包含全 BUG-0056 / BUG-0057 守护用例的回归 — 全绿、无 regression

真实交互验证（task 11.1-11.6）待用户手测，覆盖：
1. 上传一张 1920×1080 截图 → 询问截图内容 → qwen3-VL 正确返回内容描述（不再"payload too large"）
2. 后端 log 出现 `INFO [file-read] image compression: ... applied=true compressedBytes < originalBytes`
3. 动画 GIF：log 出现 `compressionApplied=false skipReason=animated_passthrough`

## Notes

- 与 [BUG-0057](BUG-0057-composer-attachment-stuck-uploading-button-locked.md) / [BUG-0056](BUG-0056-bubble-attachments-file-upload-part-not-roundtripped.md) 是同一上传链路的不同断点：56 修了 part 协议层降级、57 修了前端 state 引用，本 BUG 修服务端 base64 输出体积。
- **不修方向**：客户端上传前压缩被排除，因为会丢失原图（用户在 chat 中点开附件预览/下载时需要原图）。如确要做"双份（原图 + 压缩版给 LLM）"，留作后续 enhancement。
- **不修方向**：返回 `image_url` 而非 base64 被排除，因为部署形态多样（Tauri 桌面 + 后端可能容器化），OpenCode 进程未必能 HTTP 直连 backend；该方案留作未来"backend 与 OpenCode 同机/同网"的部署模式专属优化。
- 关联 change：`openspec/changes/optimize-file-upload-image-and-latency/`

## Follow-up (2026-05-18)

第一轮"修复"（`ImageCompressor` + 50KB `SIZE_THRESHOLD_BYTES` + `MAX_EDGE=2048` + `JPEG_QUALITY=0.85`）写入 status=fixed 后，用户手测一张 824×569 / 40,678 byte 的 PNG 截图，**暴露原 fix 对真实 UI 截图场景从未实际工作过**。两轮迭代调查：

### 第一轮调查（2026-05-18 早）：阈值假设错

- 现象：40KB PNG → 因 ≤ 50KB 走 `skipReason="below_threshold"` passthrough → base64 后 54,501 byte → 触发 **OpenCode 工具层内联输出 cap（实测 ~50KB）**，输出被外溢到 `~/.local/share/opencode/tool-output/<id>`，AI 拿到的 stub 是 `Output too large (52KB). Full output saved to: ... Use Task tool to process...` → qwen3-VL 等无 Task tool 的下游模型回报"无法查看二进制数据"
- 当时结论：阈值 50KB 太高，反算后改 25KB（OpenCode cap 50KB − JSON wrapper 250B − base64 ×1.333 − 10% 裕度 ≈ 33.5KB → 取 25KB）

### 第二轮调查（2026-05-18 晚）：原 fix 整体失效

- 反向验证 25KB 阈值修复时，把用户那张 40KB PNG 喂给 `ImageCompressor`，得到惊人结果：

  ```
  原 PNG 40,678 byte (824×569)
  applied=true, mime=image/jpeg
  Compressed JPEG: 91,971 byte  ← 比 PNG 大 2.3×
  Base64: 122,628 byte           ← 仍远超 OpenCode 50KB cap
  ```

- **根因**：用户截图 824×569 < `MAX_EDGE=2048`，**完全不触发 resize**；只走 JPEG q=0.85 重编码。UI 截图（文字 + 色块）的 JPEG q=0.85 经常**比 PNG 大 1.5–2.5×**，因为锐利文字边缘的高频 DCT 系数无法压缩
- **追溯**：BUG-0058 主报告的 1920×1080 / 161KB PNG 也是同样情况：1920×1080 ≤ 2048 → 不 resize → JPEG q=0.85 仍 ~150KB → base64 ~200KB → **仍 OVER cap**。**原 fix 提交时只通过了 mvn 测试（mosaic fixture, 4K 源能强制 resize），从未在真实 UI 截图场景下成功**
- **30+ 参数矩阵实测**（用户那张 824×569 / 40KB PNG）：

  | maxEdge | quality | JPEG byte | base64 byte | fits ~50KB cap? |
  |---------|---------|-----------|-------------|----------------|
  | 2048 | 0.85 | 91,971 | 122,628 | **OVER** ← 原 fix |
  | 2048 | 0.50 | 69,727 | 92,972 | OVER |
  | 1280 | 0.75 | 40,962 | 54,616 | OVER |
  | 1024 | 0.85 | 35,803 | 47,740 | OVER（贴边） |
  | **1024** | **0.75** | **30,095** | **40,128** | **FITS ← 二次修复选用** |
  | 1024 | 0.65 | 26,639 | 35,520 | FITS（更保守） |
  | 800 | 0.85 | 23,301 | 31,068 | FITS（更保守） |

### 二次修复（同 change `optimize-file-upload-image-and-latency` task batch 14）

| 常量 | 原 | 改 | 理由 |
|------|---|---|------|
| `SIZE_THRESHOLD_BYTES` | 50KB | **25KB** | 反算 OpenCode cap，确保 raw passthrough 路径输出 base64 ≤ 33KB |
| `MAX_EDGE` | 2048 | **1024** | 强制对典型 UI 截图（800–1280 wide）真正 downscale，使 JPEG 编码有效 |
| `JPEG_QUALITY` | 0.85 | **0.75** | 体积小 30-40%，OCR 文字可读性无可感差异 |

新增 `mediumPng_inOpenCodeTruncationDangerZone_isCompressed` 守护用例（`writeRealisticScreenshot(1000, 700)` 真实 UI 截图风格 fixture，断言 base64 < 40KB）防止再次回归。

### 第三轮调查（2026-05-18 夜）：1920×1080 仍 OVER cap，用户 task 11.4 是假阳性

二次修复（maxEdge=1024 + q=0.75）后用户标 task 11.4 「1920×1080 fixture → qwen3.6-plus 正确返回」并把 BUG status 改为 `verified`。但第二天用户再发现 AI 回答与真实图片不一致。重新排查日志：

- 在 `~/.local/share/opencode/tool-output/` 找到新生成的 truncation 文件 `tool_e3702abfe00152TdbIkYBrsPt5`（**65,176 byte**），时间戳与 task 11.4 当时上传一致
- 文件 metadata 显示 `originalBytes=58764`、`compressedBytes=48715`、`compressionApplied=true`、`compressedMimeType=image/jpeg` — 即**二次修复的代码确实在跑**，1024 + 0.75 也确实压缩了，但**1920×1080 PNG → 800×450 → 48KB JPEG → base64 65KB → 仍 OVER OpenCode ~50KB cap**
- 即 task 11.4 当时 AI 实际拿到的是 truncation stub（"Output too large, saved to file, use Task tool..."），AI 在没有图的情况下"瞎说"了一通通用 dashboard 描述。用户没有交叉验证 AI 回答与图片的具体内容是否对得上，错误地标了 pass

**16 组矩阵实测**（用户 1920×1080 dashboard fixture，58,764 byte）：

| maxEdge | quality | JPEG byte | base64 byte | fits ~50KB cap? |
|---------|---------|-----------|-------------|----------------|
| 1024 | 0.75 | 48,715 | 64,956 | **OVER** ← 二次修复失败 |
| 896 | 0.75 | 38,710 | 51,616 | OVER |
| 896 | 0.65 | 31,931 | 42,576 | FITS（贴边） |
| **800** | **0.75** | **30,170** | **40,228** | **FITS ← 第三次选用** |
| 800 | 0.65 | 25,948 | 34,600 | FITS（更保守） |
| 700 | 0.75 | 24,948 | 33,264 | FITS（更保守） |

### 三次修复（同 change task batch 15）

| 常量 | 二次后 | 三次改 | 理由 |
|------|--------|--------|------|
| `MAX_EDGE` | 1024 | **800** | 1024 在 1920×1080（主流分辨率）上仍出 65KB base64；800 让 1920×1080 → 800×450 → 40KB base64 fits |

q=0.75 / SIZE_THRESHOLD_BYTES=25KB 保持不变。视觉验证：1920×1080 dashboard fixture 经 800 + 0.75 压缩后，order/amount 数字仍清晰可识别。

### 教训

1. **「fixed」≠「verified」**：第一轮和第二轮都通过了单元测试 + mosaic fixture 测试，但都没在真实截图上跑通。今后跨 LLM tool_result 体积/格式约束的修复，**必须在真实文件上端到端验证 AI 回答与图片内容的具体匹配性**，不能只看"AI 给出了 plausible 描述"
2. **检查日志中的 truncation 文件**：`~/.local/share/opencode/tool-output/<id>` 是 OpenCode 在内联输出超限时的外溢路径；任何 file_read 调用后都应该 grep 这个目录确认没有生成新 stub
3. **status=`verified` 也可以回退**：用户已经把 BUG status 改为 verified，但实测发现仍有缺陷。frontmatter 暂保持 `verified` 让用户再确认本轮修复

frontmatter `status: verified` 由用户手测后最终确认；fixCommit 在最终 commit 后回填合并 commit 的 short SHA。
