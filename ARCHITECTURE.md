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
| `adapter.actions`          | Concrete Action implementations (e.g. `GetDataContextAction`, `ExecuteSqlAction`) |
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

StageWindow 已演进为 **AI 可操作的多 Tab 工作屏**，由 `client/src/services/ui-router/` 下的 `UIRouter` 单例与 `StageUIObjectRegistry` 共同支撑：

- **对象注册**：当前 registry 会注册 `workspace` 对象，以及当前工作台中的 `query_editor` Tab。对象实例通过 `useUIObjectRegistry()` 挂接到 `uiRouter`，并由 `uiRouter.setActiveTabIdProvider()` 解析 `target=active`
- **AI / CLIENT 入口**：4 个 `Executor.CLIENT` Action（`datatalk.ui.read / patch / exec / list`）作为 OpenCode → 前端 `UIRouter` 的桥接；前端 `client/src/features/actions/ui-handlers.ts` 通过 `registerClientHandler` 把请求统一 forward 到 `uiRouter.handle()`
- **workspace 动作面**：`WorkspaceAdapter` 负责 `open / close / focus / choose_connection`；其中 `choose_connection` 复用 Composer 的全局 chooser host，返回用户选择的数据源结果
- **query_editor 对象面**：`QueryEditorAdapter` 对外暴露 SQL 编辑器的 `state / actions / capabilities`，让 AI 与用户侧工作台共用同一套对象语义
- **两条 SQL 路径**：
  - **展示路径**（用户 `!select ...` / `!with ...`）—— 前端直接打 `POST /api/query`，结果打开到 Stage 的 `query_editor`；该路径不产生 assistant 回复，但会先在聊天区写入一条 synthetic user message，保证当前会话即时可见、刷新后不丢
  - **分析路径**（AI `datatalk.execute_sql`）—— 结果以 Artifact 形式回流 AI 上下文；现有行为保持不变
- **用户 `!` 直查门槛**：Composer 仅对 `!select ...` / `!with ...` 做 direct SQL 拦截；其他 `!xxx` 输入继续走 AI，兼容自然语言强调。后端 `/api/query` 在 `QueryApplicationService` 首行调用 `SqlStatementGuard.assertSelectOnly(...)`，与 `execute_sql` 共用 SELECT/WITH 白名单

完整设计见 [docs/product-specs/2026-04-20-stage-ui-object-protocol-design.md](docs/product-specs/2026-04-20-stage-ui-object-protocol-design.md)，执行计划见 [docs/exec-plans/2026-04-20-stage-ui-object-protocol-plan.md](docs/exec-plans/2026-04-20-stage-ui-object-protocol-plan.md)。后续对象契约收紧与编辑语义增强继续由 `Query Editor Object Actions` 主线推进。
