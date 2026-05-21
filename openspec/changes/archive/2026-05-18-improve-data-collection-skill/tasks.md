## 1. 严重缺陷修复：i18n Action 描述密钥

- [x] 1.1 在 `server/data-talk-adapter/src/main/resources/i18n/messages.properties` 中新增 3 个 key：`action.script_run.description`、`action.script_stop.description`、`action.script_list.description`，填写英文描述
- [x] 1.2 在 `server/data-talk-adapter/src/main/resources/i18n/messages_zh_CN.properties` 中新增对应的 3 个 key，填写中文描述
- [x] 1.3 验证：`cd server && mvn test -pl data-talk-adapter -Dtest=AgentPromptContractTest`（骨架体积、action 描述完整性）

## 2. 严重缺陷修复：UI Stop 按钮联动后端

Design Inputs (from `client/DESIGN.md`): 不涉及视觉变更，仅修复行为逻辑。

- [x] 2.1 修改 `useScriptExecute.stop()`：`tauriStopScript` 后调用 `scriptApi.runComplete(runId, { exitCode: -1, stdoutText })` 更新后端 CANCELLED 状态；catch 异常时追加控制台提示但不阻塞本地状态流转
- [x] 2.2 验证：`cd client && npx tsc --noEmit`

## 3. 后端脚本运行器测试补齐

- [x] 3.1 编写 `ScriptTokenStoreTest`：验证 token 签发（UUID 格式、10min TTL）、验证成功/失败、撤销、过期 token 拒绝、connectionId 绑定校验、runId 绑定校验
- [x] 3.2 编写 `ScriptRunServiceTest`：验证 prepareRun（创建 run 记录、签发 token）、completeRun（成功/失败/取消状态更新）、cancelRun、updateRowsWritten、findById（含不存在的 id）、listRuns（按 connectionId 过滤、按时间降序）
- [x] 3.3 编写 `ScriptDataBatchServiceTest`：验证 createBatch（建表+首批写入）、addBatch（追加批次）、closeBatch（返回总行数）、session 超时（TTL=5min）自动清理
- [x] 3.4 编写 `ScriptRunRepositoryTest`：验证 save（SQLite 持久化）、findById、findByConnectionId（排序）、updateStatus、updateRowsWritten
- [x] 3.5 编写 `RunScriptActionHandlerTest`：WireMock + FakeOpenCodeServer，验证 `datatalk_script_run` action 的 inputSchema/outputSchema 结构、环境验证逻辑、token 签发、ScriptRunStarted 事件发射
- [x] 3.6 编写 `StopScriptActionHandlerTest`：验证 cancel 流程（RUNNING→CANCELLED 状态流转、已完成的 run 拒绝取消）
- [x] 3.7 编写 `ListScriptRunsActionHandlerTest`：验证列表返回（含分页、排序）、空结果
- [x] 3.8 编写 `ScriptControllerTest`：验证 `POST /api/script/run-prepare`（正常/未检测到环境）、`POST /api/script/{runId}/complete`（正常/无效 runId）、`GET /api/script/runs`（正常/空/无效 connectionId）
- [x] 3.9 编写 `ScriptDataControllerTest`：验证 `POST /api/script-data/write`（含 token 校验、建表、批量写入、返回 targetTable 更新）、`POST /api/script-data/batch`（流式写入完整流程）、token 无效/过期/connectionId 不匹配的错误响应
- [x] 3.10 验证：后端测试全部通过（application: 44 tests, adapter: 66 tests）

## 4. 前端脚本模块测试补齐

Design Inputs (from `client/DESIGN.md`): 测试文件使用 vitest，组件测试使用 `@testing-library/react`，store 测试直接调用 Zustand actions。

- [x] 4.1 编写 `useScriptExecute.test.ts`：验证 execute 流程（prepareRun → 设置 running → Tauri runScript → 监听输出 → onCompleted 回调 → runComplete）、stop 流程（tauriStopScript + runComplete(-1)）、stop 时 runComplete 异常不阻塞状态流转
- [x] 4.2 编写 `script-workbench-store.test.ts`：验证 per-tab 状态隔离、executeStatus 状态机流转（idle→running→success|error）、consoleOutput 追加与冻结、envInfo 设置
- [x] 4.3 编写 `ScriptConsolePanel.test.tsx`：验证 xterm.js 初始化（terminal 创建、fitAddon 加载）、控制台输出追加（stdout/stderr 着色）、主题切换（dark/light theme 对象正确传递）
- [x] 4.4 编写 `ScriptToolbar.test.tsx`：验证 Run 按钮 disabled 条件（无环境/无连接/running 中）、Stop 按钮 visible 条件、语言选择器过滤已检测到的运行时
- [x] 4.5 验证：前端 tsc --noEmit 零错误，99 tests 全部通过

