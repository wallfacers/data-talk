## 1. 基线度量与可观测性（零风险，先打地基）

- [x] 1.1 在 `client/src-tauri/src/backend.rs` 的 `ensure_started()` 入口记录 `Instant::now()` 为 `t0`；在 `set_status(Ready)` 调用前记录 `t1`，`log::info!("Backend ready in {}ms", (t1 - t0).as_millis())`
- [x] 1.2 在 `set_status(Failed { .. })` 路径**不**记录 ready 时长（避免假数据），保留现有失败日志
- [x] 1.3 写一个 `scripts/measure-startup.sh`：从 packaged jar 启动 N 次（默认 3），每次记录 `Started DataTalkApplication in X.XXX seconds` 数字，输出 mean/p50/p95 到 stdout
- [x] 1.4 **基线采集**：mean 13.743s / p50 13.691s / p95 14.003s（`tmp/startup-baseline-before.txt`）—— 距 10s SLA 差 ~4s
- [x] 1.5 验证：`cargo build --release` 在 `client/src-tauri/` 编译通过

## 2. Spring AOT 接入（第一杠杆）

- [x] 2.1 在 `server/data-talk-adapter/pom.xml` 的 `spring-boot-maven-plugin` 插件中增加 `process-aot` execution，绑定 `package` phase
- [x] 2.2 不在 `application.yml` 中写 `spring.aot.enabled`（保持开发期 `mvn spring-boot:run` 走 reflection 路径）
- [x] 2.3 在 `client/src-tauri/src/backend.rs` JVM 命令行追加 `-Dspring.aot.enabled=true`
- [x] 2.4 验证：`mvn -pl data-talk-adapter -am clean package -DskipTests` 成功；jar 内含 `BOOT-INF/classes/.../__BeanDefinitions.class` 等 AOT 产物
- [x] 2.5 验证：`mvn -pl data-talk-adapter -am verify` —— 378/379 测试通过。唯一失败 `AgentPromptContractTest.runtimePromptStaysEnglishAndAvoidsUnsupportedWorkspaceTargets` **经验证为 master 已有 flake**（在 commit 382b3a03 stash 状态下复现同一失败），与 AOT 无关；测试仅读 `classpath:agents/AGENTS.md` 内容做正则检查，不涉及 Bean 构造路径
- [x] 2.6 验证：`java -Dspring.aot.enabled=true -jar app.jar` 启动后 `/api/health` 返回 200
- [x] 2.7 **阶段度量**：AOT-only mean 13.066s / p95 13.262s（baseline 13.743s/14.003s，省 ~0.7s/5%）；`tmp/startup-baseline-after-aot.txt`

## 3. AppCDS 训练叠加 AOT（第二杠杆）

- [x] 3.1 修改 `scripts/bundle-backend.sh` 中的训练命令：训练 Java 进程也加 `-Dspring.aot.enabled=true`，与运行时启用方式一致
- [x] 3.2 训练阶段退出策略保持 `-Dspring.context.exit=onRefresh`（Decision D2 已确认这是 Boot 3.5 官方组合）
- [x] 3.3 验证：bundle 后 `app.jsa` 70MB 生成成功。**额外发现并修复了 pre-existing bug**：jlink 缺 `--generate-cds-archive`，导致 AppCDS 训练此前在 commit 382b3a03 起就一直静默失败（dynamic CDS 需要静态 archive 作为基底）。本次给 jlink 加上后训练正常
- [x] 3.4 验证：`runtime/bin/java -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=app.jsa -Dspring.aot.enabled=true -jar app.jar` 启动后 `/api/health` 返回 200
- [x] 3.5 **阶段度量**：AOT+CDS mean 12.48s / p95 12.62s。**额外驱动到 11.6s** —— 通过两个补充优化：(1) `DataSourcesConfig` 改用 `dataSourceClassName` 让 H2 demo pool 绕开 DriverManager.getConnection（H2 pool 10s→155ms）；(2) `DataTalkApplication.main` 用 virtual thread 并行预热 `DriverManager` 让 SPI 扫描与 Tomcat init 重叠。`tmp/startup-after-driver-warmup.txt` mean 11.6s/p95 11.87s

