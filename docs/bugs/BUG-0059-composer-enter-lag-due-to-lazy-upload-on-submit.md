---
id: BUG-0059
title: 输入框带文件时按回车感知卡顿 — submitText 在 Enter 时才串行 await uploadAll → sendMessage，用户气泡上屏延迟 300-600ms
status: verified
priority: P2
source: manual-report
modules: [file-upload, chat, session]
discovered: 2026-05-17
discoveredBy: human
testRunId: null
fixCommit: pending
fixPlanRef: openspec/changes/optimize-file-upload-image-and-latency/
duplicateOf: null
regression: false
---

## Summary

`prompt-composer.tsx` 的 `submitText` 在用户按 Enter 时才开始执行 `await uploadAll()`，串行：清空文本 → 等待文件 multipart 上传 + 后端写盘 + MIME 检测 + analysis（典型 100-300ms / 文件，多文件累加）→ 等待 `sendMessage` POST（~50ms）→ 等待 SSE 把用户气泡反推回前端（~100-200ms）。整段窗口里输入框已清空、按钮还是普通态、没有任何视觉反馈，用户体感是"按了回车，但屏幕卡了一下才动"。

## Reproduction Steps

1. 启动前端 + 后端
2. 任一 session，向输入框拖入或选一张图片（150-500KB，例如截图）
3. 立即输入文本 → 按 Enter
4. 观察从按 Enter 到用户气泡出现在聊天区的时间窗口
5. 期间没有任何 loading 反馈：输入框文本已清空、发送按钮仍是普通态、chip 仍显示 pending → 切到 uploading 0% → 100% → done

## Expected vs Actual

- **Expected**：按 Enter 后用户气泡 SHALL 在 <100ms 内出现（视觉响应感）；上传/发送的耗时 SHOULD 在 chip / 按钮 / 气泡上有持续反馈
- **Actual**：300-600ms 空窗（典型 150KB 截图 + 本机后端），用户气泡才上屏；期间无任何视觉反馈，体感"卡了一下"

## Environment

- Backend commit: `d2072bed` (develop)
- Frontend commit: `d2072bed` (develop)
- OS / Browser: WSL Ubuntu / Tauri webview（web dev 模式也复现）
- 网络：本机回环（生产场景跨网延迟会进一步放大）
- Data source: N/A

## Evidence

用户报告原文（2026-05-17）：

> 如果存在文件上传的时候，回车会出现稍微的卡顿现象

代码现场 — `client/src/features/session/prompt-composer.tsx:337-355`：

```ts
updateText('')

// Upload pending files first; uploadAll returns responses directly to avoid stale-closure
const alreadyDone = attachments.filter(a => a.status === 'done' && a.response).map(a => a.response!)
const newlyDone = attachments.some(a => a.status === 'pending') ? await uploadAll() : []

// Build parts array with text + any completed file uploads
const parts: unknown[] = [createTextPart(activeSessionId, trimmed)]
for (const r of [...alreadyDone, ...newlyDone]) {
  parts.push(createFileUploadPart(activeSessionId, r.fileId, r.filename, r.mimeType, r.sizeBytes, r.analysis as Record<string, unknown>))
}

const ok = await sendMessage(parts)
```

`useFileUpload.uploadAll` 内部串行 `for...of` 等待每个文件上传完成（`client/src/features/session/useFileUpload.ts:60-83`），多文件场景延迟线性叠加。

## Root Cause

整体卡顿来自**两条独立设计选择的叠加**：

1. **Lazy upload 模式** — 文件进 `attachments` 后状态保持 `pending`，**不主动上传**；只在 `submitText` 内才触发 `uploadAll`。
   - 直接后果：上传耗时全部计入"回车 → 气泡上屏"的关键路径
2. **串行 for-of 上传** — `uploadAll` 用 `for (const a of pending) await uploadFile(...)`，无并发。
   - 直接后果：多文件时延迟 ≈ Σ单文件延迟

下游连锁：
- 用户气泡的"上屏" 实际依赖 `sendMessage` 完成 → 后端 `ChannelService.sendMessage` → OpenCode 创建 user message → SSE `message.created` → 前端 `chat-parts-store` 渲染。整条链路的起点被 `await uploadAll()` 卡住
- 期间发送按钮虽然 `disabled={!canSend}`（`hasUploads` 为 true），但没有 spinner 替换、没有按钮颜色变化（只在 `isStreaming` 才切到 Loader2Icon），用户没有"系统在干活"的明确信号
- 输入框 `updateText('')` 在 await 之前，所以**文本已经清空、文件 chip 还在**，视觉上像"消息被吃了"

注意：消息和文件最终都会成功送达，只是延迟感知不可接受。这是一个 **UX 性能 BUG**，不是功能 BUG。

## Fix (applied 2026-05-17, status=fixed)

通过 OpenSpec change `optimize-file-upload-image-and-latency` 落地方案 P1 — eager upload + 并发 + 按钮多态反馈：

