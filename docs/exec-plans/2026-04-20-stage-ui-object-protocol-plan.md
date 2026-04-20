# Stage UI Object Protocol — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 [Stage UI Object Protocol 设计文档](../product-specs/2026-04-20-stage-ui-object-protocol-design.md) 的 **Phase 1 MVP**——让用户可以在 Composer 输入 `!<sql>` 直查数据库，结果渲染到 StageWindow 的新 Tab 里，**完全不经过 AI 会话**；同时搭起 `UIRouter` 协议骨架和 4 个 `Executor.CLIENT` Action 桥接（P2 的 AI 展示路径直接复用）。

**Architecture:** 前端新增 `services/ui-router/`（从 open-db-studio 移植）承载 `UIObject` 协议；StageStore 从"单 Artifact 容器"扩展为"多 Tab 容器"（artifact Tab 会话级 + 工具 Tab 工作台级）；Composer 拦截 `!` 前缀调用 `POST /api/query`（新增 `SqlStatementGuard` 安全闸），结果写入 `bang_query` Tab。后端新增 `Category.UI` + 4 个 CLIENT Action（`datatalk.ui.read / patch / exec / list`），本 Phase 注册但 AI 不使用——纯粹验证协议管道。

**Tech Stack:** Java 21 / Spring Boot 3.5 / JUnit 5 / AssertJ · React 19 / TypeScript / Zustand / Vitest · 参考源 `/home/wushengzhou/workspace/github/open-db-studio/src/mcp/ui/`

> **执行纪律（CLAUDE.md 覆盖项）**
> - 每次编辑后端后 `cd server && mvn compile -q`；前端后 `cd client && npx tsc --noEmit`
> - **Commit 需要用户显式同意**——本计划的 `git commit` 步骤执行到时应先和用户确认
> - 多步骤改动已在设计 spec 对齐，本计划按任务顺序串行推进；任务内单测必须先写后过

---

## File Structure Map

### 后端新增 / 修改

| 路径 | 动作 | 职责 |
|------|------|------|
| `server/data-talk-domain/src/main/java/com/datatalk/domain/action/Category.java` | 修改 | 新增 `UI` 枚举值 |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiReadAction.java` | 新增 | `@DataTalkAction(id="datatalk.ui.read", executor=CLIENT)` shell |
| `.../UiPatchAction.java` | 新增 | 同上（patch） |
| `.../UiExecAction.java` | 新增 | 同上（exec，`timeoutMs = 30_000`） |
| `.../UiListAction.java` | 新增 | 同上（list） |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java` | 新增 | 4 个 Action 的注解 / executor / schema / Category.UI 断言（对齐 `PinArtifactActionTest`） |
| `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java` | 修改 | 注入 `SqlStatementGuard`，`executeQuery` 第一步校验 `assertSelectOnly(command.sql())` |
| `server/data-talk-adapter/src/main/java/com/datatalk/config/ApplicationServiceConfig.java` | 修改 | 传 `SqlStatementGuard` 到 `QueryApplicationService` 构造器 |
| `server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java` | 新增 | 非 SELECT/WITH 拒绝用例 |

### 前端新增 / 修改

| 路径 | 动作 | 职责 |
|------|------|------|
| `client/src/services/ui-router/types.ts` | 新增 | `UIObject / UIRequest / UIResponse / JsonPatchOp / PatchCapability / PatchResult / ExecResult / UIObjectInfo / ActionDef` |
| `client/src/services/ui-router/errors.ts` | 新增 | `patchError / execError` factory |
| `client/src/services/ui-router/jsonPatch.ts` | 新增 | 最小 `applyPatch`（add / remove / replace + `[name=X]` 寻址）；immer 风格返回新对象 |
| `client/src/services/ui-router/pathResolver.ts` | 新增 | `matchPathPattern(actualPath, pattern)` 用于 capability 校验 |
| `client/src/services/ui-router/UIRouter.ts` | 新增 | 单例 class：`registerInstance / unregisterInstance / handle / setActiveTabIdProvider` |
| `client/src/services/ui-router/useUIObjectRegistry.ts` | 新增 | React hook：`useEffect` 挂载注册、卸载反注册 |
| `client/src/services/ui-router/index.ts` | 新增 | 重导出 + `uiRouter` 单例 |
| `client/src/services/ui-router/__tests__/UIRouter.test.ts` | 新增 | 注册 / resolve target / patch capability / exec schema 校验 |
| `client/src/services/ui-router/__tests__/jsonPatch.test.ts` | 新增 | add/remove/replace + `[name=X]` |
| `client/src/services/ui-router/__tests__/pathResolver.test.ts` | 新增 | pattern 匹配矩阵 |
| `client/src/features/stage/adapters/WorkspaceAdapter.ts` | 新增 | open / close / focus + read('state' / 'actions') |
| `client/src/features/stage/adapters/BangQueryAdapter.ts` | 新增 | read / exec(rerun, focus, close) / patch(/pinned) |
| `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts` | 新增 | tab CRUD |
| `client/src/features/stage/adapters/__tests__/BangQueryAdapter.test.ts` | 新增 | read state 不含 rows / rerun / patch 只允许 /pinned |
| `client/src/features/actions/ui-handlers.ts` | 新增 | `registerClientHandler('datatalk.ui.{read,patch,exec,list}')` 桥接到 `uiRouter.handle()` |
| `client/src/features/actions/__tests__/ui-handlers.test.ts` | 新增 | 4 个 handler 正确转发 |
| `client/src/features/actions/client-handlers.ts` | 修改 | 新增 `import './ui-handlers'` 触发副作用注册 |
| `client/src/stores/stage-store.ts` | 修改 | 新增 tabs 数据：`workspaceTabs`、`tabsBySession`（artifact-type）、`activeTabIdBySession`、`activeWorkspaceTabId`；新增 CRUD：`openTab / closeTab / focusTab / listTabs` |
| `client/src/stores/stage-store.test.ts` | 修改 | 补 tab 增删改查用例 |
| `client/src/features/stage/components/stage-window.tsx` | 修改 | 顶部 tab strip（artifact+workspace 合并渲染），内容区按 `activeTabId` 分发（artifact / bang_query） |
| `client/src/features/stage/components/bang-query-tab.tsx` | 新增 | 头部（SQL 折叠 / 连接名 / 耗时 / 行数 / 重跑 / 关闭）+ 主体复用 `DataGrid` |
| `client/src/features/stage/utils/open-bang-query-tab.ts` | 新增 | 调 `executeQuery` → 成功时 `uiRouter.handle(ui_exec workspace.open bang_query)` 填充 Tab；失败 toast |
| `client/src/features/stage/utils/__tests__/open-bang-query-tab.test.ts` | 新增 | mock `executeQuery`：成功 / 失败两分支 |
| `client/src/features/session/prompt-composer.tsx` | 修改 | `submitText` 入口首行判断 `!` 前缀，调 `openBangQueryTab` |

---

## Task 1: 后端 —— `/api/query` 增加 `SqlStatementGuard`

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/config/ApplicationServiceConfig.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java`

> **Why：** 当前 `/api/query` 直接跳过 SQL 类型校验；`!` 通道复用此端点，必须应用与 `execute_sql` 同等的 SELECT/WITH 白名单，避免用户 `!update ...` 造成变更。

- [ ] **Step 1.1: 写失败测试**

Create `server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java`:

```java
package com.datatalk.service;

import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.domain.error.DataTalkException;
import com.datatalk.entity.DbConnection;
import com.datatalk.repository.DbConnectionRepository;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class QueryApplicationServiceTest {

    private DbConnectionRepository connRepo;
    private SqlExecutionRepository execRepo;
    private QueryApplicationService svc;

    @BeforeEach
    void setUp() {
        connRepo = mock(DbConnectionRepository.class);
        execRepo = mock(SqlExecutionRepository.class);
        svc = new QueryApplicationService(connRepo, execRepo, new SqlStatementGuard());
    }

    @Test
    void rejectsNonSelectStatements() {
        var cmd = new ExecuteSqlCommand("conn-1", "UPDATE users SET x=1");
        assertThatThrownBy(() -> svc.executeQuery(cmd))
            .isInstanceOf(DataTalkException.class)
            .hasMessageContaining("SELECT / WITH");
        verifyNoInteractions(connRepo, execRepo);
    }

    @Test
    void allowsSelect() {
        when(connRepo.findById("conn-1")).thenReturn(Optional.of(stubConn()));
        when(execRepo.execute(any(), eq("SELECT 1")))
            .thenReturn(new QueryResult(List.of("c"), List.of(), 5L, 0L));

        var resp = svc.executeQuery(new ExecuteSqlCommand("conn-1", "SELECT 1"));
        assertThat(resp.durationMs()).isEqualTo(5L);
    }

    private DbConnection stubConn() {
        // 最小可编译占位；保持字段集合与既有构造器一致
        return mock(DbConnection.class);
    }
}
```

- [ ] **Step 1.2: 确认测试失败**

Run: `cd server && mvn -pl data-talk-application test -Dtest=QueryApplicationServiceTest`

Expected: 编译失败 —— `QueryApplicationService` 构造器只接受 2 个参数。

- [ ] **Step 1.3: 改 `QueryApplicationService`**

Modify `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`:

```java
package com.datatalk.service;

import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.dto.QueryResponseDto;
import com.datatalk.entity.DbConnection;
import com.datatalk.exception.ConnectionNotFoundException;
import com.datatalk.repository.DbConnectionRepository;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;

public class QueryApplicationService {

    private final DbConnectionRepository connectionRepository;
    private final SqlExecutionRepository sqlExecutionRepository;
    private final SqlStatementGuard statementGuard;

    public QueryApplicationService(DbConnectionRepository connectionRepository,
                                   SqlExecutionRepository sqlExecutionRepository,
                                   SqlStatementGuard statementGuard) {
        this.connectionRepository = connectionRepository;
        this.sqlExecutionRepository = sqlExecutionRepository;
        this.statementGuard = statementGuard;
    }

    public QueryResponseDto executeQuery(ExecuteSqlCommand command) {
        statementGuard.assertSelectOnly(command.sql());
        DbConnection connection = connectionRepository.findById(command.connectionId())
                .orElseThrow(() -> new ConnectionNotFoundException(command.connectionId()));

        QueryResult result = sqlExecutionRepository.execute(connection, command.sql());

        return new QueryResponseDto(
                result.columns(),
                result.rows(),
                result.durationMs(),
                result.rowCount()
        );
    }
}
```

- [ ] **Step 1.4: 更新 `ApplicationServiceConfig`**

Modify `server/data-talk-adapter/src/main/java/com/datatalk/config/ApplicationServiceConfig.java`——找到 `queryApplicationService` @Bean 方法，注入 `SqlStatementGuard`：

```java
@Bean
public QueryApplicationService queryApplicationService(
        DbConnectionRepository connectionRepository,
        SqlExecutionRepository sqlExecutionRepository,
        SqlStatementGuard statementGuard) {
    return new QueryApplicationService(connectionRepository, sqlExecutionRepository, statementGuard);
}
```

（如果 bean 方法原签名不同，保持原有注入方式增补 `statementGuard` 参数即可。`SqlStatementGuard` 已是 `@Component`，Spring 自动装配。）

- [ ] **Step 1.5: 测试通过 + 全量编译**

Run: `cd server && mvn -pl data-talk-application test -Dtest=QueryApplicationServiceTest && mvn -q compile`

Expected: 2 tests passed, BUILD SUCCESS.

- [ ] **Step 1.6: Commit**（用户同意后）

```bash
git add server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java \
        server/data-talk-adapter/src/main/java/com/datatalk/config/ApplicationServiceConfig.java \
        server/data-talk-application/src/test/java/com/datatalk/service/QueryApplicationServiceTest.java
