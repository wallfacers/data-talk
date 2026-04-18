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
- **Feature modules**: `features/chat`, `features/session`, `features/connection`, `features/workspace`, `features/data-grid`, `features/dashboard`
