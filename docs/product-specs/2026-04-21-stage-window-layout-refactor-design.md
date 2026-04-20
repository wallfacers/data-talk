# Stage Window Layout Refactor Design

## Status

需求备忘，待后续深入分析与细化。

## Summary

重构 Stage Window 页面窗体，使其从当前偏平铺的工作区，升级为更明确的左右分栏工作台结构。

## Draft Requirement

- 左侧为 `Category` 目录树，用于组织和承载 Stage 内的能力入口。
- 左侧目录树首批至少包含：
  - `SQL 编辑器`
  - `ER 图设计器`
  - 后续可继续扩展的分类节点
- 右侧为 `Tabs` 工作区，所有具体页面都通过左侧目录树打开或激活。
- 用户点击左侧目录树节点后，在右侧打开对应 Tab；若该 Tab 已存在，则激活而不是重复创建。
- 整体目标是让 Stage 更像一个结构化工作台，而不是单层工具条 + 内容区。

## Intent

- 强化 Stage 的信息架构，让入口组织更清晰。
- 为 SQL、ER 以及后续更多 Stage 工具提供统一承载方式。
- 让“目录树打开工具、Tab 承载工作内容”的模型成为 Stage 的长期基础交互。

## Deferred To Deep Analysis

以下内容留待明天深入分析时统一设计：

- Category 树的数据模型与节点层级
- 左侧目录树与现有 `StageStore` / Tab 模型的映射关系
- Tab 去重、激活、关闭、恢复策略
- SQL 编辑器 / ER 设计器的初始节点形态与默认打开行为
- 与现有 Dock / TabStrip / WorkspaceAdapter 的迁移路径
