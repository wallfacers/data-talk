## Context

`prompt-composer` 的 `composerDrafts` 曾通过 Zustand persist 中间件写入共享 localStorage key `data-talk.session`。发送消息后 CTRL+R 刷新时，Zustand persist 的异步 flush（microtask）可能未执行，导致旧草稿值 hydrate 回来（BUG-0037/0039）。经过 3 轮修复（同步写 localStorage → schema 对齐）后因竞态链复杂度选择整体移除（BUG-0046）。

当前状态：`composerDrafts` 仅内存，`partialize` 只持久化 `activeSessionId`。用户每次刷新后输入框清空。

## Goals / Non-Goals

**Goals:**
- 页面刷新后恢复用户正在编辑的输入框文本
- 发送消息后立刻 CTRL+R，输入框保持空（不出现旧文本）
- 每个 session 独立保存草稿，session 切换时恢复对应草稿
- 无 session 状态下的草稿也要持久化

**Non-Goals:**
- 不涉及后端改动
- 不涉及 streaming 状态持久化（已有独立 spec `composer-streaming-state`）
- 不改变 Zustand persist 对 `activeSessionId` 的行为

## Decisions

### D1: 独立 localStorage key 而非 Zustand persist

**选择**: 每个草稿使用独立 localStorage key `dt.draft.<draftKey>`。

**否决**: 继续用 Zustand persist + 同步写（方案 B）。

**理由**:
- 独立 key 的写入和删除都是原子的 `setItem`/`removeItem`，不需要读-改-写的 JSON 序列化窗口
- 完全解耦 Zustand persist 的 schema（`{ state, version }`），消除双路径不一致
- 每次 keystroke 只序列化一个短字符串，不序列化整个 session store
- 删除时 `removeItem` 是 O(1)，无竞态窗口

### D2: 发送时同步 removeItem

**选择**: 在 `submitText` 的 `updateText('')` 路径中，同步调用 `localStorage.removeItem('dt.draft.' + key)`。

**理由**: 这是解决 CTRL+R 竞态的核心——`removeItem` 是同步的、原子的。无论页面何时卸载，localStorage 里已经没有这个 key 了。

### D3: keystroke 时同步 setItem

**选择**: 每次 keystroke 调用 `setComposerDraft` 时同步 `localStorage.setItem`。

**理由**: `setItem` 对短字符串的性能开销可忽略。同步写保证即使 keystroke 后立刻 CTRL+R（极端场景），草稿也不会丢失。这比依赖 Zustand persist 的异步 flush 可靠。

### D4: hydrate 时机

**选择**: 在 `prompt-composer.tsx` 的 `useState` 初始化时，从独立 key 读取。session 切换时在 `useEffect` 中从独立 key 读取。

**理由**: 跟当前内存方案的代码路径一致，只需把 `composerDrafts[draftKey]` 替换为 `localStorage.getItem('dt.draft.' + draftKey)`。

### D5: session 删除时清理

**选择**: 在 session 删除流程中追加 `localStorage.removeItem('dt.draft.' + sessionId)`。

**理由**: 防止已删除 session 的草稿在 localStorage 中泄漏。

## Risks / Trade-offs

- **[localStorage 容量]** 大量 session 积累 draft key 可能占用少量空间 → 每个 draft 通常 < 1KB，100 个 session 也不过 100KB，远低于 5MB 限制。session 删除时清理进一步限制增长
- **[keystroke 写入性能]** 每次击键一次 `localStorage.setItem` → 实测写入短字符串 < 0.1ms，不可感知。可考虑 debounce 但目前没必要
- **[draft key 前缀冲突]** `dt.draft.` 前缀可能与其他 localStorage key 冲突 → 全局搜索确认目前无 `dt.` 前缀的 key
- **[发送失败恢复]** 发送失败时 `updateText(trimmed)` 恢复文本，但此时 localStorage 已被 removeItem → 需要在恢复路径中重新 `setItem`。当前代码中 `updateText` 已调用 `setComposerDraft`，而 `setComposerDraft` 会同步写 localStorage，所以自动恢复
