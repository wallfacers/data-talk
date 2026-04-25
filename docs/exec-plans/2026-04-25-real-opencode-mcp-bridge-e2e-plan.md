# Real OpenCode MCP Bridge E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close `TD-028` by adding an opt-in integration test that starts a real OpenCode process and proves a DataTalk MCP tool call reaches the backend through the OpenCode plugin-injected bridge fields.

**Architecture:** Keep the existing WireMock smoke tests as the default deterministic CI gate, and add a separate real-process `*IT` that is skipped unless explicitly enabled. The new test drives the normal DataTalk message path into embedded OpenCode, waits for a server-side MCP action invocation, and asserts the backend did not receive hand-built `__dt*` fields from the test harness.

**Tech Stack:** Spring Boot 3.5, Java 21, JUnit 5, Failsafe, Awaitility, JdbcTemplate, embedded OpenCode 1.4.7, existing MCP bootstrap/plugin infrastructure.

---

## Design Inputs

- Source: [docs/exec-plans/tech-debt-tracker.md](./tech-debt-tracker.md), `TD-028`
- Source: [docs/references/opencode-protocol.md](../references/opencode-protocol.md)
- Source: [docs/exec-plans/2026-04-24-opencode-mcp-tool-migration-plan.md](./2026-04-24-opencode-mcp-tool-migration-plan.md)

## Scope

- Add a real OpenCode bridge integration test in the adapter module.
- Keep the test opt-in with `DATATALK_REAL_OPENCODE_E2E=true`.
- Require `DATATALK_REAL_OPENCODE_MODEL=<provider>/<model>` for the model-driven tool-call leg.
- Verify plugin-injected bridge fields by observing successful `datatalk.list_connections` execution without constructing `__dt*` fields in the test.
- Document the default and opt-in verification commands.

## Non-Goals

- Do not replace the existing WireMock `EndToEndSmokeIT`.
- Do not make normal `mvn verify` depend on external model credentials.
- Do not add a test-only production action.
- Do not rework MCP bootstrap, naming, prompt, or frontend renderer behavior.

## File Structure Map

| File | Responsibility |
|------|----------------|
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/RealOpenCodeMcpBridgeIT.java` | Opt-in real-process integration test for OpenCode plugin bridge injection |
| `docs/exec-plans/2026-04-25-real-opencode-mcp-bridge-e2e-plan.md` | Active execution plan and checklist |
| `docs/exec-plans/index.md` | Active/Completed plan registration |
| `docs/exec-plans/tech-debt-tracker.md` | Move `TD-028` to cleared only after the opt-in test passes |

## Environment Contract

The real-process test must be skipped unless both conditions are true:

```text
DATATALK_REAL_OPENCODE_E2E=true
DATATALK_REAL_OPENCODE_MODEL=<provider>/<model>
```

The OpenCode provider credentials must already be available to OpenCode through the operator's normal config or environment. The test does not write provider credentials.

## Task 1: Add Opt-In Real OpenCode Test Scaffold

**Files:**
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/RealOpenCodeMcpBridgeIT.java`

- [x] **Step 1: Create the skipped-by-default Spring integration test class**

Add the class with the existing adapter smoke package and these key annotations:

```java
package com.datatalk.adapter.smoke;

import com.datatalk.DataTalkApplication;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@EnabledIfEnvironmentVariable(named = "DATATALK_REAL_OPENCODE_E2E", matches = "true")
@EnabledIfEnvironmentVariable(named = "DATATALK_REAL_OPENCODE_MODEL", matches = ".+")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = DataTalkApplication.class,
    properties = {
        "datatalk.mcp.enabled=true",
        "datatalk.opencode.serve.enabled=true",
        "datatalk.opencode.required=true",
        "datatalk.opencode.serve.strip-proxy-env=false"
    }
)
class RealOpenCodeMcpBridgeIT {
    static Path configDir;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) throws IOException {
        configDir = Files.createTempDirectory("datatalk-real-opencode-");
        registry.add("datatalk.mcp.config-dir", () -> configDir.toString());
        registry.add("datatalk.opencode.serve.base-port", () -> "4196");
        registry.add("datatalk.opencode.serve.port-retries", () -> "50");
    }
}
```

