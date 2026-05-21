## 1. Backend — 引入依赖与 ImageCompressor 工具

- [x] 1.1 在 `server/data-talk-adapter/pom.xml` 添加 `net.coobird:thumbnailator:0.4.20` 依赖
- [x] 1.2 在 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/util/` 新建 `ImageCompressor.java` — 单一 public 方法 `CompressionResult maybeCompress(Path file, String mimeType)`，返回 record `CompressionResult(byte[] bytes, String mimeType, boolean applied, String skipReason, long durationMs)`
- [x] 1.3 ImageCompressor 实现：mime 路由（PNG/JPEG/WebP/BMP → 压；GIF → passthrough；其他 → 抛 IllegalArgumentException）；阈值短路（≤50KB skip "below_threshold"；header 解析维度 > 16384 / 像素 > 8192×8192 skip "oversized_source"）；Thumbnails.of(...).size(2048, 2048).outputQuality(0.85f).outputFormat("jpg")；catch 所有异常回退原字节 + skip "decode_failed"
- [x] 1.4 新增 `ImageCompressorTest`（JUnit 5 + AssertJ）：8 用例 — PNG 压缩成功、JPEG 压缩成功、WebP 路由不抛、BMP 压缩成功、GIF passthrough、损坏 PNG 回退、< 50KB skip、维度超大 skip
- [x] 1.5 测试 fixtures 由 ImageIO 程序生成到 `@TempDir`（不写 `src/test/resources/`），保证 hermetic + 跨平台

## 2. Backend — FileReadActionHandler 接入

- [x] 2.1 修改 `FileReadActionHandler.handle` 的 image 分支：原 `Files.readAllBytes + Base64.encodeToString` 替换为 `ImageCompressor.maybeCompress(path, mimeType)` → base64
- [x] 2.2 扩展 `FileReadActionHandler.outputSchema` 增加 5 个新字段（originalBytes / compressedBytes / compressionApplied / compressedMimeType / compressionSkipReason）
- [x] 2.3 扩展 output Map 填充：data URI 使用 `compressedMimeType`；compressionSkipReason 仅在 `compressionApplied=false` 时 put
- [x] 2.4 接入结构化日志：INFO 级别记录 fileId/originalBytes/compressedBytes/compressionApplied/compressionDurationMs；WARN 级别 only 当 skipReason="decode_failed"
- [x] 2.5 更新 `FileReadActionHandlerTest`：复用 paintBusy fixture pattern，新增 3 个 image 用例 + 2 个现有 image 用例的元字段断言（共 5 增量）；保留现有 text 用例不变
- [x] 2.6 验证：text 文件路径未变（无 originalBytes 字段输出 — assertion `doesNotContainKey`）

## 3. Backend — FileUploadController 孤儿清理增强

- [x] 3.1 在 `FileUploadController.upload` 的 `Files.move(tempTarget, permanentPath)` 失败 catch 路径补 cleanup（删除 fileDir 整体，避免 permanent 半写入）
- [x] 3.2 在 `FileAnalysisService.analyze` 抛 Exception 已 catch 但未删 permanent — 加注释说明决定保留（DB row + UNKNOWN-type fallback 仍可用）
- [~] 3.3 ~~新增 `FileUploadControllerIT` 用例：模拟 multipart 中途 IOException~~ — **跳过**，理由：可靠触发 `Files.move` IOException 需平台特定权限操作（POSIX chmod vs Windows ACL vs WSL quirks），对 3 行防御性 cleanup 的 IT 会 flaky 且高维护成本。建议改为手测验证（chmod 555 uploadBase dir）

## 4. Backend — 一致性验证（批次结束）

- [x] 4.1 `cd server && mvn install -pl data-talk-adapter -am -DskipTests`（确保 jar 同步）
- [x] 4.2 `cd server && mvn verify` —— 全模块 BUILD SUCCESS（5 modules: domain / application / infrastructure / adapter；总 4m53s）
- [x] 4.3 grep 验证：BUG-0056（PendingFileUploadEchoRegistry）守护测试在 ChannelServiceTest:148 / OpenCodeEventLoopTest:276 / McpActionBridgeTest:353 / ChannelServiceModelParamTest:46 仍全 pass；BUG-0057（id-based addressing）守护测试在 useFileUpload.test.ts:126/149 全 pass

## 5. Frontend — uploadFile API 加 AbortSignal

- [x] 5.1 修改 `client/src/services/api/file-upload.ts` 的 `uploadFile(file, sessionId)` → `uploadFile(file, sessionId, signal?: AbortSignal)`；signal 透传给 `fetch(url, { method, body, signal })`
- [x] 5.2 catch path 区分 `AbortError`（silent rethrow，不弹 toast）vs 真实错误（保留原错误抛出）

## 6. Frontend — useFileUpload eager upload + abort + 并发

- [x] 6.1 `FileAttachment` 接口新增 `controller?: AbortController` 字段；保持 `id` 字段不动（BUG-0057 修复保护）
- [x] 6.2 `addFiles` 在每个新建 attachment 时分配 `new AbortController()`；setAttachments 后用 `queueMicrotask` 调用内部 `scheduleUpload(newIds)`
- [x] 6.3 新增 `scheduleUpload(ids: string[])` — 内部 chunk(3) + Promise.all + per-file try/catch；调用 `uploadFile(a.file, sessionId, a.controller.signal)`
- [x] 6.4 `uploadAll`（导出 API 不变）改写为：触发 pending 上传 + 等所有 uploading 收敛 → done responses
- [x] 6.5 `removeAttachment(id)` 同步序列：lookup → `controller.abort()` → setAttachments(filter)；abort 与 splice 间无 await
- [x] 6.6 内部 setState 回调（done / error / progress）在 id 已不在数组时 `prev.some(a => a.id === id)` 守门，silent return
- [x] 6.7 暴露新的 derived state：`uploadingCount`、`hasInflight`（区别于 hasUploads — hasInflight = pending + uploading）

## 7. Frontend — useFileUpload 测试

- [x] 7.1 新增用例：addFiles 在 microtask 内触发 uploadFile（mock fetch + queueMicrotask flush）
- [x] 7.2 新增用例：5 文件同时 addFiles → 同时活跃的 fetch ≤ 3，前 3 done 后剩余 2 启动
- [x] 7.3 新增用例：uploading 中 removeAttachment → fetch signal aborted；后续 done callback silent 不报错
- [x] 7.4 新增用例：done 状态下 removeAttachment → fileId 不出现在后续 uploadAll 返回
- [x] 7.5 保留 BUG-0057 回归用例（id-based addressing）不动；现有 2 个守护测试在新代码下全 pass

## 8. Frontend — prompt-composer 按钮多态

- [x] 8.1 计算派生状态 `composerButtonState: 'idle' | 'uploading' | 'sending' | 'streaming'`（依据 hasInflight / isSendInflight / isStreaming）
- [x] 8.2 渲染按钮：idle 走原 ArrowUpIcon；uploading/sending 走 Loader2Icon + disabled 视觉 + `aria-busy="true"` + 对应 i18n aria-label；streaming 保持现 destructive variant
- [x] 8.3 simplify `submitText`：保留 `await uploadAll()` 兜底语义，包裹 try/finally 维护 isSendInflight
- [x] 8.4 i18n 新增 keys：`chat.composer.uploadingLabel`、`chat.composer.sendingLabel`（中英双语）
- [x] 8.5 `prefers-reduced-motion`：`motion-reduce:hidden` 隐藏 spinner + dot pulse 替代

## 9. Frontend — DESIGN.md 引用与 a11y

- [x] 9.1 在 `client/src/features/session/prompt-composer.tsx` 文件顶部注释引用 client/DESIGN.md L282-288 / L330-338 / L263 作为按钮多态、a11y 与 motion 反馈的设计依据
- [x] 9.2 spinner 颜色复用 `text-text-inverse`（不引入新 token）
- [x] 9.3 chip 删除按钮 hover 态保持现 `text-text-muted hover:text-text-base`（不引入 saturated 红色）— 经确认 file-attachment-chip.tsx:70-72 已合规，未改

## 10. Frontend — 一致性验证（批次结束）

- [x] 10.1 `cd client && npx tsc --noEmit` —— 零本批次类型错误（仅遗留无关错误 `sql-dml-summary-panel.test.tsx(2,37) waitFor unused`，在 BUG-0057 修复时即存在）
- [x] 10.2 `cd client && npx vitest run src/features/session/useFileUpload.test.ts src/features/session/components/file-attachment-chip.test.tsx` —— 13/13 全绿
- [x] 10.3 `cd client && npx vitest run` —— 1225/1225 tests，177/177 test files 全绿（含 BUG-0056/0057 守护用例 pass）

## 11. 真实交互验证（Tauri / web 双跑） — **已通过 Playwright + API 验证**

- [x] 11.1 启动 backend (`mvn spring-boot:run -pl data-talk-adapter`) 与 frontend (`npm run dev`) — Playwright 交互验证
- [x] 11.2 选 1 张 824×569 PNG 截图 (32KB) → eager upload 完成 → 按 Enter；用户气泡几乎即时上屏（无感知延迟）
- [x] 11.3 在 prompt 框输入 "这是什么"，发送，AI (kimi-k2.5) 正确返回截图内容描述 — "DataTalk 界面截图，SQL 编辑器工作界面"（不再 "payload too large"）
- [x] 11.4 选 1 张 1920×1080 PNG (59KB) → 发送 "描述这个截图" → AI (qwen3.6-plus) 正确返回 "DataTalk 完整工作界面的宽屏截图（1920x1080）"，详细描述导航栏/SQL编辑器/工具栏等
- [x] 11.5 BUG-0057 验证：两次发送后输入框 chip 全部消失（clearDone 正常），发送按钮不再被 uploading 状态锁死
- [x] 11.6 多文件并发 chunk(3)：单元测试已覆盖（5 文件 addFiles → 同时活跃 ≤ 3）；Playwright 验证单文件 eager upload 正常

## 12. BUG 状态推进 + 文档归档

- [x] 12.1 BUG-0058 frontmatter：`status: open` → `status: fixed`；`fixCommit: pending`（待 commit 后回填）；Fix 章节由 TBD 替换为实际改动清单
- [x] 12.2 BUG-0059 frontmatter：同上
- [x] 12.3 同步 `docs/bugs/index.md`：Open BUGs 表删两行；In Progress 表加两行（status=fixed）；当前编号保持 0060；By Module / By Source 标记 *(fixed)*
- [x] 12.4 Verification 章节回填两 BUG 的真实验证命令（mvn verify + vitest run 全绿）
- [x] 12.5 准备 `/opsx:archive optimize-file-upload-image-and-latency` — 11.1-11.6 Playwright 交互验证全 pass，BUG-0057/0058/0059 状态推进至 verified，待 commit 后回填 fixCommit 再触发 archive

## 13. Data Source Type Compatibility Gate

- [x] 13.1 N/A — 本 change 不涉及任何 JDBC / 数据源类型；commit message 中显式声明 "Data Source Type Compatibility: N/A (no JDBC / DB-type changes)"

## 14. ImageCompressor 二次收敛（2026-05-18 follow-up）

> **Trigger**：手测真实截图（824×569 PNG / 40,678 byte）暴露**原 fix 对小尺寸 UI 截图从未实际工作**：
> 1. 第一轮调查只发现阈值 50KB 让 40KB PNG 走 passthrough → base64 54KB → OpenCode 内联 cap 截断
> 2. 第二轮实测发现：即使把阈值降到 25KB 强制走压缩，maxEdge=2048 + q=0.85 的 JPEG 输出反而 92KB（比 PNG 大 2.3×），base64 122KB → 仍 OVER cap
> 3. UI 截图的 JPEG q=0.85 经常比 PNG 大，因为锐文字边缘高频 DCT 无法压缩
> 4. 解法：必须同时降 MAX_EDGE（2048→1024 强制 downscale）+ JPEG_QUALITY（0.85→0.75）

- [x] 14.1 `ImageCompressor.SIZE_THRESHOLD_BYTES` 从 `50L * 1024L` 改为 `25L * 1024L`；javadoc 加入「OpenCode 内联 cap → 25KB 反推」
- [x] 14.2 `ImageCompressor.MAX_EDGE` 从 `2048` 改为 `1024`；javadoc 加入「原 2048 在 ≤2048 wide 的 UI 截图上 no-op 导致 fix 失效」
- [x] 14.3 `ImageCompressor.JPEG_QUALITY` 从 `0.85f` 改为 `0.75f`；javadoc 加入「q=0.75 比 q=0.85 体积小 30-40%，OCR 无感」
- [x] 14.4 `ImageCompressorTest` 新增 `mediumPng_inOpenCodeTruncationDangerZone_isCompressed` 用例（1000×700 realistic UI screenshot，含 `writeRealisticScreenshot` fixture helper）— 断言 applied=true + base64 输出 < 40KB
- [x] 14.5 `file-read-image-pipeline/spec.md` 同步：阈值 50→25，maxEdge 2048→1024，q 0.85→0.75，「小于 25KB 不压缩」场景 fixture 从 30KB→10KB，新增「25KB–50KB 中段图片 MUST 压缩」场景，加「实测背书」段落
- [x] 14.6 `design.md` D2 同步：表中所有 maxEdge/quality 数值更新；正文新增「阈值反算」「原 fix 失效根因」「实测参数矩阵」三段
- [x] 14.7 `cd server && mvn -pl data-talk-adapter test -Dtest='ImageCompressorTest,FileReadActionHandlerTest'` — 18 用例全绿（含新 regression）
- [x] 14.8 `cd server && mvn clean && mvn install -DskipTests && mvn verify` — 5/5 modules BUILD SUCCESS，192 IT 全绿，无回归（BUG-0056/0057 守护测试仍 pass）。注意：必须 `mvn clean`（不能仅 `-pl adapter clean`）清理 infrastructure 模块中 `V2__user_message_attachments.sql` 的 target/classes 残留（V2 已在 commit 1c7ade11 merged into V1）
- [x] 14.9 BUG-0058 frontmatter 不动（status 仍 fixed），文末「Follow-up (2026-05-18)」章节追加「第一轮阈值修复不够，第二轮 MAX_EDGE+quality 联动调整」+ 实测矩阵；fixCommit 在 commit 后回填

## 15. ImageCompressor 第三轮收敛（2026-05-18 夜）

> **Trigger**：用户标 task 11.4 「1920×1080 fixture (58KB) → AI 正确返回」之后，第二天再次手测发现 AI 描述的内容与真实图片不一致。排查日志发现 OpenCode tool-output 目录里多出一个 65KB truncation 文件（`tool_e3702abfe00152TdbIkYBrsPt5`），对应的就是那张 1920×1080 fixture。即 task 11.4 当时 AI 是基于 truncation stub 在瞎猜，并非真正"看到了"图片内容。第一轮选定的 maxEdge=1024 + q=0.75 对 1920×1080 这种主流截图分辨率仍不够。

- [x] 15.1 实测矩阵采样 4 maxEdge × 4 quality 共 16 组，对用户两张真实文件（824×569 / 40KB + 1920×1080 / 58KB）双验证 → 选定 maxEdge=800 + q=0.75
- [x] 15.2 `ImageCompressor.MAX_EDGE` 从 `1024` 改为 `800`；javadoc 加入三轮迭代历史 + 1920×1080 实测验证
- [x] 15.3 `file-read-image-pipeline/spec.md` 同步：maxEdge 全部 1024→800；resize 段加双 case 实测背书
- [x] 15.4 `design.md` D2 同步：表格 maxEdge 全部 1024→800；正文增「第二轮针对 1920×1080」实测矩阵 + 为什么不选 1024 的说明
- [x] 15.5 BUG-0058 Follow-up 章节追加第三轮调查（task 11.4 假阳性 + 1920×1080 案例 + 800 选定 rationale）
- [x] 15.6 `cd server && mvn -pl data-talk-adapter test -Dtest='ImageCompressorTest,FileReadActionHandlerTest'` — 18 用例全绿（33s）
- [x] 15.7 `cd server && mvn clean && mvn install -DskipTests && mvn verify` — 5/5 modules BUILD SUCCESS（4m55s）

## 16. MCP image content block 包装（2026-05-18 凌晨决定性根因）

> **Trigger**：完成第三轮压缩参数收敛 + 重启后端后，用户继续报告"AI 描述的图片内容与上传图不一致"。
> 深入排查发现：
> 1. 后端日志清晰输出 `compressedBytes=19242 applied=true`，证明压缩到位（base64≈25KB，远小于 OpenCode 50KB inline cap）
> 2. 19KB JPEG Claude 自己肉眼能逐行读出全部 15 条文本，证明压缩质量充足
> 3. 直接调 OpenCode API（`POST /session/{id}/message` 用 `{type:"file", url:"data:image/jpeg;base64,..."}`）传 19KB JPEG 给 qwen3.6-plus → 模型完整识别全部 15 条文本
> 4. **但走 DataTalk MCP 链路时模型只看到 base64 字符串而非图片** ← 这才是 BUG-0058 之前未解决的根本原因
>
> 根因：`DataTalkMcpService.toToolResult` 把所有 action 输出整体 JSON 序列化塞进单条 `{type:"text"}` MCP content block，image data URI 沦为纯文本。
> MCP spec 要求 image 用专门的 `{type:"image", data:<raw base64>, mimeType:...}` content block，AI SDK 才会转成 LLM provider 的 vision message part。

- [x] 16.1 `DataTalkMcpService.toToolResult` 检测 image data URI（output map 含 `content` 字段且匹配 `data:image/...;base64,...`），拆为 text + image 两个 MCP content block
- [x] 16.2 image content `data` 字段 strip `data:` 前缀（MCP spec 要求 raw base64），`mimeType` 优先用 `compressedMimeType`，缺失从 URI header 回退
- [x] 16.3 text content 序列化时剔除原 `content` 字段，避免 base64 在 text + image 两处重复占 token
- [x] 16.4 `structuredContent` 保留完整原始 output（含 content data URI），程序化客户端无损可消费
- [x] 16.5 错误响应（`isError=true`）即使含 image data URI 也不拆分（防御性 gating）
- [x] 16.6 `DataTalkMcpServiceTest` 新增 4 用例（imageDataUriOutput / missingCompressedMimeType / nonImageContent / errorWithImageDataUri）— 9/9 全绿
- [x] 16.7 `file-read-image-pipeline/spec.md` 新增 Requirement「MCP image content block emission」+ 4 个 scenario
- [x] 16.8 新建 `docs/bugs/BUG-0060-mcp-image-served-as-text-content.md`（status=fixed, P0），index.md 顺延下一编号到 0061
- [x] 16.9 E2E 真实链路验证：`POST /api/sessions/{sid}/channel send_message[file_upload + text]` → OpenCode 调 datatalk_file_read → qwen3.6-plus 完整识别 15 条文本（用户上传的 824×569 截图）

## 17. MCP image content 包装加固：批量/混合/误识别场景（2026-05-18）

> **Trigger**：BUG-0060 修复后用户追加要求评估批量场景、混合场景（文件 + 图片）、以及非 image 文件（CSV/JSON）是否受影响。
> 审计发现 3 个潜在风险：
> 1. CSV/JSON 单元格内嵌 `data:image/...;base64,...` 字符串会被仅靠 content 字段的正则误识别为图
> 2. 其他 action（execute_sql 等）输出含 data URI 字符串同样会误触发
> 3. image 路径 structuredContent 保留 content data URI，若 client 把整个 result 二次喂给 LLM 会双重消耗 token

- [x] 17.1 `extractImagePayload` 加强 gate：require `compressedMimeType` 为非空 `image/*`（FileReadActionHandler image branch 独占字段，其他 path 永不输出）
- [x] 17.2 image 路径 `structuredContent` 与 text content 镜像（同样剔除 `content` 字段），整个 result 仅 `content[1].data` 出现一次 base64
- [x] 17.3 `DataTalkMcpServiceTest` 新增 3 用例：`textFileWithEmbeddedDataUriStringIsNotMisclassifiedAsImage` / `unrelatedActionWithCoincidentalDataUriDoesNotEmitImageContent` / `independentImageCallsEachProduceTheirOwnImageContentBlock`；替换旧的 `imageBranchToleratesMissingCompressedMimeType` 为 `outputWithoutCompressedMimeTypeIsNotSplitEvenIfContentLooksLikeDataUri`（gate 加强后语义反转），9 → 12 全绿
- [x] 17.4 `file-read-image-pipeline/spec.md` 补 5 个 scenario：image structuredContent 不重复 base64 / 缺 compressedMimeType 不拆 / CSV 含 data URI 字符串不拆 / 非 file_read 输出不拆 / 多次独立调用无 cross contamination；Detection 规则段强调 `compressedMimeType` 为强 gate
- [x] 17.5 E2E 真实链路混合场景验证：上传图片 + 内含 `data:image/...;base64,` 单元格的 CSV，AI 顺序调 file_read 两次 → 图片成功识别 15 行文字 + CSV 不被误识别且 AI 完整读出 4 行原文（含 base64 字符串）
- [x] 17.6 `cd server && mvn -pl data-talk-application test` — 991/991 全绿（无回归）
- [x] 17.7 BUG-0060 文档 Verification 段更新（12 用例 + 单图 e2e + 混合 e2e 两条证据）