### 代码改动清单

- `client/src/services/api/file-upload.ts` — `uploadFile(file, sessionId, signal?: AbortSignal)` 第三参数透传给 `fetch`；catch 区分 `AbortError`（silent rethrow）vs 真实错误
- `client/src/features/session/useFileUpload.ts`:
  - `FileAttachment.controller?: AbortController` 字段（保留 BUG-0057 修复的 `id` 字段不动）
  - `addFiles` 每个新建 attachment 分配 `new AbortController()` + `queueMicrotask(scheduleUpload)`
  - `scheduleUpload(ids)` chunk(3) + Promise.all 并发；per-file try/catch + AbortError 静默
  - `uploadAll`（导出 API 不变）改为兜底触发 pending + 等收敛 → done responses
  - `removeAttachment(id)` 同步 lookup → `controller.abort()` → `setAttachments(filter)`，无 await
  - 所有 setState 回调内 `prev.some(a => a.id === id)` 守门，被删 attachment 的回调 silent return
  - 暴露新 derived state：`uploadingCount` / `hasInflight`（保留 `hasUploads` 原义不变）
- `client/src/features/session/prompt-composer.tsx`:
  - 文件顶部注释引用 `client/DESIGN.md L282-288 / L330-338 / L263`
  - 派生状态 `composerButtonState: 'idle' | 'uploading' | 'sending' | 'streaming'`
  - 按钮三态渲染：idle/ArrowUp、uploading|sending/Loader2Icon + `aria-busy="true"` + i18n aria-label、streaming/destructive variant
  - `submitText` 包裹 try/finally 维护 `isSendInflight`；保留 `await uploadAll()` 兜底
  - `motion-reduce:hidden` 隐藏 spinner，dot pulse 替代
- `client/src/i18n/messages.ts` — 新增 `chat.composer.uploadingLabel` / `chat.composer.sendingLabel`（中英双语）
- `client/src/features/session/components/file-attachment-chip.tsx` — 经确认 hover 态已合规（`text-text-muted hover:text-text-base`），未改

### 测试改动

- `client/src/features/session/useFileUpload.test.ts` — 新增 4 个用例：
  - `addFiles in microtask triggers uploadFile`
  - `5 files addFiles → concurrent active ≤ 3 (chunked)`
  - `removeAttachment during uploading aborts fetch`（含 `signal.aborted === true` 断言）
  - `removeAttachment after done excludes from uploadAll`
- BUG-0057 守护用例（uploadAll 收敛 + id-based addressing）原样保留并继续通过
- 现有 2 个测试（"accepts image extensions"、"accepts existing text-based extensions"）的状态断言放宽为 `['pending','uploading','done']`，因为 eager upload 后状态会立刻推进，原 `status==='pending'` 严格断言会假阴

## Verification

```bash
# Frontend 类型检查
cd client && npx tsc --noEmit
# 仅遗留无关错误：sql-dml-summary-panel.test.tsx(2,37) waitFor unused（BUG-0057 修复时即存在）

# Frontend 焦点测试
cd client && npx vitest run \
  src/features/session/useFileUpload.test.ts \
  src/features/session/components/file-attachment-chip.test.tsx
# 13/13 pass

# Frontend 全量回归
cd client && npx vitest run
# 1225/1225 tests, 177/177 test files 全绿（含 BUG-0056/0057 守护用例）
```

真实交互验证（task 11.1-11.6）待用户手测，覆盖：
1. 选 1 张 500KB 图 → 等 1s → 按 Enter，肉眼测量 "按 Enter → 用户气泡出现" < 200ms
2. 选 1 张 5MB 图 → 立即按 Enter（上传未完成），按钮立刻切 spinner，气泡在 uploadAll 完成后立刻上屏
3. 多文件并发：一次拖入 5 张图 → DevTools Network 面板确认同时活跃 ≤ 3 个 multipart POST

## Notes

- 与 BUG-0058 同属"file upload 链路优化"主题，但根因独立：0058 是 LLM 消费端的 base64 体积，0059 是前端 UX 延迟。两 BUG 都被 change `optimize-file-upload-image-and-latency` 覆盖，但 tasks.md 内分组追踪
- 与 [BUG-0057](BUG-0057-composer-attachment-stuck-uploading-button-locked.md) 不冲突 — 0057 修的是"卡 uploading 不退出"的状态机 bug，本 BUG 修的是"在 Enter 时才开始 upload"的产品设计选择
- 优先级 P2 因：用户消息最终能发出去、AI 最终能收到，仅 UX 体感问题；P1 优先级保留给"功能不可用"的 BUG-0058
- 不修方向：放弃了"乐观渲染用户气泡（前端立即假渲染，后端 echo 回来再 reconcile）"方案，因为现有 `PendingFileUploadEchoRegistry` 协作复杂、失败回滚边界多、ROI 不如 eager upload
