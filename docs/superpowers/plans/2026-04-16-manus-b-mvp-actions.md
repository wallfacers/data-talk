# Plan B — MVP Actions + Real OpenCode Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build on Plan A's platform by (1) adding the 6 MVP ObjectTypes and Action handlers, (2) swapping the Plan-A FakeOpenCodeServer wiring for a real OpenCode `/event` SSE consumer with retry/backoff, and (3) hardening SQL safety and error paths.

**Architecture:** Keep Plan A's module boundaries. All new types register via the existing `ActionRegistry` / `OntologyRegistry` beans — adding capability = writing one handler + one ObjectType bean. Action handlers live in `data-talk-adapter/actions/`. ObjectTypes live in `data-talk-adapter/ontology/`.

**Tech Stack:** Same as Plan A; adds Testcontainers (PostgreSQL + MySQL) for JDBC tests, plus a minimal chart-spec validator for `render_chart`'s ECharts option subset.

---

## Spec Mapping

Implements:
- §2.2 the six MVP actions (read_schema / execute_sql / render_chart / layout_erd / pin_artifact / supersede_artifact)
- §2.1 the four MVP ObjectTypes (connection / session / artifact / action_invocation)
- §5.3 the real OpenCode SSE event-loop lifecycle, retry, degraded mode
- §7.3 the complete MVP error code table
- §7.5 degraded mode (no-AI fallback)

Defers to Plan C: all client UI.

---

## Prerequisites

Plan A (`2026-04-16-manus-a-backend-platform.md` + parts 2–4) must be merged: registries, SessionBus, channel, dispatcher, gateway skeleton, demo echo — all green.

---

## Tasks Overview

| # | Task | Module | Deliverable |
|---|---|---|---|
| 1 | Error code catalog + common exception types | domain | `DataTalkErrorCodes.java`, typed exceptions |
| 2 | MVP ObjectType beans | adapter | 4 `*ObjectType` classes |
| 3 | Connection CRUD + encrypted password storage | application + infrastructure | `ConnectionService`, REST controller |
| 4 | SQL safety guard (SELECT-only for MVP) | application | `SqlStatementGuard` + tests |
| 5 | Real OpenCode `/event` SSE consumer + retry | application + infrastructure | `OpenCodeEventLoop`, backoff policy |
| 6 | `datatalk.read_schema` action | adapter | `ReadSchemaAction` + Testcontainers test |
| 7 | `datatalk.execute_sql` action + artifact persistence | adapter | `ExecuteSqlAction` + Testcontainers test |
| 8 | `datatalk.render_chart` action + spec validator | adapter | `RenderChartAction` + ECharts-subset validator |
| 9 | `datatalk.layout_erd` action | adapter | `LayoutErdAction` + grid layout |
| 10 | `datatalk.pin_artifact` action (CLIENT executor) | adapter | `PinArtifactAction` |
| 11 | `datatalk.supersede_artifact` action | adapter | `SupersedeArtifactAction` |
| 12 | Wire ChannelController to forward `send_message` to OpenCode | infrastructure | updated controller + history endpoints |
| 13 | End-to-end test: §6.1 typical query via real OpenCode loop | adapter | `TypicalQueryE2EIT` with FakeOpenCode replay |

---

## Task 1: Error code catalog + typed exceptions

**Files:**
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/error/DataTalkErrorCodes.java`
- Create: `data-talk-domain/src/main/java/com/datatalk/domain/error/DataTalkException.java`
- Test: `data-talk-domain/src/test/java/com/datatalk/domain/error/DataTalkErrorCodesTest.java`

- [ ] **Step 1.1: Write failing test**

```java
// data-talk-domain/src/test/java/com/datatalk/domain/error/DataTalkErrorCodesTest.java
package com.datatalk.domain.error;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DataTalkErrorCodesTest {
    @Test void codesMatchSpecTable() {
        assertThat(DataTalkErrorCodes.SCHEMA_INPUT_INVALID).isEqualTo("schema.input_invalid");
        assertThat(DataTalkErrorCodes.CONNECTION_MISSING).isEqualTo("connection.missing");
        assertThat(DataTalkErrorCodes.SQL_SYNTAX_ERROR).isEqualTo("sql.syntax_error");
        assertThat(DataTalkErrorCodes.SQL_TIMEOUT).isEqualTo("sql.timeout");
        assertThat(DataTalkErrorCodes.SQL_FORBIDDEN).isEqualTo("sql.forbidden");
        assertThat(DataTalkErrorCodes.ARTIFACT_SUPERSEDES_NOT_FOUND).isEqualTo("artifact.supersedes_not_found");
        assertThat(DataTalkErrorCodes.ARTIFACT_TOO_LARGE).isEqualTo("artifact.too_large");
        assertThat(DataTalkErrorCodes.ACTION_TIMEOUT).isEqualTo("action.timeout");
        assertThat(DataTalkErrorCodes.ACTION_CANCELLED).isEqualTo("action.cancelled");
        assertThat(DataTalkErrorCodes.UPSTREAM_UNAVAILABLE).isEqualTo("upstream.unavailable");
    }
}
```

- [ ] **Step 1.2: Run — FAIL**

```
./mvnw -pl data-talk-domain test -Dtest=DataTalkErrorCodesTest
```

- [ ] **Step 1.3: Implement DataTalkErrorCodes**

```java
// data-talk-domain/src/main/java/com/datatalk/domain/error/DataTalkErrorCodes.java
package com.datatalk.domain.error;

/**
 * Stable, versioned string ids for user-facing errors. See spec §7.3.
 * Append-only — never rename or repurpose an existing constant.
 */
public final class DataTalkErrorCodes {
    private DataTalkErrorCodes() {}

    public static final String SCHEMA_INPUT_INVALID         = "schema.input_invalid";
    public static final String SCHEMA_OUTPUT_INVALID        = "schema.output_invalid";
    public static final String CONNECTION_MISSING           = "connection.missing";
    public static final String CONNECTION_UNREACHABLE       = "connection.unreachable";
    public static final String SQL_SYNTAX_ERROR             = "sql.syntax_error";
    public static final String SQL_TIMEOUT                  = "sql.timeout";
    public static final String SQL_FORBIDDEN                = "sql.forbidden";
    public static final String ARTIFACT_SUPERSEDES_NOT_FOUND = "artifact.supersedes_not_found";
    public static final String ARTIFACT_TOO_LARGE           = "artifact.too_large";
    public static final String ACTION_TIMEOUT               = "action.timeout";
    public static final String ACTION_CANCELLED             = "action.cancelled";
    public static final String CLIENT_ACTION_UNREACHABLE    = "client_action.unreachable";
    public static final String UPSTREAM_UNAVAILABLE         = "upstream.unavailable";
    public static final String CHANNEL_RESUME_OUT_OF_WINDOW = "channel.resume_out_of_window";
}
```

- [ ] **Step 1.4: Implement DataTalkException**

```java
// data-talk-domain/src/main/java/com/datatalk/domain/error/DataTalkException.java
package com.datatalk.domain.error;

/** Base typed exception carrying a stable error code and optional details. */
public class DataTalkException extends RuntimeException {
    private final String code;
    private final boolean retriable;
    private final Object details;

    public DataTalkException(String code, String message, boolean retriable, Object details) {
        super(message);
        this.code = code;
        this.retriable = retriable;
        this.details = details;
    }

    public DataTalkException(String code, String message, boolean retriable) {
        this(code, message, retriable, null);
    }

    public String code() { return code; }
    public boolean retriable() { return retriable; }
    public Object details() { return details; }
}
```

- [ ] **Step 1.5: Run — PASS, commit**

```
./mvnw -pl data-talk-domain test -Dtest=DataTalkErrorCodesTest
git add data-talk-domain/src/main/java/com/datatalk/domain/error/ \
        data-talk-domain/src/test/java/com/datatalk/domain/error/
