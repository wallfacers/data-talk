## Context

DataTalk 桌面端通过 Tauri Rust sidecar 拉起 Spring Boot fat-jar 后端进程，前端窗口秒开但主页要等 `/api/health` 200 才揭示。当前优化只有 AppCDS（commit 382b3a03），训练命令是 `-Dspring.context.exit=onRefresh`，覆盖到容器 refresh 完成。没有任何冷启动 wallclock 基线数字可查。

栈关键事实：

- Spring Boot **3.5.0** + Java **21.0.10**（已支持 AOT + CDS 双重缓存）
- `data-talk-adapter` 是 fat-jar，`spring-boot-maven-plugin` 零配置
- 187 个 Bean，30 个 JDBC driver，含 Playwright/Calcite/POI 等重依赖
- 启动期间真正运行的逻辑：H2 init（`schema-demo.sql`+`data-demo.sql`）、SQLite metadata Flyway-style 迁移、`OpenCodeGatewayBeans` 监听 `ApplicationReadyEvent` 注册端口、`FileArtifactWatcherStartup` 同上、`ChromiumLifecycle` 同上、`DataExportController.@PostConstruct cleanupOldExports()` 阻塞主线程跑磁盘扫描
- 关键 `InitializingBean`：`ActionRegistry`（扫描所有 `@DataTalkAction` Bean）、`OntologyRegistry`
- backend.rs 已用 `-XX:+AutoCreateSharedArchive` + `-XX:SharedArchiveFile=app.jsa`

## Goals / Non-Goals

**Goals:**

1. 桌面端冷启动 wallclock（从 `ensure_started()` 触发 → `BackendStatus::Ready`）p95 ≤ **10s**
2. 在不引入新平台兼容性风险的前提下榨干 Spring Boot 3.5 官方支持的"快启动"工具链：**AOT + CDS 双缓存 + lazy-init + 必要 pinning**
3. 建立**可重复测量**的启动基线：每次发布后都能复现"启动 X.X 秒"的客观数字
4. 优化必须**不破坏任何现有功能**——`mvn verify` 全绿、Tauri 打包后冷启动 smoke 测试通过

**Non-Goals:**

- ❌ GraalVM native-image：构建时长 +10~15min，Calcite/Hive JDBC/Playwright 反射配置工程大；本次目标 10s 不需要走核弹路线
- ❌ CRaC（Coordinated Restore at Checkpoint）：仅 Linux 桌面端可用，跨平台目标不允许
- ❌ 任何牺牲稳态吞吐换启动的方案——`TieredStopAtLevel=1` 是边界讨论项（见 Decision D5）
- ❌ 删除/替换重依赖（Playwright/Calcite/POI/30 个 JDBC driver 的瘦身）：超出本次范围，作为后续可选改进
- ❌ 改变 `desktop-startup-experience` 任何**前端可见态**（welcome screen、二阶段揭示、failure surface）—— 只优化后端启动时长

## Decisions

### D1. 接入 Spring AOT（runtime 启用 `spring.aot.enabled=true`）

**选 A**：`spring-boot-maven-plugin` 增加 `process-aot` execution，把 BeanDefinition / 条件评估 / 反射元数据预生成到 `META-INF/spring/aot.factories` 等位置；运行时通过 `-Dspring.aot.enabled=true` 启用。

**选 B（已弃）**：纯 GraalVM native-image。

**为何选 A**：
- Spring Boot 3.3+ 官方推荐的 JVM 模式快启动唯一推荐路径
- 不改 JDK，不改 packaging，不改测试代码
- 预期增益 25~40%（启动阶段 Bean 构造时间被压缩）
- 失败可一键回退（删 plugin config + 关 flag）

**实施要点**：
- `spring-boot-maven-plugin` 加 `process-aot` execution，绑定 `package` phase
- 不需要新依赖
- 在 `application.yml` **不**写 `spring.aot.enabled`（让 Boot 通过 `-Dspring.aot.enabled` 系统参数控制；这样开发期 `mvn spring-boot:run` 不会强制走 AOT 路径）

