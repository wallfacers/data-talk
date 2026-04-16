# 设计模式与编码约定

## 架构模式

### DDD 四层分层

依赖方向：`adapter → infrastructure → application → domain`。内层不可引用外层。

- **domain**：纯 Java，零框架依赖。使用 sealed interface（`DtEvent`, `Part`）保证类型安全
- **application**：编排领域逻辑，定义仓储接口。依赖 domain，不依赖具体数据库或 HTTP 实现
- **infrastructure**：JdbcTemplate 实现仓储，WebClient 实现 HTTP 调用
- **adapter**：Spring Boot 装配层 — 控制器、配置类、启动类

### Action Registry 模式

面向 AI 的能力统一走注册表，不允许后门。扩展公式：

```
新能力 = 1 个 @DataTalkAction Handler + 1 个 JSON Schema + 0 处核心代码改动
```

Handler 实现 `ActionHandler<I, O>` 接口，声明：
- `inputSchema()` / `outputSchema()`：JSON Schema 定义输入输出
- `sideEffects()`：`OntologyEffect` 列表（create/update/delete 哪些 ObjectType）
- `handle(ctx, input)`：返回 `CompletionStage<O>`，支持异步执行

### 事件溯源 (Event-Sourced Broadcast)

SessionBus 收集事件 → 16ms 窗口合并 → 批量推送 SSE + 持久化到 events 表。

## 编码约定

### Java (后端)

- Java 21，优先使用 record、sealed interface、pattern matching
- 避免 Lombok — record 已足够
- 方法粒度：public 方法 ≤ 20 行，否则拆分 private 方法
- 测试命名：`方法名_场景_期望结果`（如 `dispatch_unknownAction_returnsError`）
- 包可见性优先，仅必要时 public
- 异步使用 `CompletionStage`，不用 `CompletableFuture.get()` 阻塞

### TypeScript (前端)

- 严格模式（`strict: true`）
- Zod 做运行时校验，类型从 Schema 推导
- 组件文件名 kebab-case（`chat-input.tsx`）
- 一个 feature = 一个目录（`components/`, `hooks/`, `store.ts`, `types.ts`）
- 状态管理：服务端状态用 TanStack Query，客户端状态用 Zustand

### 命名约定

| 层 | 类后缀 | 示例 |
|----|--------|------|
| domain 实体 | 无后缀 | `Session`, `DbConnection` |
| domain 值对象 | 无后缀 | `QueryResult` |
| domain 接口 | 无后缀 | `ActionHandler`, `ObjectType` |
| application 服务 | Service / Registry / Bus | `ChannelService`, `ActionRegistry`, `SessionBus` |
| infrastructure 实现 | Jdbc / Http 前缀 | `JdbcDbConnectionRepository`, `OpenCodeHttpClient` |
| adapter 控制器 | Controller | `QueryController`, `ChannelController` |
| adapter 配置 | Config | `CorsConfig`, `JdbcConfig` |
