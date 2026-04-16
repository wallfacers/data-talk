# 设计文档目录

所有设计文档的集中索引。每份 spec 需标注验证状态。

## 状态说明

| 状态     | 含义                         |
|---------|------------------------------|
| draft   | 初稿，尚未评审                 |
| review  | 评审中                        |
| approved| 已批准，可进入实施              |
| shipped | 已实施完毕并上线               |
| stale   | 已过时，仅作历史参考            |

## 设计文档清单

| 文档 | 状态 | 摘要 |
|------|------|------|
| [client-rebuild-tauri-vite-design](../superpowers/specs/2026-04-16-client-rebuild-tauri-vite-design.md) | approved | Tauri v2 + React 19 + Vite 客户端重建方案 |
| [manus-split-view-design](../superpowers/specs/2026-04-16-manus-split-view-design.md) | approved | Manus 风格分屏交互 + Action Registry + Ontology 层 |

## 核心理念

见 [core-beliefs.md](core-beliefs.md) — 定义了本项目的智能体优先操作原则。

## 新增设计文档

1. 在 `docs/superpowers/specs/` 中创建 `YYYY-MM-DD-<topic>-design.md`
2. 在本文件中添加索引条目
3. 标注初始状态为 `draft`
4. 经评审后更新状态
