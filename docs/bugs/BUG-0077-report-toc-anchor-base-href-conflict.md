---
id: BUG-0077
title: 报告 TOC 锚点点击后跳转到 _assets/ 404（<base href> 解析冲突）
status: fixed
priority: P1
source: e2e-playwright
modules: [report]
discovered: 2026-05-20
discoveredBy: agent
testRunId: ledger-report-quality-fixes-§8
fixCommit: pending
fixPlanRef: openspec/changes/ledger-report-quality-fixes/
duplicateOf: null
regression: true
---

## Summary

报告 HTML 顶部 `<base href="/api/reports/_assets/">` 把 `<a href="#chap-1">` 这类 fragment-only 链接解析为绝对 URL `http://<host>:8080/api/reports/_assets/#chap-1`，与当前文档 URL 路径不一致 → 浏览器视为跨文档导航 → 加载 `http://<host>:8080/api/reports/_assets/`（目录无 index） → 404，iframe 内容崩坏或跳到空白。

这是 `ledger-report-quality-fixes` 新增 TOC 锚点功能时引入的回归 —— TOC 锚点本身的 HTML 渲染正确，但点击后无法工作。

## Reproduction Steps

1. 通过任意路径打开 Report Viewer（iframe 内 `src` 指向 `/api/reports/{id}/download/html`，或本仓库 `tmp/e2e-ledger-quality/host.html` fixture）
2. 在 iframe 内点击 TOC 中任意 chapter 链接（如 "执行摘要"）
3. 浏览器控制台报错：`Failed to load resource: 404 () @ http://localhost:8080/api/reports/_assets/#chap-X`

## Expected vs Actual

- **Expected**: 点击 TOC 链接，iframe 平滑滚动到对应 chapter（`#chap-N`）
- **Actual**: 触发跨文档导航到 `_assets/` 目录 → 404 → iframe 内容丢失

## Root Cause

`ReportRenderer.toHtml` 出于 CSS / 字体 / JS 相对路径解析需要，在 `<head>` 中写入 `<base href="/api/reports/_assets/">`（或测试场景下的绝对 URL）。HTML 规范：`<a href>` 一律相对 `<base>` 解析，包括 `#fragment` 形式 —— 即 `#chap-1` 被解析为 `<base-href>/...#chap-1`，与当前文档 URL（`/api/reports/{id}/download/html`）路径不同 → 不再是同文档锚点 → 触发跨页导航。

## Suggested Fix

在 `ReportRenderer.toHtml` 输出的 `<body>` 末尾追加一段小内联 `<script>`，拦截所有 `a[href^="#"]` 点击，`event.preventDefault()` + 用 `document.getElementById(hash).scrollIntoView({behavior:'smooth'})` 完成同文档滚动。绕过 `<base>` 解析问题，无需重排 asset URL 策略。

## Regression Risk

与 BUG-0073 / BUG-0049 同源：`<base>` 与 iframe srcdoc/src 加载方式的交互边界。修复时单测覆盖：渲染产物含拦截脚本 + 单击锚点不触发导航（jsdom 限定，scrollIntoView spy）。E2E 用既有 `tmp/e2e-ledger-quality/` fixture 复跑。

## Fix Implementation

`ReportRenderer.toHtml` 渲染结束前追加 `<script>` 注入 `ANCHOR_INTERCEPTOR_SCRIPT`：

```js
document.addEventListener('click', function(e){
  var a = e.target && e.target.closest && e.target.closest('a[href^="#"]');
  if (!a) return;
  var id = a.getAttribute('href').slice(1);
  if (!id) return;
  var t = document.getElementById(id);
  if (!t) return;
  e.preventDefault();
  t.scrollIntoView({behavior:'smooth', block:'start'});
});
```

委托式监听 body 上的 click 事件，匹配 `a[href^="#"]` 时 `preventDefault()` 并自行调 `scrollIntoView`。这样浏览器永远不解析 `<base>` 拼接的绝对 URL，TOC 锚点退回到同文档滚动语义。

> ⚠️ 早期实现还附带 `history.replaceState('#'+id)` 同步 URL hash，但 sandboxed iframe 中 origin 为 `null`，与 `<base>` 解析出的 origin 不一致，触发 `SecurityError`。已删除该行 —— URL hash 同步是 nice-to-have，scroll 才是核心行为。

## Verification

**渲染层单测**（`ReportRendererTest.toc_anchor_interceptor_script_injected_to_neutralize_base_href`）：

- 渲染产物含 `ANCHOR_INTERCEPTOR_SCRIPT` 全文
- 含 `a[href^="#"]` / `preventDefault` / `scrollIntoView` 关键 token

**Playwright E2E**（fixture: `tmp/e2e-ledger-quality/host.html` + `fixture-report.html`，sandbox=allow-scripts iframe）：

- 加载 fixture 后 console 0 errors（先前 click 后会出现 `Failed to load resource: 404 () @ http://localhost:8080/api/reports/_assets/#chap-2`）
- 点击 `<a href="#chap-1">执行摘要</a>` 后 console 仍 0 errors
- iframe `document.defaultView.scrollY` 从 0 → 46（短 fixture 的最大滚动距离，证明 scrollIntoView 被调用）

## Notes

- 在 `ledger-report-quality-fixes` §8 E2E 验证阶段发现 → 当场修复并入本 change（避免遗留半截功能）
- TOC HTML 渲染（`<a href="#chap-N">` + `id="chap-N"`）本身正确，只是点击行为被 `<base>` 干扰
- 风险：未来若需要从外部链接（同域）跳转到 `#chap-N`，初始加载阶段不在拦截脚本生效后，浏览器仍会基于 `<base>` 解析 URL → 跨文档导航。当前架构下报告 HTML 仅通过 iframe `src=download/html` 加载，外部直接拼 `?#chap-N` 不会触发该场景，所以不展开