git commit -m "$(cat <<'EOF'
fix(server): apply SqlStatementGuard on /api/query direct path

/api/query previously skipped statement-type validation, which would have
allowed user-initiated ! direct queries to execute non-SELECT DML. Apply
the same SELECT/WITH whitelist enforced on the AI execute_sql Action.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 后端 —— `Category.UI` + 4 个 CLIENT Actions

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/action/Category.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiReadAction.java`
- Create: `.../UiPatchAction.java`, `.../UiExecAction.java`, `.../UiListAction.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java`

- [ ] **Step 2.1: 扩 `Category` 枚举**

Modify `server/data-talk-domain/src/main/java/com/datatalk/domain/action/Category.java`：在枚举最后添加 `UI`：

```java
public enum Category {
    METADATA,
    QUERY,
    MUTATION,
    ARTIFACT,
    DDL,
    QUESTION,
    UI,        // new: datatalk.ui.* actions that operate client-side UI objects
    MISC
}
```

- [ ] **Step 2.2: 写失败测试**

Create `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java`:

```java
package com.datatalk.adapter.actions;

import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiActionsTest {

    @Test
    void uiReadAction_declaresClientExecutor() {
        DataTalkAction ann = UiReadAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann).isNotNull();
        assertThat(ann.id()).isEqualTo("datatalk.ui.read");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
        assertThat(ann.category()).containsExactly(Category.UI);
        assertThat(ann.riskLevel()).containsExactly(RiskLevel.L1);
    }

    @Test
    void uiPatchAction_declaresClientExecutor() {
        DataTalkAction ann = UiPatchAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann.id()).isEqualTo("datatalk.ui.patch");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
    }

    @Test
    void uiExecAction_allowsLongerTimeout() {
        DataTalkAction ann = UiExecAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann.id()).isEqualTo("datatalk.ui.exec");
        assertThat(ann.timeoutMs()).isGreaterThanOrEqualTo(30_000);
    }

    @Test
    void uiListAction_declaresClientExecutor() {
        DataTalkAction ann = UiListAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann.id()).isEqualTo("datatalk.ui.list");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
    }
}
```

- [ ] **Step 2.3: 确认测试失败**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=UiActionsTest`

Expected: 4 个类不存在的编译错误。

- [ ] **Step 2.4: 实现 4 个 Action（参考 `PinArtifactAction`）**

Create `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiReadAction.java`:

```java
package com.datatalk.adapter.actions;

import com.datatalk.domain.action.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.ui.read",
    executor = Executor.CLIENT,
    description = "action.ui_read.description",
    timeoutMs = 3_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.UI }
)
public class UiReadAction implements ActionHandler<Map, Map> {
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("object"),
            "properties", Map.of(
                "object", Map.of("type", "string"),
                "target", Map.of("type", "string"),
                "mode",   Map.of("type", "string", "enum", List.of("state", "schema", "actions", "full"))
            ));
    }
    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object");
    }
    @Override public List<OntologyEffect> sideEffects() { return List.of(OntologyEffect.NONE); }
    @Override public Class<Map> inputType() { return Map.class; }
    @Override
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        throw new UnsupportedOperationException("datatalk.ui.read runs on client; dispatcher must not call handler");
    }
}
```

Create `UiPatchAction.java` —— 同构，差异点：
- `id = "datatalk.ui.patch"`
- `description = "action.ui_patch.description"`
- `inputSchema.required = List.of("object", "ops")`；properties 增加 `ops: {type: array}` 和 `reason: {type: string}`
- `sideEffects = List.of(OntologyEffect.NONE)`

Create `UiExecAction.java`:
- `id = "datatalk.ui.exec"`
- `description = "action.ui_exec.description"`
- `timeoutMs = 30_000`
- `inputSchema.required = List.of("object", "action")`；properties 增加 `action: string, params: object`

Create `UiListAction.java`:
- `id = "datatalk.ui.list"`
- `description = "action.ui_list.description"`
- `timeoutMs = 1_000`
- `inputSchema.properties = Map.of("filter", Map.of("type", "object"))`（无 required）

4 个类都在 `handle()` 抛 `UnsupportedOperationException`——与 `PinArtifactAction` 一致，真实逻辑由 `ActionDispatcher` 走 CLIENT 路由。

- [ ] **Step 2.5: 编译验证**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=UiActionsTest && mvn -q compile`

Expected: 4 tests passed, BUILD SUCCESS.

- [ ] **Step 2.6: 全量测试兜底**

Run: `cd server && mvn -q test`

Expected: BUILD SUCCESS（验证 `Category.UI` 新值未破坏既有 switch/test）。如果有 `switch (category)` 不穷举报错，按地址补 `case UI -> ...`。

- [ ] **Step 2.7: Commit**（用户同意后）

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/action/Category.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiReadAction.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiListAction.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/UiActionsTest.java
git commit -m "$(cat <<'EOF'
feat(server): add datatalk.ui.{read,patch,exec,list} CLIENT actions

Register 4 CLIENT-executor actions + new Category.UI enum value as the
backend half of the UI Object protocol port. Handlers throw since actual
logic lives in the frontend UIRouter; this mirrors the PinArtifactAction
pattern. No behavior change for AI yet—P2 enables the prompt.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 前端 —— `ui-router` 基础类型 + 错误工厂

**Files:**
- Create: `client/src/services/ui-router/types.ts`
- Create: `client/src/services/ui-router/errors.ts`

- [ ] **Step 3.1: 移植 `types.ts`**

Create `client/src/services/ui-router/types.ts`（参考 open-db-studio 同名文件）:

```ts
// UI Object Protocol — types
// Ported from /home/wushengzhou/workspace/github/open-db-studio/src/mcp/ui/types.ts

export interface JsonPatchOp {
  op: 'add' | 'remove' | 'replace'
  path: string
  value?: unknown
}

export interface UIRequest {
  tool: 'ui_read' | 'ui_patch' | 'ui_exec' | 'ui_list'
  object: string
  target: string
  payload: unknown
}

export interface UIResponse {
  data?: unknown
  error?: string
  status?: 'applied' | 'pending_confirm'
  confirm_id?: string
}

export interface PatchCapability {
  pathPattern: string
  ops: ('replace' | 'add' | 'remove')[]
  description?: string
  addressableBy?: string[]
}

export interface PatchResult {
  status: 'applied' | 'pending_confirm' | 'error'
  confirm_id?: string
  preview?: JsonPatchOp[]
  message?: string
}

export interface ExecResult {
  success: boolean
  data?: unknown
  error?: string
}

export interface JsonSchema {
  type: 'object'
  properties: Record<string, unknown>
  required?: string[]
}

export interface ActionDef {
  name: string
  description: string
  paramsSchema: JsonSchema
}

export interface UIObjectInfo {
  objectId: string
  type: string
  title: string
  connectionId?: string
  database?: string
}

export interface UIObject {
  type: string
  objectId: string
  title: string
  connectionId?: string
  database?: string
  tabId?: string
  patchCapabilities?: PatchCapability[]

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown
  patch(ops: JsonPatchOp[], reason?: string): PatchResult | Promise<PatchResult>
  exec(action: string, params?: unknown): ExecResult | Promise<ExecResult>
}
```

- [ ] **Step 3.2: 写 `errors.ts`**

Create `client/src/services/ui-router/errors.ts`:

```ts
import type { PatchResult, ExecResult } from './types'

export function patchError(message: string, ...hints: string[]): PatchResult {
  return {
    status: 'error',
    message: hints.length ? `${message}. ${hints.join(' ')}` : message,
  }
}

export function execError(error: string, ...hints: string[]): ExecResult {
  return {
    success: false,
    error: hints.length ? `${error}. ${hints.join(' ')}` : error,
  }
}
```

- [ ] **Step 3.3: Type-check**

Run: `cd client && npx tsc --noEmit`

Expected: 0 errors.

---

## Task 4: 前端 —— JSON Patch + pathResolver

**Files:**
- Create: `client/src/services/ui-router/jsonPatch.ts`
- Create: `client/src/services/ui-router/pathResolver.ts`
- Create: `client/src/services/ui-router/__tests__/jsonPatch.test.ts`
- Create: `client/src/services/ui-router/__tests__/pathResolver.test.ts`

- [ ] **Step 4.1: 写 jsonPatch 失败测试**

Create `client/src/services/ui-router/__tests__/jsonPatch.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { applyPatch } from '../jsonPatch'

describe('applyPatch', () => {
  it('replaces top-level field', () => {
    const state = { content: 'a', connectionId: 'c1' }
    const out = applyPatch(state, [{ op: 'replace', path: '/content', value: 'b' }])
    expect(out.content).toBe('b')
    expect(out.connectionId).toBe('c1')
    expect(state.content).toBe('a') // immutability
  })

  it('adds to array tail with /arr/-', () => {
    const state = { tags: ['a', 'b'] }
    const out = applyPatch(state, [{ op: 'add', path: '/tags/-', value: 'c' }])
    expect(out.tags).toEqual(['a', 'b', 'c'])
  })

  it('addresses array element by [name=X]', () => {
    const state = { columns: [{ name: 'id', dataType: 'BIGINT' }, { name: 'email', dataType: 'VARCHAR' }] }
    const out = applyPatch(state, [
      { op: 'replace', path: '/columns[name=email]/dataType', value: 'TEXT' },
    ])
    expect(out.columns[1].dataType).toBe('TEXT')
    expect(out.columns[0].dataType).toBe('BIGINT')
  })

  it('removes element', () => {
    const state = { tags: ['a', 'b', 'c'] }
    const out = applyPatch(state, [{ op: 'remove', path: '/tags/1' }])
    expect(out.tags).toEqual(['a', 'c'])
  })
})
```

- [ ] **Step 4.2: 运行测试确认失败**

Run: `cd client && npx vitest run src/services/ui-router/__tests__/jsonPatch.test.ts`

Expected: 文件不存在。

- [ ] **Step 4.3: 实现 jsonPatch.ts**

Create `client/src/services/ui-router/jsonPatch.ts`:

```ts
import type { JsonPatchOp } from './types'

// 最小 JSON Patch (RFC 6902) 子集：支持 add / remove / replace，带 [name=X] 寻址扩展。
// 返回新对象；不 mutate 入参。
export function applyPatch<T extends Record<string, unknown>>(state: T, ops: JsonPatchOp[]): T {
  let current = clone(state) as unknown
  for (const op of ops) {
    current = applyOne(current, op)
  }
  return current as T
}

function applyOne(root: unknown, op: JsonPatchOp): unknown {
  const segments = parsePath(op.path)
  return setAt(root, segments, op)
}

type Segment = { kind: 'key'; value: string } | { kind: 'index'; value: number } | { kind: 'tail' } | { kind: 'match'; key: string; value: string }

