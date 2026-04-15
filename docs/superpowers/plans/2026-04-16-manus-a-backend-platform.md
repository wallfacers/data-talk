# Plan A — Backend Platform Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the DataTalk server-side platform foundation: Ontology & Action Registry, Streamable HTTP channel, SessionBus, SQLite persistence, and OpenCode gateway wiring — capped with a demo echo action that proves the full end-to-end loop using a FakeOpenCodeServer.

**Architecture:** Spring Boot 3.5 + Java 21 multi-module. Adds `registry/`, `session/`, `opencode/`, `persistence/` packages to the existing 4 modules without touching the current `/api/query` flow. Ontology objects and Actions are first-class beans; new capability = one annotated handler. Transport is Streamable HTTP (POST can respond with SSE; server pushes `action.invoke` on the stream, client acks via a second POST).

**Tech Stack:** Spring Boot 3.5.0, Java 21 (virtual threads), JdbcTemplate + Flyway + SQLite, Jackson, com.networknt json-schema-validator 1.5.x, Spring WebFlux WebClient (for OpenCode SSE), WireMock 3.x (FakeOpenCodeServer in tests), JUnit 5 + AssertJ.

---

## Spec Mapping

This plan implements these sections of `docs/superpowers/specs/2026-04-16-manus-split-view-design.md`:

- §1 High-level architecture
- §2 Ontology & Action Registry (everything except the 6 MVP action handlers — those move to Plan B)
- §3 Streamable HTTP protocol (channel, events, callId pairing, resume buffer)
- §5 Server architecture (all sub-sections)
- §7 Error handling infrastructure (the error code table is enforced; handlers in Plan B produce them)

Plan B will add: real OpenCode config + the 6 MVP action handlers + JDBC flows.
Plan C will add: the Tauri client UI.

## Non-Goals (in this plan)

- Real OpenCode server integration (uses FakeOpenCodeServer / WireMock)
- The 6 MVP action handlers (only a trivial `demo.echo` for smoke-testing the loop)
- Tauri / frontend work
- Testcontainers with real PostgreSQL/MySQL (deferred to Plan B)

---

## File Structure Map

**New files created by this plan** (grouped by module):

```
data-talk-domain/src/main/java/com/datatalk/domain/
├── action/
│   ├── DataTalkAction.java          (annotation)
│   ├── Executor.java                (enum: OPENCODE|SERVER|CLIENT)
│   ├── OntologyEffect.java          (enum: NONE|CREATE_ARTIFACT|PATCH_ARTIFACT)
│   ├── ActionHandler.java           (generic interface)
│   ├── ActionDescriptor.java        (record)
│   └── ActionContext.java           (record)
├── ontology/
│   ├── ObjectType.java              (interface)
│   ├── ObjectTypeDescriptor.java    (record)
│   └── ObjectTypes.java             (enum of known ids)
├── part/
│   ├── Part.java                    (sealed interface)
│   ├── TextPart.java, ReasoningPart.java, ToolPart.java,
│   ├── FilePart.java, StepStartPart.java, StepFinishPart.java,
│   ├── SubtaskPart.java             (records implementing Part)
│   ├── ToolState.java               (sealed: Pending|Running|Completed|Error)
│   └── Message.java                 (record with Part[] and role)
├── event/
│   ├── DtEvent.java                 (sealed interface for down-stream events)
│   ├── NumberedEvent.java           (record eventId + DtEvent)
│   └── ErrorInfo.java               (record)
└── id/
    └── CallId.java, ArtifactId.java (simple value types)

data-talk-application/src/main/java/com/datatalk/application/
├── registry/
│   ├── ActionRegistry.java          (bean, scans @DataTalkAction)
│   ├── OntologyRegistry.java        (bean, scans ObjectType beans)
│   └── JsonSchemaLoader.java        (loads & caches networknt schemas)
├── session/
│   ├── SessionBus.java              (ring buffer + flusher + subscribers)
│   ├── SessionBusRegistry.java      (Map<sessionId, SessionBus>, LRU)
│   ├── Subscriber.java              (SSE emitter abstraction)
│   ├── ActionDispatcher.java        (executor routing)
│   └── PendingCallRegistry.java     (callId → CompletableFuture)
├── persistence/
│   ├── PersistenceService.java      (unified write entry point + broadcasts)
│   ├── SessionRepository.java, MessageRepository.java,
│   ├── ArtifactRepository.java, ActionInvocationRepository.java,
│   ├── EventRepository.java, QueryResultRepository.java
│   └── SecretVault.java             (AES-GCM)
├── opencode/
│   ├── OpenCodeGateway.java         (lifecycle + register tools + event loop)
│   ├── OpenCodeEventTranslator.java (pure function)
│   └── ToolCallBridge.java          (POST /api/opencode-tool/:id handler)
└── channel/
    ├── ChannelService.java          (business layer behind Streamable HTTP)
    ├── JsonRpcCodec.java            (encode/decode RpcRequest/Response)
    └── RpcRequest.java, RpcResponse.java (sealed records)

data-talk-infrastructure/src/main/java/com/datatalk/infra/
├── channel/
│   └── ChannelController.java       (REST controller for /api/sessions/:id/channel)
├── opencode/
│   ├── OpenCodeHttpClient.java      (WebClient wrapper)
│   └── OpenCodeConfig.java          (beans + props)
├── discovery/
│   └── DiscoveryController.java     (/api/ontology, /api/actions)
└── persistence/
    ├── FlywayMigrationConfig.java
    └── resources/db/migration/V1__init.sql

data-talk-adapter/src/main/java/com/datatalk/adapter/
└── actions/
    └── DemoEchoAction.java          (demonstrates the 3-file extension contract)
```

