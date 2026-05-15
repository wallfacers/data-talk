## Why

用户在 AI 输入框编辑的文本在页面刷新后丢失。之前的实现（BUG-0037/0039）将 `composerDrafts` 通过 Zustand persist 中间件写入 `data-talk.session` 这个共享 localStorage key，导致发送→CTRL+R 时异步 flush 未完成，旧草稿 hydrate 回来。经过 3 轮修复（异步→同步写→schema 对齐），最终因竞态链复杂度选择整体移除。现在用独立 localStorage key 方案重新实现，从根本上消除双写竞态。

## What Changes

- 引入独立 localStorage key 前缀 `dt.draft.<draftKey>`，每个 session（含 `__nosession__`）一个 key
- keystroke 时同步 `localStorage.setItem`（轻量，只写一个短字符串）
- **发送成功时同步 `localStorage.removeItem`**——这是解决 CTRL+R 竞态的核心：清除操作是原子的，不依赖 Zustand persist 的异步 flush
- 组件 mount / session 切换时从独立 key hydrate 到内存
- session 删除时清理对应 draft key，防止泄漏
- Zustand persist `partialize` 保持不变，仍只持久化 `activeSessionId`

## Capabilities

### New Capabilities
- `composer-draft-persistence`: 独立 localStorage key 方案实现 AI 输入框草稿持久化，覆盖写入、发送清除、hydrate、session 删除清理四个生命周期

### Modified Capabilities

（无——`composer-streaming-state` spec 不受影响，本方案与 streaming 状态完全无关）

## Impact

- **`client/src/stores/session-store.ts`**：`setComposerDraft` 增加独立 localStorage 读写逻辑；新增 `clearComposerDraft` action
- **`client/src/features/session/prompt-composer.tsx`**：mount 时 hydrate 从独立 key 读取；发送路径调用 `clearComposerDraft`；session 切换时 hydrate
- **`client/src/features/session/hooks/use-pending-prompt-resume.ts`**：pending prompt 发送成功后清理对应 draft key
- **session 删除流程**：需检查 session 删除时是否清理对应 draft key
- **测试**：更新 `session-store.test.ts`，新增独立 key 读写、发送清除、hydrate 测试用例