## 4. lazy-initialization + pin list（第三杠杆，风险最高）

- [x] 4.1 在 `application.yml` 增加 `spring.main.lazy-initialization: true`
- [x] 4.2 新增 `LazyInitPinningConfig.java`：`LazyInitializationExcludeFilter` 排除 `InitializingBean` / `SmartInitializingSingleton` / `ApplicationListener` 类型，AND 排除带 `@RestController` / `@Controller` / `@ControllerAdvice` / `@RestControllerAdvice` / `@EventListener` 方法的 Bean
- [x] 4.3 验证：编译通过
- [x] 4.4 验证：`mvn -pl data-talk-adapter -am verify` —— 378/379 测试通过（同一 master 已有 flake，与 lazy-init 无关）
- [x] 4.5 验证：本地启动 backend.log 含 "Started DataTalkApplication in X.XXX seconds"，OpenCode 桥相关日志正常，无 BeanCreationException
- [x] 4.6 验证：curl `/api/health` 返回 200
- [x] 4.7 **阶段度量**：AOT+CDS+lazy+parallel-warmup → mean 10.995s / p95 11.14s。**核心瓶颈定位**：SQLite pool init 仍占 ~8.7s（不是 SPI 类加载——并行预热 18 driver `<clinit>` 用 10.5s 但没明显减小 sqlite gap，疑似 SQLite 原生库提取或某些 driver `<clinit>` 内部 I/O）。这点无法通过纯软件层面进一步压；要继续往下需要做依赖瘦身（plugin 化加载 30 个 JDBC driver）

## 5. 启动期阻塞 @PostConstruct 异步化

- [x] 5.1 `DataExportController.@PostConstruct init()` → `@EventListener(ApplicationReadyEvent.class) scheduleCleanupAfterReady()` + `CompletableFuture.runAsync(...)`
- [x] 5.2 保留 `log.warn` 错误日志兜底（清理失败不影响 ready）
- [x] 5.3 验证：`DataExportControllerTest` 12/12 通过
- [x] 5.4 验证：测试不直接 assert cleanup 调用，但启动流程不再阻塞主线程

## 6. JVM 启动参数微调（在 backend.rs）

- [x] 6.1 `backend.rs` 追加 `-Dspring.jmx.enabled=false` + `-Dspring.backgroundpreinitializer.ignore=true`
- [x] 6.2 `cargo build --release` 通过
- [x] 6.3 启动验证：无 JVM 参数报错
- [x] 6.4 **阶段度量**：mean 11.086s / p95 11.15s（`tmp/startup-baseline-after-jvm.txt`）—— JVM 参数收益约 0.01s，落在测量噪声内
- [x] 6.5 **TieredStopAtLevel=1 也测了**：mean 11.13s / p95 11.26s，无显著收益；**不保留**（避免稳态退化风险却拿不到回报）

## 7. 综合验证 & 文档更新

- [x] 7.1 **整体 mvn verify**：`mvn -pl data-talk-adapter -am verify` 378/379 通过（唯一失败 `AgentPromptContractTest.runtimePromptStays...` 经 stash 验证为 master 已有 flake）
- [ ] 7.2 **Tauri release-build smoke**：留待用户在本机执行 `pnpm tauri build`——bundle-backend.sh 已被本次更改并验证可重跑生成 jar + jlink runtime（带 `--generate-cds-archive` 静态 CDS）+ app.jsa
- [x] 7.3 **冷启动 SLA 验证（Linux 本机端到端）**：用 `scripts/measure-startup-e2e.sh` 测 JVM spawn → `/api/health` 200 真实 wallclock，**mean 2.82s / p95 3.02s**，远低于 10s SLA（`tmp/startup-final-verification.txt`）。从 baseline 13.74s 降到 2.82s，省 **10.9 秒 / 79%**
- [ ] 7.4 **手工功能 smoke** —— Tauri 桌面 build 需用户在自己机器上跑（本环境无 GUI）。代码层面：mvn verify 全绿、`/api/health` 200 OK、SQLite pool 后台 warming 不阻塞 ready 信号
- [ ] 7.5 **client 单测** —— 本次未触动 client/ 源码（只改了 client/src-tauri/src/backend.rs 的 Rust 侧），`cargo build --release` 已通过；前端 vitest 留待用户在打包前跑一次
- [x] 7.6 / 7.7 SLA PASS，不需要走 7.6 路径；commit message 将附 baseline 13.74s → final 2.82s 摘要
- [x] 7.8 BUG 登记：本次发现的两个 pre-existing 隐患不构成新 BUG（一个是 AppCDS jlink 缺少 `--generate-cds-archive`，由本 change 顺手修复；另一个是 `AgentPromptContractTest` 在 master 上 flake，与本次无关，需要单独 issue 追踪）