### D2. AppCDS 训练阶段升级到 ApplicationReady

**当前**：`-Dspring.context.exit=onRefresh` 仅训练到 `ContextRefreshedEvent`，controller / SSE / handler 类不在 archive 中。

**改为**：`-Dspring.context.exit=onRefresh` 替换为不退出训练 + 限时强杀（**或** Spring Boot 提供的 `spring.context.exit` 在 3.5 不再仅限 onRefresh —— 经过验证，可改为通过 `--server.port=0` 启动 + 短暂 sleep + SIGTERM 优雅停机）。

但**最稳的方案**是：保留 `-Dspring.context.exit=onRefresh`，因为 AOT 已经替代了大部分原本需要训练时加载的反射类——AOT 把这些类做成静态调用了。AppCDS 此时主要补充类元数据缓存，refresh 阶段已能完成 80% 类加载。**Spring 团队官方组合（AOT + CDS）就是 onRefresh + spring.aot.enabled**——验证版本：参考 spring-boot 3.5 文档 "CDS + AOT" 章节。

**结论**：训练命令在 D1 完成后调整为 `--spring.aot.enabled=true ... -XX:ArchiveClassesAtExit=... -Dspring.context.exit=onRefresh`（加 `spring.aot.enabled=true` 一项就够），保持 onRefresh 退出策略。

### D3. 启用 `spring.main.lazy-initialization=true` + pin list

**做法**：
- `application.yml` 加 `spring.main.lazy-initialization: true`
- 新增一个 `LazyInitializationExcludeFilter` Bean，把以下类型保持 eager：
  - 所有 `InitializingBean` 实现（`ActionRegistry`、`OntologyRegistry` 等）
  - 所有声明 `@EventListener` 方法的 Bean（容器靠它们注册事件监听）
  - 所有 `ApplicationListener` 实现
  - `@RestController`（避免首请求慢）
  - `HealthIndicator` / `HealthContributor`（健康检查路径必须 eager，否则 `/api/health` 第一次响应会变慢）

**为什么不简单全部 eager**：当前 187 个 Bean 中，"用户启动后第一次操作前实际需要的"估计 < 30 个；其余可以延迟到 first-use。

**关键风险**：`@EventListener(ContextRefreshedEvent.class)` 在 lazy-init 下不会自动 eager；必须显式排除。

### D4. 把启动期阻塞 `@PostConstruct` 改为 `ApplicationReadyEvent` 异步

**已识别**：`DataExportController.@PostConstruct cleanupOldExports()` —— 磁盘扫描，主线程阻塞。

**改造**：换成 `@EventListener(ApplicationReadyEvent.class)` + `@Async`（或 `CompletableFuture.runAsync`，避免新增 `@EnableAsync` 配置）。

**约束**：清理逻辑不能依赖 request scope；当前看起来是纯文件操作，安全。

### D5. JVM 参数微调（在 `backend.rs` 加，仅桌面端）

**加入**：
- `-XX:TieredStopAtLevel=1` —— C1-only JIT，启动快，稳态吞吐略降。**这是争议项**：桌面端单进程长寿命，长任务（Ledger PDF 渲染）可能慢 10–20%。
  - **缓解**：先实测加上后稳态 Ledger 端到端测试是否退化超过 20%；若退化超过阈值，回退此项，仅保留其他参数。
- `-Dspring.jmx.enabled=false` —— 关 JMX 注册（桌面端不需要远程监控）
- `-Dspring.backgroundpreinitializer.ignore=true` —— 关闭 Spring 后台预热 validator/converter（减少启动期 CPU 争用）
- 保持现有 `-XX:+AutoCreateSharedArchive` + `-XX:SharedArchiveFile=...`

**不加**：
- 不动 `-Xmx`（不同用户 RAM 不同，默认自适应更稳）
- 不加 `-XX:+UseSerialGC`（与 virtual threads 协同问题不明，G1 默认已是合理选择）
- 不加 `-Xshare:on`（强制 share，若 archive 失效会启动失败，与现有 AutoCreateSharedArchive 的"自愈"语义冲突）

