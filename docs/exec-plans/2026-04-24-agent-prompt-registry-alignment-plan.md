# Agent Prompt Registry Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the runtime agent prompt with the actually registered DataTalk actions so the AI only sees supported, implemented workflows.

**Architecture:** Keep the authoritative action surface in code (`@DataTalkAction` on the server plus `registerClientHandler(...)` on the client), then shrink `server/.../agents/AGENTS.md` to the currently implemented subset. Add small regression tests so prompt drift is caught automatically.

**Tech Stack:** Spring Boot 3.5, JUnit 5, React/Vitest, runtime agent prompt resources.

---

## Scope

- Refactor `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` into a shorter English prompt.
- Remove undocumented or unimplemented UI scenarios from the prompt.
- Verify prompt-listed actions are actually registered.
- Verify client-executed actions have client handlers.
- Remove clearly unused exposed actions if they do not belong in the production tool surface.

## Non-Goals

- No new agent capabilities.
- No frontend UI feature implementation for placeholder tabs.
- No protocol redesign.

## File Map

- Modify: `docs/exec-plans/index.md`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/discovery/DiscoveryControllerIT.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- Create: `client/src/features/actions/__tests__/client-handler-registration.test.ts`
- Optional modify/remove: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/DemoEchoAction.java` and related tests/resources if the action is removed from the production registry

## Tasks

### Task 1: Lock the current contract with tests

- [x] Add a backend test that parses `AGENTS.md`, extracts referenced `datatalk.*` actions, and asserts every listed action exists in `ActionRegistry`.
- [x] Add a frontend test that asserts every client-executed action currently exposed to AI has a registered client handler.
- [x] Run the targeted tests first and confirm they fail for the current drift or missing coverage.

### Task 2: Refactor the runtime prompt

- [x] Rewrite `AGENTS.md` in concise English.
- [x] Keep only implemented actions and supported workflows.
- [x] Remove placeholder or not-yet-implemented UI scenarios, especially unsupported `workspace.open` targets and misleading persistence claims.
- [x] Update any action descriptions whose examples contradict the current runtime schema or behavior.

### Task 3: Reduce accidental tool surface

- [x] Evaluate `datatalk.demo.echo` and remove it from production if it is only legacy smoke-test surface.
- [x] Update the affected smoke/unit tests to use a supported action or generic tool name.

### Task 4: Verify and close

- [x] Run `cd server && mvn compile -q`
- [x] Run the targeted backend tests for prompt/action discovery.
- [x] Run `cd client && npx vitest run src/features/actions/__tests__/client-handler-registration.test.ts src/features/actions/__tests__/ui-handlers.test.ts`
- [x] Run `cd client && npx tsc --noEmit`
- [x] Mark this plan complete and move it from Active to Completed in `docs/exec-plans/index.md`.

## Execution Notes

- `datatalk.demo.echo` was removed from the production action surface because it only existed as legacy smoke-test tooling.
- The smoke callback test now uses `datatalk.list_connections`, which still proves the full callback-to-dispatch path without relying on a synthetic production tool.
- The runtime prompt now documents only the currently implemented UI object types: `workspace` and `query_editor`.
