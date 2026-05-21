# Stage Query Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 接通 Stage 特性到 useStageStore，新增完整的 Query Editor tab（CodeMirror SQL 编辑器 + 结果面板 + 双路径执行），并新增后端 `POST /api/sql/execute` 直连端点。

**Architecture:** 前端 stage-window/tab-bar/dock 改为读写 useStageStore；新增 QueryEditorTab 组件（CodeMirror 6 SQL 编辑器 + 结果表格 + 高风险拦截 UI）；后端新增 SqlExecuteService 编排 CalciteSqlRiskAnalyzer 风险判级 + DriverManager JDBC 直连执行，通过 SqlExecuteController 暴露 REST 端点；AI 生成 SQL 跳过风险判级，用户手写 SQL 经判级，HIGH 风险返回 422 由前端引导"发给 AI 审查"。

**Tech Stack:** CodeMirror 6 (`codemirror` + `@codemirror/lang-sql`)；Spring Boot JdbcTemplate 模式（DriverManager.getConnection）；CalciteSqlRiskAnalyzer（已有）；useChannel().sendMessage（已有）

**Design Spec:** `docs/product-specs/2026-04-21-stage-query-editor-design.md`

---

## 文件清单

### 新增
| 文件 | 说明 |
|------|------|
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteRequest.java` | 请求 DTO |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteResult.java` | 成功响应 DTO |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlRiskBlockedDto.java` | 422 响应 DTO |
| `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java` | 风险判级 + JDBC 执行 |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java` | REST 端点 |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java` | 集成测试 |
| `client/src/services/api/sql.ts` | 前端 API 客户端 |
| `client/src/features/stage/hooks/use-sql-execute.ts` | 执行状态 hook |
| `client/src/features/stage/hooks/use-sql-execute.test.ts` | hook 单元测试 |
| `client/src/features/stage/components/query-editor-tab.tsx` | Query Editor 组件 |
| `client/src/features/stage/components/query-editor-tab.test.tsx` | 组件测试 |

### 修改
| 文件 | 改动 |
|------|------|
| `server/data-talk-adapter/src/main/resources/application.yml` | 加 `datatalk.sql.max-rows: 5000` |
| `client/package.json` | 加 `codemirror` + `@codemirror/lang-sql` |
| `client/src/features/stage/components/stage-window.tsx` | 删 mockTabs/useState，接 store |
| `client/src/features/stage/components/stage-window.test.tsx` | 扩展测试 |
| `client/src/features/stage/components/stage-tab-bar.tsx` | X 按钮 + 右键菜单接回调 |
| `client/src/features/stage/components/stage-dock.tsx` | SQL 按钮 onClick + sessionId prop |
| `client/src/features/stage/components/stage-tab-content.tsx` | 加 query_editor case |

---

## Task 1: 后端 DTOs + application.yml

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteRequest.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteResult.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlRiskBlockedDto.java`
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`

- [ ] **Step 1: 创建 SqlExecuteRequest.java**

```java
package com.datatalk.adapter.dto;

public record SqlExecuteRequest(String connectionId, String sql, String source) {}
```

- [ ] **Step 2: 创建 SqlExecuteResult.java**

```java
package com.datatalk.adapter.dto;

import java.util.List;

public record SqlExecuteResult(
    List<String> columns,
    List<List<Object>> rows,
    int rowCount,
    long executionMs,
    boolean truncated
) {}
```

- [ ] **Step 3: 创建 SqlRiskBlockedDto.java**

```java
package com.datatalk.adapter.dto;

public record SqlRiskBlockedDto(String riskLevel, String riskReason) {}
```

- [ ] **Step 4: 在 application.yml 加入配置**

在 `datatalk:` 块下（与 `channel:` 同级）追加：

```yaml
  sql:
    max-rows: 5000
```

最终 datatalk 块形如：
```yaml
datatalk:
  channel:
    flush-interval: PT0.016S
    post-stream-timeout-ms: 600000
  sql:
    max-rows: 5000
```

- [ ] **Step 5: 编译验证**

```bash
cd server && mvn compile -q -pl data-talk-adapter -am
```

预期：BUILD SUCCESS，无报错。

- [ ] **Step 6: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteRequest.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteResult.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlRiskBlockedDto.java \
        server/data-talk-adapter/src/main/resources/application.yml
git commit -m "feat(server): add SQL execute DTOs and max-rows config"
```

---