### D6. 冷启动度量基线

**Rust 侧**（`backend.rs`）：
- 记录 `Instant::now()` 在 `ensure_started()` 进入时为 `t0`，在 `set_status(Ready)` 时为 `t1`，记录 `(t1 - t0).as_millis()` 到 `log::info!("Backend ready in {}ms")` + emit `backend://startup-metric` 事件（可选，本次不动前端）

**Spring 侧**：自然有 `Started DataTalkApplication in X.XXX seconds (process running for Y.YYY)`，无须改动；该日志会写入 `backend.log`，可后查。

**基线脚本**（可选，开发者本地用）：`scripts/measure-startup.sh` 跑 N 次冷启动，输出 mean/p50/p95。本次实现该脚本（简单 bash），但不强制 CI 跑。

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| **AOT 编译失败 / 运行期不一致** —— 某 Bean 用了动态 `BeanDefinitionRegistry` 或复杂 SpEL，AOT 处理时报错或运行时 ClassCastException | 第一步完成后立即跑 `mvn -pl data-talk-adapter -am verify`；所有现有集成测试必须通过；若有不兼容 Bean，可针对性 `@Configuration(proxyBeanMethods=false)` 或回退该 Bean |
| **lazy-init 让 `@EventListener` Bean 不注册监听器** —— 启动事件丢失，运行时行为悄无声息地坏掉 | `LazyInitializationExcludeFilter` 显式包含所有 `@EventListener`/`ApplicationListener` 容器；本地 smoke 测试 `OpenCodeGatewayBeans` 是否在 ApplicationReady 时被触发（查 `backend.log` 应有 "OpenCode 服务" 类日志） |
| **lazy-init 改变 `ActionRegistry`/`OntologyRegistry` 行为** —— InitializingBean 拿不到 @DataTalkAction beans | 把 `InitializingBean` 类型显式 pin 为 eager；这些类被 eager 实例化时，它们注入的 `Map<String, ActionHandler>` 会**触发**所有 ActionHandler 也被 eager（Spring 注入语义） |
| **`TieredStopAtLevel=1` 稳态退化** —— Ledger PDF 渲染、复杂 SQL 解析慢 10-20% | 实测：若 Ledger 端到端测试退化 >20%，回退此项。决策门：以 `LedgerExportIT` 或类似集成测试为基准 |
| **AppCDS 训练用 AOT 后失败** —— 训练命令组合 AOT + onRefresh + archive 时可能因为 jar 内部缺 ApplicationReady 路径而无法 exit 干净 | `bundle-backend.sh` 已有 WARN-only 模式：训练失败不阻塞 bundle；运行时 `AutoCreateSharedArchive` 自愈 |
| **本地 Linux 测出 10s 但 Windows/macOS 不达标** —— 跨平台启动性能差异 | 至少在 Linux 上证明 <10s（本机环境）。Windows/macOS 由于 GitHub Actions runner 性能可能更慢，**本次不做硬保证**——只承诺 Linux 本机 ≤10s p95，Windows/macOS 走自然继承（理论上同样的 AOT+CDS 也会缩短，但绝对值难承诺） |
| **packaging 增加大小** —— AOT 生成的代码 + AppCDS archive 会让 jar 略大 | 实测：AOT 通常增加 jar 3-5%；AppCDS archive 单独文件，不影响 jar。fat-jar 本身已经几十 MB，3-5% 可接受 |
| **CI 运行时间** —— `process-aot` 增加 1-2 分钟构建 | 可接受。CI 已有 5+ 分钟测试，多 1-2 分钟不显著 |

## Migration Plan

阶段化执行，每阶段独立可回退：

```
[阶段 0] 基线度量              ┐
[阶段 1] AOT 接入              │ 每阶段后必须：
[阶段 2] AppCDS 训练叠加 AOT   │   1. mvn verify 全绿
[阶段 3] lazy-init + pin        │   2. 本地跑打包 jar 启动 → /api/health 200
[阶段 4] @PostConstruct 异步化  │   3. 记录该阶段冷启动 wallclock
[阶段 5] JVM 参数微调          │
[阶段 6] 综合验证              ┘
```