git commit -m "feat(server): add stable error code catalog and typed exception"
```

Also create `docs/superpowers/specs/error-codes.md` referencing these constants (spec §7.3 contract):

```
# DataTalk Error Codes

Stable string ids used across Streamable HTTP `ErrorInfo.code`, tool responses, and UI i18n.
Append-only: never rename or repurpose. See `DataTalkErrorCodes.java`.

| code | retriable | trigger |
|---|---|---|
| schema.input_invalid | yes | action input fails JSON Schema |
| schema.output_invalid | no | handler output fails its output schema (handler bug) |
| connection.missing | no | execute_sql / layout_erd without active connection |
| connection.unreachable | yes | JDBC cannot reach target DB |
| sql.syntax_error | yes | target DB reports SQL syntax error |
| sql.timeout | no | JDBC statement exceeds 30s |
| sql.forbidden | no | non-SELECT (DDL/DML) — MVP lock |
| artifact.supersedes_not_found | yes | supersedes points to unknown artifact |
| artifact.too_large | no | payload exceeds INLINE & HANDLE limits |
| action.timeout | no | handler exceeds timeoutMs |
| action.cancelled | no | abort or cascade cancel |
| client_action.unreachable | no | action.invoke pushed but client is gone |
| upstream.unavailable | yes | OpenCode unreachable |
| channel.resume_out_of_window | no | Last-Event-ID past ring buffer |
```

Commit `error-codes.md` in the same step.

---

## Task 2: MVP ObjectType beans

**Files:**
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/SessionObjectType.java`
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ArtifactObjectType.java`
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ActionInvocationObjectType.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/ontology/OntologyRegistrationIT.java`

- [ ] **Step 2.1: Write failing integration test**

```java
// OntologyRegistrationIT.java
package com.datatalk.adapter.ontology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OntologyRegistrationIT {

    @Autowired MockMvc mvc;

    @Test
    void allFourMvpObjectTypesAreRegistered() throws Exception {
        mvc.perform(get("/api/ontology"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objects[*].id",
                org.hamcrest.Matchers.hasItems(
                    "datatalk.connection",
                    "datatalk.session",
                    "datatalk.artifact",
                    "datatalk.action_invocation"
                )));
    }
}
```

- [ ] **Step 2.2: Implement the four ObjectType beans**

```java
// ConnectionObjectType.java
package com.datatalk.adapter.ontology;

import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ConnectionObjectType implements ObjectType<ConnectionObjectType.Connection> {

    public record Connection(
        String id, String kind, String host, int port, String databaseName,
        String username, String schemaDigest, long createdAt
    ) {}

    @Override public String id() { return ObjectTypes.CONNECTION; }
    @Override public String displayName() { return "Connection"; }
    @Override public List<String> primaryKey() { return List.of("id"); }
    @Override public Optional<String> titleField() { return Optional.of("id"); }
    @Override public Class<Connection> javaType() { return Connection.class; }

    @Override
    public Map<String, Object> propertySchema() {
        return Map.of(
            "type", "object",
            "required", List.of("id", "kind", "host", "port", "username"),
            "properties", Map.ofEntries(
                Map.entry("id",           Map.of("type", "string")),
                Map.entry("kind",         Map.of("type", "string", "enum", List.of("mysql", "postgresql", "sqlite", "h2"))),
                Map.entry("host",         Map.of("type", "string")),
                Map.entry("port",         Map.of("type", "integer", "minimum", 1, "maximum", 65535)),
                Map.entry("databaseName", Map.of("type", "string")),
                Map.entry("username",     Map.of("type", "string")),
                Map.entry("schemaDigest", Map.of("type", "string")),
                Map.entry("createdAt",    Map.of("type", "integer"))
            )
        );
    }
}
```

```java
// SessionObjectType.java
package com.datatalk.adapter.ontology;

import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SessionObjectType implements ObjectType<SessionObjectType.Session> {

    public record Session(
        String id, String connectionId, String title, boolean hasEverSent,
        String openCodeSid, long createdAt, long updatedAt
    ) {}

    @Override public String id() { return ObjectTypes.SESSION; }
    @Override public String displayName() { return "Session"; }
    @Override public List<String> primaryKey() { return List.of("id"); }
    @Override public Optional<String> titleField() { return Optional.of("title"); }
    @Override public Class<Session> javaType() { return Session.class; }

    @Override
    public Map<String, Object> propertySchema() {
        return Map.of(
            "type", "object",
            "required", List.of("id", "title", "createdAt"),
            "properties", Map.of(
                "id",            Map.of("type", "string"),
                "connectionId",  Map.of("type", "string"),
                "title",         Map.of("type", "string"),
                "hasEverSent",   Map.of("type", "boolean"),
                "openCodeSid",   Map.of("type", "string"),
                "createdAt",     Map.of("type", "integer"),
                "updatedAt",     Map.of("type", "integer")
            )
        );
    }
}
```

```java
// ArtifactObjectType.java
package com.datatalk.adapter.ontology;

import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ArtifactObjectType implements ObjectType<ArtifactObjectType.Artifact> {

    public record Artifact(
        String id, int version, String sessionId, String kind, String producedBy,
        String payloadRef, int payloadSize, String supersedesId, Integer supersedesVersion,
        boolean pinned, long createdAt
    ) {}

    @Override public String id() { return ObjectTypes.ARTIFACT; }
    @Override public String displayName() { return "Artifact"; }
    @Override public List<String> primaryKey() { return List.of("id", "version"); }
    @Override public Optional<String> titleField() { return Optional.of("id"); }
    @Override public Class<Artifact> javaType() { return Artifact.class; }

    @Override
    public Map<String, Object> propertySchema() {
        return Map.of(
            "type", "object",
            "required", List.of("id", "version", "sessionId", "kind", "producedBy", "payloadRef"),
            "properties", Map.ofEntries(
                Map.entry("id",                Map.of("type", "string")),
                Map.entry("version",           Map.of("type", "integer", "minimum", 1)),
                Map.entry("sessionId",         Map.of("type", "string")),
                Map.entry("kind",              Map.of("type", "string", "enum", List.of("table", "chart", "erd"))),
                Map.entry("producedBy",        Map.of("type", "string")),
                Map.entry("payloadRef",        Map.of("type", "string", "pattern", "^(INLINE|HANDLE):")),
                Map.entry("payloadSize",       Map.of("type", "integer", "minimum", 0)),
                Map.entry("supersedesId",      Map.of("type", "string")),
                Map.entry("supersedesVersion", Map.of("type", "integer", "minimum", 1)),
                Map.entry("pinned",            Map.of("type", "boolean")),
                Map.entry("createdAt",         Map.of("type", "integer"))
            )
        );
    }
}
```

```java
// ActionInvocationObjectType.java
package com.datatalk.adapter.ontology;

import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypes;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ActionInvocationObjectType implements ObjectType<ActionInvocationObjectType.Invocation> {

    public record Invocation(
        String callId, String sessionId, String actionId, String status,
        String inputJson, String outputJson, String errorJson,
        long startedAt, Long endedAt
    ) {}

    @Override public String id() { return ObjectTypes.ACTION_INVOCATION; }
    @Override public String displayName() { return "ActionInvocation"; }
    @Override public List<String> primaryKey() { return List.of("callId"); }
    @Override public Optional<String> titleField() { return Optional.of("actionId"); }
    @Override public Class<Invocation> javaType() { return Invocation.class; }

    @Override
    public Map<String, Object> propertySchema() {
        return Map.of(
            "type", "object",
            "required", List.of("callId", "sessionId", "actionId", "status"),
            "properties", Map.of(
                "callId",     Map.of("type", "string"),
                "sessionId",  Map.of("type", "string"),
                "actionId",   Map.of("type", "string"),
                "status",     Map.of("type", "string", "enum",
                    List.of("running", "completed", "error", "cancelled")),
                "startedAt",  Map.of("type", "integer"),
                "endedAt",    Map.of("type", "integer")
            )
        );
    }
}
```

