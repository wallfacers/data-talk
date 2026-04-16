# 计划工作流

计划是 DataTalk 项目中的一等工件，进行版本控制并提交到仓库。

## 计划层级

```
设计 Spec (Design Doc)
    ↓ 评审通过后
执行计划 (Execution Plan)
    ↓ 可拆分为
子计划 (Part 1, Part 2, ...)
```

## 计划类型

### 轻量计划

用于小幅变更（≤ 3 个文件，≤ 2 小时工作量）。无需正式文档，在 PR 描述中说明即可。

### 执行计划

用于复杂工作。记录在 `docs/superpowers/plans/` 中，包含：

- 目标和非目标
- Spec 映射（关联哪份设计文档的哪些章节）
- 文件结构地图（将创建/修改哪些文件）
- 分步 Checkbox 任务列表
- 决策日志（执行中的重要决定和权衡）

### 子计划拆分

当单个计划过大时（> 30 个 checkbox），拆分为 Part N。每个 Part：
- 独立可测试
- 有明确的交接点（上一个 Part 的输出是下一个的输入）
- 在 [exec-plans/index.md](exec-plans/index.md) 中独立跟踪

## 生命周期

1. **规划 (pending)**：设计 spec 通过后，创建执行计划
2. **执行 (in_progress)**：按 checkbox 逐项实施，在计划文件中标记进度
3. **完成 (completed)**：所有 checkbox 完成，测试通过
4. **归档**：从活跃列表移到已完成列表

## 命名规则

```
YYYY-MM-DD-<project>-<part>-<topic>.md

示例：
2026-04-16-manus-a-backend-platform.md
2026-04-16-manus-a-backend-platform-part2.md
2026-04-16-manus-b-mvp-actions.md
2026-04-16-manus-c-client-split-view.md
```

- 字母前缀（a/b/c）表示计划之间的依赖顺序
- Part N 表示同一计划的拆分

## 技术债务

执行过程中发现的技术债务记录到 [exec-plans/tech-debt-tracker.md](exec-plans/tech-debt-tracker.md)，不在计划中堆积。
