## Context

当前 `upsertPendingUser(sessionId, text)` 仅创建一个 `type: 'text'` 的临时 Part。当用户消息携带文件附件时，乐观渲染的 `UserBubble` 没有附件数据，`BubbleAttachmentList` 不渲染。SSE 回推 `message.part.created` (file_upload / file parts) 后，附件区域才出现，导致布局高度突变、气泡位置抖动。

关键文件：
- `chat-parts-store.ts` — `upsertPendingUser` / `promotePendingUser` 实现
- `use-channel.ts` — `sendMessage` 调用 `upsertPendingUser` 的位置
- `prompt-composer.tsx` — `submitText` 构建 parts 数组，调用 `sendMessage(parts)`
- `user-bubble.tsx` — 从 parts 过滤附件，传给 `BubbleAttachmentList`
- `bubble-attachment-list.tsx` — 渲染附件 chip（已能处理 file_upload / file 类型）

## Goals / Non-Goals

**Goals:**
- 乐观渲染阶段首次渲染即包含附件 chip，消除气泡抖动
- 保持 `promotePendingUser` 时 React key 稳定（`__renderKey` 不变），不触发重挂载动画
- 最小改动：只修改 store 签名 + channel hook 传参，不改 UI 组件

**Non-Goals:**
- 不改变附件上传流程（eager upload 保持不变）
- 不改变 SSE 事件协议或后端行为
- 不改变 `BubbleAttachmentList` 的渲染逻辑

## Decisions

### D1: `upsertPendingUser` 新增 `pendingFileParts?: Part[]` 参数

签名变更：

```ts
upsertPendingUser: (sessionId: string, text: string, pendingFileParts?: Part[]) => string
```

当 `pendingFileParts` 非空时，除了 text Part 外，也将这些 Part 写入 `partsBySession[sessionId][pendingId]`。每个临时 Part 的 id 使用 `pending_prt_` 前缀，与 `promotePendingUser` 后的真实 Part id 不冲突（SSE 回推的真实 Part 使用服务端生成的 id）。

**Why**: 将已有附件数据提前写入 store 是最直接的方案。附件上传在 `uploadAll()` 后已全部完成，数据在手，无需额外网络请求。

### D2: `sendMessage` 从 parts 数组提取 file parts 传入

`use-channel.ts` 的 `sendMessage(parts)` 在调用 `upsertPendingUser` 前，从 `parts` 中筛选 `type === 'file_upload' || type === 'file'` 的项，作为 `pendingFileParts` 传入：

```ts
const fileParts = parts.filter(
  (p: any) => p?.type === 'file_upload' || p?.type === 'file'
)
const pendingId = useChatPartsStore.getState().upsertPendingUser(sessionId, pendingText, fileParts)
```

**Why**: `sendMessage` 已经收到完整的 parts 数组（text + files），提取 file parts 只是浅过滤，零额外开销。

### D3: `promotePendingUser` 替换整组 Parts 而非追加

当前 `promotePendingUser` 只交换 MessageInfo 的 id，不触及 Parts 数组。SSE 后续 `message.part.created` 事件通过 `upsertPart` 逐个插入，逐渐替换掉临时 Part。

但由于乐观 file parts 使用 `pending_prt_` 前缀 id，SSE 回推的真实 file parts 使用服务端 id，两者 id 不同。`upsertPart` 会追加而非覆盖，导致 parts 数组中出现重复（临时 + 真实）。

解决方案：在 `promotePendingUser` 时**清空旧 parts 数组**，让后续 `upsertPart` 从空数组开始插入真实 parts。这样：
- t0: `upsertPendingUser` 写入 `[text_part_pending, file_part_pending]` → 首次渲染有附件
- t1: SSE `message.created` → `promotePendingUser` 清空 parts 数组 → 短暂无 parts
- t2: SSE `message.part.created` (text + files) → `upsertPart` 逐个插入 → 最终有附件

t1 到 t2 间隔极短（同一个 SSE 流的连续事件，通常 < 50ms），但存在一个帧可能短暂丢失附件。

**改进方案**: `promotePendingUser` 不清空 parts，而是保留现有 parts。`upsertPart` 按 part id 做 upsert（已有逻辑），临时 parts 的 id 与真实 parts 的 id 不同，所以临时 parts 会残留。需要在真实 parts 全部到达后清理临时 parts。

**最终方案**: `promotePendingUser` 保留旧 parts 不清空。新增逻辑：当 SSE 的 `message.part.created` 为该消息的第一个非 pending part 时，标记该消息的 pending parts 已被 hydrate。`upsertPart` 中，如果消息已有真实 parts（非 `pending_prt_` 前缀），则在插入新 part 时同时移除同类型的 pending parts。

简化实现：在 `promotePendingUser` 时，对旧 parts 做类型分类保留——保留 text Part（因为 SSE 也会推 text part，id 不同但位置 0 的 text part 可被覆盖），对于 file parts，因为 SSE 推回的 file part id 必然不同，会导致重复。所以更简洁的方案是：

**最终采用**: `promotePendingUser` 执行时，将 parts 数组清空为 `[]`。但为了保证视觉不闪烁，利用 `__renderKey` 稳定性 + React batch 更新——SSE 的 `message.created` 和紧随其后的 `message.part.created` 通常在同一个 event loop tick 内被 Zustand 处理，React 只会重渲染一次。

如果实测发现有闪烁，备选方案是给 `UserBubble` 加 `min-height` CSS 约束（基于附件数量计算），但这增加复杂度，先不做。

### D4: 临时 file Part 的数据完整性

`pendingFileParts` 来自 `submitText` 构建的 parts 数组，其内容与发给后端的完全一致：
- `file_upload` 类型：包含 `fileId`、`filename`、`mimeType`、`sizeBytes`、`analysis`、`url`（图片 data URI）
- `file` 类型：包含 `mime`、`filename`、`url`

`BubbleAttachmentList.pickAttachments()` 已经能处理这两种类型，渲染缩略图和 chip 信息，无需任何改动。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
|------|------|------|
| `promotePendingUser` 清空 parts 后短暂无数据帧 | 气泡可能闪烁一帧 | SSE 事件通常 batch 到同一个 React render cycle，实测验证；如有闪烁再补 min-height |
| `upsertPendingUser` 签名变更为可选参数 | 调用方默认行为不变，未传 `pendingFileParts` 时与现有行为完全一致 | 无额外风险 |
| store 写入更多临时 Part 对内存的影响 | 每条 pending 消息多几个 Part 对象 | Part 对象极小（几 KB），生命周期从 send 到 promote（通常 < 1s），可忽略 |