- [x] **Step 2: Skip when the model contract is incomplete**

Also keep a method-level guard so the failure mode remains a skipped test if the class-level condition is changed later:

```java
String model = System.getenv("DATATALK_REAL_OPENCODE_MODEL");
assumeTrue(model != null && !model.isBlank(),
    "DATATALK_REAL_OPENCODE_MODEL must be set for real OpenCode E2E");
```

Expected: the class is skipped unless `DATATALK_REAL_OPENCODE_E2E=true` and `DATATALK_REAL_OPENCODE_MODEL` is non-empty; if that class-level condition is changed later, the method-level assumption still reports the real test as skipped rather than failed.

- [x] **Step 3: Add cleanup for generated config files**

Add an `@AfterAll` method that deletes `configDir` recursively using `Files.walk(configDir).sorted(reverseOrder())`.

- [x] **Step 4: Run the default command and verify the test is skipped**

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am verify -Dit.test=RealOpenCodeMcpBridgeIT
```

Expected: build succeeds and reports the test skipped when `DATATALK_REAL_OPENCODE_E2E` is not set to `true`.

Observed 2026-04-25: exit code 0. `RealOpenCodeMcpBridgeIT` report shows 1 test, 0 failures, 0 errors, 1 skipped with reason `Environment variable [DATATALK_REAL_OPENCODE_E2E] does not exist`.

## Task 2: Drive the Real Plugin Bridge Path

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/RealOpenCodeMcpBridgeIT.java`

- [x] **Step 1: Wire existing services and repositories into the test**

Add fields for:

```java
@Autowired AiUserPrefsRepository userPrefs;
@Autowired ChannelService channelService;
@Autowired SessionService sessionService;
@Autowired OpenCodeBridgeStatus bridgeStatus;
@Autowired OpenCodeHttpClient openCodeHttpClient;
@Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;
```

- [x] **Step 2: Clean SQLite-backed test state before each run**

Add a `@BeforeEach` method that deletes rows in dependency order:

```java
@BeforeEach
void cleanDatabase() {
    datatalkJdbc.update("DELETE FROM events");
    datatalkJdbc.update("DELETE FROM action_invocations");
    datatalkJdbc.update("DELETE FROM artifacts");
    datatalkJdbc.update("DELETE FROM query_results");
    datatalkJdbc.update("DELETE FROM session_data_contexts");
    datatalkJdbc.update("DELETE FROM synthetic_session_messages");
    datatalkJdbc.update("DELETE FROM sessions");
    datatalkJdbc.update("DELETE FROM connections");
}
```

- [x] **Step 3: Add the real E2E assertion**

Add a test method named `realOpenCodeToolCallInjectsBridgeFieldsThroughPlugin` that:

1. Reads `DATATALK_REAL_OPENCODE_MODEL`.
2. Calls `userPrefs.setCurrentModel(model)`.
3. Creates a DataTalk session through `sessionService.create(null, "real opencode mcp bridge").record()`.
4. Calls `channelService.sendMessage(session.id(), List.of(new TextPart("Call the datatalk_list_connections tool exactly once, then answer with the number of connections. Do not call any other tools.")))`.
5. Uses Awaitility for up to 90 seconds until `action_invocations` contains one completed row for `session.id()` and `action_id = 'datatalk.list_connections'`.
6. Asserts `input_json` does not contain `__dtOpenCodeSessionId`, `__dtCallId`, or `__dtBridgeNonce`.
7. Asserts `sessions.opencode_sid` is non-empty and `bridgeStatus.snapshot().status()` is `ok`.

The assertion proves the missing path because the test never builds bridge args manually; without the real OpenCode plugin mutating `output.args` in place, `McpActionBridge` rejects `tools/call` before `ActionDispatcher` can write `action_invocations`.

- [x] **Step 4: Add failure diagnostics**

When the Awaitility assertion times out, include these values in the assertion message:

```text
OpenCode base URL
OpenCode bridge status
OpenCode bridge reason
session opencode_sid
recent action_invocations rows
recent events rows
```

