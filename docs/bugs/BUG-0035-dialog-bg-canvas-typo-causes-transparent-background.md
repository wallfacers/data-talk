---
id: BUG-0035
title: 数据源/凭据管理设置页删除弹框背景半透明，bg-canvas 类拼写错误
status: fixed
priority: P2
source: manual-report
modules: [settings, connection, dialog]
discovered: 2026-05-13
discoveredBy: human
testRunId: null
fixCommit: bb33f1bc
fixPlanRef: openspec/changes/archive/2026-05-13-unify-sql-confirmation-dialog/
duplicateOf: null
regression: false
---

## Summary

`DeleteConnectionModal` 与 `CredentialsPage` 的 `<DialogContent>` 上写了 `className="... bg-canvas ..."`，但 `bg-canvas` 不是项目 Tailwind 配置中存在的工具类（实际生成的 class 是 `bg-bg-canvas`，因为 token 名为 `--color-bg-canvas`）。结果该 class 被 Tailwind 静默丢弃，DialogContent 退化为只有默认的 `bg-popover` + `ring-1 ring-foreground/10` + Overlay `bg-black/10 backdrop-blur-xs`，呈现整体半透明 / 透出底层内容的视觉异常。

## Reproduction Steps

1. 启动 client (`cd client && npm run dev`)，打开"设置 → 数据源"页面
2. 在数据源列表点击任意一行的删除按钮（🗑 图标）
3. 观察弹出的"删除连接"确认对话框

## Expected vs Actual

- **Expected**: 对话框背景为不透明实色（`bg.panel` 表面色），不能透出页面底层数据源列表内容
- **Actual**: 对话框背景实际只生效 DialogContent 默认 `bg-popover`，配合极弱的 `ring-foreground/10` 边框 + `bg-black/10` overlay backdrop，整体视觉感为"半透明、透出底层"

## Environment

- Branch / commit: `develop @ 0f06a82c`
- Frontend: Tauri v2 + React 19 + Vite + Tailwind v4
- OS: Linux 6.6 (WSL2) — 也复现于 macOS Tauri 桌面端
- Theme: 同时影响 light / dark（dark 模式下 `--dt-bg-elevated` oklch(0.27...) 视觉更"通透"，更明显）

## Evidence

- 用户截图：删除弹框透出列表行内容
- 实证：从 Vite 服务的编译 CSS (`/src/styles/globals.css?import`) 中 grep `.bg-canvas` 无匹配；只有 `.bg-bg-canvas`、`.bg-bg-panel`、`.bg-popover` 三个相关规则被生成
- `client/src/styles/globals.css` 中 `@theme inline { ... }` 块定义了 `--color-bg-canvas`、`--color-bg-panel`，但**没有** `--color-canvas` —— Tailwind v4 的 class 派生规则使得 `bg-canvas` 不会被生成

## Root Cause

`bg-canvas` 这一 className 在以下两个文件中存在拼写错误：

- `client/src/features/connection/components/delete-connection-modal.tsx:72` — `<DialogContent className="w-[480px] bg-canvas p-6" ...>`
- `client/src/features/settings/credentials/credentials-page.tsx:55` — `<DialogContent className="w-[520px] bg-canvas p-6" ...>`

Tailwind v4 的 token-to-class 派生规则为 `bg-{X}` 需要 `--color-{X}` 存在。项目 `globals.css` 中：

```css
--color-bg-canvas: var(--dt-bg-canvas);   /* → 生成 .bg-bg-canvas */
--color-bg-panel:  var(--dt-bg-panel);    /* → 生成 .bg-bg-panel */
--color-popover:   var(--popover);        /* → 生成 .bg-popover */
/* 没有 --color-canvas → .bg-canvas 不存在 */
```

因此 `bg-canvas` 整体被 Tailwind 丢弃，dialog 背景退化到 DialogContent 默认的 `bg-popover`，叠加 `ring-1 ring-foreground/10`（10% foreground 1px ring）+ DialogOverlay 的 `bg-black/10 supports-backdrop-filter:backdrop-blur-xs`（10% 黑色蒙版 + 极弱模糊），形成"半透明、与底层融合"的观感。

## Fix

将两处的 `bg-canvas` 改为 `bg-bg-panel`：

- 直接命中已存在的 Tailwind 工具类 `.bg-bg-panel { background-color: var(--dt-bg-panel) }`
- 语义上符合 `client/DESIGN.md` `bg.panel` = "default contained surface for controls and focused work areas"，正是 dialog body 表面的语义
- 视觉上 `--dt-bg-panel` 是完全不透明 oklch 色（light: `oklch(1 0 0)`、dark: `oklch(0.21 0.01 255)`），覆盖默认 `bg-popover` 后对话框背景实色不透明

修复后弹框背景不再透出底层；配合既有 `ring-1 ring-foreground/10` 与 DialogOverlay 蒙版，整体形成"有边界、有层次"的标准 modal 视觉。

## Verification

- `cd client && npx tsc --noEmit` → exit 0
- 重启 Vite dev server，确认编译后的 CSS 不再有 `bg-canvas` 引用，只看到 `bg-bg-panel` 生效
- 在数据源 / 凭据管理页面手动触发删除 / 创建弹框，目测背景为不透明实色

## Notes

- 范围决策：本次同时修两处 `bg-canvas` 拼写错误，因为根因完全相同；如果只修 `delete-connection-modal.tsx`，`credentials-page.tsx` 的创建凭据弹框仍会有同样问题
- 未一并改 DialogContent 默认的 `bg-popover` → `bg-bg-panel`，因为 popover 在项目其它场景（如 `Select`、`DropdownMenu` 等）也会复用 `--popover` 链。本次只在显式 dialog 实例 className 中明确覆盖
- **同根因但本次未修**：`bg-canvas` 误写还出现在 `client/src/features/stage/components/files-tab.tsx`（line 48 / 57 / 64）与 `client/src/features/stage/components/files-library-tab.tsx`（line 86 / 97）等"非弹框"场景。这些位置同样不会生效，导致背景透明 / 退化到父级背景。本次仅修用户反馈范围的弹框两处；建议后续单独 BUG 跟踪 stage tabs 的 canvas 背景缺失