function parsePath(path: string): Segment[] {
  if (!path.startsWith('/')) throw new Error(`invalid json pointer: ${path}`)
  return path.slice(1).split('/').map((raw) => {
    if (raw === '-') return { kind: 'tail' } as Segment
    const m = raw.match(/^([^\[]+)\[(\w+)=([^\]]+)\]$/)
    if (m) {
      // e.g. "columns[name=email]" —— 先进 "columns"，再按 match 寻址
      // 这里 parsePath 只解析一段；调用点按顺序走两步
      // 简化：返回一个复合 segment，由 setAt 处理
      throw new Error('compound segment handled below')
    }
    if (/^\d+$/.test(raw)) return { kind: 'index', value: Number(raw) } as Segment
    return { kind: 'key', value: unescapePointer(raw) } as Segment
  })
}

function unescapePointer(s: string): string {
  return s.replace(/~1/g, '/').replace(/~0/g, '~')
}

function clone<T>(v: T): T {
  if (v === null || typeof v !== 'object') return v
  if (Array.isArray(v)) return v.map(clone) as unknown as T
  const out: Record<string, unknown> = {}
  for (const [k, val] of Object.entries(v as Record<string, unknown>)) out[k] = clone(val)
  return out as T
}

// 重新实现：支持 [name=X] 复合段
function setAt(root: unknown, _unused: Segment[], op: JsonPatchOp): unknown {
  const parts = op.path.slice(1).split('/')
  return walk(root, parts, 0, op)
}

function walk(node: unknown, parts: string[], i: number, op: JsonPatchOp): unknown {
  if (i === parts.length) {
    // 不应该到达（最后一段总由父节点处理）
    throw new Error('empty path')
  }
  const raw = parts[i]
  const isLast = i === parts.length - 1

  // [name=X] 复合段
  const matchKey = raw.match(/^([^\[]+)\[(\w+)=([^\]]+)\]$/)
  if (matchKey) {
    const [, arrKey, addrKey, addrValue] = matchKey
    const arr = (node as Record<string, unknown>)[arrKey]
    if (!Array.isArray(arr)) throw new Error(`${arrKey} is not an array`)
    const idx = arr.findIndex((el) => (el as Record<string, unknown>)[addrKey] === addrValue)
    if (idx < 0) {
      if (op.op === 'add' && isLast) {
        const nextArr = [...arr, { [addrKey]: addrValue, ...((op.value as object) ?? {}) }]
        return { ...(node as object), [arrKey]: nextArr }
      }
      throw new Error(`element ${addrKey}=${addrValue} not found in ${arrKey}`)
    }
    if (isLast) {
      if (op.op === 'remove') {
        const next = [...arr]; next.splice(idx, 1)
        return { ...(node as object), [arrKey]: next }
      }
      if (op.op === 'replace') {
        const next = [...arr]; next[idx] = op.value
        return { ...(node as object), [arrKey]: next }
      }
      // add 在命中时降级为 replace
      const next = [...arr]; next[idx] = { ...(arr[idx] as object), ...(op.value as object) }
      return { ...(node as object), [arrKey]: next }
    }
    const updated = walk(arr[idx], parts, i + 1, op)
    const next = [...arr]; next[idx] = updated
    return { ...(node as object), [arrKey]: next }
  }

  // tail: /arr/-
  if (raw === '-') {
    if (!Array.isArray(node)) throw new Error('tail on non-array')
    if (!isLast || op.op !== 'add') throw new Error('only add supports tail')
    return [...node, op.value]
  }

  // 数字索引
  if (/^\d+$/.test(raw)) {
    const idx = Number(raw)
    if (!Array.isArray(node)) throw new Error('index on non-array')
    if (isLast) {
      const next = [...node]
      if (op.op === 'remove') next.splice(idx, 1)
      else if (op.op === 'replace') next[idx] = op.value
      else next.splice(idx, 0, op.value) // add
      return next
    }
    const updated = walk(node[idx], parts, i + 1, op)
    const next = [...node]; next[idx] = updated
    return next
  }

  // 普通 key
  const key = unescapePointer(raw)
  const rec = node as Record<string, unknown>
  if (isLast) {
    const next = { ...rec }
    if (op.op === 'remove') delete next[key]
    else next[key] = op.value
    return next
  }
  const updated = walk(rec[key], parts, i + 1, op)
  return { ...rec, [key]: updated }
}
```

> 注意：parsePath / Segment 辅助类型保留备用但未被 walk 使用；可在后续清理中删除。Phase 1 优先走通，refactor 进 §9 技术债。

- [ ] **Step 4.4: jsonPatch 测试通过**

Run: `cd client && npx vitest run src/services/ui-router/__tests__/jsonPatch.test.ts`

Expected: 4 passed.

- [ ] **Step 4.5: 写 pathResolver 测试**

Create `client/src/services/ui-router/__tests__/pathResolver.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { matchPathPattern } from '../pathResolver'

describe('matchPathPattern', () => {
  it('matches exact path', () => {
    expect(matchPathPattern('/content', '/content')).toBe(true)
  })

  it('matches nested static path', () => {
    expect(matchPathPattern('/tables/users/dataType', '/tables/users/dataType')).toBe(true)
  })

  it('matches [id=X] segment against pattern wildcard', () => {
    expect(matchPathPattern('/tables[id=5]/columns[name=email]/dataType',
                             '/tables[id=<n>]/columns[name=<n>]/dataType')).toBe(true)
  })

  it('rejects mismatched tail', () => {
    expect(matchPathPattern('/content', '/database')).toBe(false)
  })

  it('rejects length mismatch', () => {
    expect(matchPathPattern('/content/foo', '/content')).toBe(false)
  })
})
```

- [ ] **Step 4.6: 实现 pathResolver.ts**

Create `client/src/services/ui-router/pathResolver.ts`:

```ts
// 判断实际 JSON Pointer 是否匹配 capability 声明的 pattern。
// pattern 使用 <n> 作为 [key=value] 的占位通配。
// 例：实际 "/tables[id=5]/dataType" 匹配 "/tables[id=<n>]/dataType"
export function matchPathPattern(actualPath: string, pattern: string): boolean {
  const a = actualPath.split('/')
  const b = pattern.split('/')
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    if (a[i] === b[i]) continue
    // 规范化 [key=value] 段：pattern 里的 <n> 通配具体值
    const aMatch = a[i].match(/^([^\[]+)\[(\w+)=([^\]]+)\]$/)
    const bMatch = b[i].match(/^([^\[]+)\[(\w+)=([^\]]+)\]$/)
    if (aMatch && bMatch && aMatch[1] === bMatch[1] && aMatch[2] === bMatch[2] && bMatch[3] === '<n>') {
      continue
    }
    return false
  }
  return true
}
```

- [ ] **Step 4.7: 测试通过 + type-check**

Run: `cd client && npx vitest run src/services/ui-router/__tests__ && npx tsc --noEmit`

Expected: 9 tests passed, 0 TS errors.

- [ ] **Step 4.8: Commit**（用户同意后）

```bash
git add client/src/services/ui-router/types.ts \
        client/src/services/ui-router/errors.ts \
        client/src/services/ui-router/jsonPatch.ts \
        client/src/services/ui-router/pathResolver.ts \
        client/src/services/ui-router/__tests__/
git commit -m "$(cat <<'EOF'
feat(client): ui-router core primitives (types / jsonPatch / pathResolver)

Port the JSON Patch subset + capability path matcher from open-db-studio
to serve as the foundation for the UI Object protocol. No integration
points yet — UIRouter and adapters land in subsequent commits.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 前端 —— `UIRouter` + `useUIObjectRegistry`

**Files:**
- Create: `client/src/services/ui-router/UIRouter.ts`
- Create: `client/src/services/ui-router/useUIObjectRegistry.ts`
- Create: `client/src/services/ui-router/index.ts`
- Create: `client/src/services/ui-router/__tests__/UIRouter.test.ts`

- [ ] **Step 5.1: 写失败测试**

Create `client/src/services/ui-router/__tests__/UIRouter.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { UIRouter } from '../UIRouter'
import type { UIObject, PatchCapability } from '../types'

function makeStub(objectId: string, opts: {
  stateValue?: unknown,
  actions?: unknown,
  patchCaps?: PatchCapability[],
} = {}): UIObject {
  return {
    type: 'query_editor',
    objectId,
    title: `Query ${objectId}`,
    patchCapabilities: opts.patchCaps,
    read: (mode) => {
      if (mode === 'state') return opts.stateValue ?? { content: '' }
      if (mode === 'actions') return opts.actions ?? []
      return {}
    },
    patch: async () => ({ status: 'applied' }),
    exec: async () => ({ success: true }),
  }
}

describe('UIRouter', () => {
  let router: UIRouter
  beforeEach(() => { router = new UIRouter() })

  it('resolves target by objectId', async () => {
    router.registerInstance('q1', makeStub('q1', { stateValue: { content: 'sql1' } }))
    const res = await router.handle({ tool: 'ui_read', object: 'query_editor', target: 'q1', payload: { mode: 'state' } })
    expect(res.data).toEqual({ content: 'sql1' })
  })

  it('returns error for unknown target', async () => {
    const res = await router.handle({ tool: 'ui_read', object: 'query_editor', target: 'nope', payload: { mode: 'state' } })
    expect(res.error).toContain('No query_editor')
  })

  it('resolves target=active via provider', async () => {
    router.registerInstance('q2', makeStub('q2', { stateValue: { content: 'active!' } }))
    router.setActiveTabIdProvider(() => 'q2')
    const res = await router.handle({ tool: 'ui_read', object: 'query_editor', target: 'active', payload: { mode: 'state' } })
    expect(res.data).toEqual({ content: 'active!' })
  })

  it('validates patch capability', async () => {
    router.registerInstance('q3', makeStub('q3', {
      patchCaps: [{ pathPattern: '/content', ops: ['replace'] }],
    }))
    const bad = await router.handle({ tool: 'ui_patch', object: 'query_editor', target: 'q3',
      payload: { ops: [{ op: 'replace', path: '/forbidden', value: 1 }] } })
    expect(bad.error).toContain('Unsupported')
  })

  it('validates exec action exists', async () => {
    router.registerInstance('q4', makeStub('q4', {
      actions: [{ name: 'run_sql', description: '', paramsSchema: { type: 'object', properties: {} } }],
    }))
    const bad = await router.handle({ tool: 'ui_exec', object: 'query_editor', target: 'q4',
      payload: { action: 'nuke', params: {} } })
    expect(bad.error).toContain('Unknown action')
  })

  it('ui_list filters by type', async () => {
    router.registerInstance('a', makeStub('a'))
    router.registerInstance('b', { ...makeStub('b'), type: 'artifact' })
    const res = await router.handle({ tool: 'ui_list', object: '', target: '', payload: { filter: { type: 'artifact' } } })
    expect((res.data as unknown[]).length).toBe(1)
  })
})
```

- [ ] **Step 5.2: 确认测试失败**

Run: `cd client && npx vitest run src/services/ui-router/__tests__/UIRouter.test.ts`

Expected: 文件不存在。

- [ ] **Step 5.3: 实现 UIRouter.ts**

