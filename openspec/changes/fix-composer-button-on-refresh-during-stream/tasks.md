## 1. BUG Registration (Pre-Implementation)

- [x] 1.1 Bump `docs/bugs/index.md` "当前编号" from `BUG-0046` to `BUG-0047`
- [x] 1.2 Create `docs/bugs/BUG-0046-composer-button-refresh-during-stream-regression.md` with frontmatter: `status: open`, `priority: P1`, `source: manual-report`, `modules: [session, chat, channel]`, `discoveredBy: human`, `regression: true`, `discovered: 2026-05-15`. Body: Summary, Reproduction Steps, Expected vs Actual, Environment, Evidence (link to BUG-0037 / BUG-0038 closure context), Root Cause (TBD), Fix (TBD), Verification (TBD)
- [x] 1.3 Update `docs/bugs/index.md` "Open BUGs" table with the new row and "By Module" / "By Source" aggregations

## 2. Implementation (Frontend)

Design Inputs: [client/DESIGN.md](../../../client/DESIGN.md) — no token / visual change; modifies only `streamingBySession` state machine. Composer button uses existing `bg.panel` + `interaction.focusRing` + `Loader2Icon` (spin) / `ArrowUpIcon` shape-based state differentiation (accessibility: "State cannot be communicated by color alone" satisfied unchanged).

- [x] 2.1 In `client/src/services/channel/use-channel.ts`, refactor `sendMessage`:
  - Introduce `let streamOpened = false` before the try block
  - Wrap `sink` in a `guardedSink` that flips `streamOpened = true` on the first `evt.event === 'connected'`
  - Pass `guardedSink` to `client.sendMessage(parts, guardedSink)`
  - Remove the `finally { setStreaming(false) }` block entirely
  - In the catch block, add: `if (!streamOpened) { useChatPartsStore.getState().setStreaming(sessionId, false) }` BEFORE the existing `markPendingUserFailed`
- [x] 2.2 Apply the identical refactor to `retryPendingUser` in the same file
- [x] 2.3 Verify `client/src/features/session/prompt-composer.tsx` requires no change (read-only check: it still derives `isStreaming` from `useChannel`)

## 3. Tests (Frontend)

- [x] 3.1 Add `BUG-0046 stream-lifecycle vs request-lifecycle` describe block to `client/src/services/channel/use-channel.test.ts`
- [x] 3.2 Test case: `sendMessage clears streaming when POST fails before connected frame` — mock `ChannelClient.sendMessage` to reject without ever invoking sink → assert `streamingBySession.has(sessionId)` is `false` after the catch path
- [x] 3.3 Test case: `sendMessage preserves streaming when POST fails after connected frame` — mock `ChannelClient.sendMessage` to invoke sink with `{event:'connected', id:1, data:{}}` then reject → assert `streamingBySession.has(sessionId)` is still `true`
- [x] 3.4 Test case: `sendMessage clears streaming via session.idle on natural completion` — mock to invoke sink with `connected` + `session.idle` then resolve → assert `streamingBySession.has(sessionId)` is `false` (cleared via SSE path, not finally)
- [x] 3.5 Test case: `retryPendingUser preserves streaming when POST aborts after connected` — mirror case 3.3 for retryPendingUser entry point
- [x] 3.6 Test case: `retryPendingUser clears streaming on pre-stream failure` — mirror case 3.2 for retryPendingUser
- [x] 3.7 Ensure existing BUG-0037 and BUG-0038 test suites (`replay suppression`, `setStreaming sync write`) continue to pass without modification

## 4. Verification

- [x] 4.1 Run `cd client && npx tsc --noEmit` — expect zero type errors (passed; no output)
- [x] 4.2 Run `cd client && npx vitest run src/services/channel src/stores` — expect all tests (existing + new) green (114/114 passed across 7 files)
- [x] 4.3 Run `cd client && npx eslint src/services/channel/use-channel.ts` — 12 errors reported but **all pre-existing** (`any` types + 1 empty block); zero new errors introduced by this change (verified by stashing + re-running). File-level tech debt is out of scope for this BUG fix.
- [ ] 4.4 Manual Tauri repro: send a long-running AI message; mid-stream press Ctrl+R; confirm composer button stays as `Loader2Icon` (spin) until OpenCode emits `session.idle` over the GET subscribe stream. Capture screenshot under `tmp/bug-0046-verify/` (**requires interactive Tauri session — to be performed by user after commit**)

## 5. BUG Closure

- [x] 5.1 Update `docs/bugs/BUG-0046-*.md` Root Cause / Fix / Verification sections with concrete content (filled during initial registration)
- [ ] 5.2 Change frontmatter `status` to `fixed`, fill `fixCommit` with the implementation commit short SHA, optionally fill `testRunId` (**deferred until commit is created**)
- [ ] 5.3 Move BUG-0046 row from "Open BUGs" to "In Progress" in `docs/bugs/index.md` with `status=fixed` (**deferred until 5.2**)
- [ ] 5.4 Update "By Module" entries for `session`, `chat`, `channel` to include BUG-0046 (fixed) (**deferred until 5.2**)

## 6. OpenSpec Archive

- [ ] 6.1 Run `/opsx:archive fix-composer-button-on-refresh-during-stream` to merge `specs/composer-streaming-state/spec.md` into `openspec/specs/composer-streaming-state/spec.md` (new canonical spec) and move the change directory to `openspec/changes/archive/YYYY-MM-DD-fix-composer-button-on-refresh-during-stream/` (**deferred until 4.4 + 5.x complete**)
