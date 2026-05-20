---
id: BUG-0079
title: 开始页草稿会话未持久化时上传附件触发 500（uploaded_file 外键失败），chip 显示 Internal Server Error
status: open
priority: P1
source: e2e-playwright
modules: [file-upload, session, chat]
discovered: 2026-05-20
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary
在开始页（brand-new 草稿会话，尚未发送第一条消息）直接附加文件时，前端 eager upload 立即 `POST /api/files/upload`，但该 session 尚未写入 `sessions` 表。后端 `uploaded_file` 插入存在指向 `sessions(id)` 的外键，触发 `SQLITE_CONSTRAINT_FOREIGNKEY`，返回 500，chip 落到 error 态并显示 "Error: Internal Server Error"。与文件类型无关（任意类型复现）。

## Reproduction Steps
1. 打开应用，停留在开始页（未发送任何消息，会话为草稿态、未持久化）。
2. 点击 "Attach files" 选择任意文件（如本次用无后缀 `create_test`，27 B）。
3. 前端 eager upload 立即发起 `POST /api/files/upload`，sessionId 为当前草稿会话 id。

对照（happy path）：先发送一条消息使会话持久化（`sessions` 行数 1→2），再附加同一文件，上传返回 200，`uploaded_file` 正常落库。

## Expected vs Actual
- **Expected**: 草稿会话下附加文件应能成功上传（或上传被推迟到会话持久化后），chip 进入 done，不应向用户暴露 500。
- **Actual**: 上传返回 500，chip 显示 "Error: Internal Server Error"，预览按钮 disabled。

## Environment
- Backend commit: fbf21e16
- Frontend commit: fbf21e16
- OS / Browser: WSL2 Linux / Chromium (playwright-cli)
- Data source: N/A（元数据 SQLite，metadata.db）

## Evidence
- 控制台错误：
  ```
  [ERROR] Failed to load resource: the server responded with a status of 500 (Internal Server Error) @ http://localhost:1420/api/files/upload:0
  ```
- 后端堆栈（tmp/backend-restart.log）：
  ```
  POST /api/files/upload ... → 200 | 1ms [ERROR] UncategorizedSQLException: PreparedStatementCallback;
  uncategorized SQLException for SQL [INSERT INTO uploaded_file
  (id, session_id, filename, mime_type, size_bytes, physical_path, analysis_json, created_at) VALUES (?,?,?,?,?,?,?,?)];
  SQL state [null]; error code [19]; [SQLITE_CONSTRAINT_FOREIGNKEY] A foreign key constraint failed
      at com.datatalk.infra.upload.JdbcUploadedFileRepository.insert(JdbcUploadedFileRepository.java:35)
      at com.datatalk.adapter.controller.FileUploadController.upload(FileUploadController.java:132)
  ```
- chip 快照片段：`button "create_test, 预览" [disabled]` + `generic: "Error: Internal Server Error"`

## Root Cause
TBD（待分流）。初步：附件 eager upload（`useFileUpload.addFiles` → `queueMicrotask(scheduleUpload)`，见 BUG-0059）在草稿会话尚未持久化时即触发；`FileUploadController.upload` 第 132 行 `uploadedFileRepo.insert` 的 `uploaded_file.session_id` 外键引用 `sessions(id)`，草稿 session 行不存在 → 外键失败。需决定策略：草稿会话首附件时 lazy-persist session，或上传放行/延迟到会话持久化后再 flush。

## Fix
TBD

## Verification
TBD

## Notes
- 本 BUG 在为「无后缀可读文件上传支持」做 E2E 时旁路发现，与该 change 无关（任意文件类型均复现）。该 change 的 happy path 已在持久化会话下端到端验证通过（`uploaded_file`: `create_test | text/x-sql | ce531495-...`，200）。
- 关联：[BUG-0059](BUG-0059-composer-enter-lag-due-to-lazy-upload-on-submit.md)（eager upload 行为来源）、[BUG-0061](BUG-0061-missing-flyway-migration-for-user-message-attachments.md)（附件持久化迁移）。