Create `client/src/services/ui-router/UIRouter.ts`（直接移植 open-db-studio 同名文件，去掉 resolveTargetWithRetry 的异步轮询——data-talk Adapter 注册发生在组件 mount 时，不存在 MigrationJob 那种延迟场景）:

```ts
import type { UIObject, UIRequest, UIResponse, UIObjectInfo, ActionDef, PatchResult } from './types'
import { patchError, execError } from './errors'
import { matchPathPattern } from './pathResolver'

export class UIRouter {
  private instances = new Map<string, UIObject>()
  private _getActiveTabId: (() => string | null) | null = null

  setActiveTabIdProvider(fn: () => string | null) { this._getActiveTabId = fn }

  registerInstance(objectId: string, instance: UIObject) { this.instances.set(objectId, instance) }
  unregisterInstance(objectId: string) { this.instances.delete(objectId) }

  async handle(req: UIRequest): Promise<UIResponse> {
    if (req.tool === 'ui_list') {
      return this.handleList((req.payload as { filter?: ListFilter } | undefined)?.filter)
    }
    const instance = this.resolveTarget(req.object, req.target)
    if (!instance) return { error: `No ${req.object} found for target '${req.target}'` }

    try {
      switch (req.tool) {
        case 'ui_read': {
          const mode = (req.payload as { mode?: 'state' | 'schema' | 'actions' | 'full' } | undefined)?.mode ?? 'state'
          return { data: instance.read(mode) }
        }
        case 'ui_patch':
          return this.handlePatch(instance, req.payload)
        case 'ui_exec':
          return this.handleExec(instance, req.payload)
      }
    } catch (e) {
      return { error: String(e) }
    }
    return { error: `Unknown tool: ${req.tool}` }
  }

  private patchResponse(result: PatchResult): UIResponse {
    return {
      data: result,
      status: result.status === 'error' ? undefined : result.status,
      confirm_id: result.confirm_id,
      error: result.status === 'error' ? result.message : undefined,
    }
  }

  private async handlePatch(instance: UIObject, payload: unknown): Promise<UIResponse> {
    const p = (payload ?? {}) as { ops?: Array<{ op: 'add' | 'remove' | 'replace'; path: string; value?: unknown }>; reason?: string }
    const ops = p.ops ?? []
    const caps = instance.patchCapabilities
    if (!caps?.length) {
      const result = await instance.patch(ops, p.reason)
      return this.patchResponse(result)
    }
    for (const op of ops) {
      const match = caps.find((cap) => cap.ops.includes(op.op) && matchPathPattern(op.path, cap.pathPattern))
      if (!match) {
        const supported = caps.map((c) => `${c.ops.join('/')} ${c.pathPattern}`).join(', ')
        const err = patchError(`Unsupported: ${op.op} ${op.path}`, `Supported paths: [${supported}]`)
        return { error: err.message }
      }
    }
    const result = await instance.patch(ops, p.reason)
    return this.patchResponse(result)
  }

  private async handleExec(instance: UIObject, payload: unknown): Promise<UIResponse> {
    const p = (payload ?? {}) as { action?: string; params?: unknown }
    const action = p.action ?? ''
    const params = p.params

    const rawActions = instance.read('actions')
    if (Array.isArray(rawActions) && rawActions.length > 0) {
      const actions = rawActions as ActionDef[]
      const def = actions.find((a) => a.name === action)
      if (!def) {
        const available = actions.map((a) => a.name).join(', ')
        const err = execError(`Unknown action '${action}'`, `Available: [${available}]`)
        return { data: err, error: err.error }
      }
      const required = def.paramsSchema?.required ?? []
      const missing = required.filter((k) => (params as Record<string, unknown> | undefined)?.[k] === undefined)
      if (missing.length) {
        const err = execError(`Missing required params: ${missing.join(', ')}`, `Schema: ${JSON.stringify(def.paramsSchema)}`)
        return { data: err, error: err.error }
      }
    }

    const result = await instance.exec(action, params)
    return { data: result, error: result.success ? undefined : result.error }
  }

  private resolveTarget(objectType: string, target: string): UIObject | null {
    if (target && target !== 'active') return this.instances.get(target) ?? null
    const activeTabId = this._getActiveTabId?.()
    if (activeTabId) {
      const direct = this.instances.get(activeTabId)
      if (direct && (!objectType || direct.type === objectType)) return direct
      for (const [, obj] of this.instances) {
        if ((!objectType || obj.type === objectType) && obj.tabId === activeTabId) return obj
      }
    }
    for (const [, obj] of this.instances) {
      if (!objectType || obj.type === objectType) return obj
    }
    return null
  }

  private handleList(filter?: ListFilter): UIResponse {
    const results: UIObjectInfo[] = []
    for (const [, obj] of this.instances) {
      if (filter?.type && obj.type !== filter.type) continue
      if (filter?.connectionId != null && obj.connectionId !== filter.connectionId) continue
      if (filter?.keyword) {
        const hay = `${obj.title} ${obj.objectId}`.toLowerCase()
        if (!hay.includes(filter.keyword.toLowerCase())) continue
      }
      results.push({ objectId: obj.objectId, type: obj.type, title: obj.title, connectionId: obj.connectionId, database: obj.database })
    }
    return { data: results }
  }
}

type ListFilter = { type?: string; keyword?: string; connectionId?: string; database?: string }

export const uiRouter = new UIRouter()
```

- [ ] **Step 5.4: 实现 useUIObjectRegistry.ts**

Create `client/src/services/ui-router/useUIObjectRegistry.ts`:

```ts
import { useEffect } from 'react'
import { uiRouter } from './UIRouter'
import type { UIObject } from './types'

export function useUIObjectRegistry(instance: UIObject | null) {
  useEffect(() => {
    if (!instance) return
    uiRouter.registerInstance(instance.objectId, instance)
    return () => uiRouter.unregisterInstance(instance.objectId)
  }, [instance])
}
```

- [ ] **Step 5.5: 导出入口**

Create `client/src/services/ui-router/index.ts`:

```ts
export * from './types'
export * from './errors'
export { applyPatch } from './jsonPatch'
export { matchPathPattern } from './pathResolver'
export { UIRouter, uiRouter } from './UIRouter'
export { useUIObjectRegistry } from './useUIObjectRegistry'
```

- [ ] **Step 5.6: 测试 + type-check**

Run: `cd client && npx vitest run src/services/ui-router/__tests__/UIRouter.test.ts && npx tsc --noEmit`

Expected: 6 passed, 0 TS errors.

- [ ] **Step 5.7: Commit**（用户同意后）

```bash
git add client/src/services/ui-router/
git commit -m "$(cat <<'EOF'
feat(client): UIRouter singleton + useUIObjectRegistry hook

Ports the routing core from open-db-studio's src/mcp/ui/UIRouter.ts:
instance registry, target='active' resolution via injected provider,
patch capability whitelisting, exec action schema validation, ui_list
filtering. No adapters yet — those land next.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: 前端 —— StageStore 重构（多 Tab 模型）

**Files:**
- Modify: `client/src/stores/stage-store.ts`
- Modify: `client/src/stores/stage-store.test.ts`

> **Why：** Adapter 和 Composer 都需要 StageStore 暴露 tab CRUD；必须先把 store 扩开，后续代码才有依附点。保持既有 `openBySession/maximizedBySession/revealOrigin` 等 API 不变，向后兼容。

- [ ] **Step 6.1: 扩 StageStore 测试**

Modify `client/src/stores/stage-store.test.ts` —— 在既有测试后追加：

```ts
// ... 既有 imports + tests ...

describe('StageStore tabs', () => {
  beforeEach(() => { useStageStore.setState({
    workspaceTabs: [], tabsBySession: new Map(),
    activeTabIdBySession: new Map(), activeWorkspaceTabId: null,
  } as unknown as Record<string, unknown>) })

  it('openTab(workspace) adds to workspaceTabs and sets activeWorkspaceTabId', () => {
    useStageStore.getState().openTab({ tabId: 't1', type: 'bang_query', title: 'sql', scope: 'workspace', payload: {}, createdAt: 1 })
    expect(useStageStore.getState().workspaceTabs).toHaveLength(1)
    expect(useStageStore.getState().activeWorkspaceTabId).toBe('t1')
  })

  it('openTab(session) adds to tabsBySession and sets activeTabIdBySession', () => {
    useStageStore.getState().openTab({ tabId: 'a1', type: 'artifact', title: 'art', scope: 'session', originSessionId: 's1', payload: {}, createdAt: 1 })
    expect(useStageStore.getState().tabsBySession.get('s1')).toHaveLength(1)
    expect(useStageStore.getState().activeTabIdBySession.get('s1')).toBe('a1')
  })

  it('closeTab removes and clears active', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'bang_query', title: 'x', scope: 'workspace', payload: {}, createdAt: 1 })
    st.closeTab('t1')
    expect(useStageStore.getState().workspaceTabs).toHaveLength(0)
    expect(useStageStore.getState().activeWorkspaceTabId).toBeNull()
  })

  it('focusTab switches active', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'bang_query', title: 'x', scope: 'workspace', payload: {}, createdAt: 1 })
    st.openTab({ tabId: 't2', type: 'bang_query', title: 'y', scope: 'workspace', payload: {}, createdAt: 2 })
    expect(useStageStore.getState().activeWorkspaceTabId).toBe('t2')
    st.focusTab('t1')
    expect(useStageStore.getState().activeWorkspaceTabId).toBe('t1')
  })

  it('listTabs(sid) merges workspace + session tabs', () => {
    const st = useStageStore.getState()
    st.openTab({ tabId: 't1', type: 'bang_query', title: 'x', scope: 'workspace', payload: {}, createdAt: 1 })
    st.openTab({ tabId: 'a1', type: 'artifact', title: 'y', scope: 'session', originSessionId: 's1', payload: {}, createdAt: 2 })
    const merged = st.listTabs('s1')
    expect(merged.map(t => t.tabId).sort()).toEqual(['a1', 't1'])
  })
})
```

- [ ] **Step 6.2: 确认失败**

Run: `cd client && npx vitest run src/stores/stage-store.test.ts`

Expected: `openTab/closeTab/focusTab/listTabs` undefined 错误。

- [ ] **Step 6.3: 实现 StageStore 扩展**

Modify `client/src/stores/stage-store.ts`:

```ts
import { create } from 'zustand'

type RevealOrigin = { x: number; y: number }

export interface StageTab {
  tabId: string
  type: string
  title: string
  connectionId?: string
  database?: string
  schema?: string
  originSessionId?: string
  scope: 'session' | 'workspace'
  pinned?: boolean
  payload: unknown
  createdAt: number
}

type StageState = {
  openBySession: Map<string, boolean>
  autoOpenedSessions: Set<string>
  maximizedBySession: Map<string, boolean>
  revealOrigin: RevealOrigin | null

  workspaceTabs: StageTab[]
  tabsBySession: Map<string, StageTab[]>
  activeWorkspaceTabId: string | null
  activeTabIdBySession: Map<string, string | null>

  openStage: (sessionId: string) => void
  closeStage: (sessionId: string) => void
  toggleStage: (sessionId: string) => void
  toggleMaximized: (sessionId: string) => void
  setRevealOrigin: (origin: RevealOrigin | null) => void
  notifyArtifactArrived: (sessionId: string) => void
  syncCollapsed: (sessionId: string, collapsed: boolean) => void
  clear: (sessionId: string) => void

  // Tab CRUD（新）
  openTab: (tab: StageTab) => void
  closeTab: (tabId: string) => void
  focusTab: (tabId: string) => void
  listTabs: (sessionId: string | null) => StageTab[]
  updateTabPayload: (tabId: string, updater: (prev: unknown) => unknown) => void
}

