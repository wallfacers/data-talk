## Why

`FileUploadController.MAX_SIZE_BYTES = 50 * 1024 * 1024`（50MB），前端 `useFileUpload.ts MAX_SIZE_BYTES = 50 * 1024 * 1024`（也是 50MB），代码层意图明确：≤ 50MB 的文件应能上传。

但 Spring Boot 默认 `spring.servlet.multipart.max-file-size=1MB`，**1MB 即触发 `MaxUploadSizeExceededException`**，请求在 multipart 解析层被拒，连不到 Controller 就被 4xx。

实际表现：用户上传 >1MB 图片时前端 chip 立即变 error、文案 "Error: Upload failed"，后端日志：

```
WARN ... Resolved [org.springframework.web.multipart.MaxUploadSizeExceededException: Maximum upload size exceeded]
```

`batch-image-attachments-via-fileparts` change 设计的 `IMAGE_PAYLOAD_HARD_LIMIT_BYTES = 5MB` 单消息图片总和上限，在当前 Spring 配置下根本无法触达 — 单图 1MB 就先被拒了。

参见 [BUG-0062](../../../docs/bugs/BUG-0062-spring-multipart-1mb-limit-blocks-image-uploads.md)（P2, status open, 由 `batch-image-attachments-via-fileparts` E2E 验证阶段发现）。

## What Changes

- 在 `server/data-talk-adapter/src/main/resources/application.yml` 顶层 `spring:` 节点下增加 `servlet.multipart.max-file-size: 50MB` 和 `servlet.multipart.max-request-size: 50MB`，与 `FileUploadController.MAX_SIZE_BYTES` 同源对齐
- 添加注释说明这两个数值必须跟 Controller 的 `MAX_SIZE_BYTES` 保持一致（暂不引入读同一 property 的代码改动 — 简单常量已足够）
- 在 BUG-0062 文档追加 "Fix Plan" 引用本 change

## Capabilities

### Modified Capabilities

- `chat-message-attachments`: multipart 上传链路的有效字节上限 SHALL 与 `FileUploadController.MAX_SIZE_BYTES`（50MB）一致；具体到图片附件，使 `batch-image-attachments-via-fileparts` 引入的 5MB `IMAGE_PAYLOAD_HARD_LIMIT_BYTES` 在 multipart 层有可达空间

## Impact

- **代码**：仅 `application.yml` 4 行追加 + 注释；无 Java 改动
- **运行时**：单文件上传上限从 1MB 提升到 50MB；request 总上限同步 50MB（防止多文件同时上传穿透 request 层级限制）
- **测试**：现有 `FileUploadControllerIT` 假定 50MB 边界，本变更让运行时与测试假设一致
- **安全**：50MB 上限跟 Controller 一致，未放宽超出代码已 enforce 的边界
- **关联 BUG**：close BUG-0062；解锁 `batch-image-attachments-via-fileparts` 的 5MB image payload 路径在生产环境真实可用
