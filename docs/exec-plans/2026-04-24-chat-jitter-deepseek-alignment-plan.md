# Chat Jitter DeepSeek Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 参考 DeepSeek 聊天页可观察到的消息列表与滚动稳定化思路，定位并修复 DataTalk 聊天区发送气泡抖动。

**Architecture:** 先做“可观察实现对照表”，把 DeepSeek 的公开行为信号映射到 DataTalk 当前实现，再按风险从低到高推进修复。优先修正 turn 生命周期、底部 follow 状态机和非核心 footer 挂载时机；只有在证据明确时才考虑更重的列表/布局调整。

**Tech Stack:** React 19, TypeScript, Zustand, Vitest, Testing Library.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 聊天消息与 composer 属于 `comfortable` 密度，修复不能改变消息宽度、气泡节奏、底部留白和对齐方式。
  - 保持现有 semantic token、字体与表面层级，不通过新增显眼动画或视觉重设计掩盖抖动。
  - 优先修复布局稳定性、滚动时序和 turn 挂载顺序；避免大范围改动聊天视觉语言。

## External Inputs

- Public DeepSeek web entry: <https://chat.deepseek.com>
- Public DeepSeek product/docs:
  - <https://www.deepseek.com/en/>
  - <https://api-docs.deepseek.com/news/news250115>
- Observable DeepSeek front-end signals from public assets:
  - React app with `createRoot` / `useLayoutEffect` / `flushSync`
  - virtual list container (`.ds-virtual-list`)
  - explicit `overflow-anchor:none`
  - explicit `scrollbar-gutter:stable`
  - dedicated bottom-follow triggers (`scrollToBottomTrigger` / `forceScrollToBottomTrigger`)
  - `requestAnimationFrame` / `IntersectionObserver` / `ResizeObserver` participation in list behavior

## Current vs DeepSeek

| Concern | DeepSeek observable behavior | DataTalk current behavior | Gap / Risk |
|---|---|---|---|
| Message list container | Uses a virtual list container | [`TurnList`](../../client/src/features/chat/components/turn/turn-list.tsx) renders full DOM list | Large DOM reflow remains possible when older turns mutate |
| Scroll anchoring | Explicitly disables browser anchoring | [`SplitView`](../../client/src/features/session/split-view.tsx) already sets `overflowAnchor: 'none'` on chat scroller | Likely not root cause, but must remain intact |
| Scrollbar stability | Uses stable scrollbar gutter | [`SplitView`](../../client/src/features/session/split-view.tsx) already sets `scrollbarGutter: 'stable'` | Baseline aligned |
| Bottom follow controller | Dedicated bottom-follow triggers + observer/raf signals | [`useAutoScroll`](../../client/src/hooks/use-auto-scroll.ts) uses `useLayoutEffect` + `MutationObserver` + frame suppression | Similar direction, but still vulnerable to turn-height mutations |
| Pending user identity | Public behavior suggests same pending turn upgrades in place | [`use-channel.ts`](../../client/src/services/channel/use-channel.ts) + [`use-session-turns.ts`](../../client/src/features/chat/components/helpers/use-session-turns.ts) already preserve stable pending→real user key | Aligned; not primary suspect unless DOM node still remounts |
| Assistant preallocation | Public behavior suggests assistant region does not jump when first response arrives | [`SessionTurn`](../../client/src/features/chat/components/turn/session-turn.tsx) currently mixes `min-h-9`, thinking shell, and visible-part gating | High-risk area for height change |
| Footer/meta timing | Public behavior suggests non-essential controls appear after content stabilizes | `Copy` / time / model footer still participate in turn height when conditions change | High-risk area for second-send jitter |
| Composer coupling | DeepSeek likely separates message list scroll ownership from composer shell | [`SplitView`](../../client/src/features/session/split-view.tsx) keeps composer in the same column layout | Medium risk, but prior hide/float experiment did not isolate the bug |

## Non-Goals

- 不做聊天 UI 重设计。
- 不在缺乏证据时直接上虚拟列表重构。
- 不通过关闭 auto-scroll、删除 footer、移除 composer 等方式“掩盖”问题。

## File Structure

- Inspect / Modify: `client/src/features/chat/components/turn/session-turn.tsx`
- Inspect / Modify: `client/src/features/chat/components/turn/user-bubble.tsx`
- Inspect / Modify: `client/src/features/chat/components/turn/assistant-stream.tsx`
- Inspect / Modify: `client/src/features/chat/components/helpers/use-session-turns.ts`
- Inspect / Modify: `client/src/features/session/split-view.tsx`
- Inspect / Modify: `client/src/hooks/use-auto-scroll.ts`
- Test: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- Test: `client/src/features/session/split-view.test.tsx`
- Test: `client/src/hooks/use-auto-scroll.test.tsx`
- Test: `client/src/services/channel/use-channel.test.ts`
- Modify: `docs/exec-plans/2026-04-24-chat-jitter-deepseek-alignment-plan.md`
- Modify: `docs/exec-plans/index.md`

## Task 1: Lock The Repro With DOM-Level Evidence

**Files:**
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- Modify: `client/src/features/session/split-view.test.tsx`

- [x] Add a failing regression test for the user-reported path: refreshed page, first send, second send, existing scrollbar present.
- [x] Assert not only scroll position, but also whether any previous turn grows in height during the next send.
- [x] Capture the exact DOM invariant we want: new pending user turn mounts once, previous completed/incomplete turn height stays stable across the same send.

