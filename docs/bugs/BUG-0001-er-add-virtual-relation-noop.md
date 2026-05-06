---
id: BUG-0001
title: ER Inspector "Add virtual relation" toolbar 按钮无效
status: open
priority: P1
source: E2E test
modules:
  - er-canvas
  - er-inspector
discoveredAt: 2026-05-06
discoveredBy: er-module-e2e-test-plan I5
---

## 现象

Inspector toolbar 上 "Add virtual relation" 按钮点击后无任何反应：不弹窗、不进入编辑模式、不写 `/virtualRelations`。

## 复现路径

1. 打开任意 ER Inspector Tab
2. 点 toolbar "Add virtual relation"

## 期望

按 ER product spec / `er-tab-protocol.md` `/virtualRelations` 路径定义，应进入"选源表/源列 → 选目标表/目标列 → 填备注"编辑流程，最终 `ui_patch /virtualRelations/-` 落入 payload。

## 根因

`client/src/features/stage/components/er-canvas/ErCanvas.tsx:223`

```ts
const onAddVirtualRelation = useCallback(() => undefined, [])
```

回调是 noop。Designer 的 `onAddTable` 等其他 toolbar 按钮均有实现，唯独此按钮被遗留。

## 影响

P1 — 功能缺失但 AI 仍可走 `ui_patch /virtualRelations/-` 兜底路径；用户侧此按钮整段不可用。

## 关联

- Test：`client/tests/e2e/er-inspector-ui.spec.ts` I5（test.fixme）
- Plan：`docs/exec-plans/2026-05-06-er-module-e2e-test-plan.md`