## Task 2: SqlExecuteService（TDD）

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`

**参考：** `ExecuteSqlAction.java` 是 JDBC 执行的标准模式（DriverManager.getConnection + ConnectionRepository.findById + ConnectionService.decryptPassword + JdbcUrlBuilder.build）。

- [ ] **Step 1: 写 SqlExecuteService 骨架（无逻辑，让后续测试能引用类）**

```java
package com.datatalk.application.sql;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class SqlExecuteService {

    public record Result(
        List<String> columns,
        List<List<Object>> rows,
        int rowCount,
        long executionMs,
        boolean truncated
    ) {}

    public record RiskBlocked(String riskLevel, String riskReason) {}

    public static class SqlRiskBlockedException extends RuntimeException {
        private final RiskBlocked risk;
        public SqlRiskBlockedException(RiskBlocked risk) {
            super("SQL risk blocked: " + risk.riskLevel());
            this.risk = risk;
        }
        public RiskBlocked risk() { return risk; }
    }

    private final SqlRiskAnalyzer riskAnalyzer;
    private final ConnectionRepository connRepo;
    private final ConnectionService connSvc;
    private final int maxRows;

    public SqlExecuteService(SqlRiskAnalyzer riskAnalyzer,
                             ConnectionRepository connRepo,
                             ConnectionService connSvc,
                             @Value("${datatalk.sql.max-rows:5000}") int maxRows) {
        this.riskAnalyzer = riskAnalyzer;
        this.connRepo = connRepo;
        this.connSvc = connSvc;
        this.maxRows = maxRows;
    }

    public Result execute(String connectionId, String sql, String source) {
        throw new UnsupportedOperationException("not implemented");
    }
}
```

- [ ] **Step 2: 编译**

```bash
cd server && mvn compile -q -pl data-talk-application -am
```

预期：BUILD SUCCESS。

- [ ] **Step 3: 实现 execute 方法**

替换 `execute` 方法体：

```java
public Result execute(String connectionId, String sql, String source) {
    if (connectionId == null || connectionId.isBlank())
        throw new IllegalArgumentException("connectionId required");
    if (sql == null || sql.isBlank())
        throw new IllegalArgumentException("sql required");

    if ("user".equals(source)) {
        SqlRiskAnalysis risk = riskAnalyzer.analyze(sql, Category.QUERY);
        if (risk.riskLevel() == RiskLevel.L3) {
            throw new SqlRiskBlockedException(new RiskBlocked("HIGH", risk.reason()));
        }
    }

    ConnectionRecord cr = connRepo.findById(connectionId)
        .orElseThrow(() -> new NoSuchElementException("unknown connection: " + connectionId));

    long started = System.currentTimeMillis();
    List<String> columns = new ArrayList<>();
    List<List<Object>> rows = new ArrayList<>();
    boolean truncated = false;

    try (Connection c = DriverManager.getConnection(
             JdbcUrlBuilder.build(cr), cr.username(), connSvc.decryptPassword(connectionId));
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setQueryTimeout(30);
        try (ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            for (int i = 1; i <= colCount; i++) columns.add(md.getColumnLabel(i));
            while (rs.next()) {
                if (rows.size() >= maxRows) { truncated = true; break; }
                List<Object> row = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) row.add(rs.getObject(i));
                rows.add(row);
            }
        }
    } catch (SQLException e) {
        throw new RuntimeException("SQL execution failed: " + e.getMessage(), e);
    }

    return new Result(columns, rows, rows.size(), System.currentTimeMillis() - started, truncated);
}
```

- [ ] **Step 4: 编译**

```bash
cd server && mvn compile -q -pl data-talk-application -am
```

预期：BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java
git commit -m "feat(server): add SqlExecuteService with risk gate and JDBC execution"
```

---

## Task 3: SqlExecuteController + 集成测试

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

- [ ] **Step 1: 写集成测试（先跑红）**

```java
package com.datatalk.adapter.controller;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.connection.ConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.DriverManager;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = "datatalk.sql.max-rows=3")
class SqlExecuteControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ConnectionRepository connRepo;
    @Autowired ConnectionService connSvc;

    static final String CONN_ID = "c-sql-it";

    @BeforeEach
    void setUp() throws Exception {
        connRepo.deleteAll();
        // H2 in-memory test DB (no password, no encryption needed)
        var cr = new ConnectionRecord(CONN_ID, "IT DB", "h2",
            "mem:sqlit;DB_CLOSE_DELAY=-1", 0, "sqlit", "sa", new byte[0],
            null, System.currentTimeMillis(), 10, null, null);
        connRepo.insert(cr);
        // Seed test table
        try (var c = DriverManager.getConnection("jdbc:h2:mem:sqlit;DB_CLOSE_DELAY=-1", "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS items");
            st.execute("CREATE TABLE items(id INT, name VARCHAR(50))");
            st.execute("INSERT INTO items VALUES(1,'a'),(2,'b'),(3,'c'),(4,'d')");
        }
    }

    @Test
    void select_returns_200_with_columns_and_rows() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"SELECT * FROM items ORDER BY id","source":"user"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.columns", hasItems("ID", "NAME")))
            .andExpect(jsonPath("$.rowCount", is(3)))
            .andExpect(jsonPath("$.truncated", is(true)));  // max-rows=3, table has 4 rows
    }

    @Test
    void ai_source_skips_risk_check_and_executes() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"SELECT 1","source":"ai"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rowCount", is(1)));
    }

    @Test
    void high_risk_user_sql_returns_422() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"%s","sql":"DELETE FROM items","source":"user"}
                    """.formatted(CONN_ID)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.riskLevel", is("HIGH")))
            .andExpect(jsonPath("$.riskReason", notNullValue()));
    }

    @Test
    void unknown_connection_returns_400() throws Exception {
        mvc.perform(post("/api/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"connectionId":"no-such","sql":"SELECT 1","source":"ai"}
                    """.formatted()))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 确认测试失败（Controller 不存在）**

```bash
cd server && mvn failsafe:integration-test -pl data-talk-adapter -Dit.test=SqlExecuteControllerIT 2>&1 | tail -20
```

预期：FAILED — Controller 不存在导致 404。

- [ ] **Step 3: 创建 SqlExecuteController.java**

```java
package com.datatalk.adapter.controller;

import com.datatalk.adapter.dto.SqlExecuteRequest;
import com.datatalk.adapter.dto.SqlExecuteResult;
import com.datatalk.adapter.dto.SqlRiskBlockedDto;
import com.datatalk.application.sql.SqlExecuteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/sql")
public class SqlExecuteController {

    private final SqlExecuteService service;