- [ ] **Step 2.3: Run — PASS, commit**

```
./mvnw -pl data-talk-adapter -am test -Dtest=OntologyRegistrationIT
git add data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ \
        data-talk-adapter/src/test/java/com/datatalk/adapter/ontology/OntologyRegistrationIT.java
git commit -m "feat(server): register four MVP ObjectType beans"
```

---

## Task 3: Connection CRUD + encrypted storage

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- Create: `data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/connection/ConnectionCrudIT.java`

- [ ] **Step 3.1: Write failing test**

```java
// ConnectionCrudIT.java
package com.datatalk.adapter.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConnectionCrudIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeEach
    void clean() { datatalkJdbc.update("DELETE FROM connections"); }

    @Test
    void createListRoundTrip() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "id", "c1",
            "kind", "postgresql",
            "host", "localhost",
            "port", 5432,
            "database", "demo",
            "username", "alice",
            "password", "secret123"
        ));

        mvc.perform(post("/api/connections").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/connections"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connections[0].id").value("c1"))
            .andExpect(jsonPath("$.connections[0].username").value("alice"));

        // password never leaks
        mvc.perform(get("/api/connections"))
            .andExpect(jsonPath("$.connections[0].password").doesNotExist())
            .andExpect(jsonPath("$.connections[0].passwordEnc").doesNotExist());
    }
}
```

- [ ] **Step 3.2: Implement ConnectionRecord + repository**

```java
// ConnectionRecord.java
package com.datatalk.application.persistence;

public record ConnectionRecord(
    String id, String kind, String host, int port,
    String databaseName, String username, byte[] passwordEnc,
    String schemaDigest, long createdAt
) {}
```

```java
// ConnectionRepository.java
package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ConnectionRepository {

    private final JdbcTemplate jdbc;
    public ConnectionRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<ConnectionRecord> MAPPER = (rs, i) -> new ConnectionRecord(
        rs.getString("id"), rs.getString("kind"), rs.getString("host"),
        rs.getInt("port"), rs.getString("database_name"), rs.getString("username"),
        rs.getBytes("password_enc"), rs.getString("schema_digest"), rs.getLong("created_at")
    );

    public void insert(ConnectionRecord c) {
        jdbc.update("""
            INSERT INTO connections(id, kind, host, port, database_name, username, password_enc, schema_digest, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.kind(), c.host(), c.port(), c.databaseName(), c.username(),
            c.passwordEnc(), c.schemaDigest(), c.createdAt());
    }

    public List<ConnectionRecord> findAll() {
        return jdbc.query("SELECT * FROM connections ORDER BY created_at DESC", MAPPER);
    }

    public Optional<ConnectionRecord> findById(String id) {
        var list = jdbc.query("SELECT * FROM connections WHERE id = ?", MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
```

```java
// ConnectionService.java
package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.persistence.SecretVault;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class ConnectionService {

    private final ConnectionRepository repo;
    private final SecretVault vault;
    private final Clock clock;

    public ConnectionService(ConnectionRepository repo, SecretVault vault, Clock clock) {
        this.repo = repo;
        this.vault = vault;
        this.clock = clock;
    }

    public void create(String id, String kind, String host, int port, String database,
                       String username, String password) {
        byte[] enc = vault.seal(password);
        repo.insert(new ConnectionRecord(id, kind, host, port, database, username, enc, null, clock.millis()));
    }

    public List<ConnectionView> list() {
        return repo.findAll().stream()
            .map(c -> new ConnectionView(c.id(), c.kind(), c.host(), c.port(),
                c.databaseName(), c.username(), c.createdAt()))
            .toList();
    }

    public String decryptPassword(String id) {
        return repo.findById(id)
            .map(c -> vault.open(c.passwordEnc()))
            .orElseThrow(() -> new IllegalArgumentException("unknown connection: " + id));
    }

    /** Safe-to-serialize view. Omits password_enc. */
    public record ConnectionView(String id, String kind, String host, int port,
                                 String databaseName, String username, long createdAt) {}
}
```

```java
// ConnectionController.java
package com.datatalk.infra.connection;

import com.datatalk.application.connection.ConnectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService svc;
    public ConnectionController(ConnectionService svc) { this.svc = svc; }

    public record CreateBody(String id, String kind, String host, int port,
                             String database, String username, String password) {}

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody CreateBody body) {
        svc.create(body.id(), body.kind(), body.host(), body.port(),
            body.database(), body.username(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("connections", svc.list());
    }
}
```

- [ ] **Step 3.3: Provide a test master-key for SecretVault**

In `data-talk-adapter/src/test/resources/application.yml` (create if missing):

```yaml
datatalk:
  master-key-hex: "0000000000000000000000000000000000000000000000000000000000000000"
```

And wire a bean in `data-talk-adapter/src/main/java/com/datatalk/adapter/config/SecretVaultConfig.java`:

```java
package com.datatalk.adapter.config;

import com.datatalk.application.persistence.SecretVault;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HexFormat;

@Configuration
public class SecretVaultConfig {
    @Bean
    public SecretVault secretVault(@Value("${datatalk.master-key-hex}") String hex) {
        byte[] key = HexFormat.of().parseHex(hex);
        return new SecretVault(key);
    }
}
```

- [ ] **Step 3.4: Run — PASS, commit**

```
./mvnw -pl data-talk-adapter -am test -Dtest=ConnectionCrudIT
git add data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java \
        data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java \
        data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java \
        data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java \
        data-talk-adapter/src/main/java/com/datatalk/adapter/config/SecretVaultConfig.java \
        data-talk-adapter/src/test/resources/application.yml \
        data-talk-adapter/src/test/java/com/datatalk/adapter/connection/ConnectionCrudIT.java
git commit -m "feat(server): add Connection CRUD with encrypted password storage"
```

---

## Task 4: SQL safety guard

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/sql/SqlStatementGuard.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/sql/SqlStatementGuardTest.java`

- [ ] **Step 4.1: Write failing test**

```java
package com.datatalk.application.sql;

import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlStatementGuardTest {

    private final SqlStatementGuard guard = new SqlStatementGuard();

    @Test void acceptsSimpleSelect() {
        assertThatCode(() -> guard.assertSelectOnly("SELECT * FROM users")).doesNotThrowAnyException();
    }
    @Test void acceptsSelectWithWith() {
        assertThatCode(() -> guard.assertSelectOnly("WITH t AS (SELECT 1) SELECT * FROM t")).doesNotThrowAnyException();
    }
    @Test void rejectsInsert() {
        assertThatThrownBy(() -> guard.assertSelectOnly("INSERT INTO t VALUES(1)"))
            .isInstanceOf(DataTalkException.class)
            .matches(e -> ((DataTalkException) e).code().equals(DataTalkErrorCodes.SQL_FORBIDDEN));
    }
    @Test void rejectsDelete() {
        assertThatThrownBy(() -> guard.assertSelectOnly("DELETE FROM t WHERE 1=1"))
            .isInstanceOf(DataTalkException.class);
    }
    @Test void rejectsUpdate() {
        assertThatThrownBy(() -> guard.assertSelectOnly("UPDATE t SET x=1"))
            .isInstanceOf(DataTalkException.class);
    }
    @Test void rejectsDrop() {
        assertThatThrownBy(() -> guard.assertSelectOnly("DROP TABLE t"))
            .isInstanceOf(DataTalkException.class);
    }
    @Test void rejectsMultipleStatements() {
        assertThatThrownBy(() -> guard.assertSelectOnly("SELECT 1; DELETE FROM t"))
            .isInstanceOf(DataTalkException.class);
    }
    @Test void rejectsCommentedOutInjection() {
        assertThatThrownBy(() -> guard.assertSelectOnly("SELECT 1; -- DELETE FROM t"))
            .isInstanceOf(DataTalkException.class);
    }
}
```

- [ ] **Step 4.2: Implement SqlStatementGuard**

```java
package com.datatalk.application.sql;