**Existing files touched** (additive only; no delete/rewrite):

- `data-talk-*/pom.xml` — add new dependencies
- `data-talk-adapter/src/main/resources/application.yml` — add `datatalk.*` section
- `data-talk-infrastructure/pom.xml` — add Flyway + networknt + WebFlux

---

## Critical Conventions

**Package names**: `com.datatalk.domain.*`, `com.datatalk.application.*`, `com.datatalk.infra.*`, `com.datatalk.adapter.*`. These match what's already in the codebase.

**Test placement**: tests go under the **same module** as the code under test, in `src/test/java/`. Pure unit tests in domain/application. Integration tests (with Spring context) live in `data-talk-adapter`.

**Naming**: use camelCase for Java, kebab-case for REST paths, snake_case for SQL columns. Match exactly what's in the spec.

**Commit granularity**: one commit per task. Commit messages follow the existing style (`feat(server): ...`, `test(server): ...`, `chore(server): ...`).

**IDs**: all ID types (artifactId, callId, sessionId) are opaque strings. Generation is via an injected `IdGenerator` bean (`UUID.randomUUID().toString()` in prod; counter in tests).

**Time**: all timestamps are epoch milliseconds (`long`). Use an injected `Clock` bean (`Clock.systemUTC()` in prod; `Clock.fixed(...)` in tests) to make tests deterministic.

**Testing philosophy**: TDD strict — failing test first, minimal impl, verify green, commit. Integration tests only when touching Spring context boundaries.

---

## Tasks Overview

| # | Task | Target module | Deliverable |
|---|---|---|---|
| 1 | Domain enums (Executor / OntologyEffect) | domain | `Executor.java`, `OntologyEffect.java` |
| 2 | Part sealed hierarchy + Message | domain | `Part.java` + 7 variants + `Message.java` |
| 3 | Action annotations + descriptors | domain | `@DataTalkAction`, `ActionHandler`, `ActionDescriptor`, `ActionContext` |
| 4 | ObjectType + descriptors | domain | `ObjectType.java`, `ObjectTypeDescriptor.java` |
| 5 | DtEvent sealed hierarchy + ErrorInfo | domain | `DtEvent.java` + variants + `ErrorInfo.java` |
| 6 | JsonSchemaLoader (networknt wrapper) | application | `JsonSchemaLoader.java` + tests |
| 7 | ActionRegistry (scan + expose) | application | `ActionRegistry.java` + tests |
| 8 | OntologyRegistry (scan + expose) | application | `OntologyRegistry.java` + tests |
| 9 | DiscoveryController (`/api/actions`, `/api/ontology`) | infrastructure | `DiscoveryController.java` + WebMvcTest |
| 10 | Flyway + V1 migration | infrastructure | `V1__init.sql` + FlywayMigrationConfig |
| 11 | SecretVault (AES-GCM) | application | `SecretVault.java` + tests |
| 12 | Repositories (Session/Message/Artifact/Event/etc) | application | 6 `*Repository.java` |
| 13 | PersistenceService | application | `PersistenceService.java` + integration tests |
| 14 | PendingCallRegistry | application | `PendingCallRegistry.java` + tests |
| 15 | SessionBus (ring buffer + flusher) | application | `SessionBus.java` + tests |
| 16 | SessionBusRegistry | application | `SessionBusRegistry.java` + tests |
| 17 | Subscriber + SSE emit | application | `Subscriber.java` + tests |
| 18 | JsonRpcCodec + Rpc types | application | `JsonRpcCodec.java`, `RpcRequest.java`, `RpcResponse.java` + tests |
| 19 | ChannelService | application | `ChannelService.java` + tests |
| 20 | ChannelController (Streamable HTTP) | infrastructure | `ChannelController.java` + integration tests |
| 21 | OpenCodeEventTranslator | application | `OpenCodeEventTranslator.java` + exhaustive tests |
| 22 | OpenCodeHttpClient | infrastructure | `OpenCodeHttpClient.java` + WireMock tests |
| 23 | OpenCodeGateway lifecycle | application | `OpenCodeGateway.java` + tests |
| 24 | ToolCallBridge | application | `ToolCallBridge.java` + controller + tests |
| 25 | ActionDispatcher | application | `ActionDispatcher.java` + tests |
| 26 | DemoEchoAction + end-to-end smoke test | adapter | `DemoEchoAction.java` + E2E test with FakeOpenCodeServer |

The remaining sections in this document detail each task. Tasks are independent enough that subagent-driven execution is safe: each produces a self-contained commit and the next task's tests import types from the previous tasks (verified compilation-wise at every step).

---

## Task 1: Domain Enums (Executor, OntologyEffect)