## 5. xterm.js 控制台主题切换

Design Inputs (from `client/DESIGN.md`):
- Dark theme: `bg.canvas`=neutral.900 (`#1a1a19`), `text.primary`=neutral.100 (`#f1f1ef`)
- Light theme: `bg.canvas`=neutral.25 (`#fcfcfb`), `text.primary`=neutral.800 (`#34322d`)
- ANSI 颜色映射：Red→status.danger, Green→status.success, Yellow→status.warning, Blue→accent.primary, Cyan→sky, Magenta→品红色
- 使用 `term.options.theme = newTheme` 切换，不得 dispose + 重建
- 主题切换无闪烁，终端缓冲保留

- [x] 5.1 修改 `ScriptConsolePanel`：接受当前 theme 参数，构建 dark/light 两套 `ITerminalOptions.theme` hex 值配置，监听 theme 变化时调用 `term.options.theme = newTheme`
- [x] 5.2 在 dark/light theme 对象中配置 ANSI 16 色映射（Black/Red/Green/Yellow/Blue/Magenta/Cyan/White 及其 Bright 变体），dark 用浅色值，light 用深色值
- [x] 5.3 验证：tsc --noEmit 零错误，测试通过

## 6. data-collection skill 配方补充

- [x] 6.1 新建 `recipes/python-rest-pagination.md`：requests 分页遍历示例，覆盖 page number、offset/limit、cursor/next 三种模式，含速率限制和错误处理，≤80 行
- [x] 6.2 新建 `recipes/python-html-parse.md`：BeautifulSoup HTML 解析示例，含表格提取、列表提取、CSS 选择器，解析后通过 write API 写入数据库，≤80 行
- [x] 6.3 新建 `recipes/python-csv-parse.md`：csv.DictReader 读取 + 批量写入示例，含流式写入模式（batch/close），≤80 行
- [x] 6.4 新建 `recipes/python-auth-patterns.md`：Bearer Token / API Key / Basic Auth 三种认证模式，凭证从环境变量读取，禁止硬编码，≤80 行
- [x] 6.5 新建 `recipes/python-stream-download.md`：requests stream=True 大文件下载 + iter_lines 解析 + 分批 batch write，含进度日志，≤80 行
- [x] 6.6 验证：SkillRoutingContractTest 通过（确认新配方文件可被 AI 路由系统正确索引）

## 7. targetTable 回写

- [x] 7.1 在 `ScriptRunRepository` 中新增 `updateTargetTable(runId, tableName)` 方法（SQLite UPDATE）
- [x] 7.2 在 `ScriptDataController.write()` 中，写入成功后调用 `updateTargetTable`（仅当当前 targetTable 为 null 时）；流式写入 `batch`（batchIndex=0）同理
- [x] 7.3 编写 targetTable 回写测试（在 `ScriptDataControllerTest` 和 `ScriptDataWriteServiceTest` 中补充）
- [x] 7.4 验证：后端测试全部通过

## 8. 清理：CLAUDE.md 幽灵引用 + 残留文件

- [x] 8.1 删除 CLAUDE.md 中 "Ingestion Artifact Path Convention" 章节（引入已不存在的 `IngestionPayloadFetcher`、`IngestionConfirmedToken`）
- [x] 8.2 删除 CLAUDE.md 中 "Ingestion E2E Profile" 章节（引入已不存在的 `tests/e2e/ingestion-*.spec.ts`）
- [x] 8.3 删除残留 `.opencode/skills/data-ingestion/` 目录（server 和 server/data-talk-adapter 两处）
- [x] 8.4 验证：AgentPromptContractTest 通过（skill 引用闭合检查）

## 9. 集成验证

- [x] 9.1 后端验证：application 44 tests + adapter 66 tests = 110 tests, 0 failures
- [x] 9.2 前端验证：tsc --noEmit 零错误, 99 tests passed (4 files)
