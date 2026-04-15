# Plan A — Backend Platform Foundation (Part 2 of 3)

> Continuation of `2026-04-16-manus-a-backend-platform.md`. Tasks 7–14 cover registries, discovery endpoints, Flyway migration, SecretVault, and JDBC repositories.

---

## Task 7: ActionRegistry

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/registry/ActionRegistryTest.java`

The registry scans Spring beans annotated with `@DataTalkAction`, verifies they implement `ActionHandler`, and exposes descriptor + handler lookup.

- [ ] **Step 7.1: Add spring-boot-starter as a compile dep to `data-talk-application/pom.xml`** (needed for `ApplicationContext` in tests)

Inside the existing `<dependencies>` block, add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 7.2: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/registry/ActionRegistryTest.java`:

```java
package com.datatalk.application.registry;

import com.datatalk.domain.action.ActionContext;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.OntologyEffect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {ActionRegistry.class, JsonSchemaLoader.class, ActionRegistryTest.TestActions.class})
class ActionRegistryTest {

    @Autowired ActionRegistry registry;

    @Test
    void discoversAnnotatedHandlers() {
        assertThat(registry.all())
            .extracting("id")
            .containsExactlyInAnyOrder("test.alpha", "test.beta");
    }

    @Test
    void returnsDescriptorWithAnnotationMetadata() {
        var d = registry.require("test.alpha");
        assertThat(d.executor()).isEqualTo(Executor.SERVER);
        assertThat(d.description()).isEqualTo("Alpha");
        assertThat(d.timeoutMs()).isEqualTo(12_345);
    }

    @Test
    void throwsForUnknownActionId() {
        assertThatThrownBy(() -> registry.require("test.missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("test.missing");
    }

    @Test
    void lookupHandlerReturnsBean() {
        ActionHandler<?, ?> h = registry.handler("test.alpha");
        assertThat(h).isInstanceOf(AlphaHandler.class);
    }

    @Configuration
    static class TestActions {
        @Bean AlphaHandler alpha() { return new AlphaHandler(); }
        @Bean BetaHandler  beta()  { return new BetaHandler(); }
    }

    @DataTalkAction(id = "test.alpha", executor = Executor.SERVER, description = "Alpha", timeoutMs = 12_345)
    static class AlphaHandler implements ActionHandler<Map, Map> {
        @Override public Map<String, Object> inputSchema()  { return Map.of("type", "object"); }
        @Override public Map<String, Object> outputSchema() { return Map.of("type", "object"); }
        @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }
        @Override public Class<Map> inputType() { return Map.class; }
        @Override public CompletionStage<Map> handle(ActionContext ctx, Map input) {
            return CompletableFuture.completedFuture(input);
        }
    }

    @DataTalkAction(id = "test.beta", executor = Executor.CLIENT, description = "Beta", requiresConnection = true)
    static class BetaHandler implements ActionHandler<Map, Map> {
        @Override public Map<String, Object> inputSchema()  { return Map.of("type", "object"); }
        @Override public Map<String, Object> outputSchema() { return Map.of("type", "object"); }
        @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }
        @Override public Class<Map> inputType() { return Map.class; }
        @Override public CompletionStage<Map> handle(ActionContext ctx, Map input) {
            return CompletableFuture.completedFuture(Map.of());
        }
    }
}
```

- [ ] **Step 7.3: Run — expected FAIL (class missing)**

```
./mvnw -pl data-talk-application test -Dtest=ActionRegistryTest
```

- [ ] **Step 7.4: Implement ActionRegistry**

Create `data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java`:

```java
package com.datatalk.application.registry;

import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.DataTalkAction;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers {@link DataTalkAction}-annotated Spring beans at startup and exposes
 * them by id. The registry is immutable after {@link #afterPropertiesSet()}.
 *
 * <p>Invariant: a bean must carry the annotation AND implement {@link ActionHandler}.
 * Beans failing either check throw at startup rather than being silently ignored.</p>
 */
@Component
public class ActionRegistry implements InitializingBean {

    private final ApplicationContext ctx;
    private final Map<String, ActionDescriptor> descriptorsById = new LinkedHashMap<>();
    private final Map<String, ActionHandler<?, ?>> handlersById = new LinkedHashMap<>();

    public ActionRegistry(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, Object> beans = ctx.getBeansWithAnnotation(DataTalkAction.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            DataTalkAction meta = bean.getClass().getAnnotation(DataTalkAction.class);
            if (meta == null) {
                // covers AOP proxies — fall back to annotated class
                meta = org.springframework.core.annotation.AnnotationUtils
                    .findAnnotation(bean.getClass(), DataTalkAction.class);
            }
            if (meta == null) {
                throw new IllegalStateException("Bean " + entry.getKey() + " expected @DataTalkAction");
            }
            if (!(bean instanceof ActionHandler<?, ?> handler)) {
                throw new IllegalStateException(
                    "Bean " + entry.getKey() + " annotated @DataTalkAction but does not implement ActionHandler");
            }
            ActionDescriptor desc = buildDescriptor(meta, handler);
            if (descriptorsById.put(desc.id(), desc) != null) {
                throw new IllegalStateException("Duplicate action id: " + desc.id());
            }
            handlersById.put(desc.id(), handler);
        }
    }

    private ActionDescriptor buildDescriptor(DataTalkAction meta, ActionHandler<?, ?> handler) {
        return new ActionDescriptor(
            meta.id(),
            meta.executor(),
            meta.description(),
            handler.inputSchema(),
            handler.outputSchema(),
            Arrays.asList(meta.produces()),
            List.copyOf(handler.sideEffects()),
            meta.requiresConnection(),
            meta.timeoutMs()
        );
    }

    public Collection<ActionDescriptor> all() {
        return Collections.unmodifiableCollection(descriptorsById.values());
    }

    public ActionDescriptor require(String id) {
        ActionDescriptor d = descriptorsById.get(id);
        if (d == null) throw new IllegalArgumentException("Unknown action: " + id);
        return d;
    }

    public ActionHandler<?, ?> handler(String id) {
        ActionHandler<?, ?> h = handlersById.get(id);
        if (h == null) throw new IllegalArgumentException("Unknown action: " + id);
        return h;
    }
}
```