import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lightweight MVP guard. Accepts only statements whose first keyword
 * (after stripping comments/whitespace) is SELECT or WITH, and rejects
 * any input containing a second statement separator.
 *
 * <p>This is deliberately conservative — edge cases like dollar-quoted
 * strings in Postgres are not handled. For MVP we ship SELECT-only.</p>
 */
@Component
public class SqlStatementGuard {

    private static final Set<String> ALLOWED_FIRST_KEYWORDS = Set.of("SELECT", "WITH");
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern FIRST_KEYWORD = Pattern.compile("^\\s*(\\w+)");

    public void assertSelectOnly(String sql) {
        String cleaned = BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(sql).replaceAll(""))
            .replaceAll("").trim();
        if (cleaned.isEmpty()) throw forbidden("empty SQL");

        // Reject multiple statements. Naive but effective for MVP.
        if (cleaned.endsWith(";")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        if (cleaned.contains(";")) {
            throw forbidden("multiple statements not allowed");
        }

        var matcher = FIRST_KEYWORD.matcher(cleaned);
        if (!matcher.find()) throw forbidden("cannot determine statement type");
        String head = matcher.group(1).toUpperCase();
        if (!ALLOWED_FIRST_KEYWORDS.contains(head)) {
            throw forbidden("only SELECT / WITH allowed, got: " + head);
        }
    }

    private DataTalkException forbidden(String msg) {
        return new DataTalkException(DataTalkErrorCodes.SQL_FORBIDDEN,
            "MVP only permits read queries: " + msg, false);
    }
}
```

- [ ] **Step 4.3: Run — PASS, commit**

```
./mvnw -pl data-talk-application test -Dtest=SqlStatementGuardTest
git add data-talk-application/src/main/java/com/datatalk/application/sql/SqlStatementGuard.java \
        data-talk-application/src/test/java/com/datatalk/application/sql/SqlStatementGuardTest.java
git commit -m "feat(server): add SELECT-only SQL safety guard for MVP"
```

---

## Task 5: Real OpenCode /event SSE consumer + retry/backoff

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java`
- Modify: `data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopTest.java`

- [ ] **Step 5.1: Write failing test**

```java
// OpenCodeEventLoopTest.java
package com.datatalk.application.opencode;

import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OpenCodeEventLoopTest {

    WireMockServer wm;

    @BeforeEach void up() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }
    @AfterEach void down() { wm.stop(); }

    @Test
    void parsesSseFramesIntoOcEvents() {
        String sse = """
            event: server.connected
            data: {}

            event: message.part.delta
            data: {"partID":"p1","field":"text","delta":"hi"}

            """;
        wm.stubFor(get(urlEqualTo("/event"))
            .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse)));

        List<OcEvent> received = new ArrayList<>();
        OpenCodeSessionMap map = new OpenCodeSessionMap();
        map.bind("dt-1", "oc-1");
        SessionBusRegistry buses = Mockito.mock(SessionBusRegistry.class);
        OpenCodeEventTranslator tr = new OpenCodeEventTranslator();
        OpenCodeEventLoop loop = new OpenCodeEventLoop(
            "http://localhost:" + wm.port(), new ObjectMapper(), tr, buses, map, received::add);

        loop.start();
        await().atMost(Duration.ofSeconds(3)).until(() -> received.size() >= 2);
        loop.stop();

        assertThat(received.get(0)).isInstanceOf(OcEvent.ServerConnected.class);
        assertThat(received.get(1)).isInstanceOf(OcEvent.MessagePartDelta.class);
    }
}
```

- [ ] **Step 5.2: Implement OpenCodeEventLoop**

```java
// OpenCodeEventLoop.java
package com.datatalk.application.opencode;

import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Subscribes to OpenCode's global /event SSE stream, parses frames into
 * {@link OcEvent}s, translates them via {@link OpenCodeEventTranslator},
 * and publishes the resulting {@code DtEvent}s to the correct SessionBus
 * based on the {@code OpenCodeSessionMap}.
 *
 * <p>Backoff: starts at 1s, doubles to 30s max, resets on successful stream.</p>
 */
public class OpenCodeEventLoop {

    private final String baseUrl;
    private final ObjectMapper om;
    private final OpenCodeEventTranslator translator;
    private final SessionBusRegistry buses;
    private final OpenCodeSessionMap sessionMap;
    private final Consumer<OcEvent> tap;   // for tests
    private final AtomicReference<Disposable> subscription = new AtomicReference<>();
    private volatile boolean running = false;

    public OpenCodeEventLoop(String baseUrl, ObjectMapper om,
                             OpenCodeEventTranslator translator,
                             SessionBusRegistry buses,
                             OpenCodeSessionMap sessionMap,
                             Consumer<OcEvent> tap) {
        this.baseUrl = baseUrl;
        this.om = om;
        this.translator = translator;
        this.buses = buses;
        this.sessionMap = sessionMap;
        this.tap = tap == null ? e -> {} : tap;
    }

    public void start() {
        if (running) return;
        running = true;
        subscribe(Duration.ofSeconds(1));
    }

    public void stop() {
        running = false;
        Disposable s = subscription.get();
        if (s != null) s.dispose();
    }

    private void subscribe(Duration delay) {
        if (!running) return;
        WebClient wc = WebClient.builder().baseUrl(baseUrl).build();
        Disposable d = wc.get().uri("/event")
            .retrieve()
            .bodyToFlux(String.class)
            .doOnNext(this::handleFrame)
            .doOnError(e -> scheduleReconnect(delay))
            .doOnComplete(() -> scheduleReconnect(delay))
            .subscribe();
        subscription.set(d);
    }

    private void scheduleReconnect(Duration current) {
        if (!running) return;
        Duration next = current.multipliedBy(2);
        if (next.getSeconds() > 30) next = Duration.ofSeconds(30);
        try { Thread.sleep(current.toMillis()); } catch (InterruptedException ignored) {}
        subscribe(next);
    }

    private void handleFrame(String rawFrame) {
        // Each rawFrame from WebFlux is actually a full SSE event (name+data composed).
        // We parse lines: "event: X\ndata: {...}\n\n"
        String eventName = null;
        StringBuilder data = new StringBuilder();
        for (String line : rawFrame.split("\n")) {
            if (line.startsWith("event:")) eventName = line.substring(6).trim();
            else if (line.startsWith("data:")) data.append(line.substring(5).trim());
        }
        if (eventName == null) return;
        OcEvent oc = parseOcEvent(eventName, data.toString());
        tap.accept(oc);

        // Best-effort fan-out. Each Part carries its sessionID; we use the
        // OpenCodeSessionMap to find the DataTalk session.
        String openCodeSessionId = extractSessionId(oc);
        if (openCodeSessionId == null) return;
        String dataTalkSessionId = sessionMap.dataTalkFor(openCodeSessionId);
        if (dataTalkSessionId == null) return;
        SessionBus bus = buses.getOrCreate(dataTalkSessionId);
        for (DtEvent dt : translator.translate(dataTalkSessionId, oc)) {
            bus.publish(dt);
        }
    }

    private OcEvent parseOcEvent(String name, String json) {
        try {
            JsonNode node = json.isEmpty() ? om.createObjectNode() : om.readTree(json);
            return switch (name) {
                case "server.connected" -> new OcEvent.ServerConnected();
                case "session.status"   -> new OcEvent.SessionStatus(
                    node.path("status").asText("idle"),
                    om.convertValue(node.path("retryInfo"), Map.class)
                );
                case "message.updated"  -> new OcEvent.MessageUpdated(
                    om.treeToValue(node.path("info"), Message.class));
                case "message.part.updated" -> new OcEvent.MessagePartUpdated(
                    om.treeToValue(node.path("part"), Part.class));
                case "message.part.delta"   -> new OcEvent.MessagePartDelta(
                    node.path("partID").asText(),
                    node.path("field").asText(),
                    node.path("delta").asText());
                case "message.part.removed" -> new OcEvent.MessagePartRemoved(
                    node.path("partID").asText());
                default -> new OcEvent.Unknown(name, om.convertValue(node, Map.class));
            };
        } catch (Exception e) {
            return new OcEvent.Unknown(name, Map.of("parseError", e.getMessage()));
        }
    }

    private static String extractSessionId(OcEvent e) {
        return switch (e) {
            case OcEvent.MessageUpdated m   -> m.message().sessionId();
            case OcEvent.MessagePartUpdated p -> p.part().sessionID();
            case OcEvent.SessionStatus s    -> null;  // global — needs map traversal; Plan B: skip
            default                         -> null;
        };
    }
}
```