**Status notes (2026-04-24):**
- `session-turn.test.tsx` now fails with previous-turn synthetic DOM height growing from `120` to `136` when a new pending user turn appends.
- `split-view.test.tsx` now fails with bottom scroll delta `124` while the new pending turn itself contributes only `108`, showing that an older turn also grows during the same send.

## Task 2: Audit Turn Height Mutations Against DeepSeek Invariants

**Files:**
- Modify: `client/src/features/chat/components/turn/session-turn.tsx`
- Modify: `client/src/features/chat/components/turn/user-bubble.tsx`
- Modify: `client/src/features/chat/components/turn/assistant-stream.tsx`

- [x] Enumerate every send-time height mutation source inside a turn: thinking placeholder, reasoning shell, copy footer, duration/model footer, pending placeholders.
- [x] Delay or reserve any non-essential footer/meta that still changes an older turn’s height during a new send.
- [x] Keep pending user and assistant placeholder DOM shape stable across `pending -> streaming -> completed`.

**Status notes (2026-04-24):**
- Root cause confirmed for the locked repro: when an incomplete assistant turn stopped being `isLastTurn`, `showCopyPartID` flipped from `null` to the last text part id, so the assistant copy/model/duration footer mounted into the older turn and added the extra `16px`.
- `SessionTurn` now gates assistant footer reveal on the turn being settled (`assistantMessages.every(time.completed is set)`), rather than merely no longer being the last turn.
- Audited send-time turn shell mutation sources in `SessionTurn` / `AssistantStream`; no second height source beyond the footer/meta mount was needed to eliminate the locked repro.
- Pending user bubble identity and assistant placeholder shell stayed stable for the reproduced `pending -> streaming -> completed` path; no additional DOM shape rewrite was required in this fix.

## Task 3: Re-Check Scroll Ownership Only After Turn Stability

**Files:**
- Modify: `client/src/hooks/use-auto-scroll.ts`
- Modify: `client/src/hooks/use-auto-scroll.test.tsx`
- Modify: `client/src/features/session/split-view.tsx`

- [x] Re-verify that bottom-follow writes happen only once per structural append wave.
- [x] Confirm the existing `lastScrollTop` path should stay untouched for this fix, since changing that ownership path previously reintroduced duplicate-message behavior. (No code change required for the locked repro.)
- [x] Only if Task 2 is green and jitter remains, evaluate whether composer/list ownership needs to move closer to DeepSeek’s “list owns scroll, composer is shell” model. (Not needed once Task 2/3 removed the reproduced jitter.)

**Status notes (2026-04-24):**
- `useAutoScroll` mutation follow is now batched behind `requestAnimationFrame`, so repeated streaming text mutations before the next paint only trigger one bottom-follow write.
- The synchronous `useLayoutEffect` scroll remains in place for structural appends, preserving the earlier fix that prevents a newly sent bubble from landing low for one frame and then jumping upward.
- Added `use-auto-scroll.test.tsx` regression covering repeated streaming text mutations while pinned to bottom.
- For fenced code blocks, `PacedMarkdown` now bypasses local 24ms staged reveal and renders the latest upstream text directly, reducing extra intermediate reflows during code streaming.
- The locked repro no longer required a deeper scroll-ownership rewrite; existing append-time ownership stayed in place after the turn-height fix and mutation batching landed.

## Task 4: Escalate Only If Evidence Still Points To List Architecture

**Files:**
- Inspect: `client/src/features/chat/components/turn/turn-list.tsx`
- Inspect: `client/src/features/chat/components/helpers/use-session-turns.ts`
- Inspect: `client/src/services/channel/use-channel.ts`

- [x] Compare full-list rendering vs. targeted virtualization cost using the locked repro. (Result: no follow-up needed for this fix.)
- [x] Decide whether virtualization is justified, or whether stable turn shells are sufficient. (Result: stable turn shells + follow batching were sufficient for the reproduced issue.)
- [x] If virtualization is needed, write a separate follow-up plan rather than bundling it into the jitter fix. (Not needed.)

## Task 5: Consolidated Verification

**Files:**
- Modify: `docs/exec-plans/2026-04-24-chat-jitter-deepseek-alignment-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] Run `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx src/features/session/split-view.test.tsx src/hooks/use-auto-scroll.test.tsx src/services/channel/use-channel.test.ts`.
- [x] Run `cd client && npx tsc --noEmit`.
- [x] Mark the plan with actual outcomes and move it from Active to Completed only after the repro path is verified manually.

**Final outcomes (2026-04-24):**
- Automated verification passed for the targeted chat regressions (`session-turn`, `split-view`, `use-auto-scroll`, `use-channel`) and related streaming/code-fence regressions.
- `npx tsc --noEmit` passed after the final chat, chooser, and connection-persistence fixes.
- Manual verification on the worktree app confirmed the reproduced “refresh then second send” jitter path no longer jumps, and the remaining code-stream jitter was reduced enough to close without a virtualization follow-up.

## Decision Rule

- If the repro is eliminated after Task 2, stop there.
- If Task 2 passes and jitter remains, continue to Task 3.
- If Task 3 still leaves jitter, open a separate virtualization / list-architecture plan instead of expanding scope here.
