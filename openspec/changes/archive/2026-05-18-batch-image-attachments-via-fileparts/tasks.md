## 1. Backend — domain & application

- [x] 1.1 给 `FileUploadPart` (domain record) 增加可选字段 `String url`，默认 null，更新 toString/equals/hashCode 与所有调用方构造器
- [x] 1.2 修改 `ChannelService.partForWire(FileUploadPart u)`：当 `u.mimeType()` 以 `image/` 开头且 `u.url()` 非空时，输出 `{type:"file", mime, filename, url}`；否则保持现状走 text 降级路径
- [x] 1.3 修改 `ChannelService.sendMessage()` 中的 `uploadParts` 过滤逻辑：只把"非图片或缺 url 的" FileUploadPart 入队 `PendingFileUploadEchoRegistry`，图片新路径跳过 enqueue
- [x] 1.4 修改 `ChannelService.buildFileUploadContext()`：保留尾句 "Use `datatalk_file_read` with fileId..." 仅用于非图片分支（不需要代码改动，只确认图片不会再走到这里）
- [x] 1.5 在图片缺 url 的"降级路径"分支增加 WARN 日志（"image part missing dataUri, falling back to read_file path"）便于排查预热失效场景

## 2. Backend — adapter (skill / MCP 引导清理)

- [x] 2.1 编辑 `server/data-talk-adapter/src/main/resources/skills/file-upload-routing/SKILL.md` IMAGE Files 节，删除 "Use `datatalk_file_read` to retrieve the image content (returned as ...)" 整行；保留"承认收到图片"与尺寸/格式回报指引
- [x] 2.2 确认 `AGENTS.md` 中 `datatalk_file_read` 通用描述无需改动（适用于非图片按需读取场景）
- [x] 2.3 `FileReadActionHandler` 与 `@DataTalkAction(id="datatalk.file_read")` 注册保持不变（不删除工具，仅调整引导）

## 3. Backend — 验证（批后一次跑）

- [x] 3.1 新增 `ChannelServiceTest` 单元测试用例覆盖：单图、多图、图片+CSV混合、图片缺url降级 四个 partForWire 场景，断言 OpenCode body.parts 形状与 echo registry enqueue 内容
- [x] 3.2 扩展 `FakeOpenCodeServer` (WireMock) 集成测试断言：POST `/session/:id/message` body.parts 含 `type:"file"` 且 `url` 以 `data:` 开头，且 MUST NOT 含 "datatalk_file_read" 文本
- [x] 3.3 `cd server && mvn install -pl data-talk-domain,data-talk-application -am -DskipTests` 推 jar（因为 FileUploadPart 在 domain，ChannelService 在 application）
- [x] 3.4 `cd server && mvn verify` 全量跑测试，确保零回归（domain 43 + application 995 全绿；ChannelControllerIT 7/7）

## 4. Frontend — 类型与缓存

- [x] 4.1 `client/src/services/channel/types.ts`：`FileUploadPart` 增加可选字段 `url?: string`；`createFileUploadPart()` 第七个可选参数 `url`
- [x] 4.2 `client/src/features/session/useFileUpload.ts`：新增内存缓存 `Map<fileId, Promise<dataUri>>`；上传成功后对 `mimeType.startsWith('image/')` 的图片异步触发 `prefetchDataUri(fileId)`（复用现有 `MAX_CONCURRENT = 3` 并发限制）
- [x] 4.3 `prefetchDataUri` 实现：`fetch('/api/files/{fileId}/content')` → `response.blob()` → `FileReader.readAsDataURL` → 拼成 `data:<mime>;base64,<...>` 返回 Promise<string>
- [x] 4.4 新增 `getDataUri(fileId, timeoutMs = 5000): Promise<string | null>` API：缓存命中直接返回；未命中则等待预热或主动触发，超时返回 null

## 5. Frontend — 发送链路

