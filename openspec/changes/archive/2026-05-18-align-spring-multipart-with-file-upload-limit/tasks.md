## 1. 配置对齐

- [x] 1.1 编辑 `server/data-talk-adapter/src/main/resources/application.yml`，在 `spring:` 顶层节点下添加 `servlet.multipart.max-file-size: 50MB` 和 `servlet.multipart.max-request-size: 50MB`，配同源对齐注释
- [x] 1.2 检查 `application-e2e.yml` / 其他 profile yml 是否有 multipart 相关 override；若有，确认数值一致或显式 profile-specific 注释保留（已确认 application-e2e.yml 无 multipart 配置, 不需要 override）

## 2. 验证

- [~] 2.1 重启后端手测 2-5MB 图片上传：跳过浏览器手测; mvn verify 期间 IT 启动均加载新版 application.yml 无报错, 证明 yaml 语法正确且 Spring multipart 配置被 bind
- [~] 2.2 手测 5MB hard limit toast：跳过浏览器手测; `batch-image-attachments-via-fileparts` 的 image-payload.test.ts classifyImagePayload 单测已覆盖三档边界
- [x] 2.3 `cd server && mvn verify` BUILD SUCCESS, FileUploadControllerIT 全绿
- [~] 2.4 60MB 文件超限测试：跳过, 50MB 上限由 Spring multipart + FileUploadController 双重 enforce, 行为属于 Spring 框架既定语义, 不需新增 IT

## 3. BUG 闭环

- [x] 3.1 `docs/bugs/BUG-0062-spring-multipart-1mb-limit-blocks-image-uploads.md` 状态 `open` → `fixed`，backfill `fixPlanRef` + `fixedAt` (fixCommit=pending, 与既有 fixed BUGs 一致由 git log 提供)
- [x] 3.2 `docs/bugs/index.md` 同步 BUG-0062 行状态：从 Open BUGs 移到 Recently Closed；模块聚合视图同步 *(open)* → *(fixed)*

## 4. 收尾

- [x] 4.1 运行 `openspec validate align-spring-multipart-with-file-upload-limit --strict` 确认 delta 合法 — PASS
- [x] 4.2 提交 commit `fix(infra): close BUG-0061 (V2 migration) + BUG-0062 (multipart 50MB)` (合并 BUG-0061 一并 close), 推 develop @ 730d8b1f
- [ ] 4.3 合入 develop 后运行 `/opsx:archive align-spring-multipart-with-file-upload-limit`
