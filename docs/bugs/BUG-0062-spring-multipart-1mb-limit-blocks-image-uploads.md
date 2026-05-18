---
id: BUG-0062
title: Spring 默认 multipart 上限 1MB 阻止 >1MB 图片上传，比前端 50MB 限制和 FileUploadController 50MB 检查严
status: open
priority: P2
source: e2e
modules: [server, file-upload, config]
discovered: 2026-05-18
discoveredBy: agent
testRunId: openspec/changes/batch-image-attachments-via-fileparts/ (E2E run)
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

`FileUploadController.MAX_SIZE_BYTES = 50 * 1024 * 1024`（50MB），`client/src/features/session/useFileUpload.ts MAX_SIZE_BYTES = 50 * 1024 * 1024`（前端也是 50MB）。但 Spring Boot 默认 `spring.servlet.multipart.max-file-size=1MB`，**1MB 即触发 `MaxUploadSizeExceededException`**，请求在 multipart 解析层被拒，连不到 Controller。

实际表现：用户上传 1MB+ 图片时前端 chip 立即变 error，文案 "Error: Upload failed"。后端日志：

```
WARN ... Resolved [org.springframework.web.multipart.MaxUploadSizeExceededException: Maximum upload size exceeded]
```

## Reproduction Steps

1. 启动后端默认 profile
2. 任一 session 上传一张 >1MB 的真实截图
3. chip 进入 disabled error 状态，文案 "Error: Upload failed"
4. 看后端日志可见 `MaxUploadSizeExceededException`

## Expected vs Actual

- **Expected**：≤ 50MB 的文件均能上传（与前后端代码层 `MAX_SIZE_BYTES = 50MB` 一致）
- **Actual**：>1MB 即拒，与代码意图不符

## Suggested Fix

在 `server/data-talk-adapter/src/main/resources/application.yml` 顶层 `spring:` 节点下添加：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

数值应保持与 `FileUploadController.MAX_SIZE_BYTES` 同源（取常量或在配置中读同一 property）。

## 相关

- 与 `batch-image-attachments-via-fileparts` change 协同发现：该 change 计划支持 5MB 单消息图片总和（`IMAGE_PAYLOAD_HARD_LIMIT_BYTES`），但当前 Spring 配置使单图上传上限就只有 1MB。修复后用户才能真正用到批量大图功能
