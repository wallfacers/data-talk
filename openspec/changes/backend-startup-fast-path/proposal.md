## Why

桌面端目前后端冷启动只接入了 AppCDS（commit 382b3a03），而 Spring Boot 3.5 官方"快启动配方"是 **AOT + CDS 双轮驱动 + Lazy Init**。我们只走了一半，估算冷启动仍在 8–13s 浮动；用户要求把 user-perceived 启动时间锁定到 **≤ 10s p95**（Tauri spawn → `backend://status: ready`），并按"大神级"标准把剩余可挤的空间挤干净。

当前已知未利用的杠杆：

1. **没有 Spring AOT** — `spring-boot-maven-plugin` 零配置，没有 `process-aot`，所有 BeanDefinition 仍在运行时反射构建（典型 -25~40% 启动时间）
2. **没有 lazy-initialization** — 187 个 Bean、30 个 JDBC driver、Playwright/Calcite/POI 全部 eager 构造，用户启动后 90% 当次不使用
3. **AppCDS 训练覆盖窄** — 训练用 `spring.context.exit=onRefresh`，只覆盖到容器 refresh 完成，controller / SSE pipeline / handler 类未进 archive
4. **缺少冷启动度量基线** — 没有任何对外能查到的"实际启动多少秒"的数据；优化前必须先量

## What Changes

- 在 `data-talk-adapter` 接入 Spring AOT：`spring-boot-maven-plugin` 增加 `process-aot` execution，运行时通过 `-Dspring.aot.enabled=true` 启用预生成的 `BeanFactoryInitializer`
- 升级 AppCDS 训练脚本：训练目标从 `onRefresh` 改为 `ApplicationReady` 阶段（叠加 AOT 后重新训练），让 archive 覆盖到 controller / SSE pipeline 类
- 启用 `spring.main.lazy-initialization=true`，并对**必须 eager 的 Bean**（`DataSource`、`Flyway` migration runner、`OpenCodeServeManager` 监听器、`@RestController`、`HealthIndicator`）显式 `@Lazy(false)` 或在配置中提供 eager pin 列表
- 把启动期阻塞型 `@PostConstruct`（已识别：`DataExportController.cleanupOldExports()`）改为监听 `ApplicationReadyEvent` 异步执行
- 在 `backend.rs` 启动 JVM 时追加桌面端优化参数：
  - `-XX:TieredStopAtLevel=1`（C1-only JIT，启动快、稳态吞吐略降——桌面场景换得过）
  - `-Dspring.jmx.enabled=false`（关闭 JMX 注册）
  - `-Dspring.backgroundpreinitializer.ignore=true`（避免 Spring 后台预热某些 validator/converter 加剧 CPU 争用）
  - `-Xshare:auto`（与现有 `-XX:SharedArchiveFile` 协同，明确 CDS 模式）
  - 不动堆大小默认值（用户 RAM 不同；只在确实拖慢时再加 `-Xmx`）
- 增加冷启动度量基线：`backend.rs` 在 `set_status(Ready)` 时记录 wallclock 启动时长到 `backend.log`；Spring 端日志保留 `Started DataTalkApplication in X.XXX seconds`；并写一个简单的本地基线脚本用于 before/after 对比
- 不引入 GraalVM native-image、不引入 CRaC——本次目标 10s 用 AOT+CDS+lazy 已绰绰有余，避免引入跨平台兼容/构建时长爆炸的风险

## Capabilities

### New Capabilities

（无新 capability）

### Modified Capabilities

- `desktop-startup-experience`: 现有 "Backend cold-start performance target" 仅定性要求"低于优化前基线"。本次将其扩为**定量 SLA**（≤10s p95），并新增以下要求子项：
  - 后端必须以 Spring AOT + AppCDS 联合模式启动（archive 命中且 `spring.aot.enabled=true`）
  - 后端必须启用 lazy-initialization 且对必须 eager 的 Bean 显式 pin
  - 启动期日志必须可观测到 wallclock 时长，便于后续回归检测

## Impact

**Affected code**:

- `server/data-talk-adapter/pom.xml` — `spring-boot-maven-plugin` 增加 `process-aot` execution
- `server/data-talk-adapter/src/main/resources/application.yml` — 增加 `spring.main.lazy-initialization`、`spring.jmx.enabled=false`、`spring.threads.virtual.enabled` 已开则保持
- `server/data-talk-adapter/src/main/java/com/datatalk/DataTalkApplication.java` — 可能新增 `LazyInitializationExcludeFilter` Bean 来 pin 关键类
- `server/.../DataExportController.java` — `@PostConstruct cleanupOldExports()` 改为 `@EventListener(ApplicationReadyEvent.class)` 异步
- `client/src-tauri/src/backend.rs` — JVM 启动参数追加（AOT enable + JIT tier + JMX off）
- `scripts/bundle-backend.sh` — AOT 步骤前置 / AppCDS 训练命令调整
- `openspec/specs/desktop-startup-experience/spec.md` — delta spec 修改 cold-start 要求

**Affected APIs**: 无对外 API 改动（所有改动局限于启动期行为）

**Dependencies**: 不新增依赖；不改 JDK 版本

**Data sources** (`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`): N/A — 不改 JDBC 驱动列表或 DB 连接逻辑，仅可能让 JDBC `DataSource` Bean 由 lazy 改为 eager 显式 pin（保持现有连接初始化语义）

**Frontend design** (`client/DESIGN.md`): N/A — 不动 UI 控件；welcome screen 由已有 `desktop-startup-experience` capability 覆盖，本次不变更前端可见态

**Risks**:

- **Spring AOT 兼容性**：项目里若有动态 `BeanDefinitionRegistry` 操作、复杂 `@ConditionalOnExpression`、运行期构造的 proxy，AOT 可能编译失败或运行期不一致。缓解：先在本地跑全量 `mvn verify`，所有集成测试通过才推进；若发现 AOT 不兼容 bean，先排除该模块再处理
- **Lazy-init 改变 Bean 启动顺序**：某些 `@EventListener(ContextRefreshedEvent.class)` 可能因为目标 Bean 还未实例化而失效。缓解：扫描所有 `@EventListener` / `ApplicationListener` 实现，确认依赖关系；对受影响 Bean 用 `LazyInitializationExcludeFilter` pin
- **`TieredStopAtLevel=1` 影响稳态性能**：桌面应用偶发长任务（如 Ledger PDF 渲染）可能慢 10–20%。缓解：把该参数限定在桌面 sidecar 启动；服务端运行（`mvn spring-boot:run`）不受影响；如果实测稳态退化明显，回退此项
- **训练阶段 ApplicationReady 失败**：训练运行需要真启动到 ready，可能因为缺 OpenCode/数据库环境失败。缓解：训练命令保持 `--server.port=0` + 适当 env，失败不阻塞构建（沿用现 `bundle-backend.sh` 的 WARN-only 模式）

**Open BUGs in module area** (`docs/bugs/index.md`):

- BUG-0087 `tauri-resource-dir-extended-path-prefix-crashes-java` — Windows extended-path 已通过 `strip_extended_path_prefix` 修复；本次新增 JVM 参数若涉及路径需复用同样的清洗逻辑
- BUG-0036 `skills-extracted-to-wrong-cwd-not-found-by-opencode` — 已修复，但提醒：调整 cwd / 工作目录任何相关项时需注意 skill extraction 路径

**Backward compatibility**: 本次纯优化路径；如所有 JVM 参数和 AOT 编排都正确，外部观察到的只是"启动更快"，无功能行为变更。回退路径：删除新增 JVM 参数 + 关闭 `spring.aot.enabled` 即可恢复到当前行为。