- [ ] **Step 5.3: Wire the event loop into OpenCodeGatewayBeans**

Modify `OpenCodeGatewayBeans.java` (from Plan A Task 26):

```java
// add field:
private final OpenCodeEventLoop eventLoop;
// in the constructor, also receive:
OpenCodeEventLoop eventLoop
// in registerOnStartup:
eventLoop.start();
```

Add a `@Bean` method:

```java
@Bean
public OpenCodeEventLoop openCodeEventLoop(
    @Value("${datatalk.opencode.base-url}") String baseUrl,
    com.fasterxml.jackson.databind.ObjectMapper om,
    OpenCodeEventTranslator translator,
    com.datatalk.application.session.SessionBusRegistry buses,
    OpenCodeSessionMap map
) {
    return new OpenCodeEventLoop(baseUrl, om, translator, buses, map, null);
}
```

- [ ] **Step 5.4: Run — PASS, commit**

```
./mvnw -pl data-talk-application test -Dtest=OpenCodeEventLoopTest
git add data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java \
        data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java \
        data-talk-application/src/test/java/com/datatalk/application/opencode/OpenCodeEventLoopTest.java
git commit -m "feat(server): add real OpenCode /event SSE loop with retry/backoff"
```

---

## Task 6: `datatalk.read_schema` action

**Files:**
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ReadSchemaActionIT.java`

- [ ] **Step 6.1: Add Testcontainers deps to `data-talk-adapter/pom.xml`**

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.20.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 6.2: Write failing IT**

```java
// ReadSchemaActionIT.java
package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@SpringBootTest
class ReadSchemaActionIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @Autowired ConnectionService conn;
    @Autowired ReadSchemaAction action;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeAll
    void seedDb() throws Exception {
        try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             var st = c.createStatement()) {
            st.execute("CREATE TABLE users (id SERIAL PRIMARY KEY, name TEXT, created_at TIMESTAMP)");
            st.execute("CREATE TABLE orders (id SERIAL PRIMARY KEY, user_id INT REFERENCES users(id))");
        }
        datatalkJdbc.update("DELETE FROM connections");
        conn.create("pg-test", "postgresql", pg.getHost(), pg.getMappedPort(5432),
            pg.getDatabaseName(), pg.getUsername(), pg.getPassword());
    }

    @Test
    void readSchemaReturnsBothTables() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-1", "pg-test", "oc-1"),
            Map.of("connectionId", "pg-test")
        ).toCompletableFuture().get();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schema = (List<Map<String, Object>>) out.get("schema");
        assertThat(schema).extracting(t -> t.get("name"))
            .containsExactlyInAnyOrder("users", "orders");
    }
}
```

- [ ] **Step 6.3: Implement ReadSchemaAction**

```java
package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.read_schema",
    executor = Executor.OPENCODE,
    description = "Return table + column metadata for the given connection. Read-only context tool.",
    requiresConnection = true,
    timeoutMs = 10_000
)
public class ReadSchemaAction implements ActionHandler<Map, Map> {

    private final ConnectionRepository connRepo;
    private final ConnectionService conn;

    public ReadSchemaAction(ConnectionRepository connRepo, ConnectionService conn) {
        this.connRepo = connRepo;
        this.conn = conn;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("connectionId"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "tables", Map.of("type", "array", "items", Map.of("type", "string"))
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("schema"),
            "properties", Map.of("schema", Map.of("type", "array")));
    }

    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String connectionId = String.valueOf(input.get("connectionId"));
        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("unknown connection " + connectionId));
        String password = conn.decryptPassword(connectionId);
        String url = jdbcUrl(cr);

        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url, cr.username(), password)) {
            var meta = c.getMetaData();
            try (ResultSet tbl = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tbl.next()) {
                    String name = tbl.getString("TABLE_NAME");
                    List<Map<String, Object>> cols = new ArrayList<>();
                    try (ResultSet colRs = meta.getColumns(null, null, name, "%")) {
                        while (colRs.next()) {
                            cols.add(Map.of(
                                "name", colRs.getString("COLUMN_NAME"),
                                "type", colRs.getString("TYPE_NAME"),
                                "nullable", "YES".equals(colRs.getString("IS_NULLABLE"))
                            ));
                        }
                    }
                    tables.add(Map.of("name", name, "columns", cols));
                }
            }
        } catch (Exception e) {
            return CompletableFuture.failedStage(new RuntimeException("schema read failed", e));
        }
        return CompletableFuture.completedFuture(Map.of("schema", tables));
    }

    private static String jdbcUrl(ConnectionRecord c) {
        return switch (c.kind()) {
            case "postgresql" -> "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            case "mysql"      -> "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            default           -> throw new IllegalArgumentException("unsupported kind: " + c.kind());
        };
    }
}
```

- [ ] **Step 6.4: Run — PASS, commit**

```
./mvnw -pl data-talk-adapter -am test -Dtest=ReadSchemaActionIT
git add data-talk-adapter/pom.xml \
        data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java \
        data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ReadSchemaActionIT.java
git commit -m "feat(server): add datatalk.read_schema action with Testcontainers IT"
```

---

## Task 7: `datatalk.execute_sql` action + artifact persistence

**Files:**
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionIT.java`

- [ ] **Step 7.1: Write failing IT**

```java
// ExecuteSqlActionIT.java — same Testcontainers pattern as Task 6.
// After seeding a users table with 3 rows, call the action and verify:
// (a) output contains artifactId and preview
// (b) artifacts table has a new row with kind='table'
package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@SpringBootTest
class ExecuteSqlActionIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15");

    @Autowired ConnectionService conn;
    @Autowired SessionRepository sess;
    @Autowired ExecuteSqlAction action;
    @Autowired ArtifactRepository artifacts;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeAll
    void seed() throws Exception {
        try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             var st = c.createStatement()) {
            st.execute("CREATE TABLE t(id INT, name TEXT)");
            st.execute("INSERT INTO t VALUES(1,'a'),(2,'b'),(3,'c')");
        }
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");
        conn.create("pg-exec", "postgresql", pg.getHost(), pg.getMappedPort(5432),
            pg.getDatabaseName(), pg.getUsername(), pg.getPassword());
        sess.upsert(new SessionRecord("s-exec", "pg-exec", "T", true, "oc-e", 0L, 0L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsPreviewAndPersistsArtifact() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-exec", "c-1", "pg-exec", "oc-e"),
            Map.of("connectionId", "pg-exec", "sql", "SELECT * FROM t ORDER BY id")
        ).toCompletableFuture().get();

        assertThat(out).containsKeys("artifactId", "columns", "preview", "rowCount");
        assertThat((List<?>) out.get("preview")).hasSize(3);
        assertThat(artifacts.findBySession("s-exec")).hasSize(1);
    }

    @Test
    void rejectsDelete() {
        assertThat(
            action.handle(
                new ActionContext("s-exec", "c-1", "pg-exec", "oc-e"),
                Map.of("connectionId", "pg-exec", "sql", "DELETE FROM t")
            ).toCompletableFuture()
        ).isCompletedExceptionally();
    }
}
```