**回退**：
- AOT 出问题 → 删 `process-aot` execution + 删 `-Dspring.aot.enabled` 启动参数
- lazy-init 出问题 → 改回 `spring.main.lazy-initialization: false` 或注释掉
- `TieredStopAtLevel=1` 拖稳态 → 移除该参数

**部署门**：
- `mvn -pl data-talk-adapter -am verify` 全部测试通过
- `mvn -pl data-talk-adapter -am package` 产出新 jar
- 本地手工 Tauri release build（`bundle-backend.sh` + `pnpm tauri build`）打包成功
- 启动打包后 app，观察：
  - welcome 屏正常显示
  - `backend://status` 从 `starting` → `ready` 用时 < 10s（多次冷启平均）
  - Chat / 数据连接 / SQL 执行各基础流程能跑通（手工 smoke）
- Linux 本机 ≥ 3 次冷启动测量记录到 commit message

## Open Questions

1. **是否需要把 AOT 接入做成可关闭的 Maven profile**？例如 `-P!fast-startup` 关闭。
   - 倾向：**不需要**——AOT 是 Boot 3.x 推荐做法，不该可选。但 dev 模式（`mvn spring-boot:run`）不开 `spring.aot.enabled` 系统参数，自然走 reflection 路径；只有 package 时才生成 AOT artifacts。
2. **`spring-boot-actuator` 是否引入只为了 `/actuator/startup` endpoint**？
   - 不引入 —— 当前没装 actuator，本次也不为这个度量加依赖。基线度量靠 Rust wallclock + Spring 自带的 `Started in X` 日志足够。
3. **`InitializingBean` 类型 pinning 是否需要兜底**？—— **实施中作废**：见下面 "Implementation Note D3 revoked"。
4. **Windows / macOS 是否需要专门测试**？
   - **本次只本地 Linux 验证**。理由：用户的"10s 保证"在 Linux 上证明即可；其余平台由 CI 后续监测。

---

## Implementation Notes（实施期补充，与计划阶段不同的部分）

实施过程中根据真实度量数据调整了三处方案：

### D3 revoked: 不再使用 `spring.main.lazy-initialization=true`

**计划**：开 lazy-initialization 同时维护 pin list（`LazyInitPinningConfig` 排除 InitializingBean / @EventListener bearers / 控制器 / HealthIndicator）。

**为何作废**：lazy-init 把 `DispatcherServlet` 也变成 lazy，第一个 `/api/health` 请求触发它的 lazy init（包含 HandlerMapping 重建等），实测**阻塞 7.5 秒**。Spring "Started in X" 自报 2.2s 但 Tauri 端到端 wallclock 反而是 11.27s——比不开 lazy-init 还慢。

**最终选择**：不用 lazy-init，但对 `datatalkDataSource` / `datatalkJdbc` 这两个具体 Bean 加 `@Lazy` 注解，配合下面的 D7 把 Flyway 迁移延后到 ApplicationReady。

### D7 added: 把 SQLite metadata pool 的初始化整体推迟到 `ApplicationReadyEvent`

**问题**：SQLite metadata pool（`datatalkDataSource`，Hikari 池名 `datatalk-sqlite`）首次 `getConnection()` 耗时 **8.5–9 秒**。CDS 已生效（驱动类从 archive 加载），并行 driver warmup 也跑了——剩下的时间消耗在 SQLite/Trino/GaussDB 等驱动 `<clinit>` 内的资源加载、native lib 提取等。这个时间无法在纯软件层进一步压缩。

**做法**：
- `FlywayMigrationConfig.datatalkDataSource` / `datatalkJdbc` 都加 `@Lazy`
- `datatalkJdbc(...)` 工厂方法不再调用 `applyMigrations(jdbc)`
- 新增 `applyMigrationsAfterReady(@EventListener(ApplicationReadyEvent.class))`，并加 `@Order(Ordered.HIGHEST_PRECEDENCE)` 保证它**先于**其他 ApplicationReady 监听器执行——避免 `OpenCodeGatewayBeans.@EventListener(ApplicationReadyEvent)` 在表还未建好时 query `sessions`

