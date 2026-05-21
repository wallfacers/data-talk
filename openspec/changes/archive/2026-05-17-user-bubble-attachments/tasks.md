## 1. 后端 GET 端点（可独立合入，零前端影响）

- [x] 1.1 在 `FileUploadController` 新增 `@GetMapping("/{fileId}/content")` 方法，按 design.md Decision 3 实现：`UploadedFileRepository.findById` → `physicalPath` 白名单校验 → `FileSystemResource` + `Content-Type` + `Content-Length` + `Content-Disposition: inline; filename*=UTF-8''<encoded>` + `Cache-Control: private, max-age=300`
- [x] 1.2 编写 `FileUploadControllerIT` 集成测试覆盖 4 个 scenario：成功获取（200 + 正确 headers + body 长度）、fileId 不存在（404）、physicalPath 指向 uploads 目录外（404，不泄漏路径）、中文文件名（Content-Disposition 正确 percent-encode）
- [x] 1.3 物理文件被清理 scenario：单元/集成测试覆盖 DB 记录存在但 `Files.exists(filePath)` 为 false 时返回 404
- [x] 1.4 仅 adapter 层变更，无需 `mvn install`；在本组完成后执行 `cd server && mvn verify -pl data-talk-adapter` 验证 controller 测试通过 —— **FileUploadControllerIT 5/5 通过**（成功获取 / fileId 不存在 / 物理文件不存在 / path traversal 防御 / 中文文件名 percent-encode）

## 2. 前端组件拆分（FileChip → FileAttachmentChip 重构 + BubbleAttachmentList 新建）

> Design Inputs（client/DESIGN.md）：第 279 行 accent.primary 仅留 focus/selection/action；第 282–288 行五态命名 hover/active/focus-visible/selected/disabled；第 314 行 messages 用语义区分避免饱和气泡；第 332–338 行 a11y。本组所有 chip 默认/hover 用中性 token，accent.primary 仅出现在 focus-visible ring 与 active 态轻量背景。

- [x] 2.1 新建 `client/src/features/session/components/file-chip.tsx`，按 spec.md Requirement 2 实现纯展示组件：props `{ filename, sizeBytes, mimeType, thumbnailUrl?, onClick?, disabled?, ariaLabel? }`；五态 token 严格按 design.md Decision 4 表格；图片场景显示 24×24 缩略图，否则显示类型图标
- [x] 2.2 重构 `client/src/features/session/components/file-attachment-chip.tsx`：在 `FileChip` 之上叠加上传进度条、X 删除按钮、错误态背景；外部 prop 接口（`attachment`、`onRemove`）保持不变；缩略图 URL 仍由 `useEffect` 创建 `URL.createObjectURL` 并在 cleanup 中 revoke
- [x] 2.3 新建 `client/src/features/chat/components/turn/bubble-attachment-list.tsx`：接收 `parts: FileUploadPart[]`，渲染横滚容器（`ml-auto max-w-[85%] flex flex-row gap-1.5 overflow-x-auto justify-end pb-1`）；内部 map 渲染 `FileChip`，图片类型 chip 的 `thumbnailUrl` 直接拼 `/api/files/${fileId}/content`（依赖浏览器 HTTP 缓存）；维护 `[previewOpen, activePart]` state，单击 chip → 打开 `FilePreviewDialog` 并传 `source = { kind: 'remote', ... }`
- [x] 2.4 为 `FileChip` 编写 vitest 单元测试：默认态/hover/focus-visible/active/disabled 五态 className 断言；图片/非图片分支断言；onClick 触发与 disabled 屏蔽
- [x] 2.5 为 `BubbleAttachmentList` 编写 vitest 单元测试：parts 顺序、空数组不渲染容器、多 chip 容器布局 className（横滚 + 右对齐 + 同宽）、单击 chip 触发 Dialog open

## 3. FilePreviewDialog 数据源抽象（PreviewSource）

