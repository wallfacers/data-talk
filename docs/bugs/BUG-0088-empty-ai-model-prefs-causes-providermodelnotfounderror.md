---
id: BUG-0088
title: ai_model_prefs 表为空导致 OpenCode 启动报 ProviderModelNotFoundError
status: open
priority: P1
source: manual-report
modules: [opencode, channel]
discovered: 2026-05-22
discoveredBy: user
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

Windows 桌面安装版 DataTalk 启动后，`ai_user_prefs` 表中保存了模型 `alibaba-coding-plan-cn/qwen3.6-plus`，但 `ai_model_prefs` 表为空（无可用 provider/model 注册记录）。OpenCode 进程启动时尝试使用该 provider+model，因 model prefs 为空导致 provider 未被注册，报 `ProviderModelNotFoundError`。

## Reproduction Steps

1. 全新安装 DataTalk Windows 桌面版（无历史数据）。
2. 启动 DataTalk，打开一个会话发送消息。
3. 后端日志出现 `ProviderModelNotFoundError: alibaba-coding-plan-cn / qwen3.6-plus`，AI 功能不可用。

## Root Cause

> **更正（2026-05-22 调查）**：标题与最初记录的"`ai_model_prefs` 表为空导致 provider 未注册"是**误判**。`ai_model_prefs` 仅是"被禁用模型的覆盖列表"（`AiModelPrefsRepositoryJdbc.disabledSet()` 只读 `enabled=0` 的行），空表 = 没有禁用任何模型，是全新安装的正常默认状态，与本 BUG 无因果关系。

**真实根因：打包后的 Windows 全新安装从未成功获取 models.dev 模型目录，导致 OpenCode 无法解析任何 provider/model。**

调查链路：

1. `alibaba-coding-plan-cn/qwen3.6-plus` 是用户在「供应商」设置里手动配置的阿里云 provider（DataTalk 仅通过 `AiSettingsController.putCredentials → oc.putAuth` 写入 `auth.json` 凭证，**不写**任何模型定义）。
2. `alibaba-coding-plan-cn` 是 **models.dev 目录里真实存在的 provider**（含 `qwen3.6` 系列模型）。OpenCode 启动时从 models.dev 拉取完整 provider/model 目录，缓存到 `<cache>/opencode/models.json`（Linux：`~/.cache/opencode/models.json`；Windows：`%LOCALAPPDATA%\opencode\models.json`），provider 的模型 schema 全部来自此目录，而非 `auth.json`、也非 `opencode.json`。
3. 发消息时 `ChannelService.java:119-122` 把 `current_model` 拆成 `{providerID, modelID}` 转发给 OpenCode；OpenCode 在其模型目录里查 `alibaba-coding-plan-cn/qwen3.6-plus`。
4. **环境差异（已验证）**：WSL 开发机 `~/.cache/opencode/models.json` 已存在（2MB，确含 `alibaba-coding-plan-cn`）→ 解析成功；打包后的 Windows 全新安装该缓存从未生成（首启时 models.dev 网络拉取失败/未完成）→ OpenCode 无模型目录 → 对**任何** provider/model 都抛 `ProviderModelNotFoundError`，alibaba 只是恰好被选中的那个。
5. OpenCode 二进制及其 `node_modules` **不自带**任何离线 models.dev 快照，完全依赖运行时网络拉取——这是首启脆弱点。

> **待 Windows 现场确认**：models.dev 拉取失败的具体原因（无网络 / 代理 / 防火墙 / TLS 证书 / `stripProxyEnv` 策略）需读取 `C:\Users\<user>\AppData\Roaming\com.datatalk.app\logs\backend.log` 及检查 `%LOCALAPPDATA%\opencode\models.json` 是否存在来定位。无论何种原因，"打包内置 models.dev 快照并在 bootstrap 时按需注入 OpenCode 缓存"均可兜底。

## Expected vs Actual

- **Expected**: 用户首次安装后应该有默认可用的 AI 模型，或者在没有配置模型时给出明确的引导提示。
- **Actual**: 直接报 `ProviderModelNotFoundError`，用户无法使用 AI 功能。

## Analysis Notes

- WSL 环境下正常工作，因为 WSL 的 DataTalk 数据库中 `ai_model_prefs` 有正确的 provider/model 数据。
- Windows 安装版是新数据库，`ai_model_prefs` 从未被填充过。
- ~~（已废弃的早期方向：写 `ai_model_prefs` / 迁移脚本插默认 provider）~~ 与真因无关，见上方更正。

## Fix（已实现，待 Windows 现场冒烟验证）

**思路**：把 models.dev 目录快照内置进安装包，在 OpenCode 启动前按需注入其缓存，使全新/离线安装不再依赖首启联网拉取。与 WSL 行为一致。

实现要点：

1. 内置快照资源：`server/data-talk-infrastructure/src/main/resources/opencode/models.json.gz`（~180KB gzip，含全部 models.dev provider，已提交）。
2. `OpenCodeBinaryResolver.ensureModelsCatalog()`：
   - 缓存路径用与 OpenCode **完全一致**的纯 XDG 逻辑计算：`{XDG_CACHE_HOME || ~/.cache}/opencode/models.json`。经二进制反编译确认 OpenCode 的 `Path` 模块无平台分支，三平台同构，故 Java 端单一计算即跨平台正确（Windows 默认落到 `%USERPROFILE%\.cache\opencode\models.json`）。
   - 仅当该文件缺失或 `< 1KB`（视为损坏）时才注入；已存在的有效副本（OpenCode 联网刷新得到的）不覆盖。
3. `OpenCodeProcessManager.doStart()` 在 `ensureNodeModules()` 之后、进程启动前调用 `ensureModelsCatalog()`。

**关于"依赖包"**：provider SDK `@ai-sdk/openai-compatible`（`alibaba-coding-plan-cn` 的 `npm` 字段）已分别内置于 ① 140MB opencode 二进制（编译期打入，WSL 的 node_modules 无此包却能用即为证）② `opencode-deps` 构建依赖（`src/build/opencode-deps/package.json` 已列为依赖打入 `opencode-deps.tar.gz`）。故离线唯一缺口仅 `models.json`，无需额外打包。

**验证**：
- 单元测试 `OpenCodeBinaryResolverDepsTest`（缺失注入 / 过小重注入 / 有效跳过）全绿。
- 集成：将 `XDG_CACHE_HOME` 指向仅含注入快照的全新目录启动 OpenCode，`/provider` 成功列出 `alibaba-coding-plan-cn`。
- **仍需**：在打包后的 Windows 全新安装上冒烟，确认首启即可发消息、不再 `ProviderModelNotFoundError`。

**Data Source Type Compatibility Gate**：N/A — 本变更针对 OpenCode AI provider/model 目录加载，不涉及数据库/数据源类型。

## Environment

- OS: Windows 11 Pro 10.0.26200
- Install: fresh install, no prior database
- Backend: Spring Boot 3.5, Java 21
- OpenCode: v1.14.41