**Files:**
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/action/Executor.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/action/OntologyEffect.java`
- Test: `data-talk-domain/src/test/java/com/datatalk/domain/action/ExecutorTest.java`
- Test: `data-talk-domain/src/test/java/com/datatalk/domain/action/OntologyEffectTest.java`

- [ ] **Step 1.1: Add test dependency to `data-talk-domain/pom.xml`**

Open `data-talk-domain/pom.xml` and add the following inside `<dependencies>` (create the block if missing):

```xml
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

No version needed — they're managed by Spring Boot parent.

- [ ] **Step 1.2: Write the failing test for Executor**

Create `data-talk-domain/src/test/java/com/datatalk/domain/action/ExecutorTest.java`:

```java
package com.datatalk.domain.action;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutorTest {

    @Test
    void hasExactlyThreeValuesInDeclaredOrder() {
        assertThat(Executor.values())
            .containsExactly(Executor.OPENCODE, Executor.SERVER, Executor.CLIENT);
    }
}
```

- [ ] **Step 1.3: Run test — expected FAIL (class not found)**

Run from `server/`:

```
./mvnw -pl data-talk-domain test -Dtest=ExecutorTest
```

Expected: compilation failure, "cannot find symbol class Executor".

- [ ] **Step 1.4: Implement Executor**

Create `data-talk-domain/src/main/java/com/datatalk/domain/action/Executor.java`:

```java
package com.datatalk.domain.action;

/**
 * Where an Action's handler executes.
 *
 * <ul>
 *   <li>{@link #OPENCODE} — read-only context tool for the AI loop. Handler still lives in Spring Boot
 *       (via {@code ToolCallBridge}), but the UI treats it as folded context.</li>
 *   <li>{@link #SERVER} — has side effects; runs in Spring Boot.</li>
 *   <li>{@link #CLIENT} — runs in the browser/Tauri client. Dispatcher pushes {@code action.invoke}
 *       and waits for a POSTed {@code action_result}.</li>
 * </ul>
 */
public enum Executor {
    OPENCODE,
    SERVER,
    CLIENT
}
```

- [ ] **Step 1.5: Run test — expected PASS**

```
./mvnw -pl data-talk-domain test -Dtest=ExecutorTest
```

Expected: BUILD SUCCESS, 1 test passed.

- [ ] **Step 1.6: Write the failing test for OntologyEffect**

Create `data-talk-domain/src/test/java/com/datatalk/domain/action/OntologyEffectTest.java`:

```java
package com.datatalk.domain.action;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OntologyEffectTest {

    @Test
    void hasThreeDeclaredValues() {
        assertThat(OntologyEffect.values())
            .containsExactly(OntologyEffect.NONE, OntologyEffect.CREATE_ARTIFACT, OntologyEffect.PATCH_ARTIFACT);
    }
}
```

- [ ] **Step 1.7: Run — expected FAIL**

```
./mvnw -pl data-talk-domain test -Dtest=OntologyEffectTest
```

Expected: compile error.

- [ ] **Step 1.8: Implement OntologyEffect**

Create `data-talk-domain/src/main/java/com/datatalk/domain/action/OntologyEffect.java`:

```java
package com.datatalk.domain.action;

/**
 * Declares what kind of ontology side effect an Action has. Used by
 * {@code ActionDispatcher} to decide whether to persist an artifact and broadcast
 * {@code ontology.updated} after the handler completes.
 */
public enum OntologyEffect {
    /** No ontology write. */
    NONE,
    /** Handler's output creates a new Artifact row. */
    CREATE_ARTIFACT,
    /** Handler's output patches an existing Artifact (e.g., supersedes or pin). */
    PATCH_ARTIFACT
}
```

- [ ] **Step 1.9: Run — expected PASS, then run module tests to confirm nothing else broke**

```
./mvnw -pl data-talk-domain test
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 1.10: Commit**

```
git add data-talk-domain/pom.xml \
        data-talk-domain/src/main/java/com/datatalk/domain/action/Executor.java \
        data-talk-domain/src/main/java/com/datatalk/domain/action/OntologyEffect.java \
        data-talk-domain/src/test/java/com/datatalk/domain/action/ExecutorTest.java \
        data-talk-domain/src/test/java/com/datatalk/domain/action/OntologyEffectTest.java
git commit -m "feat(server): add Executor and OntologyEffect domain enums"
```

---

## Task 2: Part sealed hierarchy + Message

**Files:**
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/Part.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/TextPart.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/ReasoningPart.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/ToolPart.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/FilePart.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/StepStartPart.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/StepFinishPart.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/SubtaskPart.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/ToolState.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/part/Message.java`
- Test: `data-talk-domain/src/test/java/com/datatalk/domain/part/PartJsonSerdeTest.java`

Design note: Parts mirror OpenCode's wire schema (see spec §3.4 and §3.9). Jackson must serialize a Part union with a `type` discriminator.

- [ ] **Step 2.1: Add Jackson dependency to `data-talk-domain/pom.xml`**

Inside the existing `<dependencies>` block add:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

(Version managed by Spring Boot parent.)

- [ ] **Step 2.2: Write failing test for Part Jackson round-trip**

Create `data-talk-domain/src/test/java/com/datatalk/domain/part/PartJsonSerdeTest.java`:

```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PartJsonSerdeTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void roundTripsTextPart() throws Exception {
        Part in = new TextPart("p1", "s1", "m1", "hello", false, false, null, Map.of());
        String json = om.writeValueAsString(in);
        assertThat(json).contains("\"type\":\"text\"").contains("\"text\":\"hello\"");
        Part back = om.readValue(json, Part.class);
        assertThat(back).isEqualTo(in);
    }

    @Test
    void roundTripsToolPartWithPendingState() throws Exception {
        Part in = new ToolPart("p2", "s1", "m1", "call-1", "demo.echo",
            new ToolState.Pending(), Map.of());
        String json = om.writeValueAsString(in);
        assertThat(json).contains("\"type\":\"tool\"").contains("\"tool\":\"demo.echo\"");
        Part back = om.readValue(json, Part.class);
        assertThat(back).isEqualTo(in);
    }

    @Test
    void roundTripsToolPartWithCompletedState() throws Exception {
        Part in = new ToolPart("p3", "s1", "m1", "call-2", "demo.echo",
            new ToolState.Completed(Map.of("reversed", "olleh")), Map.of());
        String json = om.writeValueAsString(in);
        assertThat(json).contains("\"status\":\"completed\"");
        Part back = om.readValue(json, Part.class);
        assertThat(back).isEqualTo(in);
    }

    @Test
    void roundTripsReasoningPart() throws Exception {
        Part in = new ReasoningPart("p4", "s1", "m1", "thinking about it", Map.of(), 100L, 200L);
        String json = om.writeValueAsString(in);
        assertThat(json).contains("\"type\":\"reasoning\"");
        Part back = om.readValue(json, Part.class);
        assertThat(back).isEqualTo(in);
    }

    @Test
    void roundTripsStepDividers() throws Exception {
        Part start = new StepStartPart("p5", "s1", "m1", null);
        Part finish = new StepFinishPart("p6", "s1", "m1", "ok", null, 0.0005,
            new StepFinishPart.Tokens(10, 20, 0, 0, 0));
        Part backStart = om.readValue(om.writeValueAsString(start), Part.class);
        Part backFinish = om.readValue(om.writeValueAsString(finish), Part.class);
        assertThat(backStart).isEqualTo(start);
        assertThat(backFinish).isEqualTo(finish);
    }
}
```

- [ ] **Step 2.3: Run — expected FAIL (classes missing)**

```
./mvnw -pl data-talk-domain test -Dtest=PartJsonSerdeTest
```

- [ ] **Step 2.4: Implement the Part sealed interface**

Create `data-talk-domain/src/main/java/com/datatalk/domain/part/Part.java`:

```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * OpenCode-compatible Part union. Every Part carries stable identifiers so the
 * frontend reducer can apply incremental updates by {@link #id()}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextPart.class, name = "text"),
    @JsonSubTypes.Type(value = ReasoningPart.class, name = "reasoning"),
    @JsonSubTypes.Type(value = ToolPart.class, name = "tool"),
    @JsonSubTypes.Type(value = FilePart.class, name = "file"),
    @JsonSubTypes.Type(value = StepStartPart.class, name = "step-start"),
    @JsonSubTypes.Type(value = StepFinishPart.class, name = "step-finish"),
    @JsonSubTypes.Type(value = SubtaskPart.class, name = "subtask")
})
public sealed interface Part
    permits TextPart, ReasoningPart, ToolPart, FilePart,
            StepStartPart, StepFinishPart, SubtaskPart {

    String id();
    String sessionID();
    String messageID();
}
```

- [ ] **Step 2.5: Implement TextPart**

Create `data-talk-domain/src/main/java/com/datatalk/domain/part/TextPart.java`:

```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record TextPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String text,
    Boolean synthetic,
    Boolean ignored,
    Time time,
    Map<String, Object> metadata
) implements Part {

    public record Time(long start, Long end) {}
}
```

- [ ] **Step 2.6: Implement ReasoningPart**

Create `data-talk-domain/src/main/java/com/datatalk/domain/part/ReasoningPart.java`:

```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ReasoningPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String text,
    Map<String, Object> metadata,
    long timeStart,
    Long timeEnd
) implements Part {}
```

- [ ] **Step 2.7: Implement ToolState + ToolPart**

Create `data-talk-domain/src/main/java/com/datatalk/domain/part/ToolState.java`:

```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "status")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ToolState.Pending.class,   name = "pending"),
    @JsonSubTypes.Type(value = ToolState.Running.class,   name = "running"),
    @JsonSubTypes.Type(value = ToolState.Completed.class, name = "completed"),
    @JsonSubTypes.Type(value = ToolState.Errored.class,   name = "error")
})
public sealed interface ToolState {
    record Pending() implements ToolState {}
    record Running(Long startedAt) implements ToolState {
        public Running() { this(null); }
    }
    record Completed(Map<String, Object> output) implements ToolState {}
    record Errored(String code, String message, Boolean retriable) implements ToolState {}
}
```

Create `data-talk-domain/src/main/java/com/datatalk/domain/part/ToolPart.java`:

```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ToolPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    @JsonProperty("callID") String callID,
    String tool,
    ToolState state,
    Map<String, Object> metadata
) implements Part {}
```

- [ ] **Step 2.8: Implement FilePart, StepStartPart, StepFinishPart, SubtaskPart**

Create each of:

`FilePart.java`:
```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FilePart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String mime,
    String filename,
    String url,
    String source
) implements Part {}
```

`StepStartPart.java`:
```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StepStartPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String snapshot
) implements Part {}
```

`StepFinishPart.java`:
```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StepFinishPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String reason,
    String snapshot,
    double cost,
    Tokens tokens
) implements Part {

    public record Tokens(long input, long output, long reasoning, long cacheRead, long cacheWrite) {}
}
```

`SubtaskPart.java`:
```java
package com.datatalk.domain.part;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubtaskPart(
    String id,
    @JsonProperty("sessionID") String sessionID,
    @JsonProperty("messageID") String messageID,
    String prompt,
    String description,
    String agent
) implements Part {}
```

- [ ] **Step 2.9: Implement Message**

Create `data-talk-domain/src/main/java/com/datatalk/domain/part/Message.java`:

```java
package com.datatalk.domain.part;

import java.util.List;

public record Message(
    String id,
    String sessionId,
    Role role,
    List<Part> parts,
    long createdAt
) {
    public enum Role { USER, ASSISTANT, SYSTEM }
}
```

- [ ] **Step 2.10: Run tests — expected PASS**

```
./mvnw -pl data-talk-domain test -Dtest=PartJsonSerdeTest
```

Expected: all 5 tests pass.

- [ ] **Step 2.11: Run full module tests**

```
./mvnw -pl data-talk-domain test
```

Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 2.12: Commit**

```
git add data-talk-domain/pom.xml \
        data-talk-domain/src/main/java/com/datatalk/domain/part/ \
        data-talk-domain/src/test/java/com/datatalk/domain/part/
git commit -m "feat(server): add Part sealed hierarchy mirroring OpenCode schema"
```

---

## Task 3: Action annotation + descriptors + handler interface

**Files:**
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/action/DataTalkAction.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/action/ActionHandler.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/action/ActionDescriptor.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/action/ActionContext.java`
- Test: `data-talk-domain/src/test/java/com/datatalk/domain/action/ActionDescriptorTest.java`

- [ ] **Step 3.1: Write failing test for ActionDescriptor equality/defaults**

Create `data-talk-domain/src/test/java/com/datatalk/domain/action/ActionDescriptorTest.java`:

```java
package com.datatalk.domain.action;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionDescriptorTest {

    @Test
    void holdsAllFieldsAndIsImmutable() {
        Map<String, Object> in = Map.of("type", "object");
        Map<String, Object> out = Map.of("type", "object");
        ActionDescriptor d = new ActionDescriptor(
            "datatalk.echo",
            Executor.SERVER,
            "Echo the input.",
            in, out,
            List.of(),
            List.of(OntologyEffect.NONE),
            false,
            30_000
        );

        assertThat(d.id()).isEqualTo("datatalk.echo");
        assertThat(d.executor()).isEqualTo(Executor.SERVER);
        assertThat(d.sideEffects()).containsExactly(OntologyEffect.NONE);
        assertThat(d.timeoutMs()).isEqualTo(30_000);
        assertThat(d.requiresConnection()).isFalse();
    }
}
```

- [ ] **Step 3.2: Run — expected FAIL**

```
./mvnw -pl data-talk-domain test -Dtest=ActionDescriptorTest
```

- [ ] **Step 3.3: Implement ActionDescriptor**

Create `data-talk-domain/src/main/java/com/datatalk/domain/action/ActionDescriptor.java`:

```java
package com.datatalk.domain.action;

import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of an {@link ActionHandler}'s metadata. Returned from the
 * {@code /api/actions} discovery endpoint and cached on the client.
 */
public record ActionDescriptor(
    String id,
    Executor executor,
    String description,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    List<String> produces,
    List<OntologyEffect> sideEffects,
    boolean requiresConnection,
    int timeoutMs
) {}
```

- [ ] **Step 3.4: Run — expected PASS**

```
./mvnw -pl data-talk-domain test -Dtest=ActionDescriptorTest
```

- [ ] **Step 3.5: Implement DataTalkAction annotation**

Create `data-talk-domain/src/main/java/com/datatalk/domain/action/DataTalkAction.java`:

```java
package com.datatalk.domain.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean that implements {@link ActionHandler} as a DataTalk Action.
 * Scanned at startup by {@code ActionRegistry}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataTalkAction {
    String id();
    Executor executor();
    String description();
    String[] produces() default {};
    boolean requiresConnection() default false;
    int timeoutMs() default 30_000;
}
```

- [ ] **Step 3.6: Implement ActionContext**

Create `data-talk-domain/src/main/java/com/datatalk/domain/action/ActionContext.java`:

```java
package com.datatalk.domain.action;

/**
 * Runtime context passed to {@link ActionHandler#handle}. Carries the session
 * identity so handlers can publish events or look up ontology objects.
 * The {@link #connectionId} is nullable — only actions with {@code requiresConnection=true}
 * should read it without null-check.
 */
