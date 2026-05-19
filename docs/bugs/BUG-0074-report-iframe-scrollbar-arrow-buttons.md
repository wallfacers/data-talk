---
id: BUG-0074
title: 报告 iframe 内滚动条正三角/倒三角按钮未隐藏
status: open
priority: P2
source: manual-report
modules: [report]
discovered: 2026-05-20
discoveredBy: agent
testRunId: null
fixCommit: null
fixPlanRef: openspec/changes/fix-report-viewer-issues/
duplicateOf: null
regression: false
---

## Summary

报告查看器 tab 内嵌 iframe 加载由 `ReportRenderer` 生成的自包含 HTML。该 HTML 通过 `<link rel="stylesheet" href="styles/ledger.css">` 引用 `ledger.css`。iframe 是独立文档，不继承宿主页面的 CSS。`ledger.css` 未定义 `::-webkit-scrollbar-*` 伪元素样式，浏览器在滚动条两端渲染默认的正三角（上箭头 ▴）和倒三角（下箭头 ▾）按钮。主应用 `client/src/styles/globals.css` 已通过 `::-webkit-scrollbar-button { display: none }` 隐藏按钮，但 iframe 不受其影响。

## Reproduction Steps

1. 打开 DataTalk → 报告库 → 点击任意报告
2. 报告内容超出 iframe 高度时，观察 iframe 右侧垂直滚动条
3. 滚动条顶部和底部各有一个方形按钮，内含三角箭头

## Expected vs Actual

- **Expected**: 滚动条仅显示圆角 thumb（滑块），无箭头按钮，与主应用风格一致
- **Actual**: 滚动条两端出现浏览器默认的三角箭头按钮

## Environment

- OS / Browser: Linux / Webkit (Tauri WebKitGTK)
- iframe sandbox: `allow-scripts`（无 `allow-same-origin`，无法从宿主侧注入样式）

## Root Cause

`ledger.css`（`server/data-talk-adapter/src/main/resources/skills/ledger/assets/styles/ledger.css`）缺少 `::-webkit-scrollbar-button { display: none }` 及配套的 `::-webkit-scrollbar-*` 规则。

注意：CSS 文件位于后端 resource 目录，需 `mvn compile -pl data-talk-adapter -am` 才能复制到 `target/classes/`，而后端运行时从此处加载。仅编辑源文件不够，必须重启后端或触发热重载。

## Fix

在 `ledger.css` 追加以下规则（已于 `fix-report-viewer-issues` change 中添加至源文件，但运行时未生效）：

```css
::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background: var(--ledger-text-faint);
  background-clip: padding-box;
  border: 3px solid transparent;
  border-radius: 999px;
}
::-webkit-scrollbar-thumb:hover {
  background: var(--ledger-text-muted);
  background-clip: padding-box;
}
::-webkit-scrollbar-button {
  display: none;
}
::-webkit-scrollbar-corner {
  background: transparent;
}
```

**强制重启后端后**在 iframe 内目视确认滚动条箭头已消失。

## Verification

1. 编辑 `ledger.css` 后 `mvn compile -pl data-talk-adapter -am`
2. 重启 Spring Boot 后端
3. 打开报告，确认 iframe 内滚动条无三角按钮
4. hover 滚动条 thumb 确认颜色变深

## Notes

- 滚动条伪元素仅 Webkit/Blink 支持。Tauri（WebKitGTK）无影响。Firefox 需 `scrollbar-width: thin` + `scrollbar-color` 属性，当前无 Firefox 目标可后续补充。
- 原 change `fix-report-viewer-issues` 中已添加 CSS 至源文件，但编译/重启步骤未完成，故 BUG 仍为 open。