- [x] 5.1 `client/src/features/session/prompt-composer.tsx`：构造 parts 时遍历 image attachments，调用 `getDataUri(fileId)` 拼入 `url` 字段（同步等待最多 5s，超时则 url 为 undefined）
- [x] 5.2 新增 `computeImagePayloadSize(parts): number` 工具：累加所有 image part 的 `url` 字符串字节量（`image-payload.ts`）
- [x] 5.3 在 `prompt-composer` 发送前调用 size guard：≤3MB 直发；>3MB 且 ≤5MB 显示 warning toast 但仍发送；>5MB 显示 error toast 阻止发送（restore draft，不进入 inflight）
- [x] 5.4 i18n：新增三个文案 key `chat.image.payloadWarning`、`chat.image.payloadTooLarge`、`chat.image.payloadHint`（zh-CN + en-US）
- [x] 5.5 阈值常量集中到 `client/src/features/session/constants.ts`：`IMAGE_PAYLOAD_WARN_BYTES = 3 * 1024 * 1024`、`IMAGE_PAYLOAD_HARD_LIMIT_BYTES = 5 * 1024 * 1024`、`IMAGE_DATA_URI_FETCH_TIMEOUT_MS = 5000`

## 6. Frontend — 气泡 chip 渲染扩展

- [x] 6.1 `BubbleAttachmentList`：扩展 `pickAttachments(parts)` helper，识别 `type === 'file'` 且 `mime` 以 `image/` 开头的 `FilePart`，映射成统一的 `{ filename, mimeType, thumbnailSrc }` 结构（thumbnailSrc 直接取 part.url）
- [x] 6.2 `FileChip` 缩略图源切换：复用既有 `thumbnailUrl` 入参（data URI 与 HTTP URL 在 `<img src>` 等价）
- [x] 6.3 确保混合场景下 chip 顺序保持原始 parts 顺序，CSV chip + 图片 chip 视觉一致（共用 `FileChip` 组件）
- [x] 6.4 Design Inputs 复核：本变更不引入新 token，沿用 `border-border-default bg-bg-soft text-text-base` 五态映射（参见 client/DESIGN.md 中 `chip` 相关 token）
- [x] 6.5 `client/src/features/chat/file-preview-dialog.tsx`：`PreviewSource` 类型新增 `{ kind: 'embedded'; dataUri: string; filename: string; mimeType: string; sizeBytes: number }` 第三种 kind
- [x] 6.6 `FilePreviewDialog` 渲染分支：`source.kind === 'embedded'` → `<img src={source.dataUri}>` 直接渲染，不调 `URL.createObjectURL`、不发 GET，关闭时无需 revoke
- [x] 6.7 `FilePreviewDialog` embedded.dataUri 为空时复用 `chat.filePreview.readError` 友好错误分支（不崩溃、header 仍正常显示文件名）
- [x] 6.8 `BubbleAttachmentList` 单击事件分发：FileUploadPart → `remote` kind（行为不变）；image FilePart → `embedded` kind（dataUri 取自 part.url，sizeBytes 估算 `Math.floor(base64Length * 3 / 4)`）
- [x] 6.9 chip 渲染 `url` 为空时显示"图片已失效"占位（disabled chip + i18n `chat.image.unavailable`）

## 7. Frontend — 验证（批后一次跑）

- [x] 7.1 新增 `data-uri-cache.test.ts` 用例：prefetch 共享 Promise、缓存命中、未命中触发、失败 null、超时 null、evict 重新拉
- [x] 7.2 新增 `image-payload.test.ts` 用例：computeImagePayloadSize 累加 / 忽略非图片 / 空 url；classifyImagePayload ok/warn/reject 三档边界
- [x] 7.3 `cd client && npx tsc --noEmit` 零类型错误（除一个无关的 sql-dml-summary-panel.test.tsx unused import，预先存在）
- [x] 7.4 `cd client && npm run test` 全量 vitest 跑通 — 1239/1239 zero failure

## 8. E2E 端到端验证（playwright-cli）

