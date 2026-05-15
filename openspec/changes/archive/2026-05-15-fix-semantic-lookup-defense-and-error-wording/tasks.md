## 1. 前置确认

- [x] 1.1 grep 仓库确认是否存在 `ActionExecutionException` 类（搜 `class ActionExecutionException` 或同义命名）。若不存在，回退到抛带 cause 的 `RuntimeException`，不新增异常类型 — **结论**：无该类。采用现有 `com.datatalk.domain.error.DataTalkException(code, message, retriable)` + `log.error(..., cause)` 模式（adapter-only，code 字符串 `"semantic.lookup_failed"` 内联，不动 DataTalkErrorCodes）
- [x] 1.2 grep 前端确认无单测断言 `'source=user editor cannot follow session'` 字符串（避免 D5 改动破坏现有测试） — **结论**：该字符串仅出现在 `QueryEditorAdapter.ts:530` 自身，无测试断言
- [x] 1.3 确认 `~/.data-talk/semantic/` 空目录在测试环境可重现（清理或使用 `@TempDir`） — **结论**：`FsSemanticModelRepositoryIT.java:17` 已用 `@TempDir Path home` 模式，沿用

## 2. 后端：SemanticLookupActionHandler 防御加固（独立子任务，可并行）

- [x] 2.1 编辑 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/semantic/SemanticLookupActionHandler.java`：在 L62 前插入 `query` null/blank guard，命中时返回 `{matches:[], total:0, warning:"empty_query"}`（设计 D1）
- [x] 2.2 同文件 L75/83/91/99 的 4 处 `Map.of(...)` 替换为 `LinkedHashMap` + `put`，保留键顺序（设计 D2） — 全部 4 处已改为 LinkedHashMap；同时把 `e.physical().table()` 改为 null-safe（防御性，当前 record 仍保证 physical 非空）
- [x] 2.3 同文件 `handle()` 方法整体包 try-catch：捕获 `RuntimeException`，`log.error` 记录 connectionId/query/cause，抛 `DataTalkException("semantic.lookup_failed", msg, false)` + `initCause(ex)` 传递 cause 链（设计 D3，使用现有 DataTalkException 替代设计中假设的 ActionExecutionException）
- [x] 2.4 顶部添加 logger 字段：`private static final Logger log = LoggerFactory.getLogger(SemanticLookupActionHandler.class);`
- [x] 2.5 **追加修复（生产 stack trace 暴露的真凶）**：handler 在调用 `repository.listDomains()` 之前对 null/blank `connectionId` 短路，返回 `{matches:[], total:0, warning:"no_active_connection"}`。底层 `FsSemanticModelRepository.connectionDir` 在 `Path.resolve(null)` 抛 NPE，必须在 handler 层防御（与其他 4 个 semantic actions 的 `NO_ACTIVE_CONNECTION` 守卫思路一致）

## 3. 后端：SemanticLookupActionHandler 单测（依赖 2 完成）

- [x] 3.1 在已有的 `SemanticActionHandlersTest.java`（mock 风格，本仓库现行约定）追加防御性 case，而非新建 @TempDir 文件——更贴合现行测试风格
- [x] 3.2 添加测试 case `semanticLookup_missingQueryField_returnsEmptyMatchesWithWarning`：input 缺 query → 返回 `warning:"empty_query"` 且 matches 为空
- [x] 3.3 添加测试 case `semanticLookup_blankQueryString_returnsEmptyMatchesWithWarning`：query="   " → 同上
- [x] 3.4 添加测试 case `semanticLookup_noModelDirectory_returnsEmptyMatchesWithoutWarning`：mock listDomains 返回空 List → matches 为空，无 warning
- [x] 3.5 添加测试 case `semanticLookup_entityWithNullDescription_returnsMatchWithoutNPE`：entity description=null（当前 record 唯一真正可空字段）→ 命中并返回正常 match map，不抛
- [x] 3.6 添加测试 case `semanticLookup_nullConnectionId_shortCircuitsBeforeRepository`：ctx.connectionId() 为 null → 短路返回 `warning:"no_active_connection"`，**`verifyNoInteractions(repo)`** 确保不触及底层（旧版本错误地 mock 了 `listDomains(null)→[]`，掩盖了真实 NPE）
- [x] 3.8 追加测试 case `semanticLookup_blankConnectionId_shortCircuitsBeforeRepository`：空白字符串 connectionId 同样短路
- [x] 3.7 额外添加 `semanticLookup_repositoryThrowsRuntimeException_translatesToDataTalkException`：repo 抛 IllegalStateException → 同步抛 DataTalkException(code="semantic.lookup_failed") 且 cause 链保留

## 4. 后端：ArchiveArtifactAction hint 增强（独立，可与 2 并行）

- [x] 4.1 编辑 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ArchiveArtifactAction.java`：把 `errorResult(String)` 改签名为 `errorResult(String error, String hint)`；所有 callers 同步更新（4 处 `path_not_found` 调用改为 `errorResult("path_not_found", null)`）
- [x] 4.2 同文件添加 `private static String hintFor(PathSafetyError err)` switch（设计 D4），覆盖 6 个 `PathSafetyError` 枚举值
- [x] 4.3 修改 L131 `outcome instanceof ArchiveCandidateOutcome.PathRejected r` 分支：调用 `errorResult(r.error().wire(), hintFor(r.error()))`
- [x] 4.4 更新 `outputSchema()`：把 `Map.of(...)` 改为 `LinkedHashMap` 以追加可选 `hint` property（向后兼容）
- [x] 4.5 确认 `ArchiveArtifactActionHandlerIT.java` 8/8 测试仍通过（断言只看 `error` 字段，hint 是新增可选键）