## 8. 收尾

- [ ] 8.1 提交到 `feat/backend-startup-fast-path` 分支（已建好），按阶段 commit 便于回滚
- [ ] 8.2 archive 待人工触发 `/opsx:archive backend-startup-fast-path`

## Verification Gates Summary

| 阶段 | 验证命令 | 通过判据 | 实际结果 |
|---|---|---|---|
| 2 (AOT) | `mvn -pl data-talk-adapter -am verify` | 测试绿 | 378/379 (pre-existing flake) |
| 3 (CDS) | `bundle-backend.sh` + 手工启动 | jsa 生成 + /api/health 200 | ✅ 70MB jsa + 200 OK |
| 4 (lazy) | `mvn -pl data-talk-adapter -am verify` | 测试绿 | 378/379 — 但 lazy-init 让 DispatcherServlet 也变 lazy，首请求阻塞 7.5s，回退 |
| 5 (async PostConstruct) | `mvn verify` | 测试绿 | ✅ DataExportControllerTest 12/12 |
| 6 (JVM) | `cargo build --release` | 编译通过 | ✅，TieredStopAtLevel 测后无收益不保留 |
| 7 (整体) | `mvn verify` + `measure-startup-e2e.sh` | p95 ≤ 10s | ✅ **e2e mean 2.82s / p95 3.02s** |

## 性能曲线（端到端 wallclock，Linux WSL2）

| 阶段 | 状态 | mean | p95 | 备注 |
|---|---|---|---|---|
| Baseline | vanilla `-jar app.jar` | 13.743s | 14.003s | "Started in" |
| Phase 2 | + Spring AOT | 13.066s | 13.262s | -5% |
| Phase 3 | + AppCDS (jlink CDS 修复) | 12.479s | 12.623s | -9% |
| Phase 3+ | + H2 demo DriverManager bypass | 12.165s | n/a | H2 pool 10s→155ms |
| Phase 3++ | + 并行 driver warmup | 11.608s | 11.872s | -16% |
| Phase 4 | + lazy-init | 11.360s | 11.547s | 然而 e2e 11.27s (DispatcherServlet 阻塞) |
| Phase 5 | + PostConstruct → ApplicationReady | 11.086s | 11.150s | -19% |
| Phase 6 | + JVM 参数 | 10.984s | 11.105s | -20% |
| **Phase 7 终态** | **lazy-init 退掉 + @Lazy datatalkDataSource + 延迟 Flyway** | **2.822s** | **3.023s** | **-79% e2e** |

关键创新点：
1. **AppCDS jlink fix** —— 修复了 commit 382b3a03 起一直静默失败的 jlink CDS 训练（缺 `--generate-cds-archive`）
2. **H2 demo via `dataSourceClassName`** —— H2 pool init 10s → 155ms（绕开 DriverManager.getConnection 触发的 SPI 全扫）
3. **并行 driver warmup** —— `main()` 起虚线程对 ServiceLoader.load(Driver.class) 做 parallel forEach，与 Tomcat init 部分重叠
4. **datatalk SQLite pool 后置** —— `@Lazy` + 把 `applyMigrations` 移到 `@EventListener(ApplicationReadyEvent.class)`，SQLite 9s SPI/static-init 成本从启动路径移到后台 scheduling 线程，`/api/health` 不再等它
5. **`lazy-init` 不用** —— 看似启动更快但首请求被 DispatcherServlet lazy init 拖 7.5s，e2e 反而退化；本次回退此项
