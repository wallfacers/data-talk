# 数据源名称字段实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为数据源添加唯一的 name 字段，修复表格样式问题。

**Architecture:** 后端 Flyway 迁移 + DTO/Record 字段扩展 + Repository SQL 更新 + Controller 唯一性校验；前端表格新增名称列、样式统一。

**Tech Stack:** Spring Boot 3.5 + Java 21, Flyway, JdbcTemplate, React 19, TanStack Query, Tailwind

---

## 文件结构

| 层 | 文件 | 变更 |
|---|---|---|
| DB | `V7__connection_name.sql` | 新建迁移 |
| DB | `schema.sql` (test) | 添加 name 列 |
| Domain | `ConnectionRecord.java` | 添加 name 字段 |
| DTO | `ConnectionDto.java` | 添加 name 字段 |
| DTO | `ConnectionCreateRequest.java` | 添加 name 字段 |
| DTO | `ConnectionUpdateRequest.java` | 添加 name 字段 |
| App | `ConnectionRepository.java` | SQL 添加 name |
| App | `ConnectionService.java` | 处理 name 参数 |
| Adapter | `ConnectionController.java` | 唯一性校验响应 |
| Test | `ConnectionControllerIT.java` | 更新 JSON + 唯一性测试 |
| Test | `ConnectionCrudIT.java` | 更新 JSON |
| Test | `ConnectionServiceTest.java` | 更新 mock |
| Frontend | `api.ts` | gen:api 自动更新 |
| Frontend | `ConnectionFormPanel.tsx` | 名称输入框 |
| Frontend | `DataSourcesPage.tsx` | 名称列 + 样式修复 |
| Frontend | `data-sources-page.test.tsx` | 更新 mock |

---

## Task 1: Flyway 迁移 + 测试 Schema

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V7__connection_name.sql`
- Modify: `server/data-talk-adapter/src/test/resources/schema.sql:12-16`

- [ ] **Step 1: 编写 Flyway 迁移脚本**

```sql
ALTER TABLE connections ADD COLUMN name TEXT NOT NULL DEFAULT '';

-- 为现有记录生成默认名称（使用 id 前8位）
UPDATE connections SET name = '数据源-' || substr(id, 1, 8) WHERE name = '';

-- 创建唯一索引确保名称不重复
CREATE UNIQUE INDEX idx_connections_name ON connections(name);
```

- [ ] **Step 2: 更新测试 schema.sql**

在 `connections` 表定义中添加 `name TEXT NOT NULL DEFAULT ''`：

```sql
CREATE TABLE connections (
  id TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', kind TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL,
  database_name TEXT, username TEXT NOT NULL, password_enc BLOB NOT NULL,
  schema_digest TEXT, created_at INTEGER NOT NULL, connect_timeout INTEGER NOT NULL DEFAULT 3000
);
```

- [ ] **Step 3: 验证编译**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add server/data-talk-infrastructure/src/main/resources/db/migration/V7__connection_name.sql \
        server/data-talk-adapter/src/test/resources/schema.sql
git commit -m "feat(db): add unique name column to connections table"
```

---

## Task 2: ConnectionRecord + ConnectionDto

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionDto.java`

- [ ] **Step 1: 更新 ConnectionRecord**

将 name 字段插入到 id 之后（第2位）：

```java
package com.datatalk.application.persistence;

public record ConnectionRecord(
    String id, String name, String kind, String host, int port,
    String databaseName,  // nullable - null for server-level connection
    String username, byte[] passwordEnc,
    String schemaDigest, long createdAt, int connectTimeout,
    String lastTestStatus, Long lastTestAt
) {}
```

- [ ] **Step 2: 更新 ConnectionDto**

将 name 字段插入到 id 之后（第2位）：

```java
package com.datatalk.dto;