    public SqlExecuteController(SqlExecuteService service) {
        this.service = service;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody SqlExecuteRequest req) {
        try {
            SqlExecuteService.Result r = service.execute(req.connectionId(), req.sql(), req.source());
            return ResponseEntity.ok(
                new SqlExecuteResult(r.columns(), r.rows(), r.rowCount(), r.executionMs(), r.truncated())
            );
        } catch (SqlExecuteService.SqlRiskBlockedException e) {
            return ResponseEntity.unprocessableEntity()
                .body(new SqlRiskBlockedDto(e.risk().riskLevel(), e.risk().riskReason()));
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }
}
```

- [ ] **Step 4: 运行 IT 验证通过**

```bash
cd server && mvn failsafe:integration-test -pl data-talk-adapter -Dit.test=SqlExecuteControllerIT 2>&1 | tail -20
```

预期：所有 4 个测试 PASSED。

注意：`ConnectionService.decryptPassword` 在测试中对空密码 H2 需要能正常工作。如果 decryptPassword 对空字节数组抛异常，在 IT 的 setUp 里改为使用 `connSvc.encryptPassword("")` 的返回值存入 `passwordEnc`，确保解密后得到空字符串。

- [ ] **Step 5: 全量后端编译+验证**

```bash
cd server && mvn clean verify -q
```

预期：BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java
git commit -m "feat(server): add SqlExecuteController with risk gate and IT coverage"
```

---

## Task 4: 前端 API 客户端

**Files:**
- Create: `client/src/services/api/sql.ts`

- [ ] **Step 1: 创建 sql.ts**

```typescript
const BASE = (() => {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  return typeof env === 'string' && env.length > 0 ? env.replace(/\/$/, '') : ''
})()

export interface SqlExecuteRequest {
  connectionId: string
  sql: string
  source: 'ai' | 'user'
}

export interface SqlResult {
  columns: string[]
  rows: unknown[][]
  rowCount: number
  executionMs: number
  truncated: boolean
}

export interface SqlRiskBlocked {
  riskLevel: string
  riskReason: string
}

export class SqlRiskError extends Error {
  constructor(public readonly risk: SqlRiskBlocked) {
    super('risk_blocked')
    this.name = 'SqlRiskError'
  }
}

export async function executeSql(req: SqlExecuteRequest): Promise<SqlResult> {
  const res = await fetch(`${BASE}/api/sql/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (res.status === 422) {
    const risk: SqlRiskBlocked = await res.json()
    throw new SqlRiskError(risk)
  }
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: 'SQL execution failed' }))
    throw new Error((err as any).message ?? 'SQL execution failed')
  }
  return res.json() as Promise<SqlResult>
}
```

- [ ] **Step 2: 类型检查**

```bash
cd client && npx tsc --noEmit
```

预期：无报错。

- [ ] **Step 3: Commit**

```bash
git add client/src/services/api/sql.ts
git commit -m "feat(client): add SQL execute API client"
```

---

## Task 5: useSqlExecute hook（TDD）

**Files:**
- Create: `client/src/features/stage/hooks/use-sql-execute.ts`
- Create: `client/src/features/stage/hooks/use-sql-execute.test.ts`

- [ ] **Step 1: 写失败测试**

```typescript
// use-sql-execute.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useSqlExecute } from './use-sql-execute'
import * as sqlApi from '@/services/api/sql'

vi.mock('@/services/api/sql')

const mockResult: sqlApi.SqlResult = {
  columns: ['id'], rows: [[1]], rowCount: 1, executionMs: 10, truncated: false,
}

describe('useSqlExecute', () => {
  beforeEach(() => vi.clearAllMocks())

  it('ai source: executes and sets success', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockResult)
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.execute('SELECT 1', 'c-1', 'ai') })
    expect(sqlApi.executeSql).toHaveBeenCalledWith({ sql: 'SELECT 1', connectionId: 'c-1', source: 'ai' })
    expect(result.current.status).toBe('success')
    expect(result.current.result).toEqual(mockResult)
  })

  it('user source low risk: executes and sets success', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockResult)
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.execute('SELECT 1', 'c-1', 'user') })
    expect(result.current.status).toBe('success')
  })

  it('user source high risk: sets risk_blocked', async () => {
    vi.mocked(sqlApi.executeSql).mockRejectedValue(
      new sqlApi.SqlRiskError({ riskLevel: 'HIGH', riskReason: 'bulk_delete' })
    )
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.execute('DELETE FROM orders', 'c-1', 'user') })
    expect(result.current.status).toBe('risk_blocked')
    expect(result.current.risk).toEqual({ riskLevel: 'HIGH', riskReason: 'bulk_delete' })
    expect(result.current.result).toBeNull()
  })

  it('network error: sets error status', async () => {
    vi.mocked(sqlApi.executeSql).mockRejectedValue(new Error('network'))
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.execute('SELECT 1', 'c-1', 'user') })
    expect(result.current.status).toBe('error')
    expect(result.current.errorMessage).toBe('network')
  })

  it('reset: clears all state', async () => {
    vi.mocked(sqlApi.executeSql).mockResolvedValue(mockResult)
    const { result } = renderHook(() => useSqlExecute())
    await act(async () => { await result.current.execute('SELECT 1', 'c-1', 'ai') })
    act(() => result.current.reset())
    expect(result.current.status).toBe('idle')
    expect(result.current.result).toBeNull()
  })
})
```

- [ ] **Step 2: 运行确认失败**

```bash
cd client && npx vitest run src/features/stage/hooks/use-sql-execute.test.ts 2>&1 | tail -10
```

预期：FAILED — 模块不存在。

- [ ] **Step 3: 实现 hook**

```typescript
// use-sql-execute.ts
import { useState, useCallback } from 'react'
import { executeSql, SqlRiskError } from '@/services/api/sql'
import type { SqlResult, SqlRiskBlocked } from '@/services/api/sql'

type Status = 'idle' | 'running' | 'success' | 'risk_blocked' | 'error'

export interface UseSqlExecuteReturn {
  execute: (sql: string, connectionId: string, source: 'ai' | 'user') => Promise<void>
  result: SqlResult | null
  risk: SqlRiskBlocked | null
  status: Status
  errorMessage: string | null
  reset: () => void
}

