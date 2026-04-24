# User Bubble Scroll Jitter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天区存在滚动条时，用户新发送气泡在底部出现时的垂直位置抖动。

**Architecture:** 保持现有 `SplitView` 布局和消息视觉不变，修复分三步推进。第一步收紧 `useAutoScroll`，避免同一轮 DOM mutation 对新消息追加做重复滚底；第二步把 `SessionTurn` 的 assistant 预留区从“粗略 `min-h-9` 空盒子”改成与真实 thinking indicator 同构的隐形壳，让 pending user 刚出现到 streaming 接管之间的 turn 高度保持完全一致；第三步禁止上一条尚未 completed 的 assistant turn 在下一次发送时突然长出 copy/meta footer，避免“第二条消息必现”的额外高度跳变。

**Tech Stack:** React 19, TypeScript, Zustand, Vitest, Testing Library.

---

## Design Inputs

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 聊天消息区属于 `comfortable` 密度，修复必须保持消息宽度、节奏、对齐和底部占位不变。
  - 正式 UI 继续沿用现有语义 token 与中性色体系，不通过新增动画或样式变化掩盖滚动抖动。
  - 修复优先落在滚动与占位稳定性层，不改变 `UserBubble` / `SessionTurn` 的既有视觉语言。

## Spec Mapping

- Source: [`docs/product-specs/2026-04-21-chat-scroll-jitter-reduction-design.md`](../product-specs/2026-04-21-chat-scroll-jitter-reduction-design.md)
- Covers:
  - §1/§3：消息视觉与对齐保持不变，只在滚动与占位稳定性层修复抖动。
  - §5.3/§5.4：减少不必要的 `scrollTop` 写入，并避免底部占位在发送链路里发生二次高度变化。

## File Structure

- Modify: `client/src/hooks/use-auto-scroll.ts`
- Modify: `client/src/hooks/use-auto-scroll.test.tsx`
- Modify: `client/src/features/chat/components/turn/session-turn.tsx`
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-user-bubble-scroll-jitter-plan.md`

## Task 1: 用 hook 测试锁定重复滚底

**Files:**
- Modify: `client/src/hooks/use-auto-scroll.test.tsx`

- [x] Step 1: 新增回归测试，覆盖 deps 驱动的 layout scroll 之后若连续到达多次 mutation callback，本轮发送不应再次触发 `scrollToBottom()`。
- [x] Step 2: 同一测试中补充“下一帧的新 mutation 仍可继续 auto-follow”的断言，避免修复把后续真实流式更新也一并吞掉。
- [x] Step 3: 运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx`，确认红灯来自当前 observer 仍会在同轮提交中二次滚底。

## Task 2: 最小修复 auto-scroll 抑制窗口

**Files:**
- Modify: `client/src/hooks/use-auto-scroll.ts`

- [x] Step 1: 将当前按次数扣减的 `skipMutationScrolls` 收紧为“提交后到下一帧前”的 observer 抑制窗口。
- [x] Step 2: 保持用户上滚暂停 follow、回到底部恢复 follow、session 切换显式滚底等现有状态机行为不变。
- [x] Step 3: 重新运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx`，确认转绿。

## Task 3: 稳定 pending turn 的 assistant 占位壳

**Files:**
- Modify: `client/src/features/chat/components/turn/session-turn.tsx`
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`

- [x] Step 1: 新增回归测试，覆盖 pending user 刚出现且 streaming 尚未翻上来时，assistant 区必须已经渲染与真实 thinking indicator 同构的隐形壳。
- [x] Step 2: 将 `SessionTurn` 中粗略的 `min-h-9` 预留策略升级为“同构壳 + `invisible` 切换”，保持 pending → streaming 的 turn 高度完全一致。
- [x] Step 3: 运行 `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx src/features/chat/components/turn/__tests__/reasoning-part.test.tsx`，确认转绿。

## Task 4: 集中验证与文档收尾

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/2026-04-24-user-bubble-scroll-jitter-plan.md`

- [x] Step 1: 运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx src/features/session/split-view.test.tsx`。
- [x] Step 2: 运行 `cd client && npx tsc --noEmit`。
- [x] Step 3: 将本计划 checklist 更新为实际执行结果。
- [x] Step 4: 将本计划从 `docs/exec-plans/index.md` 的 Active 移到 Completed。

## Execution Notes

- [x] Task 1 Step 1-2: 在 `client/src/hooks/use-auto-scroll.test.tsx` 新增 `suppresses repeated mutation callbacks from the same append until the next frame`，用 mock `MutationObserver + requestAnimationFrame` 锁定同轮连续 callback 的重复滚底问题，同时保留下一帧继续 follow 的断言。
- [x] Task 1 Step 3: 初次运行 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx` 红灯，失败为 `expected "scrollTo" to not be called at all, but actually been called 1 times`，说明第三次同轮 mutation callback 仍触发了二次滚底。
- [x] Task 2 Step 1: `useAutoScroll` 已将计数型 `skipMutationScrolls` 改为整帧抑制窗口：layout-effect 滚底后直到下一帧前，observer callback 统一忽略。
- [x] Task 2 Step 2: 现有 `followEnabled` / `isAtBottom` 状态机、session 切换显式滚底、用户上滚暂停 follow 的语义保持不变。
- [x] Task 2 Step 3: 修复后 `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx` 通过（4 tests）。
- [x] Task 3 Step 1: 在 `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx` 新增“pending last turn 在 streaming 翻上来前就应渲染隐形 thinking shell”的回归测试；初次运行红灯，DOM 中只有粗略 `.min-h-9` 空盒子，没有同构壳。
- [x] Task 3 Step 2: `SessionTurn` 现在在 `reserveAssistantSpace && !anyVisiblePart` 时始终挂同构的 assistant thinking shell；未进入 streaming 时通过 `invisible` 隐藏，进入 streaming 后复用同一 DOM 直接显现。
- [x] Task 3 Step 3: `cd client && npx vitest run src/features/chat/components/turn/__tests__/session-turn.test.tsx src/features/chat/components/turn/__tests__/reasoning-part.test.tsx` 通过（2 files, 22 tests）。
- [x] Task 3 Step 4: 结合“刷新后第一条正常、第二条必现”的新线索，补充回归测试锁定上一条 assistant turn 在未 completed 时失去 `isLastTurn` 会突然长出 `Copy` footer；`SessionTurn` 现仅在 assistant turn 完整 completed 后才允许展示 `showCopyPartID`，避免第二次发送时旧 turn 额外增高。
- [x] Task 4 Step 1: `cd client && npx vitest run src/hooks/use-auto-scroll.test.tsx src/features/session/split-view.test.tsx` 通过（2 files, 11 tests）。
- [x] Task 4 Step 2: `cd client && npx tsc --noEmit` 通过。
