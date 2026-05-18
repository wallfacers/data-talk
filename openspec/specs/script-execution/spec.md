## ADDED Requirements

### Requirement: Script run lifecycle management

系统 SHALL 管理脚本运行的完整生命周期：创建 → 运行 → 完成/失败/取消。每次运行产生一条 `script_run` 记录，包含脚本内容、语言、状态、输出、耗时、写入行数。

#### Scenario: 创建并启动脚本运行

- **GIVEN** 用户在 script_editor tab 中编辑好 Python 脚本，并选择了一个目标数据源连接
- **WHEN** 用户点击 Run 按钮
- **THEN** 后端签发一次性 ScriptToken（UUID，TTL=10min，绑定 connectionId 和 runId）
- **AND** 后端创建 `script_run` 记录，status=`running`
- **AND** Tauri 端 spawn 子进程，将 `DT_BACKEND_URL`、`DT_SCRIPT_TOKEN`、`DT_CONNECTION_ID` 作为环境变量注入
- **AND** 前端收到 `ScriptRunStarted` 事件，控制台面板开始接收输出

#### Scenario: 脚本正常完成

- **WHEN** 子进程退出码为 0
- **THEN** 前端收到 `ScriptRunCompleted` 事件（exitCode=0, durationMs, rowsWritten）
- **AND** 后端更新 `script_run` 记录 status=`completed`
- **AND** 控制台面板显示 `Process finished with exit code 0`

#### Scenario: 脚本执行失败

- **WHEN** 子进程退出码非 0 或进程被信号终止
- **THEN** 前端收到 `ScriptRunCompleted` 事件（exitCode≠0）
- **AND** 后端更新 `script_run` 记录 status=`failed`，记录 error_message
- **AND** 控制台面板显示 stderr 输出和退出码

#### Scenario: 用户点击 Stop 按钮

- **GIVEN** 脚本正在运行中
- **WHEN** 用户点击 Stop 按钮
- **THEN** Tauri 端发送 SIGTERM 给子进程（等待 5s），超时则 SIGKILL
- **AND** 前端 MUST 在发送 SIGTERM 后立即调用 `POST /api/script/{runId}/complete`，传入 `exitCode=-1` 和当前控制台输出
- **AND** 后端更新 `script_run` 记录 status=`cancelled`
- **AND** 控制台面板显示 `Process terminated by user`

#### Scenario: Stop 后端调用失败不阻塞 UI

- **GIVEN** 用户点击 Stop 按钮后，后端 `runComplete` 调用网络异常
- **WHEN** 网络不可达或后端返回错误
- **THEN** 前端 catch 异常，控制台追加提示 "Failed to notify server of cancellation"
- **AND** 本地 executeStatus 仍然正常流转 `running` → `idle`
- **AND** `script_run` 记录保持 `running` 状态（后续可通过 TTL 清理或手动修正）

### Requirement: Script stdout/stderr 流式输出

系统 SHALL 将脚本运行时的 stdout 和 stderr 实时流式回传到前端控制台面板。

#### Scenario: stdout 输出流式显示

- **GIVEN** 脚本正在运行
- **WHEN** 脚本调用 `print()` 或 `console.log()` 输出内容
- **THEN** Tauri 通过 event 将输出内容（带 channel 标识 stdout/stderr）发送到前端
- **AND** xterm.js 控制台实时追加显示，支持 ANSI escape codes 渲染

#### Scenario: ANSI 色彩渲染

- **WHEN** 脚本输出包含 ANSI escape codes（如 Python `colorama` / `rich` 库输出）
- **THEN** xterm.js 正确渲染彩色输出
- **AND** 颜色映射到 DESIGN.md semantic tokens：Red→status.error, Green→status.success, Yellow→status.warning, Blue→accent.primary, Cyan→chart-cyan, Magenta→chart-pink
- **AND** 切换 dark/light 主题时，控制台颜色自动适配

### Requirement: 环境检测

系统 SHALL 在首次使用脚本功能时检测用户本地 Python 和 Node.js 环境。

#### Scenario: 检测到 Python 和 Node.js

- **WHEN** 用户首次打开 script_editor tab 或点击运行
- **THEN** Tauri 端检测 PATH 中 `python3` 和 `node` 的可用性
- **AND** 返回各语言的版本号（如 `{ python: "3.12.0", node: "20.11.0" }`）
- **AND** 语言选择器只显示已检测到的语言

#### Scenario: 未检测到任何运行时

- **WHEN** Tauri 检测到 PATH 中既无 `python3` 也无 `node`
- **THEN** 编辑器区域显示安装引导面板，提供 Python 和 Node.js 官方下载链接
- **AND** Run 按钮置为 disabled 状态

### Requirement: ScriptRun 持久化

系统 SHALL 将每次脚本运行记录持久化到 SQLite `script_run` 表。

#### Scenario: 运行记录字段完整性

- **WHEN** 脚本运行结束（无论成功/失败/取消）
- **THEN** `script_run` 表中该条记录 MUST 包含：id, script_content, language, status, exit_code, stdout_text, connection_id, target_table, rows_written, name, created_by_kind, created_by_session_id, error_message, started_at, finished_at, duration_ms

#### Scenario: 查询历史运行记录

- **WHEN** 用户打开 script library tab
- **THEN** 列出所有 script_run 记录，按 started_at 降序排列
- **AND** 每条记录显示：名称、语言、状态、耗时、写入行数、开始时间

### Requirement: DtEvent script 事件类型

系统 SHALL 在 DtEvent sealed interface 中新增 3 个 script 相关事件类型。

#### Scenario: 新增事件类型

- **WHEN**DtEvent sealed interface 被加载
- **THEN** 包含以下新增 record 类型：
  - `ScriptRunStarted(runId, language, connectionId)`
  - `ScriptRunOutput(runId, output, channel)`
  - `ScriptRunCompleted(runId, exitCode, durationMs, rowsWritten)`
- **AND** `typeName()` exhaustive switch 包含对应 case 分支

#### Scenario: 旧 ingestion 事件类型已移除

- **WHEN**DtEvent sealed interface 被加载
- **THEN** 不包含任何 `Ingestion` 前缀的 record 类型
- **AND** `typeName()` exhaustive switch 不包含 `ingestion.` 前缀的分支
