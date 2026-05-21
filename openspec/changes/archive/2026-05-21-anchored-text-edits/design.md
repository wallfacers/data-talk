## Context

`query_editor` 的 `apply_text_edits` 当前要求每个 edit 同时携带 `range`（1-based 行列）、`text`、`expectedText`，并以 top-level `baseVersion` 做并发闸门。执行逻辑在 `client/src/features/stage/stores/sql-workbench-store.ts`：`resolveOffset` 把行列换算成 offset，`applyTextEdits` 先校验 `baseVersion === version`，再逐 edit 校验 `slice(range)` 与 `expectedText` 逐字（仅归一化换行）相等，全部通过才右→左套用。

这套协议把"数对 1-based 行号"和"逐字复刻含前导空白的内容"作为硬前提——恰是 LLM 最不稳定的两件事。真实会话连续 5 次失败：行号错位（`expected_text_mismatch`）、缩进 2-vs-4 空格不符、版本漂移后未重读。前次修复 `f68b0108` 仅改了错误文案分支与 prompt 坐标说明（prompt 侧），未触及协议根因。

约束：该协议仅 query_editor 使用（`applyTextEdits` store 方法唯一定义处），script/dashboard 编辑器走各自 patch 机制，爆炸半径可控。整文档替换路径 `ui_patch /content` + `baseVersion` 保持不变。

## Goals / Non-Goals

**Goals:**
- 消除编辑对"行列坐标正确"与"逐字复刻空白"的依赖，把定位收敛到单一锚点 `oldText`。
- 良性并发（版本+1 但锚点仍唯一命中）不再报错、不再逼模型重读。
- 错误码语义对 AI 清晰可行动：缺失→重读，歧义→补上下文。
- prompt 删除脆弱的坐标处方，而非新增行为引导（符合"不靠改 prompt 实现功能"原则）。

**Non-Goals:**
- 不改整文档 `/content` 替换路径。
- 不改 script/dashboard/er 编辑器的 patch 协议。
- 不引入跨编辑器共享的通用 diff 引擎；本次仅限 query_editor。
- 不做撤销/重做语义变更；编辑套用后仍走既有 `version+1` 与 history。

## Decisions

### D1 — 彻底移除 `range`，定位完全由 `oldText` 锚定（用户决策：要彻底）

`edits[]` 契约：`{ oldText: string, newText: string, hint?: { line: number } }`。
- `range` 字段及 `resolveOffset`/`resolveTextEdits` 的坐标解析整体删除。
- `hint.line`（可选，1-based）仅在锚点多命中时用于挑选"最近起始行"的那一处，不参与唯一命中场景的定位。

**为何 X 而非 Y**：保留 range 做可选 hint 仍会诱导模型继续算坐标、并制造"range 与 oldText 不一致"的新错误类。彻底删除使协议只有一个真相源，和 Aider / Claude Code Edit 的 search-replace 一致——业界已验证 LLM 复刻片段远比算行号可靠。`hint.line` 是纯数字、可错、仅用于消歧，错了最多退化为 `anchor_ambiguous`，不会误套用。

### D2 — 锚点解析算法（同快照、唯一/多/零三分支）

对当前内容快照，逐 edit 解析：
```
anchor   = compileAnchor(oldText)            # 见 D3 空白柔性
hits     = anchor.findAll(content)           # 返回 [start,end) 区间列表
len(hits)==1 → 命中区间即替换区间（忽略行号，忽略版本漂移）
len(hits) >1 → 有 hint.line 选起始行最接近者；否则 / 仍并列 → anchor_ambiguous
len(hits)==0 → anchor_not_found
```
所有 edit 先对**同一份原始快照**解析出 `[start,end)`，再校验区间两两不重叠（重叠→`invalid_params` 附冲突 editIndex），最后按 start 降序套用（复用现有 `applyResolvedTextEditsToContent` 的右→左策略）。

**为何同快照解析**：多 edit 必须基于同一基准定位，避免前一个 edit 改变后续锚点位置。重叠校验防止两个 edit 抢同一区段产生不可预期结果。

### D3 — 空白柔性匹配 W2（用户决策：本阶段就做）

`compileAnchor(oldText)`：
1. 以"是否空白"为界把 `oldText` 切成交替的 [非空白 token] 与 [空白段]。
2. 非空白 token：严格相等匹配（保留 SQL 标识符/标点的精确性）。
3. 空白段：编译为 `\s+`（含换行）；`oldText` 内部连续空白 → 至少匹配 1 个空白字符。
4. `oldText` 首尾若本身带空白，同样按 `\s+`/`\s*` 处理（首尾用 `\s*` 允许零空白，避免吞掉相邻 token 的边界）。
5. 换行先统一归一化 `\r\n|\r → \n` 后再编译与搜索；定位得到的区间映射回**原始未归一化内容**的精确 offset 进行替换，避免破坏文件真实换行风格。

**为何 token 严格 + 空白柔性**：只放宽空白可容忍模型把 4 空格写成 2 空格、把单空格写成换行；要求非空白 token 严格相等且按序相邻，把误命中风险压到"结构与目标几乎相同"的极小概率，再叠加"必须唯一命中"兜底。

