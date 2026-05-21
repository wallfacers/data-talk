## Why

用户在 AI 输入框上传文件后按 Enter 发送，文本气泡立即出现，但附件 chip 区域要等 SSE `message.part.created` 回推才渲染。两帧之间的布局高度突变导致气泡位置抖动（jitter），体验粗糙。问题根因是 `upsertPendingUser` 只写 text Part，不包含 file_upload/file Part，乐观渲染时 `BubbleAttachmentList` 缺数据。

## What Changes

- **`upsertPendingUser` 扩展签名**：新增可选参数 `pendingFileParts`，允许在创建 pending user 消息时一并写入 file_upload / file 类型的临时 Part，使 React 首次渲染即包含附件数据，消除两帧时序差。
- **`sendMessage` 传递附件数据**：channel hook 在调用 `upsertPendingUser` 前，从 `parts` 数组中提取 file 相关 parts（file_upload / file）传入。
- **`promotePendingUser` 处理 Part 替换**：当 SSE 推回真实 message 时，乐观 parts 被真实 parts 替换，保持 `__renderKey` 稳定避免 React 重挂载。

## Capabilities

### New Capabilities

（无新增能力）

### Modified Capabilities

- `chat-message-attachments`: 乐观渲染阶段要求用户气泡在首次出现时即包含附件 chip，而非等待 SSE 回推。新增 "pending 消息含附件时首次渲染即展示" 的 REQUIREMENT。

## Impact

- **前端 store**: `chat-parts-store.ts` — `upsertPendingUser` 签名变更、`promotePendingUser` Part 替换逻辑
- **前端 channel hook**: `use-channel.ts` — `sendMessage` 调用点传参变更
- **前端气泡组件**: `user-bubble.tsx`、`bubble-attachment-list.tsx` — 无变更（已能渲染临时 Part）
- **相关 BUG**: 无已知 open BUG 与此重叠。已关闭的 BUG-0056（file_upload part 不回显）和 BUG-0063（chip 发送后不消失）属于不同问题域。

## Design Inputs

本变更为纯前端 store 层改动，不涉及 UI 布局/组件 token 变更，`client/DESIGN.md` 约束不直接适用。
