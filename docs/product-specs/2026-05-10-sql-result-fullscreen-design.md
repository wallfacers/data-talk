---
title: SQL 结果集全屏查看
date: 2026-05-10
status: approved
scope: client
files:
  - client/src/features/stage/components/sql-result-table.tsx
  - client/src/i18n/messages.ts
---

## 背景

SQL 编辑器结果集表格嵌入在 Stage 面板中，空间有限。用户需要放大查看完整数据时缺乏快捷入口。

## 目标

在结果集工具栏（下载 CSV 按钮右侧）新增全屏按钮，点击后以弹框叠加层展示当前结果集的完整内容。

## 交互规格

1. **触发**：点击工具栏右侧全屏按钮（`Maximize2` 图标 + 文字）
2. **弹框内容**：与原结果集完全一致——表格、工具栏（导出范围、复制 CSV、复制 JSON、下载 CSV）、分页控件
3. **弹框标题栏**：左侧显示 "查询结果"，右侧 X 关闭按钮
4. **关闭方式**：Esc 键 / 点击遮罩 / 点击关闭按钮

## 技术方案

### 变更文件

| 文件 | 改动 |
|------|------|
| `sql-result-table.tsx` | 新增 `isExpanded` 状态、全屏按钮、Portal 弹框渲染 |
| `messages.ts` | 新增 i18n key：按钮 aria-label、弹框标题 |

### 实现细节

1. 新增 `isExpanded` state（`useState<boolean>`）
2. 下载 CSV 按钮右侧新增 `Button size="sm" variant="outline"`，图标 `Maximize2`
3. `isExpanded` 为 true 时，`createPortal` 渲染叠加层到 `document.body`
4. 弹框内渲染完整的表格内容（复用同一份数据和工具栏逻辑）

### Design Token 映射

| 元素 | Token |
|------|-------|
| 遮罩背景 | `--dt-bg-overlay` |
| 弹框面板 | `--dt-bg-panel` |
| 弹框边框 | `--dt-border-subtle` |
| 标题文字 | `--dt-text-strong` |
| 进入动画 | `180ms cubic-bezier(0.16, 1, 0.3, 1)` |

### Z-Index

弹框 `z-[300]`，与现有 `chart-expand-modal.tsx` 一致。

## 不做的事

- 不做全屏替换 Stage 区域的沉浸模式
- 不在弹框内额外显示 SQL 语句或执行元信息
- 不引入新依赖
