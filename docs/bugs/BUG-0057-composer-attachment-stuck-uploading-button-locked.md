---
id: BUG-0057
title: 输入框附件 chip 卡在 uploading 0% 永不消失，导致发送按钮被永久禁用
status: verified
priority: P1
source: manual-report
modules: [chat, file-upload, session]
discovered: 2026-05-17
discoveredBy: agent
testRunId: null
fixCommit: pending
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

`useFileUpload.uploadAll` 通过 `a === attachment` 引用比较定位 setAttachments 中待更新的附件，但**第一次** setAttachments（pending → uploading）已经把数组里那一项替换成了由 `{ ...a, status: 'uploading', progress: 0 }` 创建的**新对象**，循环里 `attachment` 仍指向被替换前的旧引用。await 完成后第二次 setAttachments 再做 `a === attachment` 比较时永远 false，状态卡死在 `status='uploading' / progress=0`。

`hasUploads = attachments.some(a => a.status === 'uploading')` 因此恒为 true → `canSend = (... ) && !hasUploads` 恒为 false → 发送按钮永远 `data-disabled`。`clearDone()` 只过滤 `done`，也无法清理这一遗留 chip。后端文件其实已经成功上传、消息也成功发送（`submitText` 直接用 `uploadAll()` 的返回值构造 parts，不依赖 React state），所以用户感知是"图片确实发出去了，但输入框那张 chip 不肯走、按钮变灰"。

## Reproduction Steps

1. 启动前端 + 后端（任意配置）
2. 打开任一 session，向输入框拖入或选一张图片
3. 输入文本 → 回车发送
4. 用户气泡正确显示文本 + 附件 chip，AI 正常回复
5. 观察输入框 chip：仍然在，显示 `× 0%`
6. 再次点击 / 回车发送按钮：发送按钮 `opacity-40` 灰态，无任何反应

## Expected vs Actual

- **Expected**：发送成功后，输入框内的 chip 在 `clearDone()` 后被清掉，按钮恢复可点击
- **Actual**：chip 留在输入框，显示 `× 0%`（uploading 态），按钮永久禁用

## Environment

- Backend commit: `d2072bed` (develop)
- Frontend commit: `d2072bed` (develop)
- OS / Browser: WSL Ubuntu / Tauri 浏览器渲染（也复现于 web dev）
- Data source: N/A

## Evidence

用户手动截图（`屏幕截图 2026-05-17 202734.png`）：用户气泡含图片 + "这是什么" 文本已发送，AI 已回复，输入框底部仍有一张 `157.5 KB × 0%` 的 chip，发送按钮灰态。

新增的回归测试 `useFileUpload.test.ts > uploadAll transitions pending → done ...` 在修复前断言失败：

```
expected 'uploading' to be 'done'
```

## Root Cause

`client/src/features/session/useFileUpload.ts` 的 `uploadAll` 内三处 `setAttachments`：

```ts
for (const attachment of pending) {
  setAttachments(prev =>
    prev.map(a => a === attachment ? { ...a, status: 'uploading', progress: 0 } : a)  // ← 替换为新对象 A'
  )
  try {
    const response = await uploadFile(attachment.file, sessionId)
    setAttachments(prev =>
      prev.map(a => a === attachment ? { ...a, status: 'done', progress: 100, response } : a)
      //              ^^^^^^^^^^^^^^^^^^^ prev 已是 [A']，attachment 还是旧 A，A' !== A → 永不匹配
    )
  } catch (err) {
    setAttachments(prev =>
      prev.map(a => a === attachment ? { ...a, status: 'error', error: String(err) } : a)
    )
  }
}
```

下游连锁：
- `hasUploads = attachments.some(a => a.status === 'uploading')` → 恒 true
- `canSend = (text || attach) && !isStreaming && !hasUploads` → 恒 false
- `<Button data-disabled={!canSend || undefined} aria-disabled={!canSend} className="... data-disabled:opacity-40">` → 视觉灰态、不可点
- `clearDone()` 只过滤 `done`，对 `uploading` 卡尸无能

注：消息和文件**都已成功送达后端**，因为 `submitText` 直接消费 `uploadAll()` 的返回值 `responses` 来构造 `file_upload` part；React state 走形不影响发送链路，只影响 UI 还原。这点也是用户陈述里"图片会上传成功了"的来源。

## Fix (applied 2026-05-17, status=fixed)

给 `FileAttachment` 增加稳定 `id`（`crypto.randomUUID()`），所有跨 setState 寻址改为按 id：

- `FileAttachment.id: string` 必填字段
- `addFiles` 在新建条目时分配 `newAttachmentId()`（randomUUID，回退到 seq）
- `uploadAll` 三处 `setAttachments` 改用 `a.id === attachmentId`
- `removeAttachment(id: string)` 由按 index 删除改为按 id 删除（避免上传过程中其它操作让索引漂移）
- `prompt-composer.tsx`：`FileAttachmentChip key={a.id}`、`onRemove={() => removeAttachment(a.id)}`

### 改动清单

- `client/src/features/session/useFileUpload.ts` — `FileAttachment.id`、`newAttachmentId`、`addFiles`/`removeAttachment`/`uploadAll` 全部按 id 寻址
- `client/src/features/session/prompt-composer.tsx` — chip key 和 remove 调用迁到 `a.id`
- `client/src/features/session/useFileUpload.test.ts` — 新增 2 个用例：
  - `uploadAll transitions pending → done with progress=100, leaving hasUploads=false`（在旧实现下失败、新实现下通过）
  - `removeAttachment removes the right item by id even after reorder/insert`
- `client/src/features/session/components/file-attachment-chip.test.tsx` — `makeAttachment` helper 加上 `id` 字段以匹配新 schema

## Verification

```bash
cd client && npx vitest run \
  src/features/session/useFileUpload.test.ts \
  src/features/session/components/file-attachment-chip.test.tsx
# 9/9 pass

cd client && npx tsc --noEmit
# 仅遗留一个无关错误：sql-dml-summary-panel.test.tsx(2,37): TS6133 'waitFor' is declared but its value is never read
# 该错误在 develop@d2072bed 已存在，与本修复无关
```

真实交互验证（Tauri / web 浏览器）建议：

1. 选一张图片 → 输入任意文本 → Enter 发送
2. 确认用户气泡正常含 chip + AI 回复
3. **关键**：输入框 chip 消失（uploadAll 现在能正确把状态推到 `done`，`clearDone()` 在 sendMessage 成功后立即清理）
4. 发送按钮恢复可点击

## Notes

- 与 [BUG-0056](BUG-0056-bubble-attachments-file-upload-part-not-roundtripped.md) 完全独立：BUG-0056 是协议层 file_upload → text 降级丢前端 part，已通过 PendingFileUploadEchoRegistry 修复；本 BUG 是 React state 引用比较失效。
- 这是 jsdom 能复现的逻辑 bug（不涉及 layout/scroll），所以新增的 vitest 测试足以守护，不需要 Tauri E2E。
- 修复同时把 `removeAttachment` 由 index 改成 id 寻址，是顺带的健壮性提升（多附件并行上传时手动删除其它 chip 不会再因为索引漂移而误伤）。
