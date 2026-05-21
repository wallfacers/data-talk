## 1. 前端：锚点解析核心（sql-workbench-store.ts）

- [x] 1.1 新增换行归一化 + 归一化↔原始 offset 映射工具：构建 `\r\n|\r → \n` 归一化串，同时记录每个归一化字符到原始 index 的映射，供命中区间映射回原始内容精确替换
- [x] 1.2 实现 `compileAnchor(oldText)`：按"是否空白"切分为非空白 token（严格相等）与空白段（`\s+`，首尾用 `\s*`）；产出可在归一化内容上 `findAll` 的匹配器
- [x] 1.3 实现 `resolveAnchors(content, edits)`：对同一原始快照逐 edit 求命中区间列表 —— 唯一命中直接定位；多命中用 `hint.line` 选最近起始行，否则标记 `ambiguous`；零命中标记 `not_found`
- [x] 1.4 实现批量不重叠校验：解析出的 `[start,end)` 区间两两不重叠，重叠则返回 `invalid_params` 并附冲突 editIndex
- [x] 1.5 重写 `applyTextEdits`：接入 1.1–1.4，按起始位置降序套用（复用 `applyResolvedTextEditsToContent`）；删除旧 `resolveOffset`/`resolveTextEdits` 坐标解析
- [x] 1.6 实现 `baseVersion` 自动 rebase：漂移但全部锚点唯一命中则套用并返回 `{ version, content, rebased: true }`；漂移且任一锚点失效则按 `anchor_not_found`/`anchor_ambiguous` 报错（message 注明版本已变）
- [x] 1.7 更新类型：`SqlWorkbenchTextEdit` 改为 `{ oldText, newText, hint? }`；`SqlWorkbenchEditResult` 用 `anchor_not_found`/`anchor_ambiguous` 取代 `expected_text_mismatch`，成功结果增加可选 `rebased`

## 2. 前端：适配层（QueryEditorAdapter.ts）

- [x] 2.1 改 `apply_text_edits` 的 `paramsSchema`：edits item 去除 `range`/`expectedText`，新增 `oldText`/`newText`（required）与可选 `hint.line`
- [x] 2.2 改 `exec('apply_text_edits')` 参数校验与到 `ExecResult` 的错误码映射：`anchor_not_found`/`anchor_ambiguous` 各带 `currentState` 与对应 `details`（`oldText` / `matchCount`），成功透传 `rebased`

## 3. 前端测试

- [x] 3.1 `sql-workbench-store.test.ts`：覆盖唯一/多/零命中、hint 消歧、缩进 2-vs-4 空格容忍、非空白 token 不命中、`\r\n` 换行保持、跨行锚点、空 oldText、多 edit 不重叠/重叠、自动 rebase 命中与失效
- [x] 3.2 `QueryEditorAdapter.test.ts`：新 paramsSchema 校验、错误码映射与 `currentState`/`details` 字段、`rebased` 透传

## 4. 服务端：错误文案与字段透传

- [x] 4.1 `EditConflictMarkdownFormatter.java`：用 `anchorNotFound(...)` / `anchorAmbiguous(...)` 取代 `expectedTextMismatch(...)`，删除 1-based 行列/逐字复刻话术；缺失类提示重读、歧义类提示补上下文
- [x] 4.2 `McpActionBridge.java`：识别 `anchor_not_found`/`anchor_ambiguous` 错误码并透传 `currentState`/`details`/`rebased` 字段
- [x] 4.3 后端测试：`EditConflictMarkdownFormatterTest.java` 改写为两类文案断言；`McpActionBridgeTest.java` 覆盖新码识别与字段透传

## 5. Spec 与 Skill prompt

- [x] 5.1 skill prompt 瘦身：`ui-contract/SKILL.md` 删 `range` 1-based 坐标段与"逐字复刻前导空白"段，改述 `oldText`/`newText`/`hint` 与"带足上下文使锚点唯一"；同步 action 速查表
- [x] 5.2 `concurrency-contract/SKILL.md`：调整并发恢复说明（rebase 自动发生，硬冲突才重读），更新错误码引用
- [x] 5.3 e2e 断言同步：`agents-batch4-ui-workspace.spec.ts` / `agents-skills-regression.spec.ts` 中涉及 `apply_text_edits` 的 `range`/`expectedText`/`expected_text_mismatch` 用例改为新契约

## 6. 验证

- [x] 6.1 前端：`cd client && npx tsc --noEmit` 零错误 + `npm run test`（vitest）相关用例通过
- [x] 6.2 后端：`cd server && mvn clean verify` 编译与测试全绿
- [x] 6.3 e2e 冒烟：用 playwright-cli 跑一轮 AI 编辑 SQL（含一次缩进不符、一次版本漂移），确认不再连环失败；按 BUG 追踪 gate 报告本轮发现的 BUG 数（即便为 0）