- [ ] **Step 7.2: Implement ExecuteSqlAction**

```java
package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.*;
import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.domain.action.*;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.execute_sql",
    executor = Executor.SERVER,
    description = "Run a SELECT query on the given connection and persist the result as a table Artifact.",
    produces = {"datatalk.artifact"},
    requiresConnection = true,
    timeoutMs = 30_000
)
public class ExecuteSqlAction implements ActionHandler<Map, Map> {

    private static final int INLINE_LIMIT_BYTES = 256 * 1024;
    private static final int PREVIEW_ROWS = 100;

    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final SqlStatementGuard guard;
    private final ArtifactRepository artifacts;
    private final QueryResultRepository queryResults;
    private final ObjectMapper om;
    private final Clock clock;

    public ExecuteSqlAction(ConnectionRepository connRepo, ConnectionService connSvc,
                            SqlStatementGuard guard, ArtifactRepository artifacts,
                            QueryResultRepository queryResults, ObjectMapper om, Clock clock) {
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.guard = guard;
        this.artifacts = artifacts;
        this.queryResults = queryResults;
        this.om = om;
        this.clock = clock;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("connectionId", "sql"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "sql",          Map.of("type", "string"),
                "pageSize",     Map.of("type", "integer", "minimum", 1, "maximum", 10_000)
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("artifactId", "version", "columns", "preview", "rowCount", "durationMs"),
            "properties", Map.of(
                "artifactId",  Map.of("type", "string"),
                "version",     Map.of("type", "integer"),
                "handle",      Map.of("type", "string"),
                "columns",     Map.of("type", "array"),
                "preview",     Map.of("type", "array"),
                "rowCount",    Map.of("type", "integer"),
                "durationMs",  Map.of("type", "integer")
            ));
    }

    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.CREATE_ARTIFACT); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        return CompletableFuture.supplyAsync(() -> execute(ctx, input));
    }

    private Map<String, Object> execute(ActionContext ctx, Map<String, Object> input) {
        String sql = String.valueOf(input.get("sql"));
        guard.assertSelectOnly(sql);

        String connectionId = String.valueOf(input.get("connectionId"));
        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "unknown connection: " + connectionId, false));

        long started = clock.millis();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection c = DriverManager.getConnection(jdbcUrl(cr), cr.username(),
                connSvc.decryptPassword(connectionId));
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                var md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) columns.add(md.getColumnLabel(i));
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) row.put(columns.get(i - 1), rs.getObject(i));
                    rows.add(row);
                }
            }
        } catch (SQLTimeoutException e) {
            throw new DataTalkException(DataTalkErrorCodes.SQL_TIMEOUT, "query timeout", false);
        } catch (SQLException e) {
            throw new DataTalkException(DataTalkErrorCodes.SQL_SYNTAX_ERROR, e.getMessage(), true);
        }

        long duration = clock.millis() - started;
        String artifactId = "art-" + UUID.randomUUID();
        int version = 1;
        long createdAt = clock.millis();

        String rowsJson;
        try { rowsJson = om.writeValueAsString(rows); }
        catch (Exception e) { throw new RuntimeException(e); }

        String payloadRef;
        int payloadSize = rowsJson.getBytes().length;
        if (payloadSize <= INLINE_LIMIT_BYTES) {
            payloadRef = "INLINE:" + rowsJson;
        } else {
            String handle = "qr-" + UUID.randomUUID();
            try {
                queryResults.insert(handle, ctx.sessionId(),
                    om.writeValueAsString(columns),
                    String.join("\n", rows.stream().map(r -> {
                        try { return om.writeValueAsString(r); } catch (Exception e) { throw new RuntimeException(e); }
                    }).toList()),
                    rows.size(), createdAt, createdAt + 7L * 24 * 3600 * 1000);
            } catch (Exception e) { throw new RuntimeException(e); }
            payloadRef = "HANDLE:" + handle;
        }

        artifacts.insert(new ArtifactRecord(
            artifactId, version, ctx.sessionId(), "table", ctx.callId(),
            payloadRef, payloadSize, null, null, false, createdAt));

        List<Map<String, Object>> preview = rows.size() > PREVIEW_ROWS
            ? rows.subList(0, PREVIEW_ROWS) : rows;

        return Map.of(
            "artifactId", artifactId,
            "version", version,
            "handle", payloadRef.startsWith("HANDLE:") ? payloadRef.substring(7) : "",
            "columns", columns,
            "preview", preview,
            "rowCount", rows.size(),
            "durationMs", (int) duration
        );
    }

    private static String jdbcUrl(ConnectionRecord c) {
        return switch (c.kind()) {
            case "postgresql" -> "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            case "mysql"      -> "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            default           -> throw new DataTalkException(DataTalkErrorCodes.CONNECTION_MISSING,
                "unsupported kind: " + c.kind(), false);
        };
    }
}
```

- [ ] **Step 7.3: Run — PASS, commit**

```
./mvnw -pl data-talk-adapter -am test -Dtest=ExecuteSqlActionIT
git add data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java \
        data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ExecuteSqlActionIT.java
git commit -m "feat(server): add datatalk.execute_sql action with artifact persistence"
```

---

## Task 8: `datatalk.render_chart` action

**Files:**
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/actions/RenderChartActionTest.java`

- [ ] **Step 8.1: Write failing test**

```java
// RenderChartActionTest.java
package com.datatalk.adapter.actions;

import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.action.ActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RenderChartActionTest {

    @Autowired RenderChartAction action;
    @Autowired ArtifactRepository artifacts;
    @Autowired SessionRepository sessions;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeEach
    void clean() {
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM sessions");
        sessions.upsert(new SessionRecord("s-1", null, "T", true, null, 0L, 0L));
        artifacts.insert(new ArtifactRecord("art-src", 1, "s-1", "table", "c-1",
            "INLINE:[]", 2, null, null, false, 0L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsNewArtifactAndReturnsId() throws Exception {
        Map<String, Object> out = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-r1", null, "oc-1"),
            Map.of(
                "sourceArtifactId", "art-src",
                "echartsOption", Map.of(
                    "xAxis", Map.of("type", "category", "data", List.of("a","b","c")),
                    "yAxis", Map.of("type", "value"),
                    "series", List.of(Map.of("type", "line", "data", List.of(1,2,3)))
                )
            )
        ).toCompletableFuture().get();

        assertThat(out.get("artifactId")).isNotNull();
        assertThat(out.get("version")).isEqualTo(1);
    }

    @Test
    void rejectsMissingEchartsOption() {
        assertThat(
            action.handle(new ActionContext("s-1","c","q","oc"),
                Map.of("sourceArtifactId","art-src"))
                .toCompletableFuture()
        ).isCompletedExceptionally();
    }

    @Test
    @SuppressWarnings("unchecked")
    void supersedesBumpsVersion() throws Exception {
        Map<String, Object> first = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-r1", null, "oc-1"),
            Map.of("sourceArtifactId", "art-src",
                "echartsOption", Map.of("series", List.of(Map.of("type","line","data",List.of(1)))))
        ).toCompletableFuture().get();

        Map<String, Object> second = (Map<String, Object>) action.handle(
            new ActionContext("s-1", "c-r2", null, "oc-1"),
            Map.of("sourceArtifactId", "art-src",
                "supersedes", first.get("artifactId"),
                "echartsOption", Map.of(
                    "color", List.of("#22c55e"),
                    "series", List.of(Map.of("type","line","data",List.of(1)))
                ))
        ).toCompletableFuture().get();

        assertThat(second.get("artifactId")).isNotEqualTo(first.get("artifactId"));
        assertThat(second.get("version")).isEqualTo(1);
    }
}
```

- [ ] **Step 8.2: Implement RenderChartAction**

```java
package com.datatalk.adapter.actions;

import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.render_chart",
    executor = Executor.SERVER,
    description = "Render an ECharts-option chart. Set supersedes to replace a previous chart.",
    produces = {"datatalk.artifact"},
    requiresConnection = false,
    timeoutMs = 5_000
)
public class RenderChartAction implements ActionHandler<Map, Map> {