Use `JdbcTemplate.queryForList(...)` for the row snapshots so failures show whether the model did not call the tool, the plugin did not inject fields, or the backend rejected the call.

## Task 3: Keep Existing Smoke Coverage Deterministic

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/EndToEndSmokeIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/RealOpenCodeMcpBridgeIT.java`

- [x] **Step 1: Add a comment in `EndToEndSmokeIT` explaining its boundary**

Add a short class-level comment:

```java
/*
 * This smoke test verifies DataTalk's MCP endpoint and bootstrap/reconcile logic
 * without starting a real OpenCode process. Real OpenCode plugin injection is
 * covered by RealOpenCodeMcpBridgeIT when DATATALK_REAL_OPENCODE_E2E=true.
 */
```

- [x] **Step 2: Ensure `EndToEndSmokeIT` keeps its current assertions**

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am verify -Dit.test=EndToEndSmokeIT
```

Expected: all existing `EndToEndSmokeIT` tests pass.

Observed 2026-04-25: exit code 0. `EndToEndSmokeIT` report shows 6 tests, 0 failures, 0 errors, 0 skipped.

- [x] **Step 3: Ensure default verify is still deterministic**

Run:

```bash
cd server && mvn -q -pl data-talk-adapter -am verify
```

Expected: build passes without requiring `DATATALK_REAL_OPENCODE_E2E` or model credentials.

Observed 2026-04-25: exit code 0. `DATATALK_REAL_OPENCODE_E2E` and `DATATALK_REAL_OPENCODE_MODEL` were unset; `RealOpenCodeMcpBridgeIT` remained skipped. The environment logged Docker/Testcontainers unavailability for the PostgreSQL container branch, and the suite handled it without failing.

## Task 4: Opt-In Verification and Documentation Housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-25-real-opencode-mcp-bridge-e2e-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/tech-debt-tracker.md`

- [x] **Step 1: Run backend compile**

Run:

```bash
cd server && mvn compile -q
```

Expected: compile succeeds.

Observed 2026-04-25: `cd server && mvn compile -q` exited 0.

- [x] **Step 2: Run the opt-in real-process bridge test**

Run with a configured model:

```bash
cd server && DATATALK_REAL_OPENCODE_E2E=true DATATALK_REAL_OPENCODE_MODEL=<provider>/<model> mvn -q -pl data-talk-adapter -am verify -Dit.test=RealOpenCodeMcpBridgeIT
```

Expected: `RealOpenCodeMcpBridgeIT` starts embedded OpenCode, completes `datatalk.list_connections`, and passes.

Observed 2026-04-25: exit code 0 with `DATATALK_REAL_OPENCODE_E2E=true` and `DATATALK_REAL_OPENCODE_MODEL=alibaba-coding-plan-cn/qwen3-coder-plus`. `RealOpenCodeMcpBridgeIT` report shows 1 test, 0 failures, 0 errors, 0 skipped, elapsed 22.33s. Logs show existing OpenCode binary `/home/wallfacers/.data-talk/opencode/v1.4.7/opencode` was used, OpenCode served on `127.0.0.1:4196`, the event loop subscribed successfully, and `McpActionBridge` dispatched `tool=list_connections action=datatalk.list_connections` through the embedded OpenCode path.

- [x] **Step 3: Update the plan checklist with observed results**

Record the exact model used, whether OpenCode came from local cache or bundled resource, and the command exit codes.

- [x] **Step 4: Close `TD-028` only after Step 2 passes**

Move `TD-028` from the current debt table to the cleared debt table in `docs/exec-plans/tech-debt-tracker.md`, referencing this plan and the successful opt-in command.

- [x] **Step 5: Move this plan to Completed**

Move the index entry for this plan from Active to Completed in `docs/exec-plans/index.md` with a summary that names the new opt-in command.

## Exit Criteria

- Default `data-talk-adapter` verify remains green without real OpenCode credentials.
- Opt-in `RealOpenCodeMcpBridgeIT` passes with a configured OpenCode model.
- The test does not construct or pass `__dt*` fields itself.
- `TD-028` is moved to cleared debt only after the opt-in real-process test passes.
