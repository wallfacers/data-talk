# 后端开发指南

## 模块结构

```
server/
├── data-talk-domain/          # 领域模型 — 纯 Java，零框架依赖
├── data-talk-application/     # 应用服务 — 编排逻辑，定义接口
├── data-talk-infrastructure/  # 基础设施 — JDBC/HTTP 实现
└── data-talk-adapter/         # 适配层 — Spring Boot 装配
```

## 构建

```bash
cd server
mvn clean verify                        # 编译 + 全量测试
mvn test -pl data-talk-domain           # 仅 domain 单元测试
mvn verify -pl data-talk-adapter        # 含集成测试 (IT)
mvn spring-boot:run -pl data-talk-adapter  # 启动 (localhost:8080)
```

## 添加新 Action

1. 在 `data-talk-adapter/src/main/java/com/datatalk/adapter/actions/` 创建 Handler：

```java
@Component
@DataTalkAction(
    id = "my_action",
    executor = Executor.SERVER,
    description = "做某件事",
    produces = {"MyObjectType"},
    requiresConnection = true,
    timeoutMs = 30_000
)
public class MyActionHandler implements ActionHandler<MyInput, MyOutput> {
    // 实现 inputSchema(), outputSchema(), sideEffects(), inputType(), handle()
}
```

2. `ActionRegistry` 会在启动时自动扫描并注册，无需手动配置
3. `GET /api/actions` 可验证注册结果

## 添加新 ObjectType

1. 在 `data-talk-domain` 中实现 `ObjectType` 接口
2. 在 `data-talk-adapter` 中标注 `@Component` 注册为 Spring Bean
3. `OntologyRegistry` 自动收集，`GET /api/ontology` 可验证

## 数据库迁移

- Flyway 管理 SQLite 元数据库 Schema
- 迁移文件位于 `data-talk-infrastructure/src/main/resources/db/migration/`
- 命名规则：`V{n}__{description}.sql`
- 迁移后同步更新 `docs/generated/db-schema.md`

## 测试策略

| 层 | 测试类型 | 工具 | 位置 |
|----|---------|------|------|
| domain | 纯单元测试 | JUnit 5 + AssertJ | `data-talk-domain/src/test/` |
| application | 单元测试 + stub | JUnit 5 + AssertJ | `data-talk-application/src/test/` |
| infrastructure | 契约测试 | WireMock 3.x | `data-talk-infrastructure/src/test/` |
| adapter | 集成测试 | Spring Boot Test + WireMock | `data-talk-adapter/src/test/` |
| E2E | Smoke test | 全栈 + FakeOpenCodeServer | `EndToEndSmokeIT` |

## 关键 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/channel` | POST | Streamable HTTP — JSON-RPC 请求，响应可升级为 SSE |
| `/api/query` | POST | 直接 SQL 查询（遗留端点） |
| `/api/actions` | GET | 列出所有注册的 Action |
| `/api/ontology` | GET | 列出所有注册的 ObjectType |

## 配置

主配置文件：`data-talk-adapter/src/main/resources/application.yml`

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.*` | 演示用 H2 数据源 |
| `spring.sqlite-datasource.*` | SQLite 元数据库 |
| `datatalk.persistence.sqlite-path` | SQLite 文件路径 |
| `server.port` | 服务端口 (默认 8080) |

## 国际化约定

- `data-talk-adapter` 通过 `MessageSource` 和 `AcceptHeaderLocaleResolver` 解析 `Accept-Language`。
- application / adapter 层统一通过 `Translator` 读取 message key，避免直接写死用户可见文案。
- 默认会话标题、默认数据源名称、连接测试结果、异常消息、Action 描述都应走 message bundle。
- 历史持久化数据不做按 locale 回写；需要本地化的默认值在运行时生成。