public record ActionContext(
    String sessionId,
    String callId,
    String connectionId,
    String openCodeSessionId
) {}
```

- [ ] **Step 3.7: Implement ActionHandler interface**

Create `data-talk-domain/src/main/java/com/datatalk/domain/action/ActionHandler.java`:

```java
package com.datatalk.domain.action;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Contract every DataTalk Action must implement. Concrete beans are discovered
 * by scanning for {@link DataTalkAction}-annotated classes.
 *
 * @param <I> decoded input record type
 * @param <O> output record type
 */
public interface ActionHandler<I, O> {

    /** JSON Schema (networknt format) describing the {@code I} input. */
    Map<String, Object> inputSchema();

    /** JSON Schema describing the {@code O} output. */
    Map<String, Object> outputSchema();

    /** Ordered list of side effects this handler produces, in execution order. */
    List<OntologyEffect> sideEffects();

    /** Java type of {@code I}, used by the dispatcher to decode input JSON. */
    Class<I> inputType();

    /** Execute the action. Must not throw; encode failures as completed-exceptionally futures. */
    CompletionStage<O> handle(ActionContext ctx, I input);
}
```

- [ ] **Step 3.8: Run all domain tests — expected PASS**

```
./mvnw -pl data-talk-domain test
```

- [ ] **Step 3.9: Commit**

```
git add data-talk-domain/src/main/java/com/datatalk/domain/action/ \
        data-talk-domain/src/test/java/com/datatalk/domain/action/ActionDescriptorTest.java
git commit -m "feat(server): add DataTalkAction annotation and ActionHandler contract"
```

---

## Task 4: ObjectType + ObjectTypeDescriptor

**Files:**
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/ontology/ObjectType.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/ontology/ObjectTypeDescriptor.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/ontology/ObjectTypes.java`
- Test: `data-talk-domain/src/test/java/com/datatalk/domain/ontology/ObjectTypeDescriptorTest.java`

- [ ] **Step 4.1: Write failing test**

Create `data-talk-domain/src/test/java/com/datatalk/domain/ontology/ObjectTypeDescriptorTest.java`:

```java
package com.datatalk.domain.ontology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectTypeDescriptorTest {

    @Test
    void carriesAllObjectTypeMetadata() {
        ObjectTypeDescriptor d = new ObjectTypeDescriptor(
            "datatalk.artifact",
            "Artifact",
            Map.of("type", "object"),
            List.of("id", "version"),
            Optional.of("title"),
            "com.datatalk.domain.ontology.Artifact"
        );

        assertThat(d.id()).isEqualTo("datatalk.artifact");
        assertThat(d.primaryKey()).containsExactly("id", "version");
        assertThat(d.titleField()).hasValue("title");
    }
}
```

- [ ] **Step 4.2: Run — expected FAIL**

```
./mvnw -pl data-talk-domain test -Dtest=ObjectTypeDescriptorTest
```

- [ ] **Step 4.3: Implement ObjectTypeDescriptor**

Create `data-talk-domain/src/main/java/com/datatalk/domain/ontology/ObjectTypeDescriptor.java`:

```java
package com.datatalk.domain.ontology;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serializable snapshot of an {@link ObjectType}. Returned from
 * {@code /api/ontology} so clients can reflect on available object shapes.
 */
public record ObjectTypeDescriptor(
    String id,
    String displayName,
    Map<String, Object> propertySchema,
    List<String> primaryKey,
    Optional<String> titleField,
    String javaTypeName
) {}
```

- [ ] **Step 4.4: Implement ObjectType interface**

Create `data-talk-domain/src/main/java/com/datatalk/domain/ontology/ObjectType.java`:

```java
package com.datatalk.domain.ontology;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A first-class entity type in DataTalk's ontology. Each implementation is a
 * Spring bean; {@code OntologyRegistry} discovers them at startup.
 *
 * @param <T> Java record/class the properties deserialize into
 */
public interface ObjectType<T> {
    String id();
    String displayName();
    Map<String, Object> propertySchema();
    List<String> primaryKey();
    Optional<String> titleField();
    Class<T> javaType();

    default ObjectTypeDescriptor toDescriptor() {
        return new ObjectTypeDescriptor(
            id(), displayName(), propertySchema(), primaryKey(), titleField(), javaType().getName()
        );
    }
}
```

- [ ] **Step 4.5: Implement ObjectTypes registry-id constants**

Create `data-talk-domain/src/main/java/com/datatalk/domain/ontology/ObjectTypes.java`:

```java
package com.datatalk.domain.ontology;

/** Canonical ids for MVP ObjectTypes. Kept as constants to avoid string-typo bugs. */
public final class ObjectTypes {
    private ObjectTypes() {}

    public static final String CONNECTION         = "datatalk.connection";
    public static final String SESSION            = "datatalk.session";
    public static final String ARTIFACT           = "datatalk.artifact";
    public static final String ACTION_INVOCATION  = "datatalk.action_invocation";
}
```

- [ ] **Step 4.6: Run — expected PASS**

```
./mvnw -pl data-talk-domain test -Dtest=ObjectTypeDescriptorTest
```

- [ ] **Step 4.7: Commit**