export const useStageStore = create<StageState>((set, get) => ({
  openBySession: new Map(),
  autoOpenedSessions: new Set(),
  maximizedBySession: new Map(),
  revealOrigin: null,

  workspaceTabs: [],
  tabsBySession: new Map(),
  activeWorkspaceTabId: null,
  activeTabIdBySession: new Map(),

  openStage: (sid) => set((s) => { const m = new Map(s.openBySession); m.set(sid, true); return { openBySession: m } }),
  closeStage: (sid) => set((s) => { const m = new Map(s.openBySession); m.set(sid, false); const a = new Set(s.autoOpenedSessions); a.add(sid); return { openBySession: m, autoOpenedSessions: a } }),
  toggleStage: (sid) => { const cur = !!get().openBySession.get(sid); if (cur) get().closeStage(sid); else get().openStage(sid) },
  toggleMaximized: (sid) => set((s) => { const m = new Map(s.maximizedBySession); m.set(sid, !m.get(sid)); return { maximizedBySession: m } }),
  setRevealOrigin: (origin) => set({ revealOrigin: origin }),
  notifyArtifactArrived: (sid) => set((s) => {
    if (s.autoOpenedSessions.has(sid)) return s
    if (s.openBySession.get(sid)) return s
    const m = new Map(s.openBySession); m.set(sid, true)
    const a = new Set(s.autoOpenedSessions); a.add(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),
  syncCollapsed: (sid, collapsed) => set((s) => {
    const cur = s.openBySession.get(sid); const next = !collapsed
    if (cur === next) return s
    const m = new Map(s.openBySession); m.set(sid, next)
    if (collapsed) { const a = new Set(s.autoOpenedSessions); a.add(sid); return { openBySession: m, autoOpenedSessions: a } }
    return { openBySession: m }
  }),
  clear: (sid) => set((s) => {
    const openMap = new Map(s.openBySession); openMap.delete(sid)
    const a = new Set(s.autoOpenedSessions); a.delete(sid)
    const maxMap = new Map(s.maximizedBySession); maxMap.delete(sid)
    const ts = new Map(s.tabsBySession); ts.delete(sid)
    const ats = new Map(s.activeTabIdBySession); ats.delete(sid)
    return { openBySession: openMap, autoOpenedSessions: a, maximizedBySession: maxMap, tabsBySession: ts, activeTabIdBySession: ats }
  }),

  openTab: (tab) => set((s) => {
    if (tab.scope === 'workspace') {
      return { workspaceTabs: [...s.workspaceTabs, tab], activeWorkspaceTabId: tab.tabId }
    }
    const sid = tab.originSessionId
    if (!sid) throw new Error('session-scoped tab requires originSessionId')
    const existing = s.tabsBySession.get(sid) ?? []
    const next = new Map(s.tabsBySession); next.set(sid, [...existing, tab])
    const active = new Map(s.activeTabIdBySession); active.set(sid, tab.tabId)
    return { tabsBySession: next, activeTabIdBySession: active }
  }),

  closeTab: (tabId) => set((s) => {
    const wsIdx = s.workspaceTabs.findIndex((t) => t.tabId === tabId)
    if (wsIdx >= 0) {
      const next = [...s.workspaceTabs]; next.splice(wsIdx, 1)
      const newActive = s.activeWorkspaceTabId === tabId ? (next.length ? next[next.length - 1].tabId : null) : s.activeWorkspaceTabId
      return { workspaceTabs: next, activeWorkspaceTabId: newActive }
    }
    // 按 session 搜索
    for (const [sid, arr] of s.tabsBySession.entries()) {
      const i = arr.findIndex((t) => t.tabId === tabId)
      if (i < 0) continue
      const nextArr = [...arr]; nextArr.splice(i, 1)
      const nextMap = new Map(s.tabsBySession); nextMap.set(sid, nextArr)
      const activeMap = new Map(s.activeTabIdBySession)
      if (activeMap.get(sid) === tabId) activeMap.set(sid, nextArr.length ? nextArr[nextArr.length - 1].tabId : null)
      return { tabsBySession: nextMap, activeTabIdBySession: activeMap }
    }
    return s
  }),

  focusTab: (tabId) => set((s) => {
    if (s.workspaceTabs.some((t) => t.tabId === tabId)) {
      return { activeWorkspaceTabId: tabId }
    }
    for (const [sid, arr] of s.tabsBySession.entries()) {
      if (arr.some((t) => t.tabId === tabId)) {
        const map = new Map(s.activeTabIdBySession); map.set(sid, tabId)
        return { activeTabIdBySession: map }
      }
    }
    return s
  }),

  listTabs: (sid) => {
    const s = get()
    const sessionTabs = sid ? (s.tabsBySession.get(sid) ?? []) : []
    return [...s.workspaceTabs, ...sessionTabs]
  },

  updateTabPayload: (tabId, updater) => set((s) => {
    const wsIdx = s.workspaceTabs.findIndex((t) => t.tabId === tabId)
    if (wsIdx >= 0) {
      const next = [...s.workspaceTabs]
      next[wsIdx] = { ...next[wsIdx], payload: updater(next[wsIdx].payload) }
      return { workspaceTabs: next }
    }
    for (const [sid, arr] of s.tabsBySession.entries()) {
      const i = arr.findIndex((t) => t.tabId === tabId)
      if (i < 0) continue
      const nextArr = [...arr]; nextArr[i] = { ...nextArr[i], payload: updater(nextArr[i].payload) }
      const map = new Map(s.tabsBySession); map.set(sid, nextArr)
      return { tabsBySession: map }
    }
    return s
  }),
}))
```

- [ ] **Step 6.4: 测试通过 + type-check**

Run: `cd client && npx vitest run src/stores/stage-store.test.ts && npx tsc --noEmit`

Expected: 既有 + 5 new tests passed, 0 TS errors.

- [ ] **Step 6.5: Commit**（用户同意后）

```bash
git add client/src/stores/stage-store.ts client/src/stores/stage-store.test.ts
git commit -m "$(cat <<'EOF'
feat(client): extend StageStore with multi-tab model

Adds workspaceTabs (cross-session) + tabsBySession (per-session) +
activeTabIdBySession tracking. openTab / closeTab / focusTab /
listTabs / updateTabPayload methods. Legacy openStage / closeStage /
maximize / revealOrigin APIs unchanged for backward compat.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: 前端 —— `WorkspaceAdapter`

**Files:**
- Create: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Create: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`

- [ ] **Step 7.1: 写测试**

Create `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { WorkspaceAdapter } from '../WorkspaceAdapter'
import { useStageStore } from '@/stores/stage-store'

describe('WorkspaceAdapter', () => {
  beforeEach(() => {
    useStageStore.setState({
      workspaceTabs: [], tabsBySession: new Map(),
      activeWorkspaceTabId: null, activeTabIdBySession: new Map(),
    } as unknown as Record<string, unknown>)
  })

  it('exec open creates workspace tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', { type: 'bang_query', title: 'SELECT 1', connection_id: 'conn-1' })
    expect(res.success).toBe(true)
    const tabs = useStageStore.getState().workspaceTabs
    expect(tabs).toHaveLength(1)
    expect(tabs[0].type).toBe('bang_query')
    expect(tabs[0].connectionId).toBe('conn-1')
    expect(tabs[0].originSessionId).toBe('s1')
  })

  it('read state returns tabs + active', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    await adapter.exec('open', { type: 'bang_query', title: 'Q' })
    const state = adapter.read('state') as { tabs: unknown[]; activeTabId: string | null }
    expect(state.tabs.length).toBe(1)
  })

  it('exec close removes tab', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const opened = await adapter.exec('open', { type: 'bang_query', title: 'Q' })
    const tabId = (opened.data as { tabId: string }).tabId
    const closed = await adapter.exec('close', { target: tabId })
    expect(closed.success).toBe(true)
    expect(useStageStore.getState().workspaceTabs).toHaveLength(0)
  })

  it('rejects open without type', async () => {
    const adapter = new WorkspaceAdapter(() => 's1')
    const res = await adapter.exec('open', {})
    expect(res.success).toBe(false)
  })
})
```

- [ ] **Step 7.2: 实现 Adapter**

Create `client/src/features/stage/adapters/WorkspaceAdapter.ts`:

```ts
import type { UIObject, ActionDef, ExecResult, PatchResult } from '@/services/ui-router'
import { execError } from '@/services/ui-router'
import { useStageStore, type StageTab } from '@/stores/stage-store'

const ACTIONS: ActionDef[] = [
  { name: 'open', description: 'Open a new tab', paramsSchema: {
    type: 'object', required: ['type'],
    properties: {
      type: { type: 'string' },
      title: { type: 'string' },
      connection_id: { type: 'string' },
      database: { type: 'string' },
      payload: { type: 'object' },
    },
  } },
  { name: 'close', description: 'Close a tab', paramsSchema: {
    type: 'object', required: ['target'], properties: { target: { type: 'string' } },
  } },
  { name: 'focus', description: 'Focus a tab', paramsSchema: {
    type: 'object', required: ['target'], properties: { target: { type: 'string' } },
  } },
]

// workspace-level Tab 默认 scope 注册表：允许新增类型时不改 WorkspaceAdapter
const WORKSPACE_SCOPE_TYPES = new Set<string>(['bang_query', 'query_editor', 'er_canvas', 'markdown_note'])

export class WorkspaceAdapter implements UIObject {
  type = 'workspace'
  objectId = 'workspace'
  title = 'Workspace'