export function useSqlExecute(): UseSqlExecuteReturn {
  const [status, setStatus] = useState<Status>('idle')
  const [result, setResult] = useState<SqlResult | null>(null)
  const [risk, setRisk] = useState<SqlRiskBlocked | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const execute = useCallback(async (sql: string, connectionId: string, source: 'ai' | 'user') => {
    setStatus('running')
    setResult(null)
    setRisk(null)
    setErrorMessage(null)
    try {
      const data = await executeSql({ sql, connectionId, source })
      setResult(data)
      setStatus('success')
    } catch (err: unknown) {
      if (err instanceof SqlRiskError) {
        setRisk(err.risk)
        setStatus('risk_blocked')
      } else {
        setErrorMessage(err instanceof Error ? err.message : 'Unknown error')
        setStatus('error')
      }
    }
  }, [])

  const reset = useCallback(() => {
    setStatus('idle')
    setResult(null)
    setRisk(null)
    setErrorMessage(null)
  }, [])

  return { execute, result, risk, status, errorMessage, reset }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
cd client && npx vitest run src/features/stage/hooks/use-sql-execute.test.ts 2>&1 | tail -10
```

预期：5 tests PASSED。

- [ ] **Step 5: Commit**

```bash
git add client/src/features/stage/hooks/use-sql-execute.ts \
        client/src/features/stage/hooks/use-sql-execute.test.ts
git commit -m "feat(client): add useSqlExecute hook with TDD coverage"
```

---

## Task 6: 安装 CodeMirror + QueryEditorTab 骨架 + 测试

**Files:**
- Modify: `client/package.json`
- Create: `client/src/features/stage/components/query-editor-tab.tsx`
- Create: `client/src/features/stage/components/query-editor-tab.test.tsx`

- [ ] **Step 1: 安装 CodeMirror 依赖**

```bash
cd client && npm install codemirror @codemirror/lang-sql
```

- [ ] **Step 2: 写失败测试**

```typescript
// query-editor-tab.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryEditorTab } from './query-editor-tab'
import type { StageTab } from '@/stores/stage-store'
import * as useSqlExecuteModule from '../hooks/use-sql-execute'
import * as useChannelModule from '@/services/channel/use-channel'

vi.mock('@codemirror/lang-sql', () => ({ sql: () => [] }))
vi.mock('codemirror', () => ({
  basicSetup: [],
  EditorView: class {
    constructor({ parent }: any) { if (parent) parent.textContent = 'editor' }
    get state() { return { doc: { toString: () => 'SELECT 1' } } }
    destroy() {}
  },
}))
vi.mock('@codemirror/view', () => ({ keymap: { of: () => [] }, Prec: { high: (x: any) => x } }))
vi.mock('@codemirror/state', () => ({ Prec: { high: (x: any) => x } }))

vi.mock('../hooks/use-sql-execute')
vi.mock('@/services/channel/use-channel')
vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (sel: any) => sel({ activeConnectionId: 'c-test' }),
}))

const mockTab: StageTab = {
  tabId: 'qe-1', type: 'query_editor', title: 'SQL 编辑器',
  scope: 'session', originSessionId: 's-1', createdAt: 0,
  payload: { sql: 'SELECT 1', source: 'user' },
}

describe('QueryEditorTab', () => {
  const mockExecute = vi.fn()
  const mockReset = vi.fn()
  const mockSendMessage = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useSqlExecuteModule.useSqlExecute).mockReturnValue({
      execute: mockExecute, result: null, risk: null,
      status: 'idle', errorMessage: null, reset: mockReset,
    })
    vi.mocked(useChannelModule.useChannel).mockReturnValue({
      sendMessage: mockSendMessage, abort: vi.fn(), isStreaming: false,
      client: null as any, retryPendingUser: vi.fn(), removePendingUser: vi.fn(),
    })
  })

  it('renders editor area and Run button', () => {
    render(<QueryEditorTab tab={mockTab} />)
    expect(screen.getByRole('button', { name: /Ctrl\+Enter 运行/i })).toBeTruthy()
  })

  it('calls execute on Run click', () => {
    render(<QueryEditorTab tab={mockTab} />)
    fireEvent.click(screen.getByRole('button', { name: /Ctrl\+Enter 运行/i }))
    expect(mockExecute).toHaveBeenCalledWith('SELECT 1', 'c-test', 'user')
  })

  it('shows risk warning when risk_blocked', () => {
    vi.mocked(useSqlExecuteModule.useSqlExecute).mockReturnValue({
      execute: mockExecute, result: null,
      risk: { riskLevel: 'HIGH', riskReason: 'bulk_delete' },
      status: 'risk_blocked', errorMessage: null, reset: mockReset,
    })
    render(<QueryEditorTab tab={mockTab} />)
    expect(screen.getByText(/高风险操作/i)).toBeTruthy()
    expect(screen.getByText(/bulk_delete/i)).toBeTruthy()
  })

  it('sends message to AI on risk warning click', () => {
    vi.mocked(useSqlExecuteModule.useSqlExecute).mockReturnValue({
      execute: mockExecute, result: null,
      risk: { riskLevel: 'HIGH', riskReason: 'bulk_delete' },
      status: 'risk_blocked', errorMessage: null, reset: mockReset,
    })
    render(<QueryEditorTab tab={mockTab} />)
    fireEvent.click(screen.getByText(/发给 AI 审查/i))
    expect(mockSendMessage).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ type: 'text' })])
    )
  })
})
```

- [ ] **Step 3: 运行确认失败**

```bash
cd client && npx vitest run src/features/stage/components/query-editor-tab.test.tsx 2>&1 | tail -10
```

预期：FAILED — 组件不存在。

- [ ] **Step 4: Commit 测试文件**

```bash
git add client/package.json client/src/features/stage/components/query-editor-tab.test.tsx
git commit -m "test(client): add QueryEditorTab failing tests"
```

---

## Task 7: QueryEditorTab 完整实现

**Files:**
- Create: `client/src/features/stage/components/query-editor-tab.tsx`

- [ ] **Step 1: 创建组件**

```typescript
import { useRef, useEffect, useCallback } from 'react'
import { EditorView, basicSetup } from 'codemirror'
import { sql } from '@codemirror/lang-sql'
import { keymap } from '@codemirror/view'
import { Prec } from '@codemirror/state'
import { PlayIcon, AlertTriangleIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { StageTab } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSqlExecute } from '../hooks/use-sql-execute'
import { useChannel } from '@/services/channel/use-channel'
import type { SqlResult } from '@/services/api/sql'

