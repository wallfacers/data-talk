# Architecture

## System Overview

```
┌──────────────┐    HTTP/SSE     ┌──────────────────┐   HTTP/SSE    ┌──────────────┐
│ Tauri Desktop │ ◄──────────► │  Spring Boot API  │ ◄──────────► │ OpenCode AI  │
│  (React 19)   │   JSON-RPC    │  (Java 21)        │              │  (external)   │
└──────────────┘               └──────────────────┘              └──────────────┘
                                       │
                                       ▼
                               ┌──────────────────┐
                               │   User Databases  │
                               │  MySQL/PG/H2/...  │
                               └──────────────────┘
```

- **Desktop**: renders UI, manages frontend state, communicates with backend via Streamable HTTP
- **Backend**: core business logic — session management, SQL execution, Action dispatch, OpenCode bridge
- **OpenCode**: AI reasoning service, provides conversation and tool-call capabilities over HTTP
- **User DBs**: backend connects to user-owned databases via dynamic JDBC connection pools

## Server Four-Module Layering (DDD)

```
adapter (adapter layer)
  │  depends on ↓
application (application layer)
  │  depends on ↓
infrastructure (infrastructure layer)
  │  depends on ↓
domain (domain layer)
```

### domain — Pure domain models, zero framework dependencies

| Package                     | Responsibility                                  |
|----------------------------|------------------------------------------------|
| `domain.action`            | `@DataTalkAction` annotation, `ActionHandler` contract, `Executor` enum, `OntologyEffect` |
| `domain.event`             | `DtEvent` sealed interface — 22 event types     |
| `domain.part`              | `Part` sealed interface — message content model (text/reasoning/tool/file/step) |
| `domain.ontology`          | `ObjectType` interface, `ObjectTypeDescriptor`   |
| `entity`                   | `Session`, `DbConnection`, `DbType`             |
| `valueobject`              | `QueryResult`                                   |
| `repository`               | Repository interfaces (SessionRepository, DbConnectionRepository, SqlExecutionRepository) |
| `exception`                | `SqlExecutionException`, `ConnectionNotFoundException` |

### application — Application services, orchestrate domain logic

| Package                     | Responsibility                                  |
|----------------------------|------------------------------------------------|
| `application.registry`     | `ActionRegistry` — scans `@DataTalkAction` beans and registers; `OntologyRegistry` — ObjectType registration |
| `application.session`      | `SessionBus` — 16ms delta coalescing + SSE push; `ActionDispatcher`; `PendingCallRegistry` (watchdog timeout) |
| `application.channel`      | `ChannelService` — JSON-RPC codec, request routing; `JsonRpcCodec` |
| `application.opencode`     | `OpenCodeGateway` — tool push + message forwarding; `OpenCodeEventTranslator`; `ToolCallBridge` |
| `application.persistence`  | Repository interfaces (SessionRepository, MessageRepository, ArtifactRepository, EventRepository, QueryResultRepository) |
| `service`                  | `QueryApplicationService` — SQL query orchestration |

### infrastructure — Technical implementations

| Package                     | Responsibility                                  |
|----------------------------|------------------------------------------------|
| `repository`               | `JdbcDbConnectionRepository`, `DynamicSqlExecutionRepository` |
| `infra.opencode`           | `OpenCodeHttpClient` — WebClient SSE streaming   |

### adapter — External interfaces & Spring wiring

| Package                     | Responsibility                                  |
|----------------------------|------------------------------------------------|
| `controller`               | `QueryController` (/api/query)                  |
| `adapter.channel`          | `ChannelController` (Streamable HTTP endpoint)   |
| `adapter.discovery`        | `DiscoveryController` (/api/actions, /api/ontology) |
| `adapter.actions`          | Concrete Action implementations (e.g. `DemoEchoAction`) |
| `config`                   | `CorsConfig`, `JdbcConfig`, `ApplicationServiceConfig`, `OpenCodeGatewayBeans` |
| `DataTalkApplication`      | Spring Boot entry point                          |

## Communication Protocol — Streamable HTTP

Client-to-backend communication uses the Streamable HTTP protocol:

1. Client sends `POST /api/channel` with a JSON-RPC request
2. Server response may upgrade to SSE stream (`Content-Type: text/event-stream`)
3. Server pushes `DtEvent` on the SSE stream (including `action.invoke` to trigger Action execution)
4. Client replies with a separate `POST` carrying `action_result`
5. Supports `Last-Event-ID` for reconnection with incremental catch-up

## Core Domain Concepts

