## 1. 数据库迁移

- [x] 1.1 创建 Flyway 迁移 `Vxx__uploaded_file_session_fk.sql`：重建 `uploaded_file` 表以添加 `FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL`。清理 session 已不存在的孤儿行后再建表

## 2. 后端：资源目录 DTO 和 Service

- [x] 2.1 创建 5 个资源 DTO：`DashboardResourceDto`、`ReportResourceDto`、`ExportResourceDto`、`SemanticResourceDto`、`UploadResourceDto`（在 `server/data-talk-adapter/src/main/java/com/datatalk/dto/`）
- [x] 2.2 创建 `ResourceDirectoryService` 在 `application` 层：实现 5 个资源目录的文件系统扫描逻辑（dashboard/report 需 join `file_artifact` 表获取 title/originSessionId；export 从文件名和 stat 推断；semantic 通过 `FsSemanticModelRepository` 读取；upload join `uploaded_file` 表）
- [x] 2.3 在 `MaintenanceController` 中添加 5 个 `GET /api/maintenance/{dashboards|reports|exports|semantic|uploads}` 列表端点
- [x] 2.4 在 `MaintenanceController` 中添加 5 个 `DELETE /api/maintenance/{dashboards|reports|exports|semantic|uploads}/{id}` 删除端点
- [x] 2.5 在 `MaintenanceController` 中扩展 `GET /api/maintenance/storage-overview`，增加 `resourceDirectories` 字段（5 个目录的 count + sizeBytes）
- [x] 2.6 运行 `cd server && mvn compile -q` 确认编译通过

## 3. 后端：资源预览 API

- [x] 3.1 创建 `ResourcePreviewService` 在 `application` 层：实现各资源类型的预览逻辑（dashboard HTML 读取、report HTML 读取、export 文件解析前 100 行、semantic YAML 读取、upload 文件 MIME 分发）
- [x] 3.2 在 `MaintenanceController` 中添加 5 个预览端点
- [x] 3.3 运行 `cd server && mvn compile -q` 确认编译通过

## 4. 后端：Export originSessionId 和 Session-Tab 联动

- [x] 4.1 修改 `DataExportService`：在创建导出（同步+异步）时记录 `originSessionId`，加入 `DataExportResult` 和 export job metadata
- [x] 4.2 修改 `SessionService.deleteRecord()`：在 session 删除前收集被删除 session 关联的资源信息（export IDs、upload IDs），返回给调用者（控制器通过 SSE 或 API 响应携带）
- [x] 4.3 运行 `cd server && mvn install -pl data-talk-application -am -DskipTests` 确认 application 层编译通过

## 5. 前端：Session-Tab 生命周期修复

- [x] 5.1 在 `stage-store.ts` 中新增 `closeSessionTabs(sessionId?: string)` 函数：通过 `tab-type-registry` 的 `scope` 字段识别 session-scoped tab，精确关闭（不传参时关闭所有 session-scoped tab；传参时只关闭指定 session 的）
- [x] 5.2 修改 `general-panel.tsx` 的 `clearAllLocalSessionResources()`：用 `closeSessionTabs()` 替换 `resetSessionResources()`
- [x] 5.3 修改 `nav-sessions.tsx` 的单 session 删除逻辑：调用 `closeSessionTabs(deletedSessionId)` 关闭关联的 session-scoped tab
- [x] 5.4 运行 `cd client && npx tsc --noEmit` 确认类型检查通过

## 6. 前端：资源目录 API Client

- [x] 6.1 在 `client/src/services/api/maintenance.ts` 中添加资源目录相关的 TypeScript 类型（5 个 DTO 接口 + API 函数：`getDashboards`、`getReports`、`getExports`、`getSemantic`、`getUploads`、`deleteDashboard`、`deleteReport`、`deleteExport`、`deleteSemantic`、`deleteUpload`、`previewDashboard`、`previewReport`、`previewExport`、`previewSemantic`、`previewUpload`）
- [x] 6.2 更新 `getStorageOverview()` 返回类型，增加 `resourceDirectories` 字段
- [x] 6.3 运行 `cd client && npx tsc --noEmit` 确认类型检查通过

## 7. 前端：存储治理资源目录 UI

- [x] 7.1 在 `maintenance-page.tsx` 中添加 `ResourceDirectoryCards` 组件：在现有 Overview cards 下方显示 5 个资源目录卡片（Dashboards、Reports、Exports、Semantic、Uploads），每个卡片显示 count + sizeBytes
- [x] 7.2 添加 `ResourceTypeTabs` 组件：5 个 Tab（Dashboard | Report | Export | Semantic | Upload），默认选中第一个有数据的
- [x] 7.3 添加 `ResourceTable` 组件：按资源类型渲染不同列定义（Dashboard: 名称/Widget数/大小/时间/操作；Report: 标题/格式badge/大小/时间/操作；Export: 文件名/格式/大小/行数/过期时间/操作；Semantic: Domain/连接/状态badge/大小/时间/操作；Upload: 文件名/MIME/大小/过期时间/操作）
- [x] 7.4 添加批量删除功能：checkbox 多选 + "批量删除" 按钮 + 确认对话框（`status.danger` 红色强调）
- [x] 7.5 运行 `cd client && npx tsc --noEmit` 确认类型检查通过

## 8. 前端：资源预览抽屉

- [x] 8.1 创建 `ResourcePreviewDrawer` 组件：根据资源类型和 content-type 选择渲染方式
- [x] 8.2 实现 `HtmlPreview`：iframe sandbox 渲染 dashboard/report HTML
- [x] 8.3 实现 `CodePreview`：Shiki 语法高亮渲染 semantic YAML 和文本类 upload
- [x] 8.4 实现 `TablePreview`：虚拟滚动表格渲染 export 前 100 行数据
- [x] 8.5 实现 `ImagePreview`：`<img>` 标签渲染图片 upload
- [x] 8.6 运行 `cd client && npx tsc --noEmit` 确认类型检查通过

## 9. 前端：i18n 翻译

- [x] 9.1 在 `client/src/i18n/messages.ts` 中添加 `maintenance.resources.*` 命名空间的翻译 key（中英文）：Tab 标签、表格列头、空状态文案、批量删除确认文案、预览抽屉标题、过期标签文案、来源会话文案等

## 10. 测试与验证

- [x] 10.1 后端：为 `ResourceDirectoryService` 编写 JUnit 5 + AssertJ 单元测试（Mock 文件系统/DB）
- [x] 10.2 后端：为 `MaintenanceController` 新增端点编写集成测试
- [x] 10.3 后端：为 Flyway 迁移编写测试（验证 FK 约束在 SQLite 下正确创建）
- [x] 10.4 前端：为 `closeSessionTabs` 编写 vitest 单元测试
- [x] 10.5 前端：为 `ResourceTable` 组件编写 vitest 单元测试（各资源类型列定义正确渲染）
- [x] 10.6 前端：为 `ResourcePreviewDrawer` 组件编写 vitest 单元测试
- [ ] 10.7 端到端：用 Playwright 验证完整链路 —— 进入存储治理 → 查看资源目录卡片 → 切换 Tab → 列表渲染 → 预览 → 删除
- [ ] 10.8 端到端：用 Playwright 验证 Session 删除后 session-scoped tab 被关闭，workspace tab 保留
- [ ] 10.9 端到端：用 Playwright 验证清空全部会话后 workspace tab 保留