type QueryEditorPayload = {
  sql?: string
  source?: 'ai' | 'user'
  connectionId?: string
}

function SqlEditor({
  initialValue,
  editorRef,
  onRun,
}: {
  initialValue: string
  editorRef: React.MutableRefObject<EditorView | undefined>
  onRun: () => void
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const onRunRef = useRef(onRun)
  onRunRef.current = onRun

  useEffect(() => {
    if (!containerRef.current) return
    editorRef.current = new EditorView({
      doc: initialValue,
      extensions: [
        basicSetup,
        sql(),
        Prec.high(
          keymap.of([{
            key: 'Ctrl-Enter',
            mac: 'Cmd-Enter',
            run: () => { onRunRef.current(); return true },
          }])
        ),
      ],
      parent: containerRef.current,
    })
    return () => { editorRef.current?.destroy(); editorRef.current = undefined }
  }, []) // intentional empty deps — value/handlers updated via refs

  return (
    <div
      ref={containerRef}
      className="h-full overflow-auto [&_.cm-editor]:h-full [&_.cm-editor]:outline-none [&_.cm-scroller]:font-mono [&_.cm-scroller]:text-sm"
    />
  )
}

function ResultTable({ result }: { result: SqlResult }) {
  return (
    <div className="flex h-full flex-col">
      <div className="flex shrink-0 items-center border-b border-border/50 px-3 py-1.5 text-xs text-muted-foreground">
        <span>
          {result.truncated
            ? `前 ${result.rowCount} 行（已截断）`
            : `${result.rowCount} 行`}{' '}
          · {result.executionMs}ms
        </span>
      </div>
      <div className="flex-1 overflow-auto">
        <table className="w-full border-collapse text-xs">
          <thead className="sticky top-0 bg-muted/50">
            <tr>
              {result.columns.map((col) => (
                <th
                  key={col}
                  className="whitespace-nowrap border-b border-border/50 px-3 py-2 text-left font-medium"
                >
                  {col}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {result.rows.map((row, i) => (
              <tr key={i} className="border-b border-border/30 last:border-0 hover:bg-muted/30">
                {row.map((cell, j) => (
                  <td key={j} className="max-w-[300px] truncate whitespace-nowrap px-3 py-1.5">
                    {cell === null ? (
                      <span className="italic text-muted-foreground/50">null</span>
                    ) : (
                      String(cell)
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export function QueryEditorTab({ tab }: { tab: StageTab }) {
  const payload = tab.payload as QueryEditorPayload
  const editorRef = useRef<EditorView>()
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const connectionId = payload.connectionId ?? tab.connectionId ?? activeConnectionId ?? ''
  const source = payload.source ?? 'user'
  const isAiSource = source === 'ai'

  const { execute, result, risk, status, reset } = useSqlExecute()
  const { sendMessage } = useChannel()

  const handleRun = useCallback(() => {
    const sqlText = editorRef.current?.state.doc.toString() ?? ''
    if (!sqlText.trim() || !connectionId) return
    execute(sqlText, connectionId, source)
  }, [execute, connectionId, source])

  const handleSendToAi = useCallback(() => {
    const sqlText = editorRef.current?.state.doc.toString() ?? ''
    sendMessage([{
      type: 'text',
      text: `请帮我检查这段 SQL 是否安全，如果可以执行请帮我执行：\n\`\`\`sql\n${sqlText}\n\`\`\``,
    }])
    reset()
  }, [sendMessage, reset])

  return (
    <div className="flex h-full flex-col">
      {/* Toolbar */}
      <div className="flex shrink-0 items-center justify-between border-b border-border/50 px-3 py-2">
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          {isAiSource && (
            <span className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium text-purple-600 dark:bg-purple-900/30 dark:text-purple-400">
              AI 生成
            </span>
          )}
          <span>{connectionId || '未选择连接'}</span>
        </div>
        <Button
          size="sm"
          variant="default"
          disabled={status === 'running' || !connectionId}
          onClick={handleRun}
          className="h-7 gap-1.5 text-xs"
        >
          <PlayIcon className="size-3.5" />
          {isAiSource ? '直接执行' : 'Ctrl+Enter 运行'}
        </Button>
      </div>

      {/* SQL Editor */}
      <div className="min-h-0 flex-1 overflow-hidden border-b border-border/50">
        <SqlEditor initialValue={payload.sql ?? ''} editorRef={editorRef} onRun={handleRun} />
      </div>

      {/* Result panel */}
      <div className="min-h-0 flex-1 overflow-auto">
        {status === 'idle' && (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
            运行 SQL 后在此查看结果
          </div>
        )}
        {status === 'running' && (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
            执行中…
          </div>
        )}
        {status === 'success' && result && <ResultTable result={result} />}
        {status === 'risk_blocked' && risk && (
          <div className="flex h-full flex-col items-center justify-center gap-4 p-6">
            <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
              <AlertTriangleIcon className="size-5" />
              <span className="text-sm font-medium">高风险操作</span>
            </div>
            <p className="text-center text-sm text-muted-foreground">{risk.riskReason}</p>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={reset}>
                取消
              </Button>
              <Button size="sm" onClick={handleSendToAi}>
                发给 AI 审查 →
              </Button>
            </div>
          </div>
        )}
        {status === 'error' && (
          <div className="flex h-full items-center justify-center p-4 text-xs text-destructive">
            执行失败，请检查 SQL 语法
          </div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: 运行测试验证通过**

```bash
cd client && npx vitest run src/features/stage/components/query-editor-tab.test.tsx 2>&1 | tail -10
```

预期：4 tests PASSED。

- [ ] **Step 3: 类型检查**

```bash
cd client && npx tsc --noEmit
```

预期：无报错。

- [ ] **Step 4: Commit**

```bash
git add client/src/features/stage/components/query-editor-tab.tsx
git commit -m "feat(client): implement QueryEditorTab with CodeMirror and result panel"
```

---

## Task 8: stage-window 接通 store

**Files:**
- Modify: `client/src/features/stage/components/stage-window.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`

- [ ] **Step 1: 更新 stage-window.tsx**

完整替换文件内容：

```typescript
import type { ReactNode } from 'react'
import { SquareIcon, CopyIcon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useStageStore } from '@/stores/stage-store'
import { useActiveArtifactTitle } from '../use-active-artifact-title'
import { StageDock } from './stage-dock'
import { StageTabBar } from './stage-tab-bar'
import { StageTabContent } from './stage-tab-content'
import { useI18n } from '@/i18n/use-i18n'

type Props = {
  sessionId?: string
  children: ReactNode
}

export function StageWindow({ sessionId, children }: Props) {
  const { t } = useI18n()
  const closeStage = useStageStore((s) => s.closeStage)
  const maximized = useStageStore((s) => (sessionId ? !!s.maximizedBySession.get(sessionId) : false))
  const toggleMaximized = useStageStore((s) => s.toggleMaximized)
  const { Icon, label } = useActiveArtifactTitle(sessionId ?? '')

  const tabs = useStageStore((s) => (sessionId ? s.listTabs(sessionId) : []))
  const activeTabId = useStageStore((s) => {
    if (!sessionId) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sessionId) ?? null
  })
  const focusTab = useStageStore((s) => s.focusTab)
  const closeTab = useStageStore((s) => s.closeTab)

  function handleClose() {
    if (sessionId) closeStage(sessionId)
  }
  function handleToggleMaximized() {
    if (sessionId) toggleMaximized(sessionId)
  }

  const handleCloseTab = (tabId: string) => closeTab(tabId)
  const handleCloseOthers = (tabId: string) =>
    tabs.filter((t) => t.tabId !== tabId).forEach((t) => closeTab(t.tabId))
  const handleCloseAll = () => tabs.forEach((t) => closeTab(t.tabId))
  const handleCloseLeft = (tabId: string) => {
    const idx = tabs.findIndex((t) => t.tabId === tabId)
    tabs.slice(0, idx).forEach((t) => closeTab(t.tabId))
  }
  const handleCloseRight = (tabId: string) => {
    const idx = tabs.findIndex((t) => t.tabId === tabId)
    tabs.slice(idx + 1).forEach((t) => closeTab(t.tabId))
  }

  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-xl bg-background shadow-[0_16px_40px_rgb(0,0,0,0.12)] ring-1 ring-border/50 transition-all duration-200">
      <div className="flex flex-col bg-muted/30">
        {/* 标题栏 */}
        <div className="group flex h-10 shrink-0 select-none items-center justify-between">
          <div className="flex items-center gap-2 pl-3 pr-2">
            {Icon ? (
              <Icon className="size-4 text-primary" />
            ) : (
              <div className="size-2 rounded-full bg-primary" />
            )}
            <span className="text-xs font-medium tracking-wide text-foreground/80">
              {label || t('stage.workspace')}
            </span>
          </div>
          <div className="flex h-full items-center">
            <Button
              type="button"
              variant="ghost"
              className="h-full w-11 rounded-none text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:ring-0"
              aria-label={maximized ? t('stage.restore') : t('stage.maximize')}
              onClick={handleToggleMaximized}
            >
              {maximized ? (
                <CopyIcon className="size-4 rotate-180" strokeWidth={1.5} />
              ) : (
                <SquareIcon className="size-4" strokeWidth={1.5} />
              )}
            </Button>
            <Button
              type="button"
              variant="ghost"
              className="h-full w-11 rounded-none text-muted-foreground transition-colors hover:bg-[#e81123] hover:text-white focus-visible:ring-0"
              aria-label={t('stage.close')}
              onClick={handleClose}
            >
              <XIcon className="size-4" strokeWidth={1.5} />
            </Button>
          </div>
        </div>

        {/* 标签栏：仅在有 tab 时显示 */}
        {tabs.length > 0 && (
          <div
            onClick={(e) => {
              const target = e.target as HTMLElement
              const tabId = target.closest('[data-tab-id]')?.getAttribute('data-tab-id')
              if (tabId) focusTab(tabId)
            }}
          >
            <StageTabBar
              tabs={tabs.map((t) => ({ tabId: t.tabId, title: t.title, type: t.type }))}
              activeId={activeTabId ?? undefined}
              onClose={handleCloseTab}
              onCloseOthers={handleCloseOthers}
              onCloseAll={handleCloseAll}
              onCloseLeft={handleCloseLeft}
              onCloseRight={handleCloseRight}
            />
          </div>
        )}
      </div>

      {/* 内容区域 */}
      <div className="relative flex flex-1 min-h-0 flex-col overflow-hidden bg-background">
        <div className="mx-3 mt-4 mb-[110px] flex flex-1 min-h-0 flex-col overflow-hidden rounded-xl border border-border/60 bg-card shadow-[0_4px_20px_rgb(0,0,0,0.05),inset_0_1px_3px_rgb(0,0,0,0.02)] relative z-0">
          <div className="relative flex-1 overflow-auto">
            {activeTabId ? <StageTabContent /> : children}
          </div>
        </div>
        <div className="absolute bottom-8 left-1/2 z-10 -translate-x-1/2">
          <StageDock sessionId={sessionId} />
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: 更新 stage-window.test.tsx**

在现有测试文件中增加以下测试（保留已有测试，追加这两个）：

```typescript
// 在 describe 内追加
it('renders tab bar when store has tabs for session', () => {
  useStageStore.setState({
    tabsBySession: new Map([['s-1', [
      { tabId: 'q1', type: 'query_editor', title: 'SQL', scope: 'session',
        originSessionId: 's-1', createdAt: 0, payload: {} },
    ]]]),
    activeTabIdBySession: new Map([['s-1', 'q1']]),
  })
  render(<StageWindow sessionId="s-1"><div>ai content</div></StageWindow>)
  expect(screen.getByText('SQL')).toBeTruthy()
  expect(screen.queryByText('ai content')).toBeNull()  // replaced by StageTabContent
})

it('shows children when store has no tabs', () => {
  useStageStore.setState({
    tabsBySession: new Map(),
    activeTabIdBySession: new Map(),
  })
  render(<StageWindow sessionId="s-1"><div>ai content</div></StageWindow>)
  expect(screen.getByText('ai content')).toBeTruthy()
})
```

- [ ] **Step 3: 运行测试**

```bash
cd client && npx vitest run src/features/stage/components/stage-window.test.tsx 2>&1 | tail -15
```

预期：全部通过。

- [ ] **Step 4: 类型检查**

```bash
cd client && npx tsc --noEmit
```

- [ ] **Step 5: Commit**

```bash
git add client/src/features/stage/components/stage-window.tsx \
        client/src/features/stage/components/stage-window.test.tsx
git commit -m "feat(client): connect stage-window to store, remove mockTabs"
```

---

## Task 9: stage-tab-bar — X 按钮 + 右键菜单

**Files:**
- Modify: `client/src/features/stage/components/stage-tab-bar.tsx`

- [ ] **Step 1: 更新 stage-tab-bar.tsx**

完整替换文件内容：

```typescript
import { XIcon, SparklesIcon, DatabaseIcon, NetworkIcon } from 'lucide-react'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

type TabItem = {
  tabId: string
  title: string
  type?: string
}

type StageTabBarProps = {
  tabs: TabItem[]
  activeId?: string
  onClose?: (tabId: string) => void
  onCloseOthers?: (tabId: string) => void
  onCloseAll?: () => void
  onCloseLeft?: (tabId: string) => void
  onCloseRight?: (tabId: string) => void
}

function getTabIcon(type?: string, isActive?: boolean) {
  switch (type) {
    case 'sql':
    case 'query_editor':
      return (
        <DatabaseIcon
          className={cn('size-4 transition-colors', isActive ? 'text-blue-500' : 'text-muted-foreground')}
        />
      )
    case 'er':
    case 'er_canvas':
      return (
        <NetworkIcon
          className={cn('size-4 transition-colors', isActive ? 'text-emerald-500' : 'text-muted-foreground')}
        />
      )
    default:
      return (
        <SparklesIcon
          className={cn('size-4 transition-colors', isActive ? 'text-purple-500' : 'text-muted-foreground')}
        />
      )
  }
}

export function StageTabBar({
  tabs, activeId, onClose, onCloseOthers, onCloseAll, onCloseLeft, onCloseRight,
}: StageTabBarProps) {
  const { t } = useI18n()
  return (
    <div className="flex shrink-0 items-center bg-transparent px-3 py-1">
      <Tabs value={activeId} className="w-auto">
        <TabsList className="flex h-10 w-fit items-center justify-start rounded-lg bg-muted/60 p-1 ring-1 ring-border/20">
          {tabs.map((tab) => {
            const isActive = tab.tabId === activeId
            return (
              <ContextMenu key={tab.tabId}>
                <ContextMenuTrigger
                  render={
                    <div data-tab-id={tab.tabId}>
                      <TabsTrigger
                        value={tab.tabId}
                        className={cn(
                          'group relative flex h-8 items-center gap-2 rounded-md px-4 text-sm font-medium transition-all duration-200 ease-out select-none',
                          'data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=active]:ring-1 data-[state=active]:ring-border/50',
                          'data-[state=inactive]:text-muted-foreground hover:data-[state=inactive]:text-foreground',
                        )}
                      >
                        {getTabIcon(tab.type, isActive)}
                        <span>{tab.title}</span>
                        <div
                          role="button"
                          aria-label={t('stage.menu.close')}
                          className={cn(
                            'ml-1 flex size-[18px] items-center justify-center rounded-[4px] transition-all',
                            isActive
                              ? 'opacity-100 hover:bg-muted'
                              : 'opacity-0 group-hover:opacity-100 hover:bg-muted-foreground/10',
                          )}
                          onClick={(e) => {
                            e.stopPropagation()
                            onClose?.(tab.tabId)
                          }}
                        >
                          <XIcon className="size-3.5" />
                        </div>
                      </TabsTrigger>
                    </div>
                  }
                />
                <ContextMenuContent className="w-48 font-sans text-xs">
                  <ContextMenuItem onSelect={() => onClose?.(tab.tabId)}>
                    {t('stage.menu.close')}
                  </ContextMenuItem>
                  <ContextMenuSeparator />
                  <ContextMenuItem onSelect={() => onCloseOthers?.(tab.tabId)}>
                    {t('stage.menu.closeOthers')}
                  </ContextMenuItem>
                  <ContextMenuItem onSelect={() => onCloseAll?.()}>
                    {t('stage.menu.closeAll')}
                  </ContextMenuItem>
                  <ContextMenuSeparator />
                  <ContextMenuItem onSelect={() => onCloseLeft?.(tab.tabId)}>
                    {t('stage.menu.closeLeft')}
                  </ContextMenuItem>
                  <ContextMenuItem onSelect={() => onCloseRight?.(tab.tabId)}>
                    {t('stage.menu.closeRight')}
                  </ContextMenuItem>
                </ContextMenuContent>
              </ContextMenu>
            )
          })}
        </TabsList>
      </Tabs>
    </div>
  )
}
```

- [ ] **Step 2: 类型检查**

```bash
cd client && npx tsc --noEmit
```

预期：无报错。

- [ ] **Step 3: Commit**

```bash
git add client/src/features/stage/components/stage-tab-bar.tsx
git commit -m "feat(client): wire stage-tab-bar X button and context menu to store callbacks"
```

---

## Task 10: stage-dock SQL 按钮 + stage-tab-content 路由

**Files:**
- Modify: `client/src/features/stage/components/stage-dock.tsx`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`

- [ ] **Step 1: 更新 stage-dock.tsx**

```typescript
import { DatabaseIcon, NetworkIcon, PieChartIcon, LayoutDashboardIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'

type Props = { sessionId?: string }

export function StageDock({ sessionId }: Props) {
  const { t } = useI18n()
  const openTab = useStageStore((s) => s.openTab)
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)

  const handleOpenSqlEditor = () => {
    if (!sessionId) return
    openTab({
      tabId: `query_editor_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      type: 'query_editor',
      title: t('stage.dock.sql'),
      scope: 'session',
      originSessionId: sessionId,
      connectionId: activeConnectionId ?? undefined,
      payload: { sql: '', source: 'user' },
      createdAt: Date.now(),
    })
  }

  const tools = [
    { id: 'sql', name: t('stage.dock.sql'), icon: DatabaseIcon, color: 'text-blue-500', onClick: handleOpenSqlEditor },
    { id: 'er', name: t('stage.dock.er'), icon: NetworkIcon, color: 'text-emerald-500', onClick: undefined },
    { id: 'report', name: t('stage.dock.report'), icon: PieChartIcon, color: 'text-purple-500', onClick: undefined },
    { id: 'dashboard', name: t('stage.dock.dashboard'), icon: LayoutDashboardIcon, color: 'text-orange-500', onClick: undefined },
  ]

  return (
    <div className="flex items-center gap-2 rounded-2xl border border-border/50 bg-background/80 p-2 shadow-2xl backdrop-blur-md transition-all hover:bg-background/95">
      {tools.map((tool) => (
        <button
          key={tool.id}
          type="button"
          disabled={!tool.onClick}
          onClick={tool.onClick}
          className="group relative flex size-12 flex-col items-center justify-center rounded-xl transition-all duration-200 hover:-translate-y-2 hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:translate-y-0"
        >
          <tool.icon className={cn('size-6 transition-transform duration-200 group-hover:scale-110', tool.color)} />
          <span className="absolute -top-12 left-1/2 -translate-x-1/2 scale-0 whitespace-nowrap rounded-lg border border-border/50 bg-popover px-3 py-1.5 text-xs font-medium text-popover-foreground shadow-lg transition-all duration-200 group-hover:scale-100">
            {tool.name}
            <span className="absolute -bottom-1 left-1/2 -z-10 size-2 -translate-x-1/2 rotate-45 border-b border-r border-border/50 bg-popover" />
          </span>
        </button>
      ))}
    </div>
  )
}
```

- [ ] **Step 2: 更新 stage-tab-content.tsx**

```typescript
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { BangQueryTab } from './bang-query-tab'
import { QueryEditorTab } from './query-editor-tab'

export function StageTabContent() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const activeTabId = useStageStore((s) => {
    if (!sid) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sid) ?? s.activeWorkspaceTabId
  })
  const tab = useStageStore((s) => {
    if (!activeTabId) return null
    return (
      s.workspaceTabs.find((t) => t.tabId === activeTabId) ??
      (sid ? s.tabsBySession.get(sid)?.find((t) => t.tabId === activeTabId) : undefined) ??
      null
    )
  })

  if (!tab) return null
  switch (tab.type) {
    case 'bang_query':    return <BangQueryTab tabId={tab.tabId} />
    case 'query_editor':  return <QueryEditorTab tab={tab} />
    default:              return (
      <div className="p-4 text-xs text-muted-foreground">Unknown tab type: {tab.type}</div>
    )
  }
}
```

- [ ] **Step 3: 类型检查**

```bash
cd client && npx tsc --noEmit
```

预期：无报错。

- [ ] **Step 4: 全量前端测试**

```bash
cd client && npx vitest run 2>&1 | tail -20
```

预期：全部通过（或仅已有的已知失败，无新增失败）。

- [ ] **Step 5: Commit**

```bash
git add client/src/features/stage/components/stage-dock.tsx \
        client/src/features/stage/components/stage-tab-content.tsx
git commit -m "feat(client): wire stage-dock SQL button and add query_editor routing"
```

---

## Task 11: 全量验证

- [ ] **Step 1: 后端全量构建+测试**

```bash
cd server && mvn clean verify -q 2>&1 | tail -20
```

预期：BUILD SUCCESS。

- [ ] **Step 2: 前端全量类型检查**

```bash
cd client && npx tsc --noEmit
```

预期：无报错。

- [ ] **Step 3: 手工验收**

按以下顺序操作：
1. 启动后端：`cd server && mvn spring-boot:run -pl data-talk-adapter`
2. 启动前端：`cd client && npm run dev`
3. 打开 Stage → 点 Dock SQL 按钮 → 确认打开 query_editor tab
4. 输入 `SELECT 1` → Ctrl+Enter → 确认结果面板出现 1 行
5. 输入 `DELETE FROM some_table` → 运行 → 确认高风险警告出现
6. 点"发给 AI 审查" → 确认 chat 里出现消息
7. 关闭 tab 的 X 按钮 → 确认 tab 关闭，返回 AI 内容
8. 右键 tab → 确认菜单各项可点击

- [ ] **Step 4: 更新技术债务跟踪器**

在 `docs/exec-plans/tech-debt-tracker.md` 中：
- 将 TD-022 / TD-023 从"当前债务"移至"已清除"，清除日期填当天，清除方式填本计划名称
- 更新对应 exec-plans/index.md 将本计划从 Active 移至 Completed

- [ ] **Step 5: Final commit**

```bash
git add docs/exec-plans/tech-debt-tracker.md docs/exec-plans/index.md
git commit -m "docs: mark TD-022/TD-023 resolved after stage query editor implementation"
```