  constructor(private getSessionId: () => string | null) {}

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    switch (mode) {
      case 'state': {
        const sid = this.getSessionId()
        const tabs = useStageStore.getState().listTabs(sid)
        const activeTabId = sid
          ? useStageStore.getState().activeTabIdBySession.get(sid) ?? useStageStore.getState().activeWorkspaceTabId
          : useStageStore.getState().activeWorkspaceTabId
        return { tabs: tabs.map((t) => ({ tabId: t.tabId, type: t.type, title: t.title, connectionId: t.connectionId })), activeTabId }
      }
      case 'actions': return ACTIONS
      case 'schema': return { type: 'object', properties: { tabs: { type: 'array' }, activeTabId: { type: ['string', 'null'] } } }
      case 'full': return { state: this.read('state'), actions: ACTIONS, schema: this.read('schema') }
    }
  }

  patch(): PatchResult { return { status: 'error', message: 'workspace is read-only; use exec' } }

  async exec(action: string, params?: unknown): Promise<ExecResult> {
    const p = (params ?? {}) as { type?: string; title?: string; connection_id?: string; database?: string; payload?: unknown; target?: string }
    const store = useStageStore.getState()
    switch (action) {
      case 'open': {
        if (!p.type) return execError('Missing param: type')
        const sid = this.getSessionId()
        const tabId = `${p.type}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
        const scope: StageTab['scope'] = WORKSPACE_SCOPE_TYPES.has(p.type) ? 'workspace' : 'session'
        const tab: StageTab = {
          tabId, type: p.type, title: p.title ?? p.type, scope,
          connectionId: p.connection_id, database: p.database,
          originSessionId: sid ?? undefined,
          payload: p.payload ?? {}, createdAt: Date.now(),
        }
        if (scope === 'session' && !sid) return execError('Cannot open session-scoped tab without active session')
        store.openTab(tab)
        return { success: true, data: { tabId } }
      }
      case 'close': {
        if (!p.target) return execError('Missing param: target')
        store.closeTab(p.target)
        return { success: true }
      }
      case 'focus': {
        if (!p.target) return execError('Missing param: target')
        store.focusTab(p.target)
        return { success: true }
      }
      default: return execError(`Unknown action: ${action}`, `Available: [${ACTIONS.map((a) => a.name).join(', ')}]`)
    }
  }
}
```

- [ ] **Step 7.3: 测试通过 + type-check**

Run: `cd client && npx vitest run src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts && npx tsc --noEmit`

Expected: 4 passed, 0 TS errors.

- [ ] **Step 7.4: Commit**（用户同意后）

```bash
git add client/src/features/stage/adapters/WorkspaceAdapter.ts \
        client/src/features/stage/adapters/__tests__/WorkspaceAdapter.test.ts
git commit -m "feat(client): WorkspaceAdapter for UI Object protocol

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: 前端 —— `BangQueryAdapter`

**Files:**
- Create: `client/src/features/stage/adapters/BangQueryAdapter.ts`
- Create: `client/src/features/stage/adapters/__tests__/BangQueryAdapter.test.ts`

- [ ] **Step 8.1: 写测试**

Create `client/src/features/stage/adapters/__tests__/BangQueryAdapter.test.ts`:

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { BangQueryAdapter } from '../BangQueryAdapter'
import { useStageStore, type StageTab } from '@/stores/stage-store'

function makeTab(tabId: string): StageTab {
  return {
    tabId, type: 'bang_query', title: 'SELECT 1', scope: 'workspace',
    connectionId: 'conn-1', pinned: false,
    payload: { sql: 'SELECT 1', lastRun: { columns: ['c'], rowCount: 1, durationMs: 5, truncated: false }, rows: [{ c: 1 }] },
    createdAt: 1,
  }
}

describe('BangQueryAdapter', () => {
  beforeEach(() => {
    useStageStore.setState({ workspaceTabs: [], tabsBySession: new Map(), activeWorkspaceTabId: null, activeTabIdBySession: new Map() } as unknown as Record<string, unknown>)
    useStageStore.getState().openTab(makeTab('bq1'))
  })

  it('read state excludes rows, includes sql + lastRun', () => {
    const adapter = new BangQueryAdapter('bq1')
    const state = adapter.read('state') as Record<string, unknown>
    expect(state.sql).toBe('SELECT 1')
    expect(state.lastRun).toBeDefined()
    expect(state.rows).toBeUndefined()
    expect(state.connectionId).toBe('conn-1')
    expect(state.pinned).toBe(false)
  })

  it('patch allows /pinned only', async () => {
    const adapter = new BangQueryAdapter('bq1')
    const ok = await adapter.patch([{ op: 'replace', path: '/pinned', value: true }])
    expect(ok.status).toBe('applied')
    expect(useStageStore.getState().workspaceTabs[0].pinned).toBe(true)
  })

  it('exec rerun calls executeQuery + updates payload', async () => {
    const spy = vi.fn(async () => ({ columns: ['c'], rows: [{ c: 2 }], durationMs: 3, rowCount: 1 }))
    const adapter = new BangQueryAdapter('bq1', { executeQuery: spy })
    const res = await adapter.exec('rerun')
    expect(res.success).toBe(true)
    expect(spy).toHaveBeenCalledWith({ connectionId: 'conn-1', sql: 'SELECT 1' })
    const updated = useStageStore.getState().workspaceTabs[0].payload as { rows: unknown[] }
    expect(updated.rows).toEqual([{ c: 2 }])
  })

  it('exec close removes tab', async () => {
    const adapter = new BangQueryAdapter('bq1')
    await adapter.exec('close')
    expect(useStageStore.getState().workspaceTabs).toHaveLength(0)
  })
})
```

- [ ] **Step 8.2: 实现 Adapter**

Create `client/src/features/stage/adapters/BangQueryAdapter.ts`:

```ts
import type { UIObject, JsonPatchOp, PatchResult, ExecResult, ActionDef, PatchCapability } from '@/services/ui-router'
import { execError } from '@/services/ui-router'
import { applyPatch } from '@/services/ui-router'
import { useStageStore } from '@/stores/stage-store'
import { executeQuery as defaultExecuteQuery } from '@/services/api/query'

const CAPS: PatchCapability[] = [
  { pathPattern: '/pinned', ops: ['replace'], description: 'Pin or unpin this tab' },
]

const ACTIONS: ActionDef[] = [
  { name: 'rerun', description: 'Re-run the SQL', paramsSchema: { type: 'object', properties: {} } },
  { name: 'focus', description: 'Focus this tab', paramsSchema: { type: 'object', properties: {} } },
  { name: 'close', description: 'Close this tab', paramsSchema: { type: 'object', properties: {} } },
]

interface BangPayload {
  sql: string
  rows?: Array<Record<string, unknown>>
  lastRun?: { columns: string[]; rowCount: number; durationMs: number; truncated: boolean }
}

export class BangQueryAdapter implements UIObject {
  type = 'bang_query'
  objectId: string
  title: string
  connectionId?: string
  patchCapabilities = CAPS

  constructor(
    tabId: string,
    private deps: { executeQuery?: typeof defaultExecuteQuery } = {},
  ) {
    this.objectId = tabId
    const tab = useStageStore.getState().workspaceTabs.find((t) => t.tabId === tabId)
    this.title = tab?.title ?? 'Bang Query'
    this.connectionId = tab?.connectionId
  }

  private getTab() {
    return useStageStore.getState().workspaceTabs.find((t) => t.tabId === this.objectId)
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const tab = this.getTab()
    switch (mode) {
      case 'state': {
        const payload = (tab?.payload ?? {}) as BangPayload
        return {
          sql: payload.sql,
          connectionId: tab?.connectionId,
          database: tab?.database,
          schema: tab?.schema,
          lastRun: payload.lastRun,
          pinned: tab?.pinned ?? false,
          // 故意不暴露 rows：结果行不进 AI 上下文
        }
      }
      case 'actions': return ACTIONS
      case 'schema': return { type: 'object', properties: { sql: { type: 'string' }, pinned: { type: 'boolean' } }, patchCapabilities: CAPS }
      case 'full': return { state: this.read('state'), actions: ACTIONS, schema: this.read('schema') }
    }
  }

  patch(ops: JsonPatchOp[]): PatchResult {
    const tab = this.getTab()
    if (!tab) return { status: 'error', message: 'Tab not found' }
    try {
      const next = applyPatch({ pinned: tab.pinned ?? false }, ops)
      useStageStore.setState((s) => {
        const i = s.workspaceTabs.findIndex((t) => t.tabId === this.objectId)
        if (i < 0) return s
        const arr = [...s.workspaceTabs]
        arr[i] = { ...arr[i], pinned: next.pinned }
        return { workspaceTabs: arr }
      })
      return { status: 'applied' }
    } catch (e) {
      return { status: 'error', message: String(e) }
    }
  }

  async exec(action: string): Promise<ExecResult> {
    const tab = this.getTab()
    if (!tab) return execError('Tab not found')
    const store = useStageStore.getState()
    switch (action) {
      case 'rerun': {
        const payload = tab.payload as BangPayload
        if (!tab.connectionId) return execError('Tab has no connectionId')
        const run = this.deps.executeQuery ?? defaultExecuteQuery
        const result = await run({ connectionId: tab.connectionId, sql: payload.sql })
        store.updateTabPayload(this.objectId, () => ({
          sql: payload.sql,
          rows: result.rows,
          lastRun: { columns: result.columns, rowCount: result.rowCount, durationMs: result.durationMs, truncated: result.rows.length < result.rowCount },
        }))
        return { success: true, data: { rowCount: result.rowCount, durationMs: result.durationMs } }
      }
      case 'focus': store.focusTab(this.objectId); return { success: true }
      case 'close':  store.closeTab(this.objectId); return { success: true }
      default: return execError(`Unknown action: ${action}`)
    }
  }
}
```

- [ ] **Step 8.3: 测试通过 + type-check**

Run: `cd client && npx vitest run src/features/stage/adapters/__tests__/BangQueryAdapter.test.ts && npx tsc --noEmit`

Expected: 4 passed, 0 TS errors.

- [ ] **Step 8.4: Commit**（用户同意后）

```bash
git add client/src/features/stage/adapters/BangQueryAdapter.ts \
        client/src/features/stage/adapters/__tests__/BangQueryAdapter.test.ts
git commit -m "feat(client): BangQueryAdapter (read-only SQL result tab)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: 前端 —— `ui-handlers.ts` 桥接 CLIENT Action 到 UIRouter

**Files:**
- Create: `client/src/features/actions/ui-handlers.ts`
- Create: `client/src/features/actions/__tests__/ui-handlers.test.ts`
- Modify: `client/src/features/actions/client-handlers.ts`

- [ ] **Step 9.1: 写测试**

Create `client/src/features/actions/__tests__/ui-handlers.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { getClientHandler } from '../registry'
import { uiRouter } from '@/services/ui-router'
import type { UIObject } from '@/services/ui-router'
import '../ui-handlers'

function stubObject(objectId: string, stateValue: unknown): UIObject {
  return {
    type: 'stub', objectId, title: objectId,
    read: () => stateValue,
    patch: async () => ({ status: 'applied' }),
    exec: async () => ({ success: true, data: { ok: 1 } }),
  }
}

describe('ui-handlers', () => {
  beforeEach(() => {
    uiRouter.registerInstance('stub1', stubObject('stub1', { foo: 'bar' }))
  })

  it('ui_read handler returns router data', async () => {
    const h = getClientHandler('datatalk.ui.read')!
    const out = await h({ object: 'stub', target: 'stub1', mode: 'state' }, { sessionId: 's1' })
    expect(out).toEqual({ foo: 'bar' })
  })

  it('ui_exec handler returns data', async () => {
    const h = getClientHandler('datatalk.ui.exec')!
    const out = await h({ object: 'stub', target: 'stub1', action: 'x' }, { sessionId: 's1' })
    expect(out).toEqual({ success: true, data: { ok: 1 } })
  })

  it('ui_read on unknown target throws', async () => {
    const h = getClientHandler('datatalk.ui.read')!
    await expect(h({ object: 'stub', target: 'nope', mode: 'state' }, { sessionId: 's1' })).rejects.toThrow()
  })

  it('ui_list returns array', async () => {
    const h = getClientHandler('datatalk.ui.list')!
    const out = await h({ filter: { type: 'stub' } }, { sessionId: 's1' })
    expect(Array.isArray(out)).toBe(true)
  })
})
```

- [ ] **Step 9.2: 实现 ui-handlers.ts**

Create `client/src/features/actions/ui-handlers.ts`:

```ts
import { registerClientHandler } from './registry'
import { uiRouter } from '@/services/ui-router'
import type { UIRequest } from '@/services/ui-router'

type ReadInput = { object: string; target?: string; mode?: 'state' | 'schema' | 'actions' | 'full' }
type PatchInput = { object: string; target?: string; ops: unknown[]; reason?: string }
type ExecInput = { object: string; target?: string; action: string; params?: unknown }
type ListInput = { filter?: { type?: string; keyword?: string; connectionId?: string; database?: string } }

async function forward(req: UIRequest): Promise<unknown> {
  const resp = await uiRouter.handle(req)
  if (resp.error) throw new Error(resp.error)
  return resp.data
}

registerClientHandler('datatalk.ui.read', async (input) => {
  const i = input as ReadInput
  return forward({ tool: 'ui_read', object: i.object, target: i.target ?? 'active', payload: { mode: i.mode } })
})

registerClientHandler('datatalk.ui.patch', async (input) => {
  const i = input as PatchInput
  return forward({ tool: 'ui_patch', object: i.object, target: i.target ?? 'active', payload: { ops: i.ops, reason: i.reason } })
})

registerClientHandler('datatalk.ui.exec', async (input) => {
  const i = input as ExecInput
  return forward({ tool: 'ui_exec', object: i.object, target: i.target ?? 'active', payload: { action: i.action, params: i.params } })
})

registerClientHandler('datatalk.ui.list', async (input) => {
  const i = input as ListInput
  return forward({ tool: 'ui_list', object: '', target: '', payload: { filter: i.filter } })
})
```

- [ ] **Step 9.3: 让应用启动时触发注册**

Modify `client/src/features/actions/client-handlers.ts` —— 末尾追加一行：

```ts
// Trigger registration of datatalk.ui.* handlers
import './ui-handlers'
```

- [ ] **Step 9.4: 测试通过**

Run: `cd client && npx vitest run src/features/actions/__tests__/ui-handlers.test.ts && npx tsc --noEmit`

Expected: 4 passed, 0 TS errors.

- [ ] **Step 9.5: Commit**（用户同意后）

```bash
git add client/src/features/actions/ui-handlers.ts \
        client/src/features/actions/__tests__/ui-handlers.test.ts \
        client/src/features/actions/client-handlers.ts
git commit -m "feat(client): bridge datatalk.ui.* CLIENT actions to UIRouter

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: 前端 —— StageWindow 多 Tab 渲染

**Files:**
- Modify: `client/src/features/stage/components/stage-window.tsx`（补 tab strip + 内容分发；已有其它逻辑保留）
- Create: `client/src/features/stage/components/stage-tab-strip.tsx`
- Create: `client/src/features/stage/components/stage-tab-content.tsx`

> **前置阅读**：当前 `stage-window.tsx` 的 children 是外面传入的（split-view 传了 ArtifactTimelineStrip + ArtifactCanvas）。改造策略：**保留现有 children 兼容**，但当 `workspaceTabs.length > 0` 或 active tab 非 artifact 时，用新 StageTabStrip + StageTabContent 替代；纯 artifact 场景沿用旧路径，减少回归面。

- [ ] **Step 10.1: 先实现 StageTabStrip 组件**

Create `client/src/features/stage/components/stage-tab-strip.tsx`:

```tsx
import { XIcon } from 'lucide-react'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'

export function StageTabStrip() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const tabs = useStageStore((s) => s.listTabs(sid))
  const activeTabId = useStageStore((s) => {
    if (!sid) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sid) ?? s.activeWorkspaceTabId
  })
  const focusTab = useStageStore((s) => s.focusTab)
  const closeTab = useStageStore((s) => s.closeTab)

  if (tabs.length === 0) return null

  return (
    <div className="flex items-center gap-1 border-b px-2 py-1 overflow-x-auto">
      {tabs.map((t) => (
        <div
          key={t.tabId}
          role="tab"
          aria-selected={t.tabId === activeTabId}
          onClick={() => focusTab(t.tabId)}
          className={`flex items-center gap-1 px-2 py-1 text-xs rounded cursor-pointer transition-colors ${
            t.tabId === activeTabId ? 'bg-muted' : 'hover:bg-muted/50'
          }`}
        >
          <span className="truncate max-w-[200px]">{t.title}</span>
          <button
            type="button"
            aria-label="Close tab"
            className="opacity-50 hover:opacity-100"
            onClick={(e) => { e.stopPropagation(); closeTab(t.tabId) }}
          >
            <XIcon className="size-3" />
          </button>
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Step 10.2: StageTabContent 分发**

Create `client/src/features/stage/components/stage-tab-content.tsx`:

```tsx
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { BangQueryTab } from './bang-query-tab'

export function StageTabContent() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const activeTabId = useStageStore((s) => {
    if (!sid) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sid) ?? s.activeWorkspaceTabId
  })
  const tab = useStageStore((s) => {
    if (!activeTabId) return null
    return s.workspaceTabs.find((t) => t.tabId === activeTabId)
      ?? (sid ? s.tabsBySession.get(sid)?.find((t) => t.tabId === activeTabId) : undefined)
      ?? null
  })

  if (!tab) return null
  switch (tab.type) {
    case 'bang_query': return <BangQueryTab tabId={tab.tabId} />
    default: return <div className="p-4 text-xs text-muted-foreground">Unknown tab type: {tab.type}</div>
  }
}
```

- [ ] **Step 10.3: 集成到 split-view**

Modify `client/src/features/session/split-view.tsx` —— 原本 StageWindow 包裹 `<ArtifactTimelineStrip /><ArtifactCanvas />`。改为：

```tsx
// 在现有 imports 附近：
import { StageTabStrip } from '@/features/stage/components/stage-tab-strip'
import { StageTabContent } from '@/features/stage/components/stage-tab-content'
import { useStageStore } from '@/stores/stage-store'

// 在 return 的 stage 列内部：
<StageWindow sessionId={sid ?? undefined}>
  {sid && <ArtifactTimelineStrip />}
  <StageTabStrip />
  {useStageStore.getState().listTabs(sid ?? null).length > 0 ? (
    <div className="flex-1 min-h-0 overflow-hidden">
      <StageTabContent />
    </div>
  ) : (
    sid && (
      <div className="flex-1 min-h-0 overflow-hidden">
        <ArtifactCanvas />
      </div>
    )
  )}
</StageWindow>
```

> **注意**：上面 `useStageStore.getState()` 是同步快照读取 —— 由于 React 在 re-render 过程中不会重新订阅，这里需改成 `useStageStore((s) => s.listTabs(sid ?? null).length > 0)` 的 subscribed 方式才能在 tab 新增时触发 re-render。具体写法：

```tsx
const hasTabs = useStageStore((s) => {
  const sessionTabs = sid ? (s.tabsBySession.get(sid) ?? []) : []
  return s.workspaceTabs.length + sessionTabs.length > 0
})
```

用 `hasTabs` 替换 `useStageStore.getState().listTabs(...).length > 0`。

- [ ] **Step 10.4: type-check + 既有测试回归**

Run: `cd client && npx tsc --noEmit && npx vitest run`

Expected: 0 TS errors, 既有测试全通过（split-view.test.tsx 等不应受影响）。

- [ ] **Step 10.5: Commit**（用户同意后）

```bash
git add client/src/features/stage/components/stage-tab-strip.tsx \
        client/src/features/stage/components/stage-tab-content.tsx \
        client/src/features/session/split-view.tsx
git commit -m "feat(client): StageWindow tab strip + content dispatcher

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: 前端 —— `BangQueryTab` 组件

**Files:**
- Create: `client/src/features/stage/components/bang-query-tab.tsx`

- [ ] **Step 11.1: 实现组件**

Create `client/src/features/stage/components/bang-query-tab.tsx`:

```tsx
import { useState } from 'react'
import { RefreshCwIcon, XIcon } from 'lucide-react'
import { DataGrid } from '@/features/data-grid/components/data-grid'
import { Button } from '@/components/ui/button'
import { useStageStore } from '@/stores/stage-store'
import { BangQueryAdapter } from '@/features/stage/adapters/BangQueryAdapter'
import { showErrorToast, normalizeError } from '@/services/http-error'
import { useI18n } from '@/i18n/use-i18n'

interface BangPayload {
  sql: string
  rows?: Array<Record<string, unknown>>
  lastRun?: { columns: string[]; rowCount: number; durationMs: number; truncated: boolean }
}

export function BangQueryTab({ tabId }: { tabId: string }) {
  const { t } = useI18n()
  const tab = useStageStore((s) => s.workspaceTabs.find((x) => x.tabId === tabId))
  const [expanded, setExpanded] = useState(false)
  const [rerunning, setRerunning] = useState(false)
  if (!tab) return null

  const payload = tab.payload as BangPayload
  const rows = payload.rows ?? []
  const columns = (payload.lastRun?.columns ?? []).map((c) => ({ key: c, header: c }))

  const onRerun = async () => {
    setRerunning(true)
    try {
      const adapter = new BangQueryAdapter(tabId)
      const res = await adapter.exec('rerun')
      if (!res.success) showErrorToast(normalizeError(new Error(res.error ?? 'rerun failed')))
    } catch (err) {
      showErrorToast(normalizeError(err))
    } finally {
      setRerunning(false)
    }
  }
  const onClose = () => useStageStore.getState().closeTab(tabId)

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 border-b px-3 py-2 text-xs">
        <span className="rounded border px-1.5 font-mono text-[10px]">{t('bangQuery.label') /* "SQL · 直查" */}</span>
        <div
          className={`flex-1 min-w-0 font-mono text-xs ${expanded ? 'whitespace-pre-wrap' : 'truncate'}`}
          onClick={() => setExpanded((v) => !v)}
          role="button"
          tabIndex={0}
        >
          {payload.sql}
        </div>
        {payload.lastRun && (
          <>
            <span className="text-muted-foreground whitespace-nowrap">{payload.lastRun.rowCount} rows</span>
            <span className="text-muted-foreground whitespace-nowrap">{payload.lastRun.durationMs}ms</span>
          </>
        )}
        <Button size="icon-xs" variant="ghost" onClick={onRerun} disabled={rerunning} aria-label={t('bangQuery.rerun')}>
          <RefreshCwIcon className={`size-3.5 ${rerunning ? 'animate-spin' : ''}`} />
        </Button>
        <Button size="icon-xs" variant="ghost" onClick={onClose} aria-label={t('bangQuery.close')}>
          <XIcon className="size-3.5" />
        </Button>
      </div>
      <div className="flex-1 min-h-0 overflow-hidden">
        <DataGrid columns={columns} rows={rows} />
      </div>
    </div>
  )
}
```

- [ ] **Step 11.2: 添加 i18n 键**

Locate the i18n message files (likely `client/src/i18n/messages.*`)，然后对每种语言补齐：

```ts
'bangQuery.label': 'SQL · Direct',  // zh: 'SQL · 直查'
'bangQuery.rerun': 'Re-run',        // zh: '重跑'
'bangQuery.close': 'Close',         // zh: '关闭'
```

（如果当前 i18n 体系还未有此 keys，找已有一处 key（如 `dataGrid.noData`）参考文件位置和格式添加。）

- [ ] **Step 11.3: type-check + test**

Run: `cd client && npx tsc --noEmit && npx vitest run`

Expected: 0 TS errors, all tests pass.

- [ ] **Step 11.4: Commit**（用户同意后）

```bash
git add client/src/features/stage/components/bang-query-tab.tsx \
        client/src/i18n/  # 或实际 i18n 目录
git commit -m "feat(client): BangQueryTab component

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 12: 前端 —— `openBangQueryTab` util + Composer `!` 拦截

**Files:**
- Create: `client/src/features/stage/utils/open-bang-query-tab.ts`
- Create: `client/src/features/stage/utils/__tests__/open-bang-query-tab.test.ts`
- Modify: `client/src/features/session/prompt-composer.tsx`

- [ ] **Step 12.1: 写 util 测试**

Create `client/src/features/stage/utils/__tests__/open-bang-query-tab.test.ts`:

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { openBangQueryTab } from '../open-bang-query-tab'
import { useStageStore } from '@/stores/stage-store'

vi.mock('@/services/api/query', () => ({
  executeQuery: vi.fn(async (input: { sql: string }) => ({
    columns: ['c'], rows: [{ c: input.sql.length }], durationMs: 5, rowCount: 1,
  })),
}))

describe('openBangQueryTab', () => {
  beforeEach(() => {
    useStageStore.setState({ workspaceTabs: [], tabsBySession: new Map(), activeWorkspaceTabId: null, activeTabIdBySession: new Map(), openBySession: new Map(), autoOpenedSessions: new Set() } as unknown as Record<string, unknown>)
  })

  it('creates a bang_query tab on success and opens stage', async () => {
    await openBangQueryTab({ sessionId: 's1', connectionId: 'c1', sql: 'SELECT 1' })
    const tabs = useStageStore.getState().workspaceTabs
    expect(tabs).toHaveLength(1)
    expect(tabs[0].type).toBe('bang_query')
    expect(tabs[0].connectionId).toBe('c1')
    expect(useStageStore.getState().openBySession.get('s1')).toBe(true)
  })

  it('throws when no connectionId provided', async () => {
    await expect(openBangQueryTab({ sessionId: 's1', connectionId: null, sql: 'SELECT 1' })).rejects.toThrow()
  })
})
```

- [ ] **Step 12.2: 实现 util**

Create `client/src/features/stage/utils/open-bang-query-tab.ts`:

```ts
import { executeQuery } from '@/services/api/query'
import { useStageStore, type StageTab } from '@/stores/stage-store'

interface Args {
  sessionId: string | null
  connectionId: string | null
  sql: string
}

export async function openBangQueryTab({ sessionId, connectionId, sql }: Args): Promise<string> {
  if (!connectionId) throw new Error('No active connection — please select a data source')
  const result = await executeQuery({ connectionId, sql })
  const tabId = `bang_query_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const tab: StageTab = {
    tabId, type: 'bang_query', title: sql.length > 40 ? sql.slice(0, 40) + '…' : sql,
    scope: 'workspace', connectionId, originSessionId: sessionId ?? undefined,
    payload: {
      sql,
      rows: result.rows,
      lastRun: { columns: result.columns, rowCount: result.rowCount, durationMs: result.durationMs, truncated: result.rows.length < result.rowCount },
    },
    createdAt: Date.now(),
  }
  const store = useStageStore.getState()
  store.openTab(tab)
  if (sessionId) store.openStage(sessionId)
  return tabId
}
```

- [ ] **Step 12.3: util 测试通过**

Run: `cd client && npx vitest run src/features/stage/utils/__tests__/open-bang-query-tab.test.ts`

Expected: 2 passed.

- [ ] **Step 12.4: Composer 拦截**

Modify `client/src/features/session/prompt-composer.tsx` —— 在 `submitText` 函数顶部（`if (!t || isStreaming) return` 之后）加入 `!` 分支：

```tsx
// 新增 imports
import { openBangQueryTab } from '@/features/stage/utils/open-bang-query-tab'
import { useSessionStore } from '@/stores/session-store'

