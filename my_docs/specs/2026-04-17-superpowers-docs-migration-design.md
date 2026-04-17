# Superpowers 文档目录迁移设计

**日期**：2026-04-17
**类型**：文档重组（无代码变更）
**状态**：待执行

## 1. 背景

项目中 Superpowers 技能（brainstorming / writing-plans）默认将设计文档写入 `docs/superpowers/specs/`、将执行计划写入 `docs/superpowers/plans/`。随着项目演进，该命名不再贴切：

- `docs/product-specs/` 已存在作为产品规格目录，适合收纳设计文档
- `docs/exec-plans/` 已存在作为执行计划目录，且 `index.md` 已经在跟踪计划

继续使用 `docs/superpowers/` 会造成：**两套目录语义重叠、导航分裂、索引分散**。

## 2. 目标

1. 把 Superpowers 技能产出的默认写入路径统一到 `docs/product-specs/` 和 `docs/exec-plans/`
2. 迁移所有历史 spec / plan 文件到新位置，保留 git 历史
3. 同步更新两份 `index.md`
4. 在 `CLAUDE.md` 增加「Documentation Paths」规则，让未来调用的 Superpowers 技能遵循新约定
5. 删除 `docs/superpowers/` 空目录

## 3. 当前状态

| 位置 | 内容 |
|------|------|
| `docs/superpowers/specs/` | 5 个设计文档 |
| `docs/superpowers/plans/` | 13 个执行计划 |
| `docs/product-specs/` | 产品总览 `index.md` + 1 个已就位的 spec |
| `docs/exec-plans/` | 计划索引 `index.md` + `tech-debt-tracker.md` + 1 个技术债文件 |
| `docs/exec-plans/index.md` | 12 条链接指向 `../superpowers/plans/...` |

## 4. 设计决策

### 4.1 迁移方式：`git mv`

使用 `git mv` 保留 git 历史（rename detection），而非 `cp + rm`。

### 4.2 `docs/product-specs/index.md` 的处理

保留原产品规格全景内容（该文件目前不是单纯索引，而是产品能力全貌 + AI 分级策略）。在末尾新增「8. 个别设计文档」章节，按时间列出迁移过来的 5 份 specs。

**原因**：该文件对产品视角有独立价值，不应被降级为纯索引。

### 4.3 `docs/exec-plans/index.md` 的处理

批量把链接路径 `../superpowers/plans/XXX.md` 改为 `XXX.md`（同目录引用）。工作流描述中的 `docs/superpowers/plans/` 改为 `docs/exec-plans/`。

### 4.4 CLAUDE.md 新规则

在「Working Rules」下新增小节：

```
### Documentation Paths

- Superpowers 技能 (brainstorming) 生成的设计 spec → `docs/product-specs/YYYY-MM-DD-<topic>-design.md`
- Superpowers 技能 (writing-plans) 生成的执行计划 → `docs/exec-plans/YYYY-MM-DD-<topic>-plan.md`
- 调用 Superpowers 技能时，必须把 spec/plan 写入上述路径，覆盖技能默认路径
```

### 4.5 清理

迁移完成后 `docs/superpowers/specs/` 和 `docs/superpowers/plans/` 成为空目录，连同父目录 `docs/superpowers/` 一起删除。

## 5. 边界条件

- **文件名冲突**：已核查，源和目标无同名文件。
- **交叉引用**：源 spec/plan 文件内部若引用自身旧路径需要排查（下一步在 plan 中列为子任务）。
- **README / 文档交叉链接**：CLAUDE.md、ARCHITECTURE.md、DESIGN.md 等 root-level 文档若提到 `docs/superpowers/` 需要更新。

## 6. 验证

- `ls docs/superpowers/` 应提示目录不存在
- `ls docs/product-specs/*.md | wc -l` 应为 6（5 迁移 + 1 原有）+ `index.md`
- `ls docs/exec-plans/*.md | wc -l` 应为 13（迁移）+ `index.md` + `tech-debt-tracker.md` + `ui-demo-stage-animation-debt.md`
- `grep -r "docs/superpowers" docs/ CLAUDE.md ARCHITECTURE.md` 返回空
- `grep "\.\./superpowers" docs/exec-plans/index.md` 返回空

## 7. 不做的事（YAGNI）

- 不重构 `docs/product-specs/index.md` 的内容结构
- 不调整 specs 内部链接指向（除非检出确认坏链）
- 不改 Superpowers 技能包自身的默认路径（靠 CLAUDE.md 规则覆盖即可）