### Action — AI-facing capability unit

```java
@DataTalkAction(id = "read_schema", executor = Executor.SERVER, ...)
public class ReadSchemaHandler implements ActionHandler<Input, Output> { ... }
```

- One Action = one `@DataTalkAction`-annotated Spring Bean
- `ActionRegistry` auto-scans and registers at startup
- Three `Executor` types: `SERVER` (backend), `CLIENT` (frontend), `AI` (OpenCode)
- `ActionDescriptor` extended with `riskLevel` and `category` for AI decision support
- Adding a capability = new Handler class, zero core code changes

### Ontology — Domain object type system

- `ObjectType` defines AI-perceivable domain objects (connections, sessions, query results, etc.)
- `OntologyRegistry` collects all ObjectType beans
- Actions may declare `OntologyEffect` (create/update/delete objects), triggering `ontology.updated` events

### SessionBus — Session event bus

- One `SessionBus` instance per session (managed by `SessionBusRegistry`)
- 16ms delta coalescing window, batch-pushes to `SseEmitterSubscriber`
- Persisted to SQLite `events` table for replay support

## Data Storage

- **Metadata DB**: SQLite — stores connections, sessions, artifacts, events, action_invocations, query_results
- **User DBs**: dynamic JDBC connections to MySQL/PostgreSQL/H2 etc. for user queries
- **AI Messages**: OpenCode is the authoritative persistence layer; DataTalk proxies via `GET /session/:id/message` for client access
- Schema management: Flyway (`V1__init.sql`)
- Password encryption: AES-GCM via `SecretVault`

## Frontend Architecture

See [docs/FRONTEND.md](docs/FRONTEND.md) for details.

- **Routing**: TanStack Router (file-based route generation)
- **Server state**: TanStack Query
- **Client state**: Zustand (per-feature store)
- **UI framework**: shadcn/ui + Tailwind CSS v4
- **Feature modules**: `features/chat`, `features/session`, `features/connection`, `features/workspace`, `features/data-grid`, `features/dashboard`, `features/stage`

### UI Object Protocol (Phase 1 — 2026-04-20)

StageWindow 已从单 Artifact 容器升级为 **AI 可操作的多 Tab 工作屏**，由 `client/src/services/ui-router/` 下的 `UIRouter` 单例支撑：

- **Tab 体系**：每个 Tab 是一个 `UIObject`，由类型化 Adapter 注册（`WorkspaceAdapter` / `ArtifactTabAdapter` / `BangQueryAdapter` / 未来的 `QueryEditorAdapter` 等）。Tab 分两类 scope：工具 Tab 工作台级（跨会话常驻，连接绑在 Tab 自身）、Artifact Tab 会话级（跟随 activeSessionId 投影）
- **AI 入口**：4 个 `Executor.CLIENT` Action（`datatalk.ui.read / patch / exec / list`）作为 OpenCode → 前端 `UIRouter` 的桥接；前端 `client/src/features/actions/ui-handlers.ts` 通过 `registerClientHandler` 把请求 forward 到 `uiRouter.handle()`；与 `PinArtifactAction` 的 CLIENT 分发模式完全同构
- **两条 SQL 路径**：
  - **展示路径**（AI `ui_exec(run_sql)` / 用户 `!sql`）—— 前端直接打 `POST /api/query`，结果写入 Tab；`ui_read('state')` 刻意不含 `rows` 字段，AI 只看到 `{columns, rowCount, durationMs}` 元数据。`/api/query` 在 `QueryApplicationService` 首行调 `SqlStatementGuard.assertSelectOnly`，与 AI `execute_sql` 共用同一白名单（仅 SELECT/WITH）
  - **分析路径**（AI `datatalk.execute_sql`）—— 结果以 Artifact 形式回流 AI 上下文；现有行为不变
- **用户 `!` 直查**：Composer 识别 `!select ...` / `!with ...` 前缀（正则 `/^(select|with)\b/i`），绕过 AI 直接调 util `openBangQueryTab` → 生成 `bang_query` Tab；其他 `!` 开头输入继续走 AI（兼容自然语言）

完整设计见 [docs/product-specs/2026-04-20-stage-ui-object-protocol-design.md](docs/product-specs/2026-04-20-stage-ui-object-protocol-design.md)，执行计划见 [docs/exec-plans/2026-04-20-stage-ui-object-protocol-plan.md](docs/exec-plans/2026-04-20-stage-ui-object-protocol-plan.md)。Phase 2（AI 展示路径 QueryEditor + Prompt 注入）待启动。
