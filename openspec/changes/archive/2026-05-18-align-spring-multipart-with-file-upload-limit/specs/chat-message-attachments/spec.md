## ADDED Requirements

### Requirement: multipart 上传上限与 Controller 业务上限对齐

`spring.servlet.multipart.max-file-size` 和 `spring.servlet.multipart.max-request-size` SHALL 在所有 profile 中 ≥ `FileUploadController.MAX_SIZE_BYTES`（当前 50MB）。当代码常量调整时，配置数值 MUST 同步调整。

实际表现：用户上传 ≤ 50MB 单文件时 SHALL NOT 因 Spring multipart 默认 1MB 上限被提前拒（在 multipart 解析层抛 `MaxUploadSizeExceededException`），SHALL 进到 Controller 层走业务校验路径。

#### Scenario: 上传 5MB 图片成功进 Controller

- **GIVEN** 后端使用 default profile 启动，`FileUploadController.MAX_SIZE_BYTES = 50MB`
- **WHEN** 用户上传一张 5MB 的真实截图
- **THEN** Spring multipart 解析层 SHALL NOT 抛 `MaxUploadSizeExceededException`
- **AND** 请求 SHALL 进入 `FileUploadController.upload` 方法
- **AND** Controller 业务校验 SHALL 通过（5MB < 50MB）
- **AND** 文件 SHALL 落盘至 `~/.data-talk/uploads/<fileId>/`
- **AND** 前端 chip SHALL 显示 `done` 状态

#### Scenario: 上传 60MB 文件按业务上限被拒

- **GIVEN** 后端使用 default profile 启动
- **WHEN** 用户上传一个 60MB 文件
- **THEN** 请求 SHALL 在 multipart 解析层（max-file-size=50MB）或 Controller 业务校验层被拒
- **AND** 响应 SHALL 是结构化错误，而非 500 内部错误
- **AND** 临时文件 SHALL NOT 残留在磁盘上

#### Scenario: batch-image-attachments-via-fileparts 的 5MB hard limit 可达

- **GIVEN** 前端按 `IMAGE_PAYLOAD_HARD_LIMIT_BYTES = 5MB` 阻止大图 payload
- **AND** 用户上传两张各 3MB 的图片
- **WHEN** 前端尝试发送
- **THEN** 单图上传 SHALL 都成功（multipart 上限 50MB ≥ 3MB）
- **AND** 前端 SHALL 在 send 时按合计 6MB 触发 `chat.image.payloadTooLarge` toast 阻止发送
- **AND** 该 toast SHALL 来自前端 `classifyImagePayload`，而非 multipart 1MB 提前拒