- [ ] **Step 7.5: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=ActionRegistryTest
```

- [ ] **Step 7.6: Commit**

```
git add data-talk-application/pom.xml \
        data-talk-application/src/main/java/com/datatalk/application/registry/ActionRegistry.java \
        data-talk-application/src/test/java/com/datatalk/application/registry/ActionRegistryTest.java
git commit -m "feat(server): add ActionRegistry that scans @DataTalkAction beans"
```

---

## Task 8: OntologyRegistry

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/registry/OntologyRegistry.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/registry/OntologyRegistryTest.java`

- [ ] **Step 8.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/registry/OntologyRegistryTest.java`:

```java
package com.datatalk.application.registry;

import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypeDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {OntologyRegistry.class, OntologyRegistryTest.TestTypes.class})
class OntologyRegistryTest {

    @Autowired OntologyRegistry registry;

    @Test
    void discoversAllObjectTypeBeans() {
        assertThat(registry.all())
            .extracting(ObjectTypeDescriptor::id)
            .containsExactlyInAnyOrder("test.foo", "test.bar");
    }

    @Test
    void throwsForUnknown() {
        assertThatThrownBy(() -> registry.require("test.missing"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration
    static class TestTypes {
        @Bean ObjectType<?> fooType() { return new FooType(); }
        @Bean ObjectType<?> barType() { return new BarType(); }
    }

    record FooValue(String id, String title) {}
    record BarValue(String id) {}

    static class FooType implements ObjectType<FooValue> {
        @Override public String id() { return "test.foo"; }
        @Override public String displayName() { return "Foo"; }
        @Override public Map<String, Object> propertySchema() { return Map.of("type", "object"); }
        @Override public List<String> primaryKey() { return List.of("id"); }
        @Override public Optional<String> titleField() { return Optional.of("title"); }
        @Override public Class<FooValue> javaType() { return FooValue.class; }
    }

    static class BarType implements ObjectType<BarValue> {
        @Override public String id() { return "test.bar"; }
        @Override public String displayName() { return "Bar"; }
        @Override public Map<String, Object> propertySchema() { return Map.of("type", "object"); }
        @Override public List<String> primaryKey() { return List.of("id"); }
        @Override public Optional<String> titleField() { return Optional.empty(); }
        @Override public Class<BarValue> javaType() { return BarValue.class; }
    }
}
```

- [ ] **Step 8.2: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=OntologyRegistryTest
```

- [ ] **Step 8.3: Implement OntologyRegistry**

Create `data-talk-application/src/main/java/com/datatalk/application/registry/OntologyRegistry.java`:

```java
package com.datatalk.application.registry;

import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypeDescriptor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Discovers all {@link ObjectType} beans at startup and exposes them as
 * immutable descriptors. Used by {@code /api/ontology} and downstream
 * persistence to validate upserts against each object's property schema.
 */
@Component
public class OntologyRegistry implements InitializingBean {

    private final ApplicationContext ctx;
    private final Map<String, ObjectType<?>> typesById = new LinkedHashMap<>();
    private final Map<String, ObjectTypeDescriptor> descriptorsById = new LinkedHashMap<>();

    public OntologyRegistry(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void afterPropertiesSet() {
        Map<String, ObjectType> beans = ctx.getBeansOfType(ObjectType.class);
        for (ObjectType<?> t : beans.values()) {
            if (typesById.put(t.id(), t) != null) {
                throw new IllegalStateException("Duplicate ObjectType id: " + t.id());
            }
            descriptorsById.put(t.id(), t.toDescriptor());
        }
    }

    public Collection<ObjectTypeDescriptor> all() {
        return Collections.unmodifiableCollection(descriptorsById.values());
    }

    public ObjectTypeDescriptor require(String id) {
        ObjectTypeDescriptor d = descriptorsById.get(id);
        if (d == null) throw new IllegalArgumentException("Unknown object type: " + id);
        return d;
    }

    public ObjectType<?> type(String id) {
        ObjectType<?> t = typesById.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown object type: " + id);
        return t;
    }
}
```

- [ ] **Step 8.4: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=OntologyRegistryTest
```

- [ ] **Step 8.5: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/registry/OntologyRegistry.java \
        data-talk-application/src/test/java/com/datatalk/application/registry/OntologyRegistryTest.java
git commit -m "feat(server): add OntologyRegistry for ObjectType beans"
```

---

## Task 9: DiscoveryController

**Files:**
- Create: `data-talk-infrastructure/src/main/java/com/datatalk/infra/discovery/DiscoveryController.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/discovery/DiscoveryControllerIT.java`

The adapter module already has `spring-boot-starter-web`. The controller lives in `data-talk-infrastructure` because it's infrastructure wiring, but the integration test runs in adapter where `@SpringBootApplication` is.

- [ ] **Step 9.1: Add `spring-boot-starter-web` to `data-talk-infrastructure/pom.xml`**

Inside existing `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

- [ ] **Step 9.2: Write failing integration test**

Create `data-talk-adapter/src/test/java/com/datatalk/adapter/discovery/DiscoveryControllerIT.java`:

```java
package com.datatalk.adapter.discovery;

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
class DiscoveryControllerIT {

    @Autowired MockMvc mvc;

    @Test
    void listsActionsReturnsArray() throws Exception {
        mvc.perform(get("/api/actions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actions").isArray());
    }

    @Test
    void listsOntologyReturnsArray() throws Exception {
        mvc.perform(get("/api/ontology"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objects").isArray());
    }

    @Test
    void getUnknownActionReturns404() throws Exception {
        mvc.perform(get("/api/actions/does.not.exist"))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 9.3: Run — expected FAIL**

```
./mvnw -pl data-talk-adapter -am test -Dtest=DiscoveryControllerIT
```

- [ ] **Step 9.4: Implement DiscoveryController**

Create `data-talk-infrastructure/src/main/java/com/datatalk/infra/discovery/DiscoveryController.java`:

```java
package com.datatalk.infra.discovery;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.registry.OntologyRegistry;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.ontology.ObjectTypeDescriptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.Map;

/**
 * Serves {@code /api/actions} and {@code /api/ontology} discovery endpoints.
 * These are the contract the client reflects on at startup.
 */
@RestController
@RequestMapping("/api")
public class DiscoveryController {

    private final ActionRegistry actions;
    private final OntologyRegistry ontology;

    public DiscoveryController(ActionRegistry actions, OntologyRegistry ontology) {
        this.actions = actions;
        this.ontology = ontology;
    }

    @GetMapping("/actions")
    public Map<String, Collection<ActionDescriptor>> listActions() {
        return Map.of("actions", actions.all());
    }

    @GetMapping("/actions/{id}")
    public ActionDescriptor getAction(@PathVariable String id) {
        try {
            return actions.require(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/ontology")
    public Map<String, Collection<ObjectTypeDescriptor>> listOntology() {
        return Map.of("objects", ontology.all());
    }

    @GetMapping("/ontology/{id}")
    public ObjectTypeDescriptor getObjectType(@PathVariable String id) {
        try {
            return ontology.require(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping(value = "/actions.schema.json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> mergedSchema() {
        return ResponseEntity.ok(Map.of(
            "$schema", "https://json-schema.org/draft/2020-12/schema",
            "actions", actions.all()
        ));
    }
}
```

- [ ] **Step 9.5: Add component-scan for `com.datatalk.infra` in `DataTalkApplication`**

Open `data-talk-adapter/src/main/java/com/datatalk/adapter/DataTalkApplication.java`. It should currently be a bare `@SpringBootApplication`. Verify the class-level annotation. If `scanBasePackages` is not set, it only scans `com.datatalk.adapter.*`. Replace the annotation to scan all datatalk packages:

Change
```java
@SpringBootApplication
```
to
```java
@SpringBootApplication(scanBasePackages = "com.datatalk")
```

- [ ] **Step 9.6: Run — expected PASS**

```
./mvnw -pl data-talk-adapter -am test -Dtest=DiscoveryControllerIT
```

Expected: all 3 tests green. Empty `actions` and `objects` arrays are OK since no beans are registered yet — Task 26 will add a demo action, and Tasks 12/13 will add ObjectType beans.

- [ ] **Step 9.7: Commit**

```
git add data-talk-infrastructure/pom.xml \
        data-talk-infrastructure/src/main/java/com/datatalk/infra/discovery/DiscoveryController.java \
        data-talk-adapter/src/main/java/com/datatalk/adapter/DataTalkApplication.java \
        data-talk-adapter/src/test/java/com/datatalk/adapter/discovery/DiscoveryControllerIT.java
git commit -m "feat(server): expose /api/actions and /api/ontology discovery endpoints"
```

---

## Task 10: Flyway + V1 migration

**Files:**
- Modify: `data-talk-infrastructure/pom.xml` (Flyway + Flyway SQLite module)
- Modify: `data-talk-adapter/src/main/resources/application.yml`
- Create: `data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql`
- Create: `data-talk-infrastructure/src/main/java/com/datatalk/infra/persistence/FlywayMigrationConfig.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/FlywayMigrationIT.java`

Spec §5.6.

- [ ] **Step 10.1: Add Flyway deps to `data-talk-infrastructure/pom.xml`**

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>com.github.sgrigorev</groupId>
    <artifactId>flyway-sqlite</artifactId>
    <version>9.8.2</version>
</dependency>
```

Note on SQLite Flyway support: Flyway Community edition dropped SQLite in v10, so we stay on the Spring Boot 3.5 bundled Flyway 10.x with the external SQLite adapter above. If Maven resolution fails, fall back to Flyway 9.22.3 explicitly:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>9.22.3</version>
</dependency>
```

- [ ] **Step 10.2: Write failing integration test**

Create `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/FlywayMigrationIT.java`:

```java
package com.datatalk.adapter.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationIT {

    @Autowired
    JdbcTemplate datatalkJdbc;  // qualifier wired in FlywayMigrationConfig

    @Test
    void allCoreTablesExistAfterMigration() {
        List<String> tables = datatalkJdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
            String.class
        );
        assertThat(tables).contains(
            "connections", "sessions", "messages", "artifacts",
            "action_invocations", "events", "query_results"
        );
    }

    @Test
    void sessionsHasHasEverSentColumn() {
        Integer count = datatalkJdbc.queryForObject(
            "SELECT COUNT(*) FROM pragma_table_info('sessions') WHERE name='has_ever_sent'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void artifactsHasCompoundPrimaryKey() {
        List<Integer> pkFlags = datatalkJdbc.queryForList(
            "SELECT pk FROM pragma_table_info('artifacts') WHERE name IN ('id','version') ORDER BY name",
            Integer.class
        );
        assertThat(pkFlags).containsExactly(1, 2);
    }
}
```

- [ ] **Step 10.3: Run — expected FAIL**

```
./mvnw -pl data-talk-adapter -am test -Dtest=FlywayMigrationIT
```

- [ ] **Step 10.4: Create V1__init.sql**

Create `data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql`:

```sql
-- DataTalk initial schema. Matches spec §5.6. SQLite dialect.

CREATE TABLE connections (
  id            TEXT PRIMARY KEY,
  kind          TEXT NOT NULL,
  host          TEXT NOT NULL,
  port          INTEGER NOT NULL,
  database_name TEXT,
  username      TEXT NOT NULL,
  password_enc  BLOB NOT NULL,
  schema_digest TEXT,
  created_at    INTEGER NOT NULL
);

CREATE TABLE sessions (
  id             TEXT PRIMARY KEY,
  connection_id  TEXT REFERENCES connections(id),
  title          TEXT NOT NULL,
  has_ever_sent  INTEGER NOT NULL DEFAULT 0,
  opencode_sid   TEXT,
  created_at     INTEGER NOT NULL,
  updated_at     INTEGER NOT NULL
);

CREATE TABLE messages (
  id         TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES sessions(id),
  role       TEXT NOT NULL,
  parts_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_messages_session ON messages(session_id, created_at);

CREATE TABLE artifacts (
  id             TEXT NOT NULL,
  version        INTEGER NOT NULL,
  session_id     TEXT NOT NULL REFERENCES sessions(id),
  kind           TEXT NOT NULL CHECK(kind IN ('table','chart','erd')),
  produced_by    TEXT NOT NULL,
  payload_ref    TEXT NOT NULL,
  payload_size   INTEGER NOT NULL,
  supersedes_id  TEXT,
  supersedes_ver INTEGER,
  pinned         INTEGER NOT NULL DEFAULT 0,
  created_at     INTEGER NOT NULL,
  PRIMARY KEY(id, version)
);
CREATE INDEX idx_artifacts_session ON artifacts(session_id, created_at);

CREATE TABLE action_invocations (
  call_id     TEXT PRIMARY KEY,
  session_id  TEXT NOT NULL REFERENCES sessions(id),
  action_id   TEXT NOT NULL,
  status      TEXT NOT NULL,
  input_json  TEXT NOT NULL,
  output_json TEXT,
  error_json  TEXT,
  started_at  INTEGER NOT NULL,
  ended_at    INTEGER
);

CREATE TABLE events (
  event_id     INTEGER NOT NULL,
  session_id   TEXT NOT NULL REFERENCES sessions(id),
  event_type   TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  ts           INTEGER NOT NULL,
  PRIMARY KEY(session_id, event_id)
);
CREATE INDEX idx_events_ts ON events(ts);

CREATE TABLE query_results (
  handle       TEXT PRIMARY KEY,
  session_id   TEXT NOT NULL REFERENCES sessions(id),
  columns_json TEXT NOT NULL,
  rows_ndjson  TEXT NOT NULL,
  row_count    INTEGER NOT NULL,
  created_at   INTEGER NOT NULL,
  ttl_at       INTEGER NOT NULL
);
```

Note: the column renamed `database` → `database_name` to avoid SQLite reserved-word quoting headaches. Spec §5.6 used `database`; update the spec if the team prefers strict parity (run `sqlite3` query `SELECT … FROM connections` both ways to verify behavior before deciding).

- [ ] **Step 10.5: Implement FlywayMigrationConfig**

Create `data-talk-infrastructure/src/main/java/com/datatalk/infra/persistence/FlywayMigrationConfig.java`:

```java
package com.datatalk.infra.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Provisions a dedicated SQLite datasource for DataTalk's ontology + channel
 * storage and runs Flyway migrations against it at startup.
 *
 * <p>This is kept separate from the existing {@code DataSourcesConfig} so the
 * legacy {@code /api/query} demo flow remains undisturbed.</p>
 */
@Configuration
public class FlywayMigrationConfig {

    @Bean("datatalkDataSource")
    public DataSource datatalkDataSource(
        @Value("${datatalk.persistence.sqlite-path:./data/datatalk.db}") String path
    ) throws Exception {
        Path p = Paths.get(path);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + path);
        cfg.setMaximumPoolSize(1); // SQLite serializes writes
        cfg.setPoolName("datatalk-sqlite");
        return new HikariDataSource(cfg);
    }

    @Bean
    public Flyway datatalkFlyway(DataSource datatalkDataSource) {
        Flyway fw = Flyway.configure()
            .dataSource(datatalkDataSource)
            .locations("classpath:db/migration")
            .load();
        fw.migrate();
        return fw;
    }

    @Bean
    public JdbcTemplate datatalkJdbc(DataSource datatalkDataSource, Flyway datatalkFlyway) {
        // injecting Flyway here forces migration to run before JdbcTemplate consumers start
        return new JdbcTemplate(datatalkDataSource);
    }
}
```

- [ ] **Step 10.6: Configure the sqlite path in `application.yml`**

Open `data-talk-adapter/src/main/resources/application.yml` and append (do not remove existing properties):

```yaml
datatalk:
  persistence:
    sqlite-path: ./data/datatalk.db
```

- [ ] **Step 10.7: Run — expected PASS**

```
./mvnw -pl data-talk-adapter -am test -Dtest=FlywayMigrationIT
```

Expected: 3 tests green. If failure says "no Flyway driver for sqlite", confirm Flyway 9.x is resolved (see Step 10.1 fallback).

- [ ] **Step 10.8: Commit**

```
git add data-talk-infrastructure/pom.xml \
        data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql \
        data-talk-infrastructure/src/main/java/com/datatalk/infra/persistence/FlywayMigrationConfig.java \
        data-talk-adapter/src/main/resources/application.yml \
        data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/FlywayMigrationIT.java
git commit -m "feat(server): add Flyway-managed SQLite schema for DataTalk tables"
```

---

## Task 11: SecretVault

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/SecretVault.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/persistence/SecretVaultTest.java`

Spec §5.7. AES-GCM with a master key injected via env var or explicit setter (Tauri will push it at first RPC in prod).

- [ ] **Step 11.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/persistence/SecretVaultTest.java`:

```java
package com.datatalk.application.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretVaultTest {

    private static final byte[] MASTER = new byte[32]; // 256-bit key, all zeros for test determinism

    @Test
    void roundTripsSecret() {
        SecretVault vault = new SecretVault(MASTER);
        byte[] cipher = vault.seal("hunter2");
        String plain = vault.open(cipher);
        assertThat(plain).isEqualTo("hunter2");
    }

    @Test
    void differentCiphertextsForSamePlaintext() {
        SecretVault vault = new SecretVault(MASTER);
        byte[] c1 = vault.seal("hunter2");
        byte[] c2 = vault.seal("hunter2");
        assertThat(c1).isNotEqualTo(c2);  // fresh nonce each call
    }

    @Test
    void rejectsTamperedCiphertext() {
        SecretVault vault = new SecretVault(MASTER);
        byte[] cipher = vault.seal("hunter2");
        cipher[cipher.length - 1] ^= 1;
        assertThatThrownBy(() -> vault.open(cipher))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMasterKeyOfWrongLength() {
        assertThatThrownBy(() -> new SecretVault(new byte[16]))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 11.2: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=SecretVaultTest
```

- [ ] **Step 11.3: Implement SecretVault**

Create `data-talk-application/src/main/java/com/datatalk/application/persistence/SecretVault.java`:

```java
package com.datatalk.application.persistence;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * AES-GCM envelope over a master key. Ciphertext layout:
 * {@code [1 byte version=0x01][12 byte nonce][ciphertext+tag]}.
 *
 * <p>The master key is supplied at construction time. In production, Tauri
 * pushes it via the first admin RPC; tests may use a fixed key.</p>
 */
public class SecretVault {

    private static final byte VERSION = 0x01;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom rng = new SecureRandom();

    public SecretVault(byte[] master32) {
        if (master32 == null || master32.length != 32) {
            throw new IllegalArgumentException("master key must be 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(master32, "AES");
    }

    public byte[] seal(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            rng.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + NONCE_BYTES + ct.length)
                .put(VERSION).put(nonce).put(ct).array();
        } catch (Exception e) {
            throw new IllegalStateException("seal failed", e);
        }
    }

    public String open(byte[] envelope) {
        if (envelope == null || envelope.length < 1 + NONCE_BYTES + 16) {
            throw new IllegalStateException("invalid envelope");
        }
        if (envelope[0] != VERSION) {
            throw new IllegalStateException("unsupported envelope version");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(envelope, 1, nonce, 0, NONCE_BYTES);
            byte[] ct = new byte[envelope.length - 1 - NONCE_BYTES];
            System.arraycopy(envelope, 1 + NONCE_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("open failed (tampered or wrong key?)", e);
        }
    }
}
```

- [ ] **Step 11.4: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=SecretVaultTest
```

- [ ] **Step 11.5: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/persistence/SecretVault.java \
        data-talk-application/src/test/java/com/datatalk/application/persistence/SecretVaultTest.java
git commit -m "feat(server): add AES-GCM SecretVault for connection passwords"
```

---

## Task 12: Session + Message repositories

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRecord.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/MessageRepository.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/SessionRepositoryIT.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/MessageRepositoryIT.java`

- [ ] **Step 12.1: Write failing test for SessionRepository**

Create `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/SessionRepositoryIT.java`:

```java
package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SessionRepositoryIT {

    @Autowired SessionRepository repo;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeEach
    void clean() {
        datatalkJdbc.update("DELETE FROM sessions");
    }

    @Test
    void savesAndLoadsSession() {
        SessionRecord s = new SessionRecord("s-1", null, "Untitled", false, null, 100L, 100L);
        repo.upsert(s);
        Optional<SessionRecord> found = repo.findById("s-1");
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Untitled");
        assertThat(found.get().hasEverSent()).isFalse();
    }

    @Test
    void markHasEverSentFlipsFlag() {
        repo.upsert(new SessionRecord("s-2", null, "T", false, null, 100L, 100L));
        repo.markHasEverSent("s-2", 200L);
        assertThat(repo.findById("s-2").orElseThrow().hasEverSent()).isTrue();
    }

    @Test
    void findByIdReturnsEmptyForUnknown() {
        assertThat(repo.findById("nope")).isEmpty();
    }
}
```

- [ ] **Step 12.2: Write failing test for MessageRepository**

Create `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/MessageRepositoryIT.java`:

```java
package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.MessageRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MessageRepositoryIT {

    @Autowired MessageRepository msgRepo;
    @Autowired SessionRepository sessRepo;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeEach
    void clean() {
        datatalkJdbc.update("DELETE FROM messages");
        datatalkJdbc.update("DELETE FROM sessions");
        sessRepo.upsert(new SessionRecord("s-1", null, "T", false, null, 100L, 100L));
    }

    @Test
    void savesMessageWithPartsAndListsByCreatedAt() {
        TextPart p1 = new TextPart("p-1", "s-1", "m-1", "hello", null, null, null, Map.of());
        Message m = new Message("m-1", "s-1", Message.Role.USER, List.of(p1), 101L);
        msgRepo.save(m);

        List<Message> all = msgRepo.findBySession("s-1");
        assertThat(all).hasSize(1);
        assertThat(all.get(0).parts()).hasSize(1);
        assertThat(((TextPart) all.get(0).parts().get(0)).text()).isEqualTo("hello");
    }
}
```

- [ ] **Step 12.3: Run — expected FAIL**

```
./mvnw -pl data-talk-adapter -am test -Dtest=SessionRepositoryIT,MessageRepositoryIT
```

- [ ] **Step 12.4: Implement SessionRecord + SessionRepository**

Create `data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRecord.java`:

```java
package com.datatalk.application.persistence;

public record SessionRecord(
    String id,
    String connectionId,
    String title,
    boolean hasEverSent,
    String openCodeSid,
    long createdAt,
    long updatedAt
) {}
```

Create `data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java`:

```java
package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SessionRepository {

    private final JdbcTemplate jdbc;

    public SessionRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<SessionRecord> MAPPER = (rs, i) -> new SessionRecord(
        rs.getString("id"),
        rs.getString("connection_id"),
        rs.getString("title"),
        rs.getInt("has_ever_sent") == 1,
        rs.getString("opencode_sid"),
        rs.getLong("created_at"),
        rs.getLong("updated_at")
    );

    public void upsert(SessionRecord s) {
        jdbc.update("""
            INSERT INTO sessions(id, connection_id, title, has_ever_sent, opencode_sid, created_at, updated_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              connection_id = excluded.connection_id,
              title         = excluded.title,
              has_ever_sent = excluded.has_ever_sent,
              opencode_sid  = excluded.opencode_sid,
              updated_at    = excluded.updated_at
            """,
            s.id(), s.connectionId(), s.title(), s.hasEverSent() ? 1 : 0,
            s.openCodeSid(), s.createdAt(), s.updatedAt()
        );
    }

    public Optional<SessionRecord> findById(String id) {
        var list = jdbc.query("SELECT * FROM sessions WHERE id = ?", MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void markHasEverSent(String id, long now) {
        jdbc.update("UPDATE sessions SET has_ever_sent = 1, updated_at = ? WHERE id = ?", now, id);
    }
}
```

- [ ] **Step 12.5: Implement MessageRepository**

Create `data-talk-application/src/main/java/com/datatalk/application/persistence/MessageRepository.java`:

```java
package com.datatalk.application.persistence;

import com.datatalk.domain.part.Message;
import com.datatalk.domain.part.Part;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public MessageRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc, ObjectMapper om) {
        this.jdbc = jdbc;
        this.om = om;
    }

    public void save(Message m) {
        String partsJson;
        try {
            partsJson = om.writeValueAsString(m.parts());
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize parts", e);
        }
        jdbc.update("""
            INSERT INTO messages(id, session_id, role, parts_json, created_at)
            VALUES(?, ?, ?, ?, ?)
            """,
            m.id(), m.sessionId(), m.role().name(), partsJson, m.createdAt()
        );
    }

    public List<Message> findBySession(String sessionId) {
        return jdbc.query("""
            SELECT id, session_id, role, parts_json, created_at
            FROM messages
            WHERE session_id = ?
            ORDER BY created_at ASC, id ASC
            """,
            (rs, i) -> {
                try {
                    List<Part> parts = om.readValue(
                        rs.getString("parts_json"),
                        new TypeReference<List<Part>>() {}
                    );
                    return new Message(
                        rs.getString("id"),
                        rs.getString("session_id"),
                        Message.Role.valueOf(rs.getString("role")),
                        parts,
                        rs.getLong("created_at")
                    );
                } catch (Exception e) {
                    throw new IllegalStateException("cannot deserialize parts", e);
                }
            },
            sessionId
        );
    }
}
```

- [ ] **Step 12.6: Run — expected PASS**

```
./mvnw -pl data-talk-adapter -am test -Dtest=SessionRepositoryIT,MessageRepositoryIT
```

- [ ] **Step 12.7: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRecord.java \
        data-talk-application/src/main/java/com/datatalk/application/persistence/SessionRepository.java \
        data-talk-application/src/main/java/com/datatalk/application/persistence/MessageRepository.java \
        data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/SessionRepositoryIT.java \
        data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/MessageRepositoryIT.java
git commit -m "feat(server): add Session and Message JDBC repositories"
```

---

## Task 13: Artifact / Event / ActionInvocation / QueryResult repositories

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRecord.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRepository.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/EventRepository.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/ActionInvocationRepository.java`
- Create: `data-talk-application/src/main/java/com/datatalk/application/persistence/QueryResultRepository.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/ArtifactRepositoryIT.java`
- Test: `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/EventRepositoryIT.java`

- [ ] **Step 13.1: Write failing test for ArtifactRepository**

Create `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/ArtifactRepositoryIT.java`:

```java
package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ArtifactRepositoryIT {

    @Autowired ArtifactRepository repo;
    @Autowired SessionRepository sessRepo;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeEach
    void clean() {
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM sessions");
        sessRepo.upsert(new SessionRecord("s-1", null, "T", true, null, 100L, 100L));
    }

    @Test
    void insertAndList() {
        repo.insert(new ArtifactRecord("art-1", 1, "s-1", "table", "call-1",
            "INLINE:{\"columns\":[\"a\"]}", 20, null, null, false, 101L));
        List<ArtifactRecord> all = repo.findBySession("s-1");
        assertThat(all).hasSize(1);
        assertThat(all.get(0).kind()).isEqualTo("table");
    }

    @Test
    void findLatestByIdReturnsHighestVersion() {
        repo.insert(new ArtifactRecord("art-1", 1, "s-1", "chart", "call-1", "INLINE:{}", 2, null, null, false, 100L));
        repo.insert(new ArtifactRecord("art-1", 2, "s-1", "chart", "call-2", "INLINE:{}", 2, "art-1", 1, false, 200L));
        assertThat(repo.findLatestById("art-1").orElseThrow().version()).isEqualTo(2);
    }
}
```

- [ ] **Step 13.2: Write failing test for EventRepository**

Create `data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/EventRepositoryIT.java`:

```java
package com.datatalk.adapter.persistence;

import com.datatalk.application.persistence.EventRepository;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventRepositoryIT {

    @Autowired EventRepository repo;
    @Autowired SessionRepository sessRepo;
    @Autowired JdbcTemplate datatalkJdbc;

    @BeforeEach
    void clean() {
        datatalkJdbc.update("DELETE FROM events");
        datatalkJdbc.update("DELETE FROM sessions");
        sessRepo.upsert(new SessionRecord("s-1", null, "T", true, null, 100L, 100L));
    }

    @Test
    void appendsAndRestoresMaxEventId() {
        repo.append("s-1", 1, "connected", "{\"sessionId\":\"s-1\"}", 100L);
        repo.append("s-1", 2, "heartbeat", "{\"ts\":101}", 101L);
        assertThat(repo.maxEventId("s-1")).isEqualTo(2);
    }

    @Test
    void maxEventIdReturnsZeroWhenEmpty() {
        assertThat(repo.maxEventId("s-1")).isZero();
    }
}
```

- [ ] **Step 13.3: Run — expected FAIL**

```
./mvnw -pl data-talk-adapter -am test -Dtest=ArtifactRepositoryIT,EventRepositoryIT
```

- [ ] **Step 13.4: Implement ArtifactRecord + ArtifactRepository**

Create `data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRecord.java`:

```java
package com.datatalk.application.persistence;

public record ArtifactRecord(
    String id,
    int version,
    String sessionId,
    String kind,           // "table" | "chart" | "erd"
    String producedBy,     // callId
    String payloadRef,     // "INLINE:<json>" | "HANDLE:<queryHandle>"
    int payloadSize,
    String supersedesId,
    Integer supersedesVersion,
    boolean pinned,
    long createdAt
) {}
```

Create `data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRepository.java`:

```java
package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ArtifactRepository {

    private final JdbcTemplate jdbc;

    public ArtifactRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ArtifactRecord> MAPPER = (rs, i) -> new ArtifactRecord(
        rs.getString("id"),
        rs.getInt("version"),
        rs.getString("session_id"),
        rs.getString("kind"),
        rs.getString("produced_by"),
        rs.getString("payload_ref"),
        rs.getInt("payload_size"),
        rs.getString("supersedes_id"),
        (Integer) rs.getObject("supersedes_ver"),
        rs.getInt("pinned") == 1,
        rs.getLong("created_at")
    );

    public void insert(ArtifactRecord a) {
        jdbc.update("""
            INSERT INTO artifacts(id, version, session_id, kind, produced_by, payload_ref, payload_size,
              supersedes_id, supersedes_ver, pinned, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            a.id(), a.version(), a.sessionId(), a.kind(), a.producedBy(),
            a.payloadRef(), a.payloadSize(), a.supersedesId(), a.supersedesVersion(),
            a.pinned() ? 1 : 0, a.createdAt()
        );
    }

    public void updatePinned(String id, int version, boolean pinned) {
        jdbc.update("UPDATE artifacts SET pinned = ? WHERE id = ? AND version = ?",
            pinned ? 1 : 0, id, version);
    }

    public Optional<ArtifactRecord> findLatestById(String id) {
        var list = jdbc.query(
            "SELECT * FROM artifacts WHERE id = ? ORDER BY version DESC LIMIT 1", MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<ArtifactRecord> findBySession(String sessionId) {
        return jdbc.query(
            "SELECT * FROM artifacts WHERE session_id = ? ORDER BY created_at ASC, version ASC",
            MAPPER, sessionId);
    }
}
```

- [ ] **Step 13.5: Implement EventRepository**

Create `data-talk-application/src/main/java/com/datatalk/application/persistence/EventRepository.java`:

```java
package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

    private final JdbcTemplate jdbc;

    public EventRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(String sessionId, long eventId, String eventType, String payloadJson, long ts) {
        jdbc.update("""
            INSERT INTO events(event_id, session_id, event_type, payload_json, ts)
            VALUES(?, ?, ?, ?, ?)
            """,
            eventId, sessionId, eventType, payloadJson, ts
        );
    }

    public long maxEventId(String sessionId) {
        Long max = jdbc.queryForObject(
            "SELECT COALESCE(MAX(event_id), 0) FROM events WHERE session_id = ?",
            Long.class, sessionId);
        return max == null ? 0L : max;
    }
}
```

- [ ] **Step 13.6: Implement ActionInvocationRepository**

Create `data-talk-application/src/main/java/com/datatalk/application/persistence/ActionInvocationRepository.java`:

```java
package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActionInvocationRepository {

    private final JdbcTemplate jdbc;

    public ActionInvocationRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void start(String callId, String sessionId, String actionId, String inputJson, long now) {
        jdbc.update("""
            INSERT INTO action_invocations(call_id, session_id, action_id, status, input_json, started_at)
            VALUES(?, ?, ?, 'running', ?, ?)
            """, callId, sessionId, actionId, inputJson, now);
    }

    public void complete(String callId, String outputJson, long now) {
        jdbc.update("""
            UPDATE action_invocations
            SET status = 'completed', output_json = ?, ended_at = ?
            WHERE call_id = ?
            """, outputJson, now, callId);
    }

    public void fail(String callId, String errorJson, long now) {
        jdbc.update("""
            UPDATE action_invocations
            SET status = 'error', error_json = ?, ended_at = ?
            WHERE call_id = ?
            """, errorJson, now, callId);
    }

    public void cancel(String callId, long now) {
        jdbc.update("""
            UPDATE action_invocations SET status = 'cancelled', ended_at = ? WHERE call_id = ?
            """, now, callId);
    }
}
```

- [ ] **Step 13.7: Implement QueryResultRepository**

Create `data-talk-application/src/main/java/com/datatalk/application/persistence/QueryResultRepository.java`:

```java
package com.datatalk.application.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QueryResultRepository {

    private final JdbcTemplate jdbc;

    public QueryResultRepository(@Qualifier("datatalkJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String handle, String sessionId, String columnsJson,
                       String rowsNdjson, int rowCount, long createdAt, long ttlAt) {
        jdbc.update("""
            INSERT INTO query_results(handle, session_id, columns_json, rows_ndjson, row_count, created_at, ttl_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """, handle, sessionId, columnsJson, rowsNdjson, rowCount, createdAt, ttlAt);
    }
}
```

- [ ] **Step 13.8: Run — expected PASS**

```
./mvnw -pl data-talk-adapter -am test -Dtest=ArtifactRepositoryIT,EventRepositoryIT
```

- [ ] **Step 13.9: Commit**

```
git add data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRecord.java \
        data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRepository.java \
        data-talk-application/src/main/java/com/datatalk/application/persistence/EventRepository.java \
        data-talk-application/src/main/java/com/datatalk/application/persistence/ActionInvocationRepository.java \
        data-talk-application/src/main/java/com/datatalk/application/persistence/QueryResultRepository.java \
        data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/ArtifactRepositoryIT.java \
        data-talk-adapter/src/test/java/com/datatalk/adapter/persistence/EventRepositoryIT.java
git commit -m "feat(server): add Artifact/Event/ActionInvocation/QueryResult repositories"
```

---

## Task 14: PendingCallRegistry

**Files:**
- Create: `data-talk-application/src/main/java/com/datatalk/application/session/PendingCallRegistry.java`
- Test: `data-talk-application/src/test/java/com/datatalk/application/session/PendingCallRegistryTest.java`

Spec §3.5 / §5.4.

- [ ] **Step 14.1: Write failing test**

Create `data-talk-application/src/test/java/com/datatalk/application/session/PendingCallRegistryTest.java`:

```java
package com.datatalk.application.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class PendingCallRegistryTest {

    private final PendingCallRegistry registry = new PendingCallRegistry();

    @Test
    void completesFutureOnResult() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        registry.register("call-1", future, 5_000);
        registry.complete("call-1", "done");
        assertThat(future.join()).isEqualTo("done");
    }

    @Test
    void timesOutWhenWatchdogFires() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        registry.register("call-2", future, 50);
        await().atMost(Duration.ofSeconds(1)).until(future::isDone);
        assertThatThrownBy(future::join)
            .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void cancelPreventsLaterCompletion() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        registry.register("call-3", future, 5_000);
        registry.cancel("call-3", "user_abort");
        assertThat(future.isCompletedExceptionally()).isTrue();
    }

    @Test
    void lookupUnknownCallIdReturnsFalse() {
        assertThat(registry.complete("nope", "x")).isFalse();
    }
}
```

- [ ] **Step 14.2: Add awaitility to `data-talk-application/pom.xml`**

```xml
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 14.3: Run — expected FAIL**

```
./mvnw -pl data-talk-application test -Dtest=PendingCallRegistryTest
```

- [ ] **Step 14.4: Implement PendingCallRegistry**

Create `data-talk-application/src/main/java/com/datatalk/application/session/PendingCallRegistry.java`:

```java
package com.datatalk.application.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tracks pending {@code CompletableFuture}s for CLIENT-executor actions.
 * The {@code ActionDispatcher} registers a future per {@code callId}; the
 * ToolCallBridge blocks on it until the client POSTs {@code action_result}.
 *
 * <p>Each registration installs a watchdog that fails the future with a
 * {@link TimeoutException} if no result arrives before {@code timeoutMs}.</p>
 */
@Component
public class PendingCallRegistry {

    private final Map<String, Entry> byCallId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdog =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pending-call-watchdog");
            t.setDaemon(true);
            return t;
        });

    public void register(String callId, CompletableFuture<Object> future, int timeoutMs) {
        ScheduledFuture<?> timer = watchdog.schedule(() -> {
            Entry e = byCallId.remove(callId);
            if (e != null) {
                e.future.completeExceptionally(
                    new TimeoutException("action.timeout after " + timeoutMs + "ms"));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        byCallId.put(callId, new Entry(future, timer));
    }

    public boolean complete(String callId, Object output) {
        Entry e = byCallId.remove(callId);
        if (e == null) return false;
        e.timer.cancel(false);
        e.future.complete(output);
        return true;
    }

    public boolean fail(String callId, Throwable error) {
        Entry e = byCallId.remove(callId);
        if (e == null) return false;
        e.timer.cancel(false);
        e.future.completeExceptionally(error);
        return true;
    }

    public boolean cancel(String callId, String reason) {
        Entry e = byCallId.remove(callId);
        if (e == null) return false;
        e.timer.cancel(false);
        e.future.completeExceptionally(new CancelledException(reason));
        return true;
    }

    public boolean hasPending(String callId) {
        return byCallId.containsKey(callId);
    }

    private record Entry(CompletableFuture<Object> future, ScheduledFuture<?> timer) {}

    public static class CancelledException extends RuntimeException {
        public CancelledException(String reason) { super(reason); }
    }
}
```

- [ ] **Step 14.5: Run — expected PASS**

```
./mvnw -pl data-talk-application test -Dtest=PendingCallRegistryTest
```

- [ ] **Step 14.6: Commit**

```
git add data-talk-application/pom.xml \
        data-talk-application/src/main/java/com/datatalk/application/session/PendingCallRegistry.java \
        data-talk-application/src/test/java/com/datatalk/application/session/PendingCallRegistryTest.java
git commit -m "feat(server): add PendingCallRegistry with watchdog timeouts"
```

---

## Continuation

Tasks 15–26 continue in `docs/superpowers/plans/2026-04-16-manus-a-backend-platform-part3.md`:

- Task 15: SessionBus (ring buffer + flusher)
- Task 16: SessionBusRegistry
- Task 17: Subscriber + SSE emit
- Task 18: JsonRpcCodec + Rpc types
- Task 19: ChannelService
- Task 20: ChannelController + integration tests
- Task 21: OpenCodeEventTranslator
- Task 22: OpenCodeHttpClient + WireMock tests
- Task 23: OpenCodeGateway lifecycle
- Task 24: ToolCallBridge
- Task 25: ActionDispatcher
- Task 26: DemoEchoAction + E2E smoke test

Execute tasks in numeric order across all three part files.
