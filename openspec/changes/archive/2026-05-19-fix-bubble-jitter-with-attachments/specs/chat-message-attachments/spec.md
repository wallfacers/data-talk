## MODIFIED Requirements

### Requirement: 乐观渲染阶段 pending 用户消息首次渲染即包含附件

`upsertPendingUser` 在创建 pending 用户消息时 SHALL 接受可选参数 `pendingFileParts`（类型 `Part[]`）。当该参数非空时，store SHALL 将这些 file parts 连同 text part 一并写入 `partsBySession[sessionId][pendingId]`，使得 React 首次渲染 `UserBubble` 时 `BubbleAttachmentList` 即有数据可渲染，MUST NOT 出现先渲染纯文本气泡再追加附件 chip 的两阶段布局。

`pendingFileParts` 中的每个 Part 其 `id` SHALL 使用 `pending_prt_` 前缀，`sessionID` 与 `messageID` SHALL 指向 pending 消息。

#### Scenario: 带图片附件发送时气泡首次渲染即含 chip

- **GIVEN** 用户在 composer 上传了一张图片（status === 'done'）并输入 "这是什么"
- **WHEN** 用户按 Enter，`sendMessage` 被调用，`upsertPendingUser(sessionId, "这是什么", [filePart])` 执行
- **THEN** `partsBySession[sessionId][pendingId]` SHALL 包含 2 个 Part（1 text + 1 file_upload）
- **AND** React 首次渲染 `UserBubble` 时 `BubbleAttachmentList` SHALL 出现对应 chip
- **AND** 气泡高度从第一帧起即包含附件区域，MUST NOT 出现布局跳动

#### Scenario: 无附件发送时行为不变

- **GIVEN** 用户只输入文本，无附件
- **WHEN** 用户按 Enter，`sendMessage` 被调用，`upsertPendingUser(sessionId, "你好")` 执行（不传 `pendingFileParts`）
- **THEN** 行为 SHALL 与变更前完全一致：`partsBySession[sessionId][pendingId]` 仅含 1 个 text Part
- **AND** `BubbleAttachmentList` MUST NOT 渲染（chip 数量为 0）

#### Scenario: 多附件首次渲染全部出现

- **GIVEN** 用户上传了 3 个文件（2 图片 + 1 CSV）并输入 "分析这些"
- **WHEN** `upsertPendingUser(sessionId, "分析这些", [filePart1, filePart2, filePart3])` 执行
- **THEN** `BubbleAttachmentList` 首次渲染 SHALL 显示 3 个 chip
- **AND** chip 顺序 SHALL 与 parts 数组中顺序一致

### Requirement: promotePendingUser 清空旧 parts 由 SSE 真实 parts 替换

`promotePendingUser` 在将 pendingId 替换为 realId 时 SHALL 同时将 `partsBySession[sessionId][pendingId]`（现变为 `realId`）的 parts 数组清空为 `[]`。后续 SSE `message.part.created` 事件通过 `upsertPart` 插入真实 parts。

清空操作 SHALL 与 id 替换在同一个 `set()` 调用中完成，确保 React 批量更新。

#### Scenario: promote 后 SSE 真实 parts 填入

- **GIVEN** pending 消息含 2 个 parts（text + file_upload），`promotePendingUser(sessionId, pendingId, realId)` 被调用
- **WHEN** promote 执行
- **THEN** `partsBySession[sessionId][realId]` SHALL 为空数组 `[]`
- **AND** 后续 SSE `message.part.created` 事件通过 `upsertPart` 逐个插入真实 text 和 file parts
- **AND** 最终 parts 数组 SHALL 包含服务端真实 ID 的 parts

#### Scenario: promote 与 SSE parts 在同一 render cycle

- **GIVEN** SSE 推回 `message.created` 后紧随 `message.part.created`（text），两者在同一个 event loop tick 被 Zustand 处理
- **WHEN** React 执行渲染
- **THEN** 用户 SHALL 看到完整的气泡（text + 附件），MUST NOT 感知到 parts 被清空的中间态
