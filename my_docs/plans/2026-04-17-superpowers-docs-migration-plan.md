# Superpowers 文档目录迁移执行计划

**日期**：2026-04-17
**Spec**：[../specs/2026-04-17-superpowers-docs-migration-design.md](../specs/2026-04-17-superpowers-docs-migration-design.md)
**状态**：✅ 已完成

## 目标

按 spec 将 `docs/superpowers/specs/` + `docs/superpowers/plans/` 合并入 `docs/product-specs/` + `docs/exec-plans/`，同步更新索引与 `CLAUDE.md` 规则。

## 执行步骤

- [x] **1. 写入设计文档** → `my_docs/specs/2026-04-17-superpowers-docs-migration-design.md`
- [x] **2. 迁移 specs** — `git mv` 5 个文件到 `docs/product-specs/`
  - `2026-04-16-client-rebuild-tauri-vite-design.md`
  - `2026-04-16-manus-split-view-design.md`
  - `2026-04-16-model-config-page-design.md`
  - `2026-04-16-opencode-embedded-process-design.md`
  - `2026-04-17-stage-as-computer-design.md`
- [x] **3. 迁移 plans** — `git mv` 14 个文件到 `docs/exec-plans/`
- [x] **4. 批量修正跨文件引用** — `sed -i` 替换 4 种路径前缀：
  - `docs/superpowers/specs/` → `docs/product-specs/`
  - `docs/superpowers/plans/` → `docs/exec-plans/`
  - `../superpowers/specs/` → `../product-specs/`
  - `../superpowers/plans/` → `../exec-plans/`
- [x] **5. 清理 `docs/exec-plans/index.md`** — 把 `../exec-plans/X.md` 冗余路径改为 `./X.md`；把 AI Settings Part 1 / 2 加入活跃计划
- [x] **6. 扩充 `docs/product-specs/index.md`** — 新增第 8 节「个别设计文档」，列出 6 份 specs（含原有 AI Settings · OpenCode Port）
- [x] **7. 更新 `CLAUDE.md`** — 在「Working Rules」下新增「Documentation Paths」规则
- [x] **8. 删除空目录** — `rmdir docs/superpowers/{specs,plans,}`
- [x] **9. 验证无残留 `docs/superpowers/` 引用**（CLAUDE.md 里的废弃声明除外）

## 验证

```bash
ls docs/superpowers/              # 目录不存在 ✓
ls docs/product-specs/*.md | wc   # 6 + index.md = 7 ✓
ls docs/exec-plans/*.md | wc      # 14 plans + index + tech-debt + ui-demo-stage-animation-debt = 17 ✓
grep -rn "superpowers/" docs/ CLAUDE.md | grep -v "superpowers:"
# 仅 CLAUDE.md 第 120 行（废弃路径声明），符合预期 ✓
```

## 产出

- **迁移**：19 份文档（5 specs + 14 plans）全部搬迁，保留 git 历史
- **索引同步**：2 份 index.md 更新
- **规则沉淀**：CLAUDE.md 新增 Documentation Paths 小节
- **零残留**：旧目录删除，旧路径引用清零