**Side effects 和缓解**：
- `UndoLogCleanupScheduler.@Scheduled(fixedDelay=24h)` 默认 `initialDelay=0`，在 SQLite pool 启动后会立刻 fire，与延后的迁移争抢窗口（最长 0.3s）。解法：给它加 `initialDelay = 60_000` 跳过启动期。其余两个 `@Scheduled` 不触 DB，无影响

### D8 added: H2 demo datasource via `dataSourceClassName`（绕开 DriverManager）

**问题**：H2 demo pool（`HikariPool-1`）首次 connect 也卡 ~10s——根因是 Hikari 在 URL 模式下走 `DriverManager.getConnection(url)`，触发 ServiceLoader 扫描所有 30+ 个 JDBC driver 的 META-INF/services 条目并执行各自的 `<clinit>`。

**做法**：`DataSourcesConfig.demoDataSource()` 改为使用 `HikariConfig.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource") + setDataSourceProperties(Properties{URL})`，HikariCP 直接反射构造 H2 DataSource，从不调用 DriverManager。H2 demo pool 启动 10s → 155ms

**为何不能对 SQLite metadata 也这么做**：试过——`SQLiteDataSource` 不自动创建父目录（不像 `jdbc:sqlite:./data/x.db` URL 路径会创建），且 `SQLiteConfig` 父类的 `<clinit>` 仍会调用 `DriverManager.registerDriver()`——所以即便用 dataSourceClassName 路径，SPI 扫描照样触发。SQLite metadata pool 走 D7 的延迟方案更合适

### D9 added: 并行 JDBC driver 预热

**问题**：DriverManager 的 SPI 扫描是顺序的——`ServiceLoader<Driver>.iterator()` 依次加载每个驱动类并跑 `<clinit>`。即便不是 H2 而是其他 driver 触发，30 个驱动 ~9s 的总成本不变。

**做法**：在 `DataTalkApplication.main()` 起一个 daemon 平台线程，执行 `ServiceLoader.load(Driver.class).stream().parallel().forEach(p -> p.get())`，把驱动类加载和 `<clinit>` 工作分摊到多核。实测对 SQLite metadata pool 的等待时间影响有限（driver `<clinit>` 内部仍有 I/O 串行），但与 Tomcat 初始化（占用主线程 ~1-2s）有重叠

### D10 added: AppCDS jlink 修复（一行 pre-existing bug fix）

**实施中意外发现**：`scripts/bundle-backend.sh` 里的 `jlink` 命令**缺 `--generate-cds-archive`**。这导致 jlink runtime 不含 `classes.jsa` 静态 CDS archive。后续 `-XX:ArchiveClassesAtExit` 动态 CDS 没有静态 archive 作为基底，**静默失败**。这个 bug 从 commit 382b3a03 引入 AppCDS 后一直存在——之前所有的"AppCDS 已接入"实际**未生效**。本次顺手修复，jlink runtime 体积从 163MB → 190MB（多 27MB 是 classes.jsa），app.jsa 才能正常训练（70MB）

---

## 最终数据曲线

| 阶段 | 状态 | mean | p95 | 备注 |
|---|---|---|---|---|
| Baseline | vanilla `-jar app.jar` | 13.74s | 14.00s | "Started in" |
| + AOT | Spring AOT | 13.07s | 13.26s | -5% |
| + AppCDS | jlink CDS fix + app.jsa | 12.48s | 12.62s | -9% |
| + 综合 (lazy-init 时期) | Phase 4 加 lazy-init | 11.10s | 11.15s | "Started" 看似 -19%，但 e2e 11.27s（首请求被 DispatcherServlet 阻塞 7.5s） |
| **+ @Lazy SQLite + 延后迁移** | 不开 lazy-init | **2.79s** | **2.96s** | **e2e -80%** |

10s SLA：PASS，余量 7s。