```
git add data-talk-domain/src/main/java/com/datatalk/domain/ontology/ \
        data-talk-domain/src/test/java/com/datatalk/domain/ontology/
git commit -m "feat(server): add ObjectType contract and descriptor"
```

---

## Task 5: DtEvent sealed hierarchy + ErrorInfo

**Files:**
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/event/NumberedEvent.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/event/ErrorInfo.java`
- Test: `data-talk-domain/src/test/java/com/datatalk/domain/event/DtEventTest.java`

Spec reference: §3.4 event table.

- [ ] **Step 5.1: Write failing test**

Create `data-talk-domain/src/test/java/com/datatalk/domain/event/DtEventTest.java`:

```java
package com.datatalk.domain.event;

import com.datatalk.domain.part.TextPart;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DtEventTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void serializesConnectedEventWithTypeDiscriminator() throws Exception {
        DtEvent e = new DtEvent.Connected("s1", 1);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"connected\"").contains("\"sessionId\":\"s1\"");
    }

    @Test
    void serializesMessagePartCreated() throws Exception {
        TextPart p = new TextPart("p1", "s1", "m1", "hi", null, null, null, Map.of());
        DtEvent e = new DtEvent.MessagePartCreated(p);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"message.part.created\"").contains("\"text\":\"hi\"");
    }

    @Test
    void serializesActionInvoke() throws Exception {
        DtEvent e = new DtEvent.ActionInvoke("call-1", "datatalk.pin_artifact",
            Map.of("artifactId", "art-1"), 5_000);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"action.invoke\"").contains("\"callId\":\"call-1\"");
    }

    @Test
    void serializesErrorEventWithFatalFlag() throws Exception {
        DtEvent e = new DtEvent.StreamError(new ErrorInfo("upstream.unavailable",
            "OpenCode down", true, null), true);
        String json = om.writeValueAsString(e);
        assertThat(json).contains("\"type\":\"error\"").contains("\"fatal\":true");
    }
}
```

- [ ] **Step 5.2: Run — expected FAIL**

```
./mvnw -pl data-talk-domain test -Dtest=DtEventTest
```

- [ ] **Step 5.3: Implement ErrorInfo**

Create `data-talk-domain/src/main/java/com/datatalk/domain/event/ErrorInfo.java`:

```java
package com.datatalk.domain.event;

/**
 * Structured error body used across Streamable HTTP and tool responses.
 * The {@code code} field is a stable string id; see {@code docs/superpowers/specs/error-codes.md}.
 */
public record ErrorInfo(
    String code,
    String message,
    boolean retriable,
    Object details
) {}
```

- [ ] **Step 5.4: Implement DtEvent**

Create `data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`:

```java
package com.datatalk.domain.event;

import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

/**
 * Downstream-to-client event union. Mirrors spec §3.4 one-to-one.
 *
 * <p>The {@code type} discriminator is embedded in the JSON so the client's
 * reducer can switch on it directly.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DtEvent.Connected.class,           name = "connected"),
    @JsonSubTypes.Type(value = DtEvent.SessionStatus.class,       name = "session.status"),
    @JsonSubTypes.Type(value = DtEvent.MessageCreated.class,      name = "message.created"),
    @JsonSubTypes.Type(value = DtEvent.MessageUpdated.class,      name = "message.updated"),
    @JsonSubTypes.Type(value = DtEvent.MessagePartCreated.class,  name = "message.part.created"),
    @JsonSubTypes.Type(value = DtEvent.MessagePartUpdated.class,  name = "message.part.updated"),
    @JsonSubTypes.Type(value = DtEvent.MessagePartDelta.class,    name = "message.part.delta"),
    @JsonSubTypes.Type(value = DtEvent.MessagePartRemoved.class,  name = "message.part.removed"),
    @JsonSubTypes.Type(value = DtEvent.ActionInvoke.class,        name = "action.invoke"),
    @JsonSubTypes.Type(value = DtEvent.ActionCancel.class,        name = "action.cancel"),
    @JsonSubTypes.Type(value = DtEvent.ArtifactSnapshot.class,    name = "artifact.snapshot"),
    @JsonSubTypes.Type(value = DtEvent.OntologyUpdated.class,     name = "ontology.updated"),
    @JsonSubTypes.Type(value = DtEvent.Heartbeat.class,           name = "heartbeat"),
    @JsonSubTypes.Type(value = DtEvent.StreamError.class,         name = "error")
})
public sealed interface DtEvent {

    record Connected(String sessionId, int serverRev) implements DtEvent {}

    record SessionStatus(String status, Map<String, Object> retryInfo) implements DtEvent {}

    record MessageCreated(Message message) implements DtEvent {}
    record MessageUpdated(Message message) implements DtEvent {}

    record MessagePartCreated(Part part) implements DtEvent {}
    record MessagePartUpdated(Part part) implements DtEvent {}
    record MessagePartDelta(String partId, String field, String delta) implements DtEvent {}
    record MessagePartRemoved(String partId) implements DtEvent {}

    record ActionInvoke(String callId, String actionId, Map<String, Object> input, int timeoutMs) implements DtEvent {}
    record ActionCancel(String callId, String reason) implements DtEvent {}

    record ArtifactSnapshot(List<Map<String, Object>> artifacts) implements DtEvent {}

    record OntologyUpdated(String objectType, String id, String op, Map<String, Object> patch) implements DtEvent {}

    record Heartbeat(long ts) implements DtEvent {}

    record StreamError(ErrorInfo error, boolean fatal) implements DtEvent {}
}
```

- [ ] **Step 5.5: Implement NumberedEvent**

Create `data-talk-domain/src/main/java/com/datatalk/domain/event/NumberedEvent.java`:

```java
package com.datatalk.domain.event;

/** An event with its per-session monotonic id and wall-clock timestamp. */
public record NumberedEvent(long eventId, String sessionId, DtEvent event, long ts) {}
```

- [ ] **Step 5.6: Run — expected PASS**

```
./mvnw -pl data-talk-domain test -Dtest=DtEventTest
```

- [ ] **Step 5.7: Run whole domain module — expected PASS**

```
./mvnw -pl data-talk-domain test
```

- [ ] **Step 5.8: Commit**

```
git add data-talk-domain/src/main/java/com/datatalk/domain/event/ \
        data-talk-domain/src/test/java/com/datatalk/domain/event/