// 新增：订阅当前会话的 connectionId（从 sessionInfo）
const sessionConnectionId = useSessionStore((s) => {
  if (!s.activeSessionId) return null
  return s.sessionInfoById.get(s.activeSessionId)?.connectionId ?? null
})
// ↑ 如果 session-store 未暴露 sessionInfoById，退而求其次：
// const sessionConnectionId = useConnectionStore((s) => s.activeConnectionId)

// 在 submitText 内部，`if (!t || isStreaming) return` 之后：
if (t.startsWith('!')) {
  const sql = t.slice(1).trim()
  if (!sql) return
  // 放宽：仅允许 select/with 前缀走直查；其他 ! 开头内容仍发 AI（兼容真有 ! 开头的自然语言）
  if (!/^(select|with)\b/i.test(sql)) {
    // 继续走普通发送
  } else {
    try {
      setText('')
      await openBangQueryTab({
        sessionId: activeSessionId,
        connectionId: sessionConnectionId ?? activeConnectionId,
        sql,
      })
    } catch (err) {
      showErrorToast(normalizeError(err))
      setText(t) // 失败时还原用户输入
    }
    return
  }
}
```

> **Why 这条前缀门槛**：`!sql` 直查要求前缀必须是 SELECT/WITH；否则交给 AI 处理（兼容用户用 `!` 作为自然语言强调）。后端 `SqlStatementGuard` 做最终兜底。

- [ ] **Step 12.5: type-check + 手动联调**

Run: `cd client && npx tsc --noEmit`

Expected: 0 errors.

**手动联调脚本**（需要后端运行 + 一个可连接的数据源）：

1. `cd server && mvn spring-boot:run -pl data-talk-adapter`
2. `cd client && npm run dev`
3. 浏览器打开前端，选择 / 创建一个测试数据源，进入某会话
4. 在 Composer 输入 `!select 1 as hello`，回车
5. 断言：
   - Stage 右栏自动弹开
   - 新 Tab 标题 `select 1 as hello`
   - 表格显示 `hello = 1`、耗时、1 row
   - 打开后端日志：无 `execute_sql` action 调用，只有 `POST /api/query`
   - 在聊天区无新 user / assistant 消息（验证不走 AI）
6. 输入 `!drop table users`，应收到错误 toast：`SELECT / WITH allowed, got: DROP`
7. 输入 `！你好`（全角感叹号）或 `!你好`：正则未匹配 select/with，走 AI 聊天（旧行为）
8. 点 Tab 关闭 X，Tab 消失；多次 `!select 1` 创建多个 Tab，切换焦点表现正常

- [ ] **Step 12.6: Commit**（用户同意后）

```bash
git add client/src/features/stage/utils/open-bang-query-tab.ts \
        client/src/features/stage/utils/__tests__/open-bang-query-tab.test.ts \
        client/src/features/session/prompt-composer.tsx
