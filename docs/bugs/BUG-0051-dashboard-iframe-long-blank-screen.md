---
id: BUG-0051
title: Dashboard tab iframe 加载长时间白屏（loader 早卸 + 外网 CDN）
status: fixed
priority: P1
source: manual-report
modules: [dashboard, stage, bezel]
discovered: 2026-05-16
discoveredBy: human
testRunId: null
fixCommit: pending
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

Stage 中打开 Dashboard tab 时，加载存在多秒可见白屏。两段问题叠加：
1. `DashboardIframeShell` 在 HTML 字节流到达瞬间就卸下 `TabContentLoader`，但此时 iframe 内 echarts/字体/widget 数据还没加载渲染完毕。
2. iframe srcDoc 内的 echarts 从 `https://cdn.jsdelivr.net` 拉取，sandbox=`allow-scripts`（无 `allow-same-origin`）的 opaque-origin iframe 无法共享父页 HTTP 缓存；弱网/断网 / 内网部署时该请求会卡到浏览器默认超时。

## Reproduction Steps

1. 启动 backend + Tauri client
2. 在 chat 让 AI 生成 bezel 大屏（任一行业模板）
3. 点击 preview / 打开到工作台 → Stage 中新增 Dashboard tab
4. 切到该 tab，观察从 tab 激活到 iframe 完整渲染之间的白屏时间
5. （加剧）断开外网或人为模拟慢速 CDN，白屏可持续 ≥10s 甚至不渲染

## Expected vs Actual

- **Expected**: tab 内全程显示统一 loading 动画，iframe 渲染完成无缝衔接
- **Actual**: HTML 拿到后 loader 立刻消失，iframe 在加载 echarts/字体/数据期间是空白；外网不可达时直接长白屏

## Environment

- Backend commit: 59d4ea98 (develop)
- Frontend commit: develop
- OS / Browser: WSL2 Linux / Tauri webview

## Evidence

- 代码定位：`client/src/features/dashboard/iframe-shell.tsx:52-54`（loader gating 由 `html === null` 决定，而非 `status === 'ready'`）
- CDN 依赖：12 份 bezel 模板 + `compile-rules.md` 全部硬编码 `https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js`
- Sandbox 设定：`<iframe sandbox="allow-scripts" srcDoc=...>` —— opaque origin，无法走父页缓存

## Root Cause

**主因（前端 gating）**：`iframe-shell.tsx` 的渲染分支

```tsx
if (html === null) return <TabContentLoader />
return <iframe srcDoc={html} ... />
```

`html` 到位的那一刻 loader 卸下，但 iframe 离 `'ready'` postMessage 还差：解析 srcDoc → 加载 CDN echarts (~280KB gz) → 加载 Google Fonts → JS 初始化 → 通过 `/api/dashboards/{id}/widgets/{wid}/data` 取数 → ECharts 渲染。这段窗口期用户面对的是白色的 iframe 视口。

**次因（CDN 外部依赖）**：sandbox 是 opaque origin，浏览器 HTTP 缓存按 origin 分键，父页面 preconnect 也救不了它。第一次进入大屏每次都要走完 CDN 网络往返。Tauri 桌面端在弱网/内网/断网场景下这是直接的可用性故障。

## Fix

**主修复（前端）**：`client/src/features/dashboard/iframe-shell.tsx`

把 loader 改成绝对定位覆盖层，gating 改为 `status !== 'ready'`：

```tsx
return (
  <div className="relative w-full h-full">
    {html !== null && (
      <iframe ref={ref} sandbox="allow-scripts" srcDoc={html} ... data-status={status} />
    )}
    {status !== 'ready' && (
      <div className="absolute inset-0"><TabContentLoader /></div>
    )}
  </div>
)
```

iframe 在 `html` 到位即挂载开始加载，loader 直到 `postMessage({type:'ready'})` 才卸下，避免白屏暴露。

**辅修复（后端 CDN 本地化）**：

1. 资源落地：`server/data-talk-adapter/src/main/resources/static/bezel/echarts.min.js`（echarts 5.5.0，sha256 `42f8329d989b6f6539dd2b15bbdf0d82025762ac112fbb60dc57b27d7bcf3946`），Spring Boot 默认静态资源处理暴露在 `/bezel/echarts.min.js`。

2. `DashboardController.serveHtml` 新增一次 `String#replace`：把所有 `https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js`（同时出现在 CSP `script-src` 和 `<script src>` 中）一次性替换为 `<origin>/bezel/echarts.min.js`。AI emit 阶段仍然提交 jsdelivr URL，`BezelHtmlValidator.ALLOWED_CDN` 校验保持不变。

## Verification

- 新增 vitest 用例（`iframe-shell.test.tsx`）：
  - "keeps loader overlay above iframe until ready signal"：HTML 到位后断言 iframe + 角色为 `status` 的 loader 同时在 DOM
  - "removes loader overlay after ready postMessage"：ready 后 loader 卸下
- 扩展 IT 用例（`DashboardControllerIT#serveHtmlRewritesEchartsCdnToLocalAsset`）：promote 一段含 jsdelivr 引用的 HTML，断言 serveHtml 出口已全量替换为 `http://localhost.../bezel/echarts.min.js`（CSP + script 标签）
- 完整验证：`cd client && npx tsc --noEmit && npx vitest run iframe-shell` + `cd server && mvn -pl data-talk-adapter -am compile && mvn -pl data-talk-adapter test -Dtest=DashboardControllerIT`

## Notes

- Google Fonts（Noto Serif SC）尚未本地化，弱网时仍会触发字体回退抖动，但已不阻塞内容渲染（首屏会用 fallback 字体显示）。下一轮可与 echarts 同样的方式落地。
- `compile-rules.md` 和 bezel 模板继续保留 jsdelivr URL：AI 生成阶段保持现状，serve 阶段透明改写，无需 AI 改动。
- `BezelHtmlValidator.ALLOWED_CDN` 保持只有 jsdelivr，确保 AI 不会偷偷换 CDN 来源；本地化属于 serve 出口的纯重写。
