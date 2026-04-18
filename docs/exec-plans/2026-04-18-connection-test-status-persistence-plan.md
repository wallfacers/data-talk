# Connection Test Status Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Persist data source connection test results in the database so they survive app restarts.

**Architecture:** Add `last_test_status` and `last_test_at` columns to the `connections` table. Update `ConnectionService.testConnection()` to persist results after each test. `list()` returns persisted status. Frontend displays it on initial load.

**Tech Stack:** Flyway (SQLite migration), Spring Boot 3.5 / Java 21, React 19 + TypeScript

---

### Task 1: Database Migration — V6

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V6__connection_test_status.sql`

- [x] **Step 1: Create migration file**

```sql
ALTER TABLE connections ADD COLUMN last_test_status TEXT;
ALTER TABLE connections ADD COLUMN last_test_at INTEGER;
```

- [x] **Step 2: Verify migration compiles**

Run: `cd server && mvn compile -q`
Expected: zero errors

---

### Task 2: ConnectionRecord + ConnectionDto — Add Test Status Fields

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionDto.java`
- Modify: `client/src/types/generated/api.ts`

- [x] **Step 1: Update ConnectionRecord**

Current file:
```java
public record ConnectionRecord(
    String id, String kind, String host, int port,
    String databaseName,
    String username, byte[] passwordEnc,
    String schemaDigest, long createdAt, int connectTimeout
) {}
```

Change to (add `lastTestStatus` and `lastTestAt` at end):
```java
public record ConnectionRecord(
    String id, String kind, String host, int port,
    String databaseName,
    String username, byte[] passwordEnc,
    String schemaDigest, long createdAt, int connectTimeout,
    String lastTestStatus, Long lastTestAt
) {}
```

- [x] **Step 2: Update ConnectionDto**

Current file:
```java
public record ConnectionDto(
    String id,
    String kind,
    String host,
    int port,
    String databaseName,  // nullable
    String username,
    long createdAt,
    int connectTimeout
) {}
```

Change to (add `lastTestStatus` and `lastTestAt` at end):
```java
public record ConnectionDto(
    String id,
    String kind,
    String host,
    int port,
    String databaseName,  // nullable
    String username,
    long createdAt,
    int connectTimeout,
    String lastTestStatus,
    Long lastTestAt
) {}
```

- [x] **Step 3: Update frontend types**

In `client/src/types/generated/api.ts`, update `ConnectionDto`:

```typescript
export interface ConnectionDto {
  id: string
  kind: string
  host: string
  port: number
  databaseName: string | null
  username: string
  createdAt: number
  connectTimeout: number
  lastTestStatus: string | null
  lastTestAt: number | null
}
```

- [x] **Step 4: Verify backend compiles**

Run: `cd server && mvn compile -q`
Expected: zero errors

- [x] **Step 5: Verify frontend types**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors (or existing errors only, no new ones from these type additions)

---

### Task 3: ConnectionRepository — Persist Test Status

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java`

- [x] **Step 1: Update RowMapper to read new columns**

Current MAPPER (line 17-22):
```java
private static final RowMapper<ConnectionRecord> MAPPER = (rs, i) -> new ConnectionRecord(
    rs.getString("id"), rs.getString("kind"), rs.getString("host"),
    rs.getInt("port"), rs.getString("database_name"), rs.getString("username"),
    rs.getBytes("password_enc"), rs.getString("schema_digest"), rs.getLong("created_at"),
    rs.getInt("connect_timeout")
);
```

Change to:
```java
private static final RowMapper<ConnectionRecord> MAPPER = (rs, i) -> new ConnectionRecord(
    rs.getString("id"), rs.getString("kind"), rs.getString("host"),
    rs.getInt("port"), rs.getString("database_name"), rs.getString("username"),
    rs.getBytes("password_enc"), rs.getString("schema_digest"), rs.getLong("created_at"),
    rs.getInt("connect_timeout"),
    rs.getString("last_test_status"),
    rs.getObject("last_test_at", Long.class)
);
```

- [x] **Step 2: Add updateTestStatus method**

Add this method to the repository:

```java
public void updateTestStatus(String id, String status, long timestamp) {
    jdbc.update("""
        UPDATE connections SET last_test_status = ?, last_test_at = ? WHERE id = ?
        """, status, timestamp, id);
}
```

- [x] **Step 3: Verify backend compiles**

Run: `cd server && mvn compile -q`
Expected: zero errors

---

### Task 4: ConnectionService — Persist Test Result After Test

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`

- [x] **Step 1: Update testConnection to persist result**

Current `testConnection` method (lines 67-86). Change the method to persist the result after computing it:

```java
public TestResult testConnection(String id) {
    var c = repo.findById(id)
        .orElseThrow(() -> new java.util.NoSuchElementException("unknown connection: " + id));
    String password = vault.open(c.passwordEnc());
    String url = JdbcUrlBuilder.build(c);
    String kind = c.kind();
    int timeoutSeconds = c.connectTimeout() / 1000;
    if (kind.equals(ConnectionKind.MYSQL) || kind.equals(ConnectionKind.POSTGRESQL)) {
        url += (url.contains("?") ? "&" : "?") + "connectTimeout=" + timeoutSeconds + "&socketTimeout=" + timeoutSeconds;
    }
    long started = clock.millis();
    try (var conn = java.sql.DriverManager.getConnection(url, c.username(), password)) {
        boolean ok = conn.isValid(timeoutSeconds);
        long ms = clock.millis() - started;
        String status = ok ? "ok" : "fail";
        repo.updateTestStatus(id, status, clock.millis());
        return new TestResult(ok, ms, ok ? null : "connection reported invalid");
    } catch (Throwable t) {
        long ms = clock.millis() - started;
        repo.updateTestStatus(id, "fail", clock.millis());
        return new TestResult(false, ms, t.getClass().getSimpleName() + ": " + t.getMessage());
    }
}
```