git commit -m "$(cat <<'EOF'
feat(client): ! direct-query intercept in Composer

Typing '!select ...' bypasses AI entirely: POST /api/query directly,
result rendered in a new bang_query Tab in StageWindow. Guards:
SELECT/WITH prefix + server-side SqlStatementGuard. ! prefix without
SELECT/WITH still routes to AI (compat with natural language use).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: 验收 + 索引 housekeeping

- [ ] **Step 13.1: 全量测试闸门**

Run 并全部通过：

```bash
cd server && mvn clean verify
cd client && npx tsc --noEmit && npx vitest run
```

Expected: BUILD SUCCESS + 所有前端测试 pass + 0 TS errors。

- [ ] **Step 13.2: 登记到 exec-plans 活跃表**

Modify `docs/exec-plans/index.md` —— 在「活跃计划」表内追加一行：

```markdown
| [Stage UI Object Protocol Phase 1](./2026-04-20-stage-ui-object-protocol-plan.md) | in_progress | 前端移植 UIRouter + 4 个 CLIENT Action 桥接；StageStore 多 Tab 模型；用户 `!sql` 直查通道落地（bang_query Tab，不走 AI）；后端 `/api/query` 补 SqlStatementGuard |
```

执行完成后移入「已完成计划」表，更新时间为 `YYYY-MM-DD`。

- [ ] **Step 13.3: 更新设计 spec 索引状态**

完工时无需改动（spec 无 in_progress/completed 列），只在 plan 侧维护状态即可。

- [ ] **Step 13.4: 同步更新 ARCHITECTURE.md**

Modify `ARCHITECTURE.md` —— 在"Frontend Architecture"或"Core Domain Concepts"节下增补一段：

```markdown
### UI Object Protocol (Phase 1)

StageWindow now renders a multi-tab workspace backed by `UIRouter`
(client/src/services/ui-router/). Tabs are either session-scoped
(artifact) or workspace-scoped (bang_query, future query_editor / …).
AI agents can discover and operate tabs via 4 CLIENT-executor actions:
`datatalk.ui.{read, patch, exec, list}`. Users can bypass AI entirely
by typing `!<select-sql>` in Composer — the query hits `/api/query`
directly and populates a bang_query tab; results never feed back into
the AI conversation context. See docs/product-specs/2026-04-20-stage-ui-object-protocol-design.md.
```

- [ ] **Step 13.5: 完工 Commit**（用户同意后）

```bash
git add docs/exec-plans/index.md ARCHITECTURE.md
git commit -m "$(cat <<'EOF'
docs: register Stage UI Object Protocol Phase 1 plan + ARCHITECTURE note

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 13.6: Phase 1 完工切换状态**

手动验收完成后，编辑 `docs/exec-plans/index.md` —— 把 Stage UI Object Protocol Phase 1 条目从「活跃计划」移到「已完成计划」，`in_progress` 改为完工日期。

---

## Self-Review 备注

**Spec 覆盖**：
- ✅ §3（协议移植）→ Task 3-5 + Task 9
- ✅ §4.1 WorkspaceAdapter → Task 7；BangQueryAdapter → Task 8；ArtifactTabAdapter / QueryEditorAdapter → P2（本 plan 不含）
- ✅ §5（后端桥接）→ Task 2
- ✅ §6（`!` 流程）→ Task 1（安全闸）+ Task 12（拦截）
- ✅ §7（Prompt 改造）→ **P2，本 plan 明确不涉及**
- ✅ §8.1 验收项 → Task 12.5 手动联调脚本
- ✅ StageWindow 多 Tab UI → Task 10 + Task 11

**Placeholder 扫描**：无 TBD / TODO / 占位符残留（Task 11.2 的 i18n key 位置给了定位指引而非 TBD）。

**Type 一致性**：`StageTab` 在 Task 6 定义，后续 Task 7/8/12 引用签名一致；`BangPayload` 在 Task 8 / 11 / 12 三处出现，字段 `{sql, rows?, lastRun?}` 统一。`executeQuery` 签名在 Task 12 / 8 均为 `{connectionId, sql}` → `{columns, rows, durationMs, rowCount}`，与既有 `client/src/services/api/query.ts` 一致。

**风险缓冲**：
- Task 6 StageStore 重构对既有 `split-view.tsx` 渲染路径的影响，Task 10 采用"兼容回退"策略（无 tab 时走旧 ArtifactCanvas）降低回归面
- Task 12.4 对 `sessionConnectionId` 的订阅源给了 fallback（`useConnectionStore.activeConnectionId`）兼容两种可能的 session-store 形态
- Task 2 的 `Category.UI` 新增可能触发既有 `switch (category)` 不穷举，Task 2.6 用 `mvn test` 兜底捕获
