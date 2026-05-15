---
id: BUG-0049
title: bezel 大屏 HTML iframe 内中文字符显示乱码
status: open
priority: P2
source: manual-report
modules: [dashboard, chat, opencode]
discovered: 2026-05-15
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

AI 通过 bezel skill 生成的 dashboard HTML 在 stage iframe 渲染后，所有中文字符（widget 标题、KPI 描述、轴 label、tooltip 等）显示成乱码（占位方块 / 问号 / 非预期字符）。HTML 本身的 ECharts 渲染、布局、颜色、动画都正常，仅 UTF-8 中文字面量被破坏。

## Reproduction Steps

1. chat 端要求 AI "做一个 ecommerce 大屏，按 bezel skill 的 Delivery contract，输出 dashboard 和 dashboard-html 两个 fenced block，HTML 部分基于 assets/templates/02-ecommerce.html"
2. AI 生成包含中文标题（如 "电商运营实时监控中心"）的 dashboard JSON + HTML
3. 点击 preview "打开到工作台" 触发 promote
4. stage tab 打开 → DashboardIframeShell 渲染 iframe srcDoc=HTML
5. 观察 iframe 内中文字符全部乱码

## Expected vs Actual

- **Expected**: iframe 内 widget 标题（如 "GMV 总额"、"实时订单监控"）等中文字符正常显示
- **Actual**: 中文位置显示为方块 / 问号 / 破坏的多字节序列，看起来像 UTF-8 字符被按单字节截断或重编码

## Environment

- Backend commit: 3f67eb44 (develop)
- Frontend commit: develop
- OS / Browser: WSL2 Linux / Chromium via playwright-cli

## Evidence

- BUG-0048 Verification 截图 `tmp/dashboard-fullscreen.png` — iframe 内 widget 排版完整、ECharts 真实渲染但中文标题乱码
- mount 的 `data-dashboard-html-b64` 编解码已确认走 `encodeUtf8Base64` / `decodeUtf8Base64`（兼容多字节，单字节 0xff 不会触发 atob 异常）
- 后端 `GET /api/dashboards/{id}/html` 返回 23524 字节 HTML，长度正常
- 因 iframe sandbox + srcDoc 注入，普通 HTML `<meta charset="UTF-8">` 应已被识别

## Root Cause

未定位。可疑路径（按优先级排序）：

1. **OpenCode SSE 传输环节**：AI 通过 SSE stream 写入 `dashboard-html` fenced block 时，部分多字节 chunk 在边界处被错误切分，重组后中文字面量丢失字节
2. **AI 端模板替换**：bezel skill 的 `assets/templates/NN-<industry>.html` 模板在 AI prompt 内可能以拉丁化转写或转义形式被注入到 LLM，LLM 输出时未正确恢复 UTF-8
3. **markdown 解码层**：`decorateDashboardBlocks` 读取 `<pre>` 子节点 `textContent` 时若 chat stream 在中途被插入零宽字符或 BOM，可能影响后续 base64 序列化
4. **后端落库 / 取出**：`POST /api/dashboards/promote` 落库 `dashboard_html` 字段时若按 latin-1 处理或 driver 误判 charset，再 GET 时取回的字节流已损坏

## Fix

TBD。建议逐层验证：
- (a) 在 `decorateDashboardBlocks` 写入 `data-dashboard-html-b64` 前打印 HTML 的中文片段（hex dump）确认 chat 端 DOM 文本已损坏 / 未损坏
- (b) 比对后端 `dashboard_html` 落库原文（直接 sqlite `SELECT` 看 hex）与前端发送 body 是否一致
- (c) iframe 内 HTML `<head>` 强制 `<meta charset="UTF-8">` 及外层 srcDoc 字符编码一致性

## Verification

TBD

## Notes

- 不阻塞 dashboard 主链路（iframe 真实渲染、widget 布局、ECharts 都工作）。属于 cosmetic 但用户感知严重
- 在 [BUG-0048](BUG-0048-dashboard-promote-v1-misses-html.md) Notes 中明确标记为独立 cosmetic 问题待 BUG-0049 收口
- 与 [BUG-0050](BUG-0050-dashboard-json-widgets-skeleton-only-no-data.md) 共享"中文乱码"症状，但发生在不同渲染链路（HTML iframe 渲染 vs JSON widget 渲染），应独立追踪
