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

`ai_user_prefs` 表中的 `current_model` 记录了 `alibaba-coding-plan-cn/qwen3.6-plus`，但 `ai_model_prefs` 表没有任何行。DataTalk 后端在初始化 OpenCode 会话时将 `current_model` 传递给 OpenCode，但 OpenCode 侧没有对应的 provider 注册信息（因为 `ai_model_prefs` 为空，provider 列表从未同步），导致模型查找失败。

## Expected vs Actual

- **Expected**: 用户首次安装后应该有默认可用的 AI 模型，或者在没有配置模型时给出明确的引导提示。
- **Actual**: 直接报 `ProviderModelNotFoundError`，用户无法使用 AI 功能。

## Analysis Notes

- WSL 环境下正常工作，因为 WSL 的 DataTalk 数据库中 `ai_model_prefs` 有正确的 provider/model 数据。
- Windows 安装版是新数据库，`ai_model_prefs` 从未被填充过。
- 可能的修复方向：
  1. 后端在首次启动时从 OpenCode 获取可用 provider/model 列表并写入 `ai_model_prefs`。
  2. 前端在检测到 `ai_model_prefs` 为空时引导用户选择 AI provider/model。
  3. 数据库迁移脚本中插入默认 provider/model 配置。

## Environment

- OS: Windows 11 Pro 10.0.26200
- Install: fresh install, no prior database
- Backend: Spring Boot 3.5, Java 21
- OpenCode: v1.14.41
