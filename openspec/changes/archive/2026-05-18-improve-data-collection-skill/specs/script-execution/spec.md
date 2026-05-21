## MODIFIED Requirements

### Requirement: 用户手动停止脚本

系统 SHALL 在用户通过 UI Stop 按钮停止脚本时，同步更新后端 `script_run` 记录为 `cancelled`。

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