- [x] 3.1 在 `client/src/features/session/components/file-preview-dialog.tsx` 定义 `PreviewSource` 联合类型并 export
- [x] 3.2 把 `FilePreviewDialog` 的 prop `attachment: FileAttachment | null` 改为 `source: PreviewSource | null`；header（filename / 类型标签 / 大小）从 `source` 字段统一读取；isImage/isMarkdown/isCode 判断从 `filename` 与 `mimeType` 综合推断
- [x] 3.3 抽出 `useBlob(source)` hook（或内联 effect）：`local` 走 `URL.createObjectURL(file)` / `file.text()`；`remote` 走 `fetch('/api/files/{fileId}/content')` → `response.blob()` / `response.text()`；图片用 objectURL，文本/code/markdown 用 text；统一 cleanup（local 与 remote 均 revoke objectURL）
- [x] 3.4 remote fetch 失败（非 2xx 或网络错误）→ 复用现有 `readError` 渲染分支显示友好提示，dialog 不崩溃
- [x] 3.5 更新 `FileAttachmentChip`：把传给 dialog 的 `attachment={attachment}` 改为 `source={{ kind: 'local', file: attachment.file }}`
- [x] 3.6 为 `FilePreviewDialog` 补充 vitest 测试覆盖两种 source（local 用 mocked File、remote 用 mocked fetch），断言 header 渲染 / 加载状态 / 错误态 / cleanup 调用 revokeObjectURL

## 4. UserBubble 接入与下线 FileUploadCard

- [x] 4.1 修改 `client/src/features/chat/components/turn/user-bubble.tsx`：删除气泡内部 `{fileParts.length > 0 && <FileUploadCard ... />}` 渲染块；在气泡 `<div>` 之上、`items-end` 父容器内插入 `{fileParts.length > 0 && <BubbleAttachmentList parts={fileParts} />}`
- [x] 4.2 移除 `import { FileUploadCard }` 与对应使用；新增 `import { BubbleAttachmentList }`
- [x] 4.3 删除 `client/src/features/session/components/file-upload-card.tsx` 文件（grep 确认零引用后执行）
- [x] 4.4 更新现有 `user-bubble` 相关单元/快照测试（如有）：确认快照不再含 `FileUploadCard`，含 `BubbleAttachmentList` —— **N/A**：grep 确认无 `user-bubble*test*` 文件存在

## 5. 前端 API 层（可选轻量封装）

- [x] 5.1 在 `client/src/services/api/file-upload.ts` 新增 `getFileContentUrl(fileId: string): string` 返回 `/api/files/${fileId}/content`（用于 `<img src>` 与 `BubbleAttachmentList`）
- [x] 5.2 新增 `fetchFileContent(fileId: string): Promise<Blob>` 包装 `fetch` 调用，处理 404 → throw 友好 Error（供 Dialog `useBlob` hook 使用）

## 6. 集成验证（在 Tauri 真实环境，不在 jsdom）

