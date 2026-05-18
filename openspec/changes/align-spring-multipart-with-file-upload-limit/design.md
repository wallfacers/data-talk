## Context

Spring Boot Web 的 multipart 上传走两层校验：

1. **Servlet 层**（`MultipartResolver`）：根据 `spring.servlet.multipart.max-file-size` 和 `max-request-size` 在请求解析阶段拒绝超大文件，抛 `MaxUploadSizeExceededException`，请求**不进 Controller 方法**
2. **应用层**（`FileUploadController.upload`）：手工检查 `multipartFile.getSize() > MAX_SIZE_BYTES`（50MB），抛业务异常

DataTalk 当前只在第 2 层设了 50MB，第 1 层走 Spring 默认 1MB → 用户实际遇到的边界是 1MB。这是配置漂移类 BUG。

参考实现做法：OpenCode 的 server 完全无 multipart（用 base64 inline），不参考；类似 Spring 项目通常都会在 `application.yml` 显式声明 multipart 上限。

## Goals / Non-Goals

**Goals:**

- 单文件 multipart 上限 50MB（与 `FileUploadController.MAX_SIZE_BYTES` 一致）
- request 总上限 50MB（多文件同时上传也覆盖单文件上限即可，不打算专门支持批量大文件场景）
- 配置项有注释说明同源约束

**Non-Goals:**

- 不引入"从 Java 常量反向生成 application.yml 数值"的工程化机制（过度设计，4 行 yaml 注释足够）
- 不调整 Controller 端 50MB 数值（用户可以接受 50MB 上限；改大需要单独 change 评估磁盘 / 网络 / OpenCode payload 风险）
- 不放宽到 100MB+（OpenCode FilePart 的 base64 data URI 会让 50MB 原图膨胀到 ~67MB，已逼近 HTTP body 单 chunk 的合理上限）

## Decisions

### Decision 1: 在 application.yml 显式声明而非 @Configuration MultipartConfigElement

**Alternatives:**

a) `@Bean MultipartConfigElement`：Java 代码声明，类型安全，但跟 Spring 默认配置体系隔离
b) `application.yml` 下的 `spring.servlet.multipart.*`：标准 Spring Boot 配置点，文档化、可被 profile override ✅

**Chosen: b)**

**Rationale:** Spring Boot 官方推荐路径，跟 `application-e2e.yml` 已有的 `spring.servlet.multipart.max-file-size: 1MB` profile override 模式一致（详见 CLAUDE.md 关于 e2e profile 的 ingestion 注释 — 该项目已有用 profile override multipart 的先例）。

### Decision 2: max-request-size = max-file-size = 50MB

**Alternatives:**

a) `max-request-size > max-file-size`（如 200MB）：支持多文件并发上传总和 > 单文件上限
b) `max-request-size = max-file-size = 50MB`：单 request 上限等于单文件上限 ✅

**Chosen: b)**

**Rationale:** 当前业务无"一次上传 5 个 30MB 文件总和 150MB"场景；前端 `MAX_CONCURRENT = 3` 但每个文件独立 POST，不共用 request；保持 max-request-size = max-file-size 防止意外的 batch upload 路径绕过单文件限制。

### Decision 3: 不改 FileUploadController 的 MAX_SIZE_BYTES

**Rationale:** Controller 层 50MB 是经过评估的业务上限，且 `FileAnalysisService`、`FileReadActionHandler` 等下游链路按此假设设计。本 change 只修配置漂移，不调业务上限 — 那是 separate concern。

## Risks / Trade-offs

- **Risk**: 提升 multipart 上限可能被恶意客户端用于占用磁盘 / 内存
  - **Mitigation**: Controller 层已有 50MB 校验 + temp-then-permanent 落盘策略 + 临时文件失败 cleanup；本 change 不引入新攻击面，只让代码意图与运行时一致
- **Risk**: 大文件 multipart 解析会占用 Spring 工作线程更长时间
  - **Mitigation**: Java 21 virtual threads 已启用，单连接长持有对吞吐影响有限；50MB 在常见带宽下 < 5s
- **Trade-off**: 不专门支持 batch upload total > 50MB；若后续有此需求，独立 change 评估 max-request-size

## Migration Plan

1. 编辑 `server/data-talk-adapter/src/main/resources/application.yml`：在 `spring:` 节点下添加：
   ```yaml
   servlet:
     multipart:
       # MUST stay in sync with FileUploadController.MAX_SIZE_BYTES (50 * 1024 * 1024)
       max-file-size: 50MB
       max-request-size: 50MB
   ```
2. 检查 `application-e2e.yml` / `application-prod.yml` 是否有 multipart 相关 override，若有，确认数值一致或显式说明 profile-specific 原因
3. 重启后端，手测：上传一张 2-5MB 图片成功（chip 进 done，不再 "Error: Upload failed"）
4. 关闭 BUG-0062，backfill `fixCommit` + `fixedAt`
5. 验证：`batch-image-attachments-via-fileparts` archived change 的 `IMAGE_PAYLOAD_HARD_LIMIT_BYTES = 5MB` 在浏览器手测时能真实触发 toast，而不是被 multipart 1MB 提前拒掉

## Open Questions

- 是否需要把 `MAX_SIZE_BYTES` 从 Java 常量改成 `@Value` 读 yaml property，做单一真相源？倾向暂不做 — 单点常量 + yaml 注释提示已经足够，引入 `@Value` 增加测试 fixture 复杂度