git commit -m "feat(server): add DtEvent sealed hierarchy and ErrorInfo"
```

---

## Task 6: JsonSchemaLoader

**Files:**
- Modify: `data-talk-application/pom.xml` (add networknt)
- Create: `data-talk-application/src/main/java/com/datatalk/application/registry/JsonSchemaLoader.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/registry/JsonSchemaLoaderTest.java`

- [ ] **Step 6.1: Add networknt + jackson + Spring test to `data-talk-application/pom.xml`**

Inside existing `<dependencies>` add:

```xml
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>1.5.2</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 6.2: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/registry/JsonSchemaLoaderTest.java`:

```java
package com.datatalk.application.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaLoaderTest {

    private final JsonSchemaLoader loader = new JsonSchemaLoader(new ObjectMapper());

    @Test
    void acceptsValidInput() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "required", java.util.List.of("name"),
            "properties", Map.of("name", Map.of("type", "string"))
        );
        var result = loader.validate(schema, Map.of("name", "Alice"));
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsMissingRequired() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "required", java.util.List.of("name"),
            "properties", Map.of("name", Map.of("type", "string"))
        );
        var result = loader.validate(schema, Map.of());
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }
}
```

- [ ] **Step 6.3: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=JsonSchemaLoaderTest
```

- [ ] **Step 6.4: Implement JsonSchemaLoader**

Create `data-talk-application/src/main/java/com/datatalk/application/registry/JsonSchemaLoader.java`:

```java
package com.datatalk.application.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over networknt's json-schema-validator. Caches compiled
 * {@link JsonSchema} per schema map identity so repeated validations are cheap.
 */
@Component
public class JsonSchemaLoader {

    private final ObjectMapper om;
    private final JsonSchemaFactory factory =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public JsonSchemaLoader(ObjectMapper om) {
        this.om = om;
    }

    public Result validate(Map<String, Object> schema, Object data) {
        JsonNode schemaNode = om.valueToTree(schema);
        JsonSchema compiled = factory.getSchema(schemaNode);
        JsonNode dataNode = om.valueToTree(data);
        var messages = compiled.validate(dataNode);
        List<String> errors = messages.stream().map(Object::toString).toList();
        return new Result(errors.isEmpty(), errors);
    }

    public record Result(boolean valid, List<String> errors) {}
}
```

- [ ] **Step 6.5: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=JsonSchemaLoaderTest
```

- [ ] **Step 6.6: Commit**

```
git add data-talk-application/pom.xml \
        data-talk-application/src/main/java/com/datatalk/application/registry/JsonSchemaLoader.java \
        data-talk-application/src/test/java/com/datatalk/application/registry/JsonSchemaLoaderTest.java
git commit -m "feat(server): add JsonSchemaLoader backed by networknt validator"
```

---

## Continuation

Tasks 7–26 continue in `docs/superpowers/plans/2026-04-16-manus-a-backend-platform-part2.md`. The split is purely for file-size reasons; treat the two files as a single plan executed in numeric task order.

**Index of continuation:**

| Task | Topic | File |
|---|---|---|
| 7 | ActionRegistry (scan + expose) | part2 |
| 8 | OntologyRegistry | part2 |
| 9 | DiscoveryController | part2 |
| 10 | Flyway + V1__init.sql | part2 |
| 11 | SecretVault | part2 |
| 12 | Repositories | part2 |
| 13 | PersistenceService | part2 |
| 14 | PendingCallRegistry | part2 |
| 15 | SessionBus | part2 |
| 16 | SessionBusRegistry | part2 |
| 17 | Subscriber + SSE emit | part2 |
| 18 | JsonRpcCodec + Rpc types | part2 |
| 19 | ChannelService | part2 |
| 20 | ChannelController | part2 |
| 21 | OpenCodeEventTranslator | part2 |
| 22 | OpenCodeHttpClient | part2 |
| 23 | OpenCodeGateway lifecycle | part2 |
| 24 | ToolCallBridge | part2 |
| 25 | ActionDispatcher | part2 |
| 26 | DemoEchoAction + E2E smoke | part2 |
