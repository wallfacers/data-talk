## 1. Store 层：独立 localStorage key 读写

- [x] 1.1 在 `session-store.ts` 的 `setComposerDraft` 中加入同步 `localStorage.setItem('dt.draft.' + key, text)` 写入；当 text 为空字符串时改为 `localStorage.removeItem`
- [x] 1.2 在 `session-store.ts` 新增 `clearComposerDraft(key)` action，同步执行 `localStorage.removeItem('dt.draft.' + key)` 并清除内存中对应 entry
- [x] 1.3 在 `session-store.ts` 新增 `hydrateComposerDraft(key)` 辅助函数，从 `localStorage.getItem('dt.draft.' + key)` 读取并写入内存 `composerDrafts[key]`

## 2. Composer 组件：hydrate 与发送清除

- [x] 2.1 修改 `prompt-composer.tsx` 的 `useState` 初始化：从 `localStorage.getItem('dt.draft.' + draftKey)` 读取初始值（优先于内存 `composerDrafts`）
- [x] 2.2 修改 `prompt-composer.tsx` 的 session 切换 `useEffect`：从独立 localStorage key hydrate 新 session 的草稿
- [x] 2.3 修改 `prompt-composer.tsx` 的 `submitText`：在 `updateText('')` 调用前/后调用 `clearComposerDraft(draftKey)` 确保同步清除 localStorage（注：`updateText('')` 内部调用 `setComposerDraft(key, '')` 已触发 `removeItem`，无需额外调用）

## 3. Session 删除清理

- [x] 3.1 找到 session 删除流程（`deleteSession` API 调用点），在删除成功后追加 `localStorage.removeItem('dt.draft.' + sessionId)` 清理

## 4. Pending prompt resume 路径

- [x] 4.1 检查 `use-pending-prompt-resume.ts`：pending prompt 发送成功后确认草稿被清除（如果该 hook 走 `submitText` 则自动覆盖；如果独立发送则需手动 clear）

## 5. 测试

- [x] 5.1 更新 `session-store.test.ts`：替换 `composerDrafts persistence removed (BUG-0046)` 测试块为新的独立 key 测试——验证 `setComposerDraft` 写入独立 key、`clearComposerDraft` 删除独立 key、hydrate 正确恢复
- [x] 5.2 新增测试：发送路径中 `clearComposerDraft` 被调用后 localStorage key 为 null
- [x] 5.3 运行 `cd client && npx tsc --noEmit` 确认零类型错误
- [x] 5.4 运行 `cd client && npx vitest run` 确认所有测试通过
