# TD-033 Remove Close Alias Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the deprecated `workspace.close` / `query_editor.close` protocol alias and close TD-033.

**Architecture:** This is a hard protocol cleanup. Backend action schema and runtime agent prompt stop advertising `close`; frontend adapters stop accepting `close`; tests assert old calls are rejected as `unknown_action`. Canonical docs and the technical debt tracker are updated after verification.

**Tech Stack:** Spring Boot 3.5 / Java 21, React 19 / Vitest, DataTalk UI object protocol.

---

## Design Inputs

- `client/DESIGN.md` was read on 2026-04-29. No UI, visual, layout, typography, token, or component-state changes are in scope.
- Existing Stage semantics remain: top-bar X and UI close controls still detach through store/UI code; only the AI protocol alias named `close` is removed.

## Tasks

- [x] **Task 1: Write failing tests for removed alias**
  - Update `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts` so `close` is rejected as `unknown_action`.
  - Update `client/src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` so `read('actions')` excludes `close` and `close` is rejected.
  - Update `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java` so backend schema excludes `close`.
  - Update `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java` so prompt tests no longer expect deprecation text.

- [x] **Task 2: Remove runtime alias support**
  - Remove `close` from `WorkspaceAdapter` and `QueryEditorAdapter` action lists and switch branches.
  - Remove `close` from `UiExecAction` action enums and descriptions.
  - Remove `close` from `client/src/features/actions/ui-handlers.ts` mutating action list.

- [x] **Task 3: Update docs and prompt**
  - Remove deprecated alias language from `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`.
  - Remove `close` rows and deprecation notes from `docs/references/ui-objects-reference.md`.
  - Move TD-033 from current debt to cleared debt in `docs/exec-plans/tech-debt-tracker.md`.
  - Update backend action i18n descriptions in `messages.properties` and `messages_zh_CN.properties`.

- [x] **Task 4: Verify and close plan**
  - Run targeted frontend adapter tests.
  - Run targeted backend action/prompt tests with `-am`.
  - Run required frontend typecheck and backend compile.
  - Mark this plan completed and move its index entry to Completed.

## Completion Notes

- RED verified:
  - `npx vitest run src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` failed on the old `close` exposure/acceptance.
  - `mvn -q -pl data-talk-adapter -am -Dtest=UiActionsTest,AgentPromptContractTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` failed on old schema/prompt deprecation text.
- GREEN verified:
  - `npx vitest run src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` passed: 36 tests.
  - `npx vitest run src/features/actions/__tests__/ui-handlers.test.ts` passed: 8 tests.
  - `mvn -q -pl data-talk-adapter -am -Dtest=UiActionsTest,AgentPromptContractTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` passed: `UiActionsTest` 8 tests, `AgentPromptContractTest` 13 tests.
  - `npx tsc --noEmit` passed.
  - `mvn compile -q` passed.
