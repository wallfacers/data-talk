## 1. 配置对齐

- [ ] 1.1 编辑 `server/data-talk-adapter/src/main/resources/application.yml`，在 `spring:` 顶层节点下添加 `servlet.multipart.max-file-size: 50MB` 和 `servlet.multipart.max-request-size: 50MB`，配同源对齐注释
- [ ] 1.2 检查 `application-e2e.yml` / 其他 profile yml 是否有 multipart 相关 override；若有，确认数值一致或显式 profile-specific 注释保留

## 2. 验证

- [ ] 2.1 重启后端 `cd server && mvn spring-boot:run -pl data-talk-adapter`；手测上传一张 2-5MB 图片，确认 chip 走 `pending → uploading → done`，不再 "Error: Upload failed"
- [ ] 2.2 手测 `batch-image-attachments-via-fileparts` 的 5MB hard limit toast：上传 4-5MB 多张图片合计 > 5MB 时前端弹 `chat.image.payloadTooLarge` 阻止发送
- [ ] 2.3 `cd server && mvn verify` BUILD SUCCESS，确认 `FileUploadControllerIT` 全绿
- [ ] 2.4 上传一张 60MB 文件（超 50MB 上限）：multipart 解析层（或 Controller 层）SHALL 返回业务错误并清理 temp；不 SHALL 静默成功

## 3. BUG 闭环

- [ ] 3.1 `docs/bugs/BUG-0062-spring-multipart-1mb-limit-blocks-image-uploads.md` 状态 `open` → `fixed`，backfill `fixCommit` + `fixPlanRef` + `fixedAt`
- [ ] 3.2 `docs/bugs/index.md` 同步 BUG-0062 行状态，从 In Progress 移到 Closed (Fixed)；模块聚合视图同步

## 4. 收尾

- [ ] 4.1 运行 `openspec validate align-spring-multipart-with-file-upload-limit --strict` 确认 delta 合法
- [ ] 4.2 提交 commit `fix(config): align spring multipart upload limit with FileUploadController 50MB (close BUG-0062)`，推 develop
- [ ] 4.3 合入 develop 后运行 `/opsx:archive align-spring-multipart-with-file-upload-limit`
