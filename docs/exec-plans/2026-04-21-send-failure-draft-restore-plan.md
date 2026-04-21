# Send Failure Draft Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复普通 AI 消息发送失败后输入内容丢失的问题，让失败文本立即回到 AI 输入框，而不是等刷新后浏览器自己恢复。

**Architecture:** 保持现有 pending-user 与错误 toast 机制不变，只补一条“失败后恢复 composer draft”的显式状态链路。`useChannel.sendMessage` 返回成功/失败，`PromptComposer` 与 `usePendingPromptResume` 在失败时把原始文本回填到 composer。

**Tech Stack:** React 19 + TypeScript + Zustand + Vitest。

---

**Execution Notes (2026-04-21):**
- `useChannel.sendMessage` 现在显式返回成功/失败，避免 composer 在错误被内部吞掉后无法恢复输入草稿。
- 新增 `session-store.composerRestoreDraft` 作为一次性恢复槽位，覆盖当前会话直发失败与 queued pending prompt 失败两条路径。
- 验证已通过：`npx vitest run src/features/session/__tests__/prompt-composer.test.tsx src/features/session/hooks/__tests__/use-pending-prompt-resume.test.tsx src/stores/session-store.test.ts`、`npx tsc --noEmit`。

## File Structure Map

### Modify

- `client/src/services/channel/use-channel.ts`
- `client/src/stores/session-store.ts`
- `client/src/stores/session-store.test.ts`
- `client/src/features/session/prompt-composer.tsx`
- `client/src/features/session/hooks/use-pending-prompt-resume.ts`
- `client/src/features/session/__tests__/prompt-composer.test.tsx`
- `client/src/features/session/hooks/__tests__/use-pending-prompt-resume.test.tsx`
- `docs/exec-plans/index.md`

## Task 1: Lock Failure Recovery With Tests

- [x] Add a composer regression test for active-session send failure restoring the textarea immediately
- [x] Add a hook regression test for queued pending-prompt send failure producing a restore draft
- [x] Run the targeted vitest cases and confirm they fail before the fix

## Task 2: Restore Drafts On Send Failure

- [x] Add transient session-store state for composer draft restoration
- [x] Return success/failure from `useChannel.sendMessage`
- [x] Restore the original text in `PromptComposer` / `usePendingPromptResume` when send fails

## Task 3: Verify And Housekeeping

- [x] Run the targeted vitest suite
- [x] Run `cd client && npx tsc --noEmit`
- [x] Mark the plan complete and move it from Active to Completed in `docs/exec-plans/index.md`