- [x] 8.1 启动后端（默认 profile，不带 e2e）：后台 spring-boot:run；前端 vite 通过 HMR 加载新代码
- [x] 8.2 场景 A：上传 real-screenshot.png (1280×720, 97KB) + "What do you see" → AI 单轮回复详细描述了 DataTalk UI；POST body 含 file_upload.url=data:image/png;base64,..., 0 次 datatalk_file_read
- [x] 8.3 场景 B：连续上传 3 张图 (img-a/b/c.png) + "How many images" → AI 回复 "I see **3 images**" 含对比表 (X-axis 差异、red circle artifact)；body parts=4 (text + 3 file_upload，每张 url=base64)，单次 promptAsync，0 read_file
- [~] 8.4 场景 C：image + CSV — 跳过 (ChannelServiceTest.sendMessage_mixedImageAndCsv_routesEachToCorrectPath 已覆盖路由逻辑)
- [~] 8.5 场景 D：hard limit toast — 跳过 (Spring multipart 默认 1MB 上限在上传层先拒；image-payload.test.ts 的 classifyImagePayload 已覆盖三档边界)
- [x] 8.6 场景 E：刷新页面进入 "DataTalk screenshot description" 历史会话 → 图片 chip 正常渲染 "real-screenshot.png 97.7 KB"，AI 回复完整显示，无回归
- [x] 8.7 场景 F：刷新后 OpenCode echo 的 FilePart 保留完整 url（data URI 130KB），chip 与 Dialog 正常；预览 `<img src>` 直接是 data URI，无任何 GET /content 请求
- [x] 8.8 场景 G：chip 视觉一致 — A+B+E 三场景均渲染同一 FileChip 组件 (border-border-default bg-bg-soft text-text-base)，无法外观区分新旧路径

**附带发现（与本变更无关，需独立 BUG 跟踪）：**
- ~/.data-talk 服务端 SQLite DB 缺 `user_message_attachments` 表（V1__init.sql 后置加入，无独立 V29 迁移）→ 已临时手工 patch；需新 BUG 文件 + 真正的 Flyway 迁移
- Spring 默认 `spring.servlet.multipart.max-file-size=1MB` 限制了 >1MB 图片上传，比前端 `MAX_SIZE_BYTES=50MB` 严；需在 application.yml 提升至 6-10MB 配合 IMAGE_PAYLOAD_HARD_LIMIT_BYTES，或在前端先 resize 再上传

## 9. BUG 闭环与文档更新

- [x] 9.1 `docs/bugs/BUG-0058-file-read-image-no-compression-base64-too-large.md`：状态由 `verified` → `fixed`，backfill `fixPlanRef` + `fixedAt`
- [x] 9.2 `docs/bugs/index.md`：同步 BUG-0058 行状态 + 在 In Progress 增加 BUG-0061/0062；模块聚合视图同步
- [x] 9.3 docs/FRONTEND.md / docs/BACKEND.md 检查：send_message RPC schema 在 `chat-message-attachments` spec 已完整描述（FileUploadPart.url 字段），未发现额外文档需对齐
- [x] 9.4 N/A 检查：本变更不涉及数据源类型，跳过 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 更新
- [x] 9.5 新增 [BUG-0061](../../../docs/bugs/BUG-0061-missing-flyway-migration-for-user-message-attachments.md)（缺 Flyway 迁移，老 DB 缺表 500）和 [BUG-0062](../../../docs/bugs/BUG-0062-spring-multipart-1mb-limit-blocks-image-uploads.md)（Spring multipart 1MB 上限），均为本 E2E 发现的伴生缺陷，与本 change 解耦

## 10. 收尾

- [x] 10.1 端到端冒烟通过后，运行 `openspec validate batch-image-attachments-via-fileparts --strict` 确认 delta 合法 — **PASS**
- [ ] 10.2 提 PR，标题 `feat(file-upload): batch image attachments via OpenCode FilePart (close BUG-0058)`，body 含 OpenSpec change 链接、关联 BUG 列表、E2E 截图
- [ ] 10.3 合入 develop 后运行 `/opsx:archive batch-image-attachments-via-fileparts` 归档 change，合并 delta specs 到 `openspec/specs/chat-message-attachments/spec.md`
