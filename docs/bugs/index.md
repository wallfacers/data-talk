# BUG 索引

DataTalk 运行时缺陷的集中记录。所有 BUG 详情请进单文件查看。

## 写作协议

新建 / 修改 BUG 文档前，**MUST** 先读 [README.md](README.md)（模板、字段语义、状态流转、index.md 同步清单）。

## 当前编号

下一个分配 ID：**BUG-0001**（永不复用，单调递增）

## Open BUGs（按 priority 倒序，P0 → P2）

| ID | Title | Priority | Source | Modules | Discovered |
|----|-------|----------|--------|---------|------------|
| —  | 当前无未修 BUG | — | — | — | — |

## In Progress（status = investigating | fixed 等待 verify）

| ID | Title | Status | Priority | Owner |
|----|-------|--------|----------|-------|
| —  | 当前无进行中 BUG | — | — | — |

## Recently Closed（最近 30 天，status = verified | closed）

| ID | Title | Status | Closed Date | FixCommit |
|----|-------|--------|-------------|-----------|
| —  | 当前无最近关闭 BUG | — | — | — |

## By Module（聚合视图，仅列 open + in-progress）

- *暂无活跃 BUG*

## By Source（聚合视图，仅列 open + in-progress）

- *暂无活跃 BUG*

## Wontfix / Duplicate（终态归档，无时间限制）

| ID | Resolution | Reason / DuplicateOf |
|----|------------|----------------------|
| —  | — | — |

## Closure History

30 天前的 closed/verified 折叠归档。详见 `git log -- docs/bugs/`，本节不维护。

## 相关文档

- 写作协议：[README.md](README.md)
- 设计 spec：[../product-specs/2026-05-05-bug-tracking-system-design.md](../product-specs/2026-05-05-bug-tracking-system-design.md)
- 技术债跟踪（互补）：[../exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md)
- 手测脚本（互补）：[../testing/](../testing/)
