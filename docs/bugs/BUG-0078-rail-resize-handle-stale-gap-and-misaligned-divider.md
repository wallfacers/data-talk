---
id: BUG-0078
title: 报告库 tab 下 rail 右边出现 4px 米灰条 + hover 分割线瞬时显双线（resize handle 几何错位）
status: fixed
priority: P2
source: manual-report
modules: [stage]
discovered: 2026-05-20
discoveredBy: human
testRunId: null
fixCommit: pending
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

stage left-rail 右侧 resize handle 用 `absolute right-0 z-10 w-2 translate-x-1/2` 实现把"鼠标可拖区"骑跨到 rail 右边界上，但 (1) `z-10 + translate-x-1/2 + bg-transparent` 把 handle 提成独立合成层，遮蔽了下层 row/group-header 的最右 4px paint，让 rail 的 `bg-bg-subtle` 米灰透出来；(2) handle 内部的 1px hover 蓝线用 `left-1/2 -translate-x-1/2 w-px` 居中，落在 [rail.right - 0.5, rail.right + 0.5]，而 rail 自己的 `border-r border-border-subtle` 灰线在 [rail.right - 1, rail.right]，两条 1px 线相邻不重合，hover 瞬间能同时看到两条 vertical divider。

## Reproduction Steps

1. 打开 DataTalk，切到任意 workspace（默认 `default`），保证 rail 上至少有 1 个 active tab。
2. 在 rail 中点击 "报告库"（report_library tab）让其成为 active row。
3. 不要去碰 rail 内任何元素，**只看 rail 右边界**：group header "活跃 N" 那一行的右侧、紧贴 `border-r` 的位置，能看到一小段 4px 宽、纵向延伸的米灰条。
4. 把鼠标移到 rail 与 main pane 之间的分割线上：
   - hover 一瞬间会同时显示两条相邻 vertical 线（rail 自己的 1px border-r + handle hover 蓝线，错位 0.5px）。
   - hover 后米灰小条会消失（任意 paint invalidation 都能刷掉它）。
5. 把鼠标移到任意 row 上再离开，或点击 main pane 任意空白处，米灰条同样消失。

## Expected vs Actual

- **Expected**: rail 内 active row 与 group header 的 bg 紧贴 `border-r` 左沿；hover 分割线时只显示一条 vertical 线（rail border-r 被 hover 蓝线覆盖）。
- **Actual**: rail 内最右 4px 像被一层透明罩遮住，下层 row bg 不能合成进去，露出 rail 的 `bg-bg-subtle` 米灰；hover 时蓝线和 rail border-r 错位 0.5px，瞬时显双线。

## Environment

- Frontend commit: `4678dae7` (develop)
- Browser: Chromium / Tauri webview (WSL2 Linux 6.6.87.2)
- Reproducible only when GPU compositing 没有被后续 paint 刷新——切走 tab / 滚动 / 鼠标交互后米灰自然消失。
- Why "only report_library tab"：query_editor / dashboard / er_designer 等 tab 首屏渲染重，会触发足够的 paint 让 stale 合成层及时刷新；report_library 首屏内容轻，handle 的合成层 stale 保留下来。

## Evidence

- 屏幕截图 015951.png（用户最早提供）：红色箭头指向 "活跃 3" 右侧的 4px 米灰带，明确落在 rail 内 `border-r` 左沿。
- 屏幕截图 024205.png（用户提供）：active row "报告库" 切换后米灰仍在 group header 右侧。
- Playwright 实测（rail shell width = 240px）：
  - `rail.right = 1051.81`，`border-r ∈ [1050.81, 1051.81]`
  - `handle.box = [1047.81, 1055.81]`（`right-0 w-2 translate-x-1/2`）
  - `handle.innerLine ∈ [1051.31, 1052.31]`（`left-1/2 -translate-x-1/2 w-px`）
  - `elementFromPoint(1048.81, *)` 全部返回 handle —— **rail 内最右 4px 完全被 z-10 handle 罩住**
  - 而 row.right / groupBtn.right 都 = 1050.81 = border-r 左沿（几何上紧贴）

## Root Cause

两个独立缺陷叠加产生 "米灰" + "双线" 视觉症状：

1. **handle 合成层遮蔽下层 paint**：`z-index: 10 + transform: translateX(50%) + bg-transparent + inset-y-0 全高` 这一组合在 Chromium 下会把 handle 提升为独立 compositor layer。该层 paint 内容为空（transparent），但它在合成顺序中位于下层 row/group-header 之上。在 first paint 后没有触发 invalidation 的情况下，下层 row 在 `[rail.right-4, rail.right]` 这 4px × 全高的区域无法被正确合成进最终图像，rail shell 的 `bg-bg-subtle` 反而透出来。任何会导致 handle 区域 paint dirty 的操作（hover handle 让内部 1px 线变蓝、hover row 触发 background transition、点击任意元素改 focus、F12 toggle CSS 强制 layout）都会刷掉这个 stale 合成结果。