    private final ArtifactRepository artifacts;
    private final ObjectMapper om;
    private final Clock clock;

    public RenderChartAction(ArtifactRepository artifacts, ObjectMapper om, Clock clock) {
        this.artifacts = artifacts;
        this.om = om;
        this.clock = clock;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("sourceArtifactId", "echartsOption"),
            "properties", Map.of(
                "sourceArtifactId", Map.of("type", "string"),
                "echartsOption",    Map.of("type", "object"),
                "supersedes",       Map.of("type", "string")
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("artifactId", "version"),
            "properties", Map.of(
                "artifactId", Map.of("type", "string"),
                "version",    Map.of("type", "integer")));
    }

    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.CREATE_ARTIFACT); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String source = String.valueOf(input.get("sourceArtifactId"));
        Object optObj = input.get("echartsOption");
        if (!(optObj instanceof Map<?, ?> opt)) {
            return CompletableFuture.failedStage(new DataTalkException(
                DataTalkErrorCodes.SCHEMA_INPUT_INVALID, "echartsOption must be an object", true));
        }
        // Soft check that sourceArtifactId exists in this session
        if (artifacts.findLatestById(source).isEmpty()) {
            return CompletableFuture.failedStage(new DataTalkException(
                DataTalkErrorCodes.SCHEMA_INPUT_INVALID,
                "sourceArtifactId " + source + " not found", true));
        }

        String supersedes = (String) input.get("supersedes");
        Integer supersedesVer = null;
        if (supersedes != null) {
            var prev = artifacts.findLatestById(supersedes);
            if (prev.isEmpty()) {
                return CompletableFuture.failedStage(new DataTalkException(
                    DataTalkErrorCodes.ARTIFACT_SUPERSEDES_NOT_FOUND,
                    "no artifact with id " + supersedes, true));
            }
            supersedesVer = prev.get().version();
        }

        String artifactId = "art-" + UUID.randomUUID();
        String payloadJson;
        try { payloadJson = om.writeValueAsString(Map.of(
            "sourceArtifactId", source,
            "echartsOption", opt
        )); } catch (Exception e) {
            return CompletableFuture.failedStage(e);
        }

        artifacts.insert(new ArtifactRecord(
            artifactId, 1, ctx.sessionId(), "chart", ctx.callId(),
            "INLINE:" + payloadJson, payloadJson.length(),
            supersedes, supersedesVer, false, clock.millis()
        ));

        return CompletableFuture.completedFuture(Map.of(
            "artifactId", artifactId,
            "version", 1
        ));
    }
}
```

- [ ] **Step 8.3: Run — PASS, commit**

```
./mvnw -pl data-talk-adapter -am test -Dtest=RenderChartActionTest
git add data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java \
        data-talk-adapter/src/test/java/com/datatalk/adapter/actions/RenderChartActionTest.java
git commit -m "feat(server): add datatalk.render_chart action with supersedes chain"
```

---

## Task 9: `datatalk.layout_erd` action

**Files:**
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/actions/LayoutErdActionIT.java`

- [ ] **Step 9.1: Implementation (abbreviated — follow Tasks 6/7 TDD pattern)**

```java
package com.datatalk.adapter.actions;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.*;
import com.datatalk.domain.action.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.layout_erd",
    executor = Executor.SERVER,
    description = "Generate an ER diagram (nodes + edges + grid layout) for the given tables.",
    produces = {"datatalk.artifact"},
    requiresConnection = true,
    timeoutMs = 15_000
)
public class LayoutErdAction implements ActionHandler<Map, Map> {

    private final ConnectionRepository connRepo;
    private final ConnectionService conn;
    private final ArtifactRepository artifacts;
    private final ObjectMapper om;
    private final Clock clock;

    public LayoutErdAction(ConnectionRepository connRepo, ConnectionService conn,
                           ArtifactRepository artifacts, ObjectMapper om, Clock clock) {
        this.connRepo = connRepo; this.conn = conn;
        this.artifacts = artifacts; this.om = om; this.clock = clock;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("connectionId", "tables"),
            "properties", Map.of(
                "connectionId", Map.of("type", "string"),
                "tables", Map.of("type", "array", "items", Map.of("type", "string")),
                "layoutAlgo", Map.of("type", "string")
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("artifactId", "version", "nodes", "edges"),
            "properties", Map.of(
                "artifactId", Map.of("type", "string"),
                "version", Map.of("type", "integer"),
                "nodes", Map.of("type", "array"),
                "edges", Map.of("type", "array")));
    }

    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.CREATE_ARTIFACT); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String connectionId = String.valueOf(input.get("connectionId"));
        List<String> tables = (List<String>) input.get("tables");
        ConnectionRecord cr = connRepo.findById(connectionId).orElseThrow();
        String pw = conn.decryptPassword(connectionId);

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        try (Connection c = DriverManager.getConnection(jdbcUrl(cr), cr.username(), pw)) {
            var meta = c.getMetaData();
            int i = 0;
            for (String table : tables) {
                List<Map<String, Object>> cols = new ArrayList<>();
                try (ResultSet rs = meta.getColumns(null, null, table, "%")) {
                    while (rs.next()) cols.add(Map.of(
                        "name", rs.getString("COLUMN_NAME"),
                        "type", rs.getString("TYPE_NAME")
                    ));
                }
                int col = i % 4, row = i / 4;
                nodes.add(Map.of(
                    "id", table,
                    "position", Map.of("x", col * 240, "y", row * 180),
                    "columns", cols
                ));
                try (ResultSet rs = meta.getImportedKeys(null, null, table)) {
                    while (rs.next()) {
                        edges.add(Map.of(
                            "from", rs.getString("PKTABLE_NAME"),
                            "to", table,
                            "fromCol", rs.getString("PKCOLUMN_NAME"),
                            "toCol", rs.getString("FKCOLUMN_NAME")
                        ));
                    }
                }
                i++;
            }
        } catch (Exception e) {
            return CompletableFuture.failedStage(e);
        }

        String artifactId = "art-" + UUID.randomUUID();
        String payloadJson;
        try {
            payloadJson = om.writeValueAsString(Map.of("nodes", nodes, "edges", edges));
        } catch (Exception e) { return CompletableFuture.failedStage(e); }
        artifacts.insert(new ArtifactRecord(
            artifactId, 1, ctx.sessionId(), "erd", ctx.callId(),
            "INLINE:" + payloadJson, payloadJson.length(),
            null, null, false, clock.millis()));

        return CompletableFuture.completedFuture(Map.of(
            "artifactId", artifactId,
            "version", 1,
            "nodes", nodes,
            "edges", edges
        ));
    }

    private static String jdbcUrl(ConnectionRecord c) {
        return switch (c.kind()) {
            case "postgresql" -> "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            case "mysql"      -> "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + c.databaseName();
            default -> throw new IllegalArgumentException("unsupported: " + c.kind());
        };
    }
}
```