## 5. 前端：QueryEditorAdapter noop 文案结构化（独立，可与 2/4 并行）

- [x] 5.1 编辑 `client/src/features/stage/adapters/QueryEditorAdapter.ts` 第 530 行：reason 从 `'source=user editor cannot follow session'` 改为 `'user_editor_pinned_to_origin'`，新增 `detail` 字段（设计 D5）
- [x] 5.2 检查 `client/src/features/stage/adapters/__tests__/` — 无任何测试断言旧 reason 字符串或 noop 数据形状（grep 返回空），无需改动

## 6. 技能文档：Known Limits 段落（独立，可并行）

- [x] 6.1 `semantic-model-usage/SKILL.md` 追加 `## Known Limits` 段落（missing model 空匹配、empty query 容忍、`semantic.lookup_failed` 错误码）
- [x] 6.2 `artifacts-output/SKILL.md` 追加 `## Known Limits` 段落（path 必须在 session 目录、hint 字段说明、ctx.sessionId null 短路）
- [x] 6.3 `query-editor-workflow/SKILL.md` 追加 `## Known Limits` 段落（user editor pinned to origin、结构化 reason、可覆盖连接/库/schema）

## 7. 批量验证（在 2-6 全部 written 后执行单次合并校验）

- [x] 7.1 刷新 adapter jar：`mvn install -pl data-talk-adapter -am -DskipTests` — `BUILD SUCCESS` (40s)
- [x] 7.2 后端编译校验 — 包含于 7.1（5 模块全部 SUCCESS）
- [x] 7.3 后端单测：`mvn -pl data-talk-adapter test -Dtest='SemanticActionHandlersTest,ArchiveArtifactActionHandlerIT'` — Tests run: 33, Failures: 0, Errors: 0
- [x] 7.4 前端类型检查：`npx tsc --noEmit` — 零错误零警告
- [x] 7.5 前端单测：`npx vitest run src/features/stage/adapters/__tests__/QueryEditorAdapter.test.ts` — 26/26 passed
- [ ] 7.6 完整 verify（可选）：`mvn verify -q` — 跳过（独立测试通过+无关模块未触及）

## 8. 端到端手动复测（**推迟到用户重启后端后执行**）

- [ ] 8.1 重启后端使 jar 刷新生效（`pkill -f spring-boot:run` 后 `mvn spring-boot:run -pl data-talk-adapter`） — **未执行**：当前运行的 backend PID 74915 启动于 12:46，早于 12:50 的 jar 刷新；为避免打断用户的活跃 session，推迟由用户在合适时机自行重启
- [ ] 8.2 在 opencode 中调一次 `mcp__datatalk__semantic_lookup({"query":"订单"})`，确认返回 `{matches:[], total:0}` 而非 500 — **待用户验证**
- [ ] 8.3 在一个没有 archive 候选 session 的状态下调 `mcp__datatalk__archive_artifact({"path":"/tmp/x.csv","kind":"dataset"})`，确认错误响应含 `hint` 字段 — **待用户验证**
- [ ] 8.4 打开 user-source SQL 编辑器后调 `mcp__datatalk__ui_exec({"object":"query_editor","action":"set_context","params":{"useSessionContext":true}})`，确认 noop 响应 reason=`user_editor_pinned_to_origin` 且含 `detail` — **待用户验证**
- [x] 8.5 更新 `~/.data-talk/opencode/skill-test-plan.md` 问题记录表的 T29/T32/T12 三行"备注"列：分别标注"已加固"、"文案已改进"、"文案已改进"，并引用 change 名称

## 9. OpenSpec 收尾

- [x] 9.1 跑 `openspec validate fix-semantic-lookup-defense-and-error-wording` — `Change is valid`
- [x] 9.2 跑 `openspec status --change fix-semantic-lookup-defense-and-error-wording` — 4/4 artifacts complete
- [ ] 9.3 在 commit message 中引用 change 名称，方便后续 `/opsx:archive` 关联 — 待 commit 时执行