**为何区间映射回原始内容**：搜索在归一化文本上做（统一换行），但替换必须落在原始 offset，否则会把用户文件里的 `\r\n` 悄悄改成 `\n`。需维护归一化↔原始的 offset 映射（构建归一化串时记录每个归一化字符对应的原始 index）。

### D4 — 并发自动 rebase，`baseVersion` 降为建议值

```
baseVersion 省略 / == version          → 正常路径
baseVersion < version（漂移）：
    所有锚点在当前内容唯一命中          → 自动 rebase 套用，回传 { version, content, rebased: true }
    任一锚点缺失 / 歧义                  → 不 rebase，按 anchor_not_found / anchor_ambiguous 报错（附当前内容）
        且若漂移确由并发引起，message 注明版本已变
```

**为何**：锚点唯一命中本身即证明"我要改的那段还在、没被别人动过"，此时版本号是否+1 无关紧要。这消灭了"用户在别处敲一行 → 版本+1 → 模型被迫重读"的良性循环。无法 rebase 时仍回退到重读流程，与现有 `ui-exec-error-semantics` "version_conflict 不带 nextAction" 兼容。

### D5 — 错误码：AI 友好、语义明确（用户决策：什么好用用什么）

闭合枚举调整：移除 `expected_text_mismatch`，新增：
| code | 触发 | 恢复提示 | nextAction |
|------|------|----------|-----------|
| `anchor_not_found` | `oldText` 在当前内容零命中 | 重读后基于最新内容重定 `oldText` | 无（需重读，无单一动作） |
| `anchor_ambiguous` | 多命中且 hint 无法消歧 | 在 `oldText` 中加入更多周边上下文使其唯一 | 无 |
| `version_conflict` | 仅保留给"漂移且锚点已不可定位"的硬冲突场景（实际并入 anchor_not_found 的注明分支） | 重读 | 无 |

错误对象附 `currentState: { tabId, version, content }`；`anchor_ambiguous` 额外附 `details: { editIndex, matchCount }`；`anchor_not_found` 附 `details: { editIndex, oldText }`。服务端 `EditConflictMarkdownFormatter` 据此渲染两类提示，删除"1-based 行列"话术。

**为何拆两码**：单一 `expected_text_mismatch` 无法区分"内容真变了（该重读）"与"匹配到多处（该加上下文）"，模型只能盲目重试。两码各自携带唯一可行动的恢复路径，是 AI 友好的关键。

### D6 — prompt 瘦身

`ui-contract/SKILL.md` 删除 `range` 1-based 坐标段与"逐字复刻前导空白"段，改述 `oldText`/`newText`/`hint` 与"带足上下文使锚点唯一"。`concurrency-contract/SKILL.md` 调整并发恢复说明（rebase 自动发生，硬冲突才重读）。属删除脆弱指令。

## Risks / Trade-offs

- **W2 空白柔性贪婪误命中** → 缓解：非空白 token 严格相等且相邻 + 整体唯一命中要求 + 多命中报 `anchor_ambiguous` 而非赌一个。
- **归一化↔原始 offset 映射实现复杂、易出 off-by-one** → 缓解：单元测试覆盖 `\r\n`/`\r`/混合换行、首尾空白、空 oldText、跨行锚点。
- **`oldText` 唯一性责任转移到模型** → 短 SQL 中重复片段常见（如多个 `id`）。缓解：D5 的 `anchor_ambiguous` 明确要求补上下文；prompt 给出"带足上下文"指引。
- **BREAKING 契约** → 旧 `range`/`expectedText` 调用失效。缓解：协议仅 query_editor 用、消费方为我方 agent；同步更新 skill prompt 与 e2e 断言；不保留兼容垫片（避免半成品双轨）。
- **自动 rebase 隐藏了真实并发** → 极端情况：他人改了别处、我的锚点恰好仍唯一，rebase 静默套用。可接受：锚点唯一即语义安全；回传 `rebased: true` 供上层提示。

## Migration Plan

1. 前端 store：实现锚点解析器（含 W2 + offset 映射）、改写 `applyTextEdits`、删坐标函数、更新类型与返回结构。
2. 前端 adapter：改 `paramsSchema` 与错误码映射。
3. 服务端：`EditConflictMarkdownFormatter` 两类文案 + `McpActionBridge` 字段透传与码识别。
4. spec：新建 `query-editor-text-edits`，改 `ui-exec-error-semantics` 枚举。
5. skill prompt 瘦身。
6. 全量测试（前端 vitest + 后端 mvn verify + e2e 相关用例）。

回滚：本变更为单一提交集，无 DB 迁移、无持久化 schema 变更，`git revert` 即可完全回退；运行态无残留状态。

## Open Questions

- `hint` 是否需要支持除 `line` 外的列/序号消歧？当前判断 `line` 足够；若实测多命中且同行仍并列再扩展。
- 是否对 `newText` 也做换行归一化以贴合文件现有风格（如文件用 `\r\n`）？倾向：`newText` 原样插入，由编辑器/保存层统一换行；本次不处理，列为后续。