2. **hover 蓝线与 rail border-r 错位 0.5px**：
   - rail.border-r 占 `[rail.right - 1, rail.right]`
   - handle 内部 hover 蓝线 `left-1/2 -translate-x-1/2 w-px` → handle 中心 = rail.right，1px 宽线居中 → 占 `[rail.right - 0.5, rail.right + 0.5]`
   - 两条 1px 线相邻不重合，hover 瞬间视觉上是 "两条分割线"。

## Fix

`client/src/features/stage/components/stage-window.tsx` 中 resize handle 改为：

```diff
- className="group absolute inset-y-0 right-0 z-10 w-2 translate-x-1/2 cursor-col-resize bg-transparent"
+ className="group absolute inset-y-0 -right-1 w-2 cursor-col-resize bg-transparent"
```

```diff
- <div className="pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-transparent transition-colors group-hover:bg-accent-primary/50" />
+ <div className="pointer-events-none absolute inset-y-0 left-[3px] w-px bg-transparent transition-colors group-hover:bg-accent-primary/50" />
```

两个改动各自承担一个职责：

1. **`-right-1 w-2` 替代 `right-0 w-2 translate-x-1/2`**：handle box 几何不变（仍然横跨 `[rail.right - 4, rail.right + 4]`），但去掉 `z-10` 与 `transform`，让浏览器不必将 handle 提为独立合成层。下层 row/group-header bg 可以在 `[rail.right - 4, rail.right]` 4px 区域正常合成，米灰条消失。注意 `-right-1` = `right: -0.25rem` = `right: -4px`，与原 `right: 0; translate: 4px` 在 box 位置上完全等价，**没有几何或交互行为变化**。
2. **`left-[3px] w-px` 替代 `left-1/2 -translate-x-1/2 w-px`**：handle.box 左边界在 rail.right - 4，要让 1px 蓝线精确覆盖 rail.border-r `[rail.right - 1, rail.right]`，蓝线应位于 handle 内相对左偏移 3px → 占 `[handle.left + 3, handle.left + 4] = [rail.right - 1, rail.right]`，与 border-r 像素完美重合。hover 时只显示一条 vertical 线。

## Why `left-[3px]` 是关键值

记 handle.left = rail.right - 4，那么：

| 写法 | 计算 | 蓝线占据像素 | rail.border-r 重合度 |
|------|------|------------|-------------------|
| `left-1/2 -translate-x-1/2` (原) | handle.left + 4 - 0.5 = rail.right - 0.5 | `[rail.right - 0.5, rail.right + 0.5]` | 仅 0.5px 重叠 → 双线 |
| `left-[3px]` | handle.left + 3 = rail.right - 1 | `[rail.right - 1, rail.right]` | 1:1 完全重合 → 单线 |
| `left-[2px]` | handle.left + 2 = rail.right - 2 | `[rail.right - 2, rail.right - 1]` | border-r 左侧 1px → 仍是双线 |
| `left-[4px]` | handle.left + 4 = rail.right | `[rail.right, rail.right + 1]` | border-r 右侧 1px → 仍是双线 |

3px 是 8px handle 宽 + 1px 蓝线宽 + rail.border-r 在 handle box 内的相对位置共同决定的唯一精确值；不能用 `left-1/2`（4px = handle 中线）或 `left-1/3` 等约数表达，**必须用 `left-[3px]` arbitrary value**。

## Verification

- 重启 dev server / HMR 应用后视觉验证：
  - 报告库 active 时 rail 右侧无米灰小条
  - hover 分割线只显示一条蓝线（与 rail border-r 像素重合）
  - 不再出现 "瞬时双线" 的视觉抖动
- Playwright `elementFromPoint(railRight - 1, *)` 仍返回 handle（覆盖关系未变，drag 命中区域未减小，交互行为零回归）
- 拖动分割线调整 rail 宽度功能验证正常

## Notes

调查中曾走过几条死路，记录给后人：

1. **错以为是 row `rounded-md` 没改 `rounded-l-md`**：active row.right 实测已经 = border-r 左沿，row bg 几何上已紧贴，问题在合成层不在 layout。
2. **错以为是 group header button `px-1` 的右内边距**：那个 4px padding 客观存在，但 button bg 是 transparent，露出的米灰来自更下层的 rail bg，不是 button 本身缺背景。
3. **错以为是 workspace-pane 内部某一边没贴满**：Playwright 实测 workspace-pane → wrap235 → wrap163 → reportRoot 四层嵌套四边 gap=0、scrollbar 占用 0px，main pane 内部完全不漏。
4. **错以为是 ReportLibraryTab mount 后需要自动 dispatch click**：实际是 handle 的合成层 stale，任何 paint invalidation 都能修，自动 click 是治标。

诊断关键转折点是用户提到 "鼠标移动到分割线上也会消失，并且一瞬间有两个分割线"——这条线索同时指向 handle 区域是 paint invalidation 入口（解释 "hover handle 消失"）和蓝线/border-r 几何错位（解释 "瞬时两条线"），把 root cause 从 layout 转向合成层 + 几何对齐。