- [x] **Step 2: Update list() to include test status in DTO**

Current `list()` method (lines 36-41). Change to pass through `lastTestStatus` and `lastTestAt`:

```java
public List<ConnectionDto> list() {
    return repo.findAll().stream()
        .map(c -> new ConnectionDto(c.id(), c.kind(), c.host(), c.port(),
            c.databaseName(), c.username(), c.createdAt(), c.connectTimeout(),
            c.lastTestStatus(), c.lastTestAt()))
        .toList();
}
```

- [x] **Step 3: Verify backend compiles**

Run: `cd server && mvn compile -q`
Expected: zero errors

---

### Task 5: ConnectionServiceTest — Test Status Persistence

**Files:**
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java`

- [x] **Step 1: Update existing tests for new ConnectionRecord fields**

Current test uses `ConnectionRecord` with 10 args (line 21-23 and 36-37). Update both records to include the two new nullable fields (`null, null`):

```java
// testConnection_succeeds_against_h2_in_memory — line 22:
new ConnectionRecord("c1", "h2", "localhost", 9999,
    "mem:it;DB_CLOSE_DELAY=-1", "sa", new byte[]{}, null, 0, 3000, null, null)

// testConnection_fails_fast_on_bad_port — line 37:
new ConnectionRecord("c1", "mysql", "127.0.0.1", 1, "x", "u", new byte[]{}, null, 0, 3000, null, null)
```

- [x] **Step 2: Add test that verifies status is persisted on success**

```java
@Test
void testConnection_persists_ok_status_on_success() {
    var repo = mock(ConnectionRepository.class);
    var vault = mock(SecretVault.class);
    var clock = Clock.systemUTC();
    var svc = new ConnectionService(repo, vault, clock);

    when(repo.findById("c1")).thenReturn(Optional.of(
        new ConnectionRecord("c1", "h2", "localhost", 9999,
            "mem:it;DB_CLOSE_DELAY=-1", "sa", new byte[]{}, null, 0, 3000, null, null)));
    when(vault.open(any())).thenReturn("");

    var r = svc.testConnection("c1");

    assertThat(r.ok()).isTrue();
    verify(repo).updateTestStatus(eq("c1"), eq("ok"), anyLong());
}
```

- [x] **Step 3: Add test that verifies status is persisted on failure**

```java
@Test
void testConnection_persists_fail_status_on_failure() {
    var repo = mock(ConnectionRepository.class);
    var vault = mock(SecretVault.class);
    var clock = Clock.systemUTC();
    var svc = new ConnectionService(repo, vault, clock);

    when(repo.findById("c1")).thenReturn(Optional.of(
        new ConnectionRecord("c1", "mysql", "127.0.0.1", 1, "x", "u", new byte[]{}, null, 0, 3000, null, null)));
    when(vault.open(any())).thenReturn("p");

    var r = svc.testConnection("c1");

    assertThat(r.ok()).isFalse();
    verify(repo).updateTestStatus(eq("c1"), eq("fail"), anyLong());
}
```

- [x] **Step 4: Run tests**

Run: `cd server && mvn test -pl data-talk-application -Dtest=ConnectionServiceTest -q`
Expected: all 4 tests pass

---

### Task 6: Frontend — Display Persisted Test Status

**Files:**
- Modify: `client/src/features/settings/data-sources/data-sources-page.tsx`

- [x] **Step 1: Initialize testResult state from connection list data**

Current initialization (line 16):
```typescript
const [testResult, setTestResult] = useState<Record<string, 'ok' | 'fail' | 'loading'>>({})
```

Add a `useEffect` to seed state from persisted data. Add `useEffect` import from React. Add this after the useState:

```typescript
useEffect(() => {
  const seeded: Record<string, 'ok' | 'fail' | 'loading'> = {}
  for (const c of connections) {
    if (c.lastTestStatus === 'ok' || c.lastTestStatus === 'fail') {
      seeded[c.id] = c.lastTestStatus
    }
  }
  setTestResult(seeded)
}, [connections])
```

- [x] **Step 2: Show persisted status in the table**

The button rendering (lines 57-62) already handles `'ok'` and `'fail'` states. No changes needed — the seeded state will make persisted results visible immediately on load.

- [x] **Step 3: Verify frontend compiles**

Run: `cd client && npx tsc --noEmit`
Expected: zero new errors

---

### Task 7: Full Integration — Verify End-to-End

**Files:**
- No code changes

- [x] **Step 1: Full backend compile**

Run: `cd server && mvn clean verify -q`
Expected: all tests pass, zero compilation errors

- [x] **Step 2: Frontend type check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors

---

### Task 8: Commit

- [x] **Step 1: Commit all changes**

```bash
git add server/data-talk-infrastructure/src/main/resources/db/migration/V6__connection_test_status.sql
git add server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java
git add server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionDto.java
git add server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java
git add server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java
git add client/src/types/generated/api.ts
git add client/src/features/settings/data-sources/data-sources-page.tsx
git commit -m "feat: persist connection test status in DB

Add last_test_status/last_test_at columns to connections table.
testConnection() now persists ok/fail + timestamp after each test.
list() returns persisted status, frontend displays on mount.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```
