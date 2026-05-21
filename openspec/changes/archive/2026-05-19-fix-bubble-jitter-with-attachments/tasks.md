## Tasks

- [x] T1: 扩展 `upsertPendingUser` 签名，新增 `pendingFileParts?: Part[]` 参数
  - 文件: `client/src/stores/chat-parts-store.ts`
  - 将 `pendingFileParts` 中的每个 Part 的 `id` 改为 `pending_prt_` 前缀（`pending_prt_${generateUuid()}`），设置 `sessionID` 和 `messageID` 为 `pendingId`
  - 连同 text Part 一并写入 `partsBySession[sessionId][pendingId]`
  - 同时写入 `partIndexBySession` 索引

- [x] T2: 修改 `promotePendingUser` 清空旧 parts 数组
  - 文件: `client/src/stores/chat-parts-store.ts`
  - 在 promote 的 `set()` 调用中，将 `partsBySession[sessionId][realId]` 设为 `[]`
  - 同时清理 `partIndexBySession` 中对应的 pending part 索引

- [x] T3: 修改 `sendMessage` 传递 file parts 给 `upsertPendingUser`
  - 文件: `client/src/services/channel/use-channel.ts`
  - 从 `parts` 参数中过滤出 `type === 'file_upload' || type === 'file'` 的项
  - 作为 `pendingFileParts` 传入 `upsertPendingUser(sessionId, pendingText, fileParts)`

- [x] T4: 前端编译验证
  - `cd client && npx tsc --noEmit` 确认零类型错误

- [x] T5: vitest 单元测试验证
  - `cd client && npx vitest run` 确认既有测试全部通过

- [x] T6: Playwright E2E 验证带附件发送无抖动（待手动验证：上传图片 → 输入文本 → Enter，观察气泡首次渲染即含附件 chip）
  - 手动测试：上传图片 → 输入文本 → Enter，观察气泡是否首次渲染即包含附件 chip