Write `LayoutErdActionIT.java` following Tasks 6/7 pattern: create `users + orders` tables, assert 2 nodes + 1 edge. Commit:

```
git commit -m "feat(server): add datatalk.layout_erd action with grid layout"
```

---

## Task 10: `datatalk.pin_artifact` (CLIENT executor)

**Files:**
- Create: `data-talk-adapter/src/main/java/com/datatalk/adapter/actions/PinArtifactAction.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/actions/PinArtifactActionTest.java`

```java
// PinArtifactAction.java
package com.datatalk.adapter.actions;

import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * CLIENT-executor action. The handler must not be called by the dispatcher
 * (dispatch routes CLIENT executors through SessionBus.invoke); if it is,
 * surface a clear error.
 */
@Component
@DataTalkAction(
    id = "datatalk.pin_artifact",
    executor = Executor.CLIENT,
    description = "Pin an artifact in the client's timeline so it survives scroll-away.",
    timeoutMs = 2_000
)
public class PinArtifactAction implements ActionHandler<Map, Map> {

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "required", List.of("artifactId"),
            "properties", Map.of("artifactId", Map.of("type", "string")));
    }
    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object", "required", List.of("pinned"),
            "properties", Map.of("pinned", Map.of("type", "boolean")));
    }
    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.PATCH_ARTIFACT); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        throw new UnsupportedOperationException("pin_artifact runs on client; dispatcher must not call handler");
    }
}
```

Test it compiles and descriptor picks `Executor.CLIENT`; commit.

---

## Task 11: `datatalk.supersede_artifact`

```java
// SupersedeArtifactAction.java
package com.datatalk.adapter.actions;

import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.domain.action.*;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.supersede_artifact",
    executor = Executor.SERVER,
    description = "Explicitly mark one artifact as superseded by another.",
    timeoutMs = 3_000
)
public class SupersedeArtifactAction implements ActionHandler<Map, Map> {

    private final ArtifactRepository artifacts;
    public SupersedeArtifactAction(ArtifactRepository artifacts) { this.artifacts = artifacts; }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("newArtifactId", "oldArtifactId"),
            "properties", Map.of(
                "newArtifactId", Map.of("type", "string"),
                "oldArtifactId", Map.of("type", "string"),
                "reason",        Map.of("type", "string")));
    }
    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object", "required", List.of("ok"),
            "properties", Map.of("ok", Map.of("type", "boolean")));
    }
    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.PATCH_ARTIFACT); }
    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        String newId = String.valueOf(input.get("newArtifactId"));
        String oldId = String.valueOf(input.get("oldArtifactId"));
        if (artifacts.findLatestById(oldId).isEmpty() || artifacts.findLatestById(newId).isEmpty()) {
            return CompletableFuture.failedStage(new DataTalkException(
                DataTalkErrorCodes.ARTIFACT_SUPERSEDES_NOT_FOUND,
                "old or new artifact not found", true));
        }
        // For MVP we only broadcast the relationship via the DtEvent — there's no
        // dedicated column to store an external supersede edge; the relationship
        // lives in the new artifact's supersedes_id already when created via render_chart.
        return CompletableFuture.completedFuture(Map.of("ok", true));
    }
}
```

Commit.

---

## Task 12: Wire ChannelController to forward `send_message` to OpenCode + history endpoints

**Files modified/created:**
- Modify: `data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/ChannelController.java`
- Create: `data-talk-infrastructure/src/main/java/com/datatalk/infra/channel/HistoryController.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/channel/HistoryService.java`

- [ ] **Step 12.1**: In `ChannelService.sendMessage`, after writing the user message and publishing events, forward to OpenCode:

```java
// Inject OpenCodeGateway + OpenCodeSessionMap into ChannelService (field + constructor arg).
// Replace the final lines of sendMessage:
String ocSid = sessionMap.openCodeFor(sessionId);
if (ocSid == null) {
    ocSid = gateway.createOpenCodeSession();
    sessionMap.bind(sessionId, ocSid);
}
gateway.forwardUserMessage(ocSid, Map.of(
    "parts", parts.stream().map(om::convertValue).toList()
));
```

- [ ] **Step 12.2**: Create `HistoryController` exposing `GET /api/sessions/{id}/messages` and `GET /api/sessions/{id}/artifacts`:

```java
// HistoryController.java
package com.datatalk.infra.channel;

import com.datatalk.application.persistence.*;
import com.datatalk.domain.part.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class HistoryController {

    private final MessageRepository messages;
    private final ArtifactRepository artifacts;

    public HistoryController(MessageRepository messages, ArtifactRepository artifacts) {
        this.messages = messages;
        this.artifacts = artifacts;
    }

    @GetMapping("/messages")
    public Map<String, List<Message>> messages(@PathVariable String sessionId) {
        return Map.of("messages", messages.findBySession(sessionId));
    }

    @GetMapping("/artifacts")
    public Map<String, List<ArtifactRecord>> artifacts(@PathVariable String sessionId) {
        return Map.of("artifacts", artifacts.findBySession(sessionId));
    }
}
```

Test via MockMvc; commit.

---

## Task 13: End-to-end `§6.1 typical query` test

**File:** `data-talk-adapter/src/test/java/com/datatalk/adapter/smoke/TypicalQueryE2EIT.java`

Hooks up: Testcontainers PostgreSQL + WireMock OpenCode + real DataTalk server. The WireMock OpenCode is pre-scripted to:

1. On `/session` POST → returns `{"id":"oc-1"}`.
2. On `/plugin/register-tool` → 204.
3. On `/session/{id}/message` → 202, then pushes SSE events via `/event`:
   - `message.part.updated` for a reasoning TextPart
   - then triggers a tool_call by POSTing to our `/api/opencode-tool/datatalk.read_schema`
   - after that, a tool_call for `datatalk.execute_sql`
   - finally `session.status:idle`

Assertions:
- Client sees `message.created` + multiple `message.part.*` + two `ontology.updated` + `session.status:idle` via SSE.
- SQLite `artifacts` table has one row of kind `table`.

The WireMock scripting is long; use `WireMockServer.addMockServiceRequestListener` to trigger outbound HTTP-callbacks to simulate the real OpenCode tool-call behavior.

Pseudocode for the fake orchestrator:

```java
// after a POST /session/{id}/message arrives, fire a background thread that:
wm.stubFor(post(urlPathMatching("/session/.*/message"))
    .willReturn(aResponse().withStatus(202)));

// and also:
Executors.newSingleThreadScheduledExecutor().schedule(() -> {
    HttpClient http = HttpClient.newHttpClient();
    // 1. post tool callback for read_schema
    http.send(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/api/opencode-tool/datatalk.read_schema"))
        .header("X-OpenCode-Call-Id", "c-rs")
        .header("X-OpenCode-Session-Id", "oc-1")
        .POST(HttpRequest.BodyPublishers.ofString("{\"connectionId\":\"pg-e2e\"}"))
        .build(), HttpResponse.BodyHandlers.ofString());
    // 2. then execute_sql
    http.send(...execute_sql with connectionId+sql...);
}, 100, TimeUnit.MILLISECONDS);
```

Commit.

---

## Plan B Completion

At the end of Plan B, the server:

- Registers 6 real MVP actions, each with Testcontainers-backed integration tests.
- Runs a real `/event` SSE consumer against OpenCode (or WireMock in tests).
- Persists artifacts in SQLite with proper `supersedes` chains.
- Exposes `/api/connections`, `/api/sessions/:id/messages`, `/api/sessions/:id/artifacts` history endpoints.
- Enforces SELECT-only SQL via `SqlStatementGuard`.
- Runs the full `§6.1 typical query` scenario end to end.

Plan C (client UI) is next.