public record ConnectionDto(
    String id,
    String name,
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

- [ ] **Step 3: 验证编译**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS（其他文件尚未更新，会有编译错误，继续后续任务修复）

- [ ] **Step 4: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java \
        server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionDto.java
git commit -m "feat(dto): add name field to ConnectionRecord and ConnectionDto"
```

---

## Task 3: ConnectionCreateRequest + ConnectionUpdateRequest

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionCreateRequest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionUpdateRequest.java`

- [ ] **Step 1: 更新 ConnectionCreateRequest**

添加 name 字段（必填，放在第一位）：

```java
package com.datatalk.dto;

public record ConnectionCreateRequest(
    String name,
    String kind,
    String host,
    int port,
    String databaseName,  // optional - null for server-level connection
    String username,
    String password,
    Integer connectTimeout  // optional, defaults to 3000ms
) {}
```

- [ ] **Step 2: 更新 ConnectionUpdateRequest**

添加 name 字段：

```java
package com.datatalk.dto;

public record ConnectionUpdateRequest(
    String name,
    String kind,
    String host,
    int port,
    String databaseName,  // optional - null for server-level connection
    String username,
    String password,
    Integer connectTimeout  // optional, defaults to 3000ms
) {}
```

- [ ] **Step 3: 验证编译**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionCreateRequest.java \
        server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionUpdateRequest.java
git commit -m "feat(dto): add name field to ConnectionCreateRequest and ConnectionUpdateRequest"
```

---

## Task 4: ConnectionRepository SQL 更新

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java`

- [ ] **Step 1: 更新 RowMapper**

在 MAPPER 中添加 name 字段映射（第2位）：

```java
private static final RowMapper<ConnectionRecord> MAPPER = (rs, i) -> new ConnectionRecord(
    rs.getString("id"), rs.getString("name"), rs.getString("kind"), rs.getString("host"),
    rs.getInt("port"), rs.getString("database_name"), rs.getString("username"),
    rs.getBytes("password_enc"), rs.getString("schema_digest"), rs.getLong("created_at"),
    rs.getInt("connect_timeout"),
    rs.getString("last_test_status"),
    rs.getObject("last_test_at") instanceof Number n ? n.longValue() : null
);
```

- [ ] **Step 2: 更新 insert SQL**

```java
public void insert(ConnectionRecord c) {
    jdbc.update("""
        INSERT INTO connections(id, name, kind, host, port, database_name, username, password_enc, schema_digest, created_at, connect_timeout)
        VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, c.id(), c.name(), c.kind(), c.host(), c.port(), c.databaseName(), c.username(),
        c.passwordEnc(), c.schemaDigest(), c.createdAt(), c.connectTimeout());
}
```

- [ ] **Step 3: 更新 update SQL**

```java
public void update(ConnectionRecord c) {
    int n = jdbc.update("""
        UPDATE connections
           SET name = ?, kind = ?, host = ?, port = ?, database_name = ?, username = ?,
               password_enc = ?, schema_digest = ?, connect_timeout = ?
         WHERE id = ?
        """, c.name(), c.kind(), c.host(), c.port(), c.databaseName(), c.username(),
        c.passwordEnc(), c.schemaDigest(), c.connectTimeout(), c.id());
    if (n == 0) throw new java.util.NoSuchElementException("unknown connection: " + c.id());
}
```

- [ ] **Step 4: 验证编译**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java
git commit -m "feat(repo): add name column to ConnectionRepository SQL"
```

---

## Task 5: ConnectionService 处理 name

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`

- [ ] **Step 1: 更新 create 方法签名和实现**

添加 name 参数，传入 ConnectionRecord：

```java
public String create(String name, String kind, String host, int port, String databaseName,
                     String username, String password, Integer connectTimeout) {
    byte[] enc = vault.seal(password);
    String id = java.util.UUID.randomUUID().toString();
    int timeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
    repo.insert(new ConnectionRecord(id, name, kind, host, port, databaseName, username, enc, null, clock.millis(), timeout, null, null));
    return id;
}
```

- [ ] **Step 2: 更新 list 方法映射**

添加 name 到 ConnectionDto：

```java
public List<ConnectionDto> list() {
    return repo.findAll().stream()
        .map(c -> new ConnectionDto(c.id(), c.name(), c.kind(), c.host(), c.port(),
            c.databaseName(), c.username(), c.createdAt(), c.connectTimeout(),
            c.lastTestStatus(), c.lastTestAt()))
        .toList();
}
```

- [ ] **Step 3: 更新 update 方法签名和实现**

添加 name 参数：

```java
public void update(String id, String name, String kind, String host, int port, String databaseName,
                   String username, String password, Integer connectTimeout) {
    var existing = repo.findById(id)
        .orElseThrow(() -> new java.util.NoSuchElementException("unknown connection: " + id));
    byte[] enc = password != null ? vault.seal(password) : existing.passwordEnc();
    int timeout = connectTimeout != null ? connectTimeout : existing.connectTimeout();
    repo.update(new ConnectionRecord(id, name, kind, host, port, databaseName, username,
        enc, existing.schemaDigest(), existing.createdAt(), timeout,
        existing.lastTestStatus(), existing.lastTestAt()));
}
```

- [ ] **Step 4: 验证编译**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java
git commit -m "feat(service): add name parameter to ConnectionService create/update"
```

---

## Task 6: ConnectionController 更新 + 唯一性校验

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java`

- [ ] **Step 1: 更新 create 方法**

添加 name 参数，添加唯一性冲突处理：

```java
@PostMapping
public ResponseEntity<ConnectionCreatedDto> create(@RequestBody ConnectionCreateRequest body) {
    try {
        String id = svc.create(body.name(), body.kind(), body.host(), body.port(),
            body.databaseName(), body.username(), body.password(), body.connectTimeout());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ConnectionCreatedDto(id));
    } catch (org.springframework.dao.DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
```

- [ ] **Step 2: 更新 update 方法**

添加 name 参数，添加唯一性冲突处理：

```java
@PutMapping("/{id}")
public ResponseEntity<Void> update(@PathVariable String id, @RequestBody ConnectionUpdateRequest body) {
    try {
        svc.update(id, body.name(), body.kind(), body.host(), body.port(),
            body.databaseName(), body.username(), body.password(), body.connectTimeout());
        return ResponseEntity.noContent().build();
    } catch (java.util.NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    } catch (org.springframework.dao.DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `cd server && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java
git commit -m "feat(controller): add name parameter and handle duplicate name conflict"
```

---

## Task 7: 后端测试更新

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionControllerIT.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/connection/ConnectionCrudIT.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java`

- [ ] **Step 1: 更新 ConnectionControllerIT**

更新所有 JSON body 添加 name 字段，添加唯一性测试：

```java
package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Objects;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionControllerIT {

    @LocalServerPort int port;
    @Autowired ObjectMapper om;

    WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String createConnection(WebTestClient w, String body) throws Exception {
        byte[] res = w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange().expectStatus().isCreated()
            .expectBody().returnResult().getResponseBody();
        JsonNode node = om.readTree(Objects.requireNonNull(res));
        return node.get("id").asText();
    }

    @Test
    void put_updates_existing_connection() throws Exception {
        var w = web();
        String id = createConnection(w, """
            {"name":"测试数据源","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        w.put().uri("/api/connections/" + id).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"更新后的名称","kind":"postgres","host":"h2","port":5432,
                 "database":"d2","username":"u2","password":"p2"}
                """)
            .exchange().expectStatus().isNoContent();

        w.get().uri("/api/connections").exchange()
            .expectBody().jsonPath("$.connections[?(@.id=='" + id + "')].name").isEqualTo("更新后的名称");
    }

    @Test
    void delete_returns_204_on_success_404_on_missing() throws Exception {
        var w = web();
        String id = createConnection(w, """
            {"name":"待删除数据源","kind":"mysql","host":"h","port":3306,
             "database":"d","username":"u","password":"p"}
            """);

        w.delete().uri("/api/connections/" + id).exchange().expectStatus().isNoContent();
        w.delete().uri("/api/connections/" + id).exchange().expectStatus().isNotFound();
    }

    @Test
    void test_endpoint_returns_ok_false_for_bad_target() throws Exception {
        var w = web();
        String id = createConnection(w, """
            {"name":"无效数据源","kind":"mysql","host":"127.0.0.1","port":1,
             "database":"x","username":"u","password":"p"}
            """);
        w.post().uri("/api/connections/" + id + "/test").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.ok").isEqualTo(false);
    }

    @Test
    void create_returns_409_on_duplicate_name() throws Exception {
        var w = web();
        createConnection(w, """
            {"name":"重复名称","kind":"mysql","host":"h1","port":3306,
             "database":"d1","username":"u1","password":"p1"}
            """);

        w.post().uri("/api/connections").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"重复名称","kind":"mysql","host":"h2","port":3306,
                 "database":"d2","username":"u2","password":"p2"}
                """)
            .exchange().expectStatus().isEqualTo(409);
    }
}
```

- [ ] **Step 2: 更新 ConnectionCrudIT**

更新 Map.of 添加 name 字段：

```java
@Test
void createListRoundTrip() throws Exception {
    String body = om.writeValueAsString(Map.of(
        "name", "测试连接",
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
        .andExpect(jsonPath("$.connections[0].name").value("测试连接"))
        .andExpect(jsonPath("$.connections[0].username").value("alice"));

    // password never leaks
    mvc.perform(get("/api/connections"))
        .andExpect(jsonPath("$.connections[0].password").doesNotExist())
        .andExpect(jsonPath("$.connections[0].passwordEnc").doesNotExist());
}
```

- [ ] **Step 3: 更新 ConnectionServiceTest**

更新所有 ConnectionRecord mock，添加 name 字段：

```java
when(repo.findById("c1")).thenReturn(Optional.of(
    new ConnectionRecord("c1", "测试连接", "h2", "localhost", 9999,
        "mem:it;DB_CLOSE_DELAY=-1", "sa", new byte[]{}, null, 0, 3000, null, null)));
```

对文件中所有 4 处 ConnectionRecord 都做同样更新（添加 `"测试连接"` 作为第二个参数）。

- [ ] **Step 4: 运行后端测试**

Run: `cd server && mvn test -q`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 5: 提交**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ConnectionControllerIT.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/connection/ConnectionCrudIT.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java
git commit -m "test: update connection tests for name field and add uniqueness test"
```

---

## Task 8: 前端类型更新

**Files:**
- Modify: `client/src/types/generated/api.ts`（通过 gen:api 自动生成）

- [ ] **Step 1: 启动后端服务**

Run: `cd server && mvn spring-boot:run -pl data-talk-adapter -DskipTests`
Expected: 服务在 8080 端口启动

等待服务启动完成（约 10-15 秒）。

- [ ] **Step 2: 运行前端类型生成**

Run: `cd client && npm run gen:api`
Expected: `src/types/generated/api.ts` 更新，包含 name 字段

- [ ] **Step 3: 验证类型文件**

确认 `api.ts` 中 `ConnectionDto` 和 `ConnectionCreateRequest` 包含 `name` 字段：

```typescript
export interface ConnectionDto {
  id: string
  name: string
  kind: string
  ...
}
```

- [ ] **Step 4: 停止后端服务**

Ctrl+C 停止 Spring Boot 进程。

- [ ] **Step 5: 提交**

```bash
git add client/src/types/generated/api.ts
git commit -m "feat(frontend): regenerate API types with name field"
```

---

## Task 9: ConnectionFormPanel 名称输入框

**Files:**
- Modify: `client/src/features/settings/data-sources/connection-form-dialog.tsx`

- [ ] **Step 1: 添加 name 到 form state**

在 useState 中添加 name 字段：

```typescript
const [form, setForm] = useState({
  name: '', kind: 'mysql', host: 'localhost', port: 3306,
  database: '', username: '', password: '',
  connectTimeout: 3000,
})
```

- [ ] **Step 2: 更新 useEffect 加载编辑数据**

```typescript
useEffect(() => {
  if (editing) {
    setForm({
      name: editing.name ?? '',
      kind: editing.kind, host: editing.host,
      port: editing.port, database: editing.databaseName ?? '',
      username: editing.username, password: '',
      connectTimeout: editing.connectTimeout ?? 3000,
    })
  } else {
    setForm({ name: '', kind: 'mysql', host: 'localhost', port: 3306,
      database: '', username: '', password: '', connectTimeout: 3000 })
  }
}, [editing])
```

- [ ] **Step 3: 添加名称输入框到表单**

在 Field 列表第一位添加名称输入框：

```tsx
<Field label="名称">
  <Input value={form.name}
    onChange={(e) => setForm(f => ({ ...f, name: e.target.value }))} />
</Field>
```

- [ ] **Step 4: 更新 save mutation 传递 name**

```typescript
mutationFn: async () => {
  const dbName = form.database.trim() || null
  const name = form.name.trim() || '未命名数据源'
  if (editing) {
    await updateConnection(editing.id, {
      name, kind: form.kind, host: form.host, port: form.port,
      databaseName: dbName, username: form.username,
      password: form.password.length > 0 ? form.password : null,
      connectTimeout: form.connectTimeout,
    })
  } else {
    await createConnection({
      name, kind: form.kind, host: form.host, port: form.port,
      databaseName: dbName, username: form.username, password: form.password,
      connectTimeout: form.connectTimeout,
    })
  }
},
```

- [ ] **Step 5: 验证 TypeScript**

Run: `cd client && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 6: 提交**

```bash
git add client/src/features/settings/data-sources/connection-form-dialog.tsx
git commit -m "feat(frontend): add name input to connection form"
```

---

## Task 10: DataSourcesPage 表格修复

**Files:**
- Modify: `client/src/features/settings/data-sources/data-sources-page.tsx`

- [ ] **Step 1: 更新表格列标题**

统一 pb-2 样式，添加"名称"和"操作"列标题：

```tsx
<thead className="text-left text-muted-foreground">
  <tr>
    <th className="pb-2">名称</th>
    <th className="pb-2">类型</th>
    <th className="pb-2">地址</th>
    <th className="pb-2">数据库</th>
    <th className="pb-2">用户</th>
    <th className="pb-2">操作</th>
  </tr>
</thead>
```

- [ ] **Step 2: 更新表格行添加名称列**

```tsx
<tr key={c.id} className="border-t">
  <td className="py-2">{c.name}</td>
  <td className="py-2">{c.kind}</td>
  <td>{c.host}:{c.port}</td>
  <td>{c.databaseName}</td>
  <td>{c.username}</td>
  <td className="text-right">
    ...
  </td>
</tr>
```

- [ ] **Step 3: 验证 TypeScript**

Run: `cd client && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 4: 提交**

```bash
git add client/src/features/settings/data-sources/data-sources-page.tsx
git commit -m "feat(frontend): add name column and fix table header spacing"
```

---

## Task 11: 前端测试更新

**Files:**
- Modify: `client/src/features/settings/data-sources/__tests__/data-sources-page.test.tsx`

- [ ] **Step 1: 更新 mock Connection 类型**

添加 name 字段到 mock 返回值：

```typescript
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { DataSourcesPage } from '../data-sources-page'
import * as api from '../api'

vi.mock('../api', () => ({
  listConnections: vi.fn(),
  deleteConnection: vi.fn(),
  testConnection: vi.fn(),
  connectionsKey: ['connections'] as const,
}))

describe('DataSourcesPage', () => {
  beforeEach(() => {
    vi.mocked(api.listConnections).mockResolvedValue([])
  })
  it('shows empty state when no connections', async () => {
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><DataSourcesPage /></QueryClientProvider>)
    expect(await screen.findByText(/还没有数据源/)).toBeInTheDocument()
  })

  it('displays connection name in table', async () => {
    vi.mocked(api.listConnections).mockResolvedValue([
      {
        id: 'c1',
        name: '生产数据库',
        kind: 'mysql',
        host: '192.168.1.100',
        port: 3306,
        databaseName: 'production',
        username: 'admin',
        createdAt: 1700000000000,
        connectTimeout: 3000,
        lastTestStatus: null,
        lastTestAt: null
      }
    ])
    const qc = new QueryClient()
    render(<QueryClientProvider client={qc}><DataSourcesPage /></QueryClientProvider>)
    expect(await screen.findByText('生产数据库')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: 运行前端测试**

Run: `cd client && npm test`
Expected: All tests pass

- [ ] **Step 3: 提交**

```bash
git add client/src/features/settings/data-sources/__tests__/data-sources-page.test.tsx
git commit -m "test(frontend): update DataSourcesPage test for name field"
```

---

## Task 12: 集成验证

- [ ] **Step 1: 后端全量测试**

Run: `cd server && mvn verify -q`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: 前端类型检查**

Run: `cd client && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: 前端测试**

Run: `cd client && npm test`
Expected: All tests pass

- [ ] **Step 4: 手动 E2E 验证**

启动后端和前端：
```bash
cd server && mvn spring-boot:run -pl data-talk-adapter -DskipTests
cd client && npm run dev
```

验证：
1. 新增数据源时填写名称
2. 名称重复时显示错误
3. 表格显示名称列
4. 表格样式间距一致

- [ ] **Step 5: 最终提交（如有遗漏）**

检查是否有未提交的文件：
```bash
git status
```

如有遗漏文件，一并提交。

---

## Spec Coverage Check

| Spec 需求 | 对应 Task |
|---|---|
| Flyway 迁移添加 name 列 | Task 1 |
| 唯一索引 | Task 1 |
| ConnectionRecord name 字段 | Task 2 |
| ConnectionDto name 字段 | Task 2 |
| ConnectionCreateRequest name 字段 | Task 3 |
| ConnectionUpdateRequest name 字段 | Task 3 |
| Repository SQL 更新 | Task 4 |
| Service 处理 name | Task 5 |
| Controller 唯一性校验 | Task 6 |
| 前端类型更新 | Task 8 |
| ConnectionFormPanel 名称输入 | Task 9 |
| DataSourcesPage 名称列 | Task 10 |
| 表格样式 pb-2 统一 | Task 10 |
| 操作列标题 | Task 10 |
| 测试更新 | Task 7, Task 11 |