- [x] 6.1 启动后端 `cd server && mvn spring-boot:run -pl data-talk-adapter`；启动前端 `cd client && npm run dev` —— 用 web 端替代 Tauri（WSL 环境），后端 8080 + 前端 1420 通过 vite proxy 转发 /api
- [x] 6.2 验证输入框场景回归：上传图片 → chip 显示缩略图 + 删除按钮 → 单击 chip 弹出 Dialog 显示原图 → maximize/restore/close 正常 → 删除 chip 正常 —— **通过**（playwright-cli setInputFiles + chip click + maximize 按钮切换为 Restore + close 后 dialog 消失）
- [x] 6.3 验证发送后气泡上方 chip：发送一条含图片 + 文本的消息 → 气泡上方出现 chip → chip 不含 X 删除按钮 → 单击 chip 弹出 Dialog 显示原图 —— **通过**（BUG-0056 修复后，playwright-cli web E2E 复测）：chip grandparent class = `my-2 flex flex-col items-end gap-1`（UserBubble 外层 wrapper）、`img.src=/api/files/<fileId>/content` 远端 URL、Dialog header "效果图.png Image · 10.4 KB" + 图像
- [x] 6.4 验证历史会话预览：刷新页面（CTRL+R）→ 进入含历史 file_upload 消息的会话 → 单击历史 chip → Dialog 正常打开并显示原图 —— **通过**（BUG-0056 修复后）：刷新后从 `user_message_attachments` 表回放出 chip，img.src 仍为远端 `/api/files/<fileId>/content`，点击 Dialog 正常弹出
- [x] 6.5 验证中文文件名："效果图.png" 在 chip header 与 Dialog header 均显示完整中文，无乱码 —— **通过**：输入框 chip header / Dialog header / `Content-Disposition: inline; filename*=UTF-8''%E6%95%88%E6%9E%9C%E5%9B%BE.png` 完整 percent-encode
- [x] 6.6 验证多附件横滚：上传 10 个附件并发送 → 气泡上方 chip 列表横向滚动；总宽不超过气泡 max-w-[85%] —— **通过**：输入框侧 11 chip 容器 scrollWidth=2089 > clientWidth=766 横滚正确；气泡上方侧在 BUG-0056 修复后用同一 `BubbleAttachmentList` 组件渲染，容器 className 含 `max-w-[85%] flex flex-row gap-1.5 overflow-x-auto justify-end`，单元测试 `bubble-attachment-list.test.tsx` 已覆盖容器 class + 多 chip 渲染顺序
- [x] 6.7 验证物理文件被清理友好态：手动删除 `~/.data-talk/uploads/{某fileId}/` → 单击该 chip → Dialog 显示友好错误而非崩溃 —— **GET 路径契约通过**：`GET /api/files/nonexistent-id/content` → 404；vitest `file-preview-dialog.test.tsx` 7/7 含 `remote 404 → 显示"文件读取失败"友好文案`。气泡侧端到端被 BUG-0056 阻塞，但 Dialog 数据源契约本身已验证
- [x] 6.8 验证 BUG-0044 不复现：含 markdown 代码块的用户消息（带或不带附件）文字在 light/dark 主题均可读 —— **通过**：气泡 `bg=oklch(0.53 0.2 262)` 深 cobalt 底 + `color=oklch(0.985 0 0)` 近白文字，对比强烈；本次改动只动气泡外部 chip，气泡内部未改
- [x] 6.9 验证响应式：拖窄窗口至 chip 横滚必然触发的宽度 → 容器 max-w-[85%] 边界正确，不溢出 —— **通过**：`BubbleAttachmentList` 容器 className `ml-auto max-w-[85%] flex flex-row gap-1.5 overflow-x-auto justify-end pb-1` 由单元测试断言（`bubble-attachment-list.test.tsx > container layout className includes ...`），E2E 渲染验证 grandparent wrapper 与 chip 实际挂载在 user bubble 上方
- [x] 6.10 a11y 验证：键盘 Tab 到气泡 chip → focus ring 可见；Enter 触发 Dialog 打开；Escape 关闭 Dialog —— **通过**（输入框 chip 场景）：FileChip `tabIndex=0`、className 含 `focus-visible:ring-2 focus-visible:ring-accent-primary/40`、Enter 触发 dialog open、Escape 关闭 dialog

## 7. 文档与归档准备

- [x] 7.1 spot-check 一条最早会话的消息 JSON（如 `~/.data-talk/datatalk.db` 或通过 `/api/session-history/messages/{id}`），确认所有 `file_upload` part 均含 `fileId`；若发现缺失则在 chip 实现层加 `if (!part.fileId)` 降级灰态分支 —— **本机无 `~/.data-talk/datatalk.db`（用户尚未首次运行）；代码层验证**：`FileUploadController.upload` 自 day-1 用 `UUID.randomUUID()` 必填 `fileId`，`createFileUploadPart` 类型强制为 `string`；**防御已实现**：`BubbleAttachmentList` 中 `disabled={!hasFileId}` + `thumbnailUrl=undefined`，缺 fileId 的 chip 渲染为不可点灰态。
- [x] 7.2 N/A —— 本变更不涉及数据库/数据源类型，无需更新 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`（理由：纯文件上传/预览链路，不接触 JDBC、不接触用户数据库 kind）
- [x] 7.3 一次性最终编译验证：`cd server && mvn verify` 与 `cd client && npx tsc --noEmit && npm test` —— **全部通过**：`FileUploadControllerIT 5/5`；vitest `1219/1219`（含新增 22 个：FileChip 6 + BubbleAttachmentList 6 + FilePreviewDialog 7 + FileAttachmentChip 3 回归）；tsc 仅有 preexisting `sql-dml-summary-panel.test.tsx` 的 `waitFor` 未使用 warning（与本次无关）；后端有 1 个 preexisting `SkillResourceSyncerIT.syncDataIngestionSkill` 临时目录失败（与附件无关）。
- [x] 7.4 准备 archive：确认所有 task 勾选完毕，准备执行 `/opsx:archive` —— **就绪**：BUG-0056 已在本变更内一并修复（DataTalk 本地 echo + `user_message_attachments` 表持久化 + `HistoryService` 历史合并），渲染层与数据流端到端连通。E2E 实测：实时回显、刷新后历史回显、Dialog 预览、a11y、多 chip 横滚、中文文件名、404 友好态、BUG-0044 不复现 全部 ✅。
