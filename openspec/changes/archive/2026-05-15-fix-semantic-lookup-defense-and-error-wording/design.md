## Context

技能测试报告（`~/.data-talk/opencode/skill-test-plan.md`）记录了 3 个非通过项：

| 编号 | 现象 | 性质 |
|---|---|---|
| T29 | `datatalk_semantic_lookup` 返回裸 HTTP 500 | 真 bug，但 stack trace 已丢（产生 500 的 backend 实例已退出，当前实例从未复现） |
| T32 | `archive_artifact` 返回 `{"error":"path_outside_session_dir","ok":false}` | 设计行为（`PathSafetyError` 枚举值），但文案对 AI 不友好 |
| T12 | `set_context` 对 user-source 编辑器返回 `{"noop":true,"reason":"source=user editor cannot follow session"}` | 设计行为（user editor pinned to origin session），但 reason 不结构化 |

**代码 review 在 `SemanticLookupActionHandler` 发现的潜在 NPE 入口**（按相关代码位置）：

| 位置 | 缺陷 | 触发条件 |
|---|---|---|
| L62 `((String) input.get("query")).toLowerCase()` | 不防 null | input 缺 `query` 字段 |
| L75/83/91/99 `Map.of(...)` | 不接受 null value | entity 的 `description`/`type`/`physical.table` 等可选字段为 null |
| 整个 `handle()` | 无 try-catch 兜底 | 任何意外异常直接冒泡到 dispatcher → 500 |

底层 `FsSemanticModelRepository.listDomains` 已经在目录不存在时安全返回 `List.of()`，问题不在 repository 层。

**当前约束**：

- `SemanticModelRepository` 接口及 `FsSemanticModelRepository` 实现已稳定（semantic-model-foundation 已实现完成、未归档）
- `datatalk.semantic_lookup` action ID 已被 AI 调用方依赖，不可改名
- 错误码字符串（`path_outside_session_dir` 等）已被现有 IT 测试断言（`ArchiveArtifactActionHandlerIT.java` 第 84/95/124 行），不可改值
- `QueryEditorAdapter.ts` 的 noop 行为（pin to origin session）由 `query-editor-context-binding` capability 保证，不可改语义

## Goals / Non-Goals

**Goals:**

1. `datatalk_semantic_lookup` 在任何合法 input 下不再返回裸 HTTP 500——要么返回成功的空匹配，要么返回结构化的、带 cause 的错误
2. T32/T12 的错误/noop 响应携带机器可读的字段标识 + 人类可读的提示，让 AI agent 不再误判为 bug
3. 新增 handler 单测覆盖 4 个边界场景，防止后续回归
4. 对应 SKILL.md 增加 "Known limits" 段落，作为 AI 调用方的预期对齐

**Non-Goals:**

- 不复现 T29 的真实 stack trace。报告产生时的 backend 实例已退出，当前 backend 从未收到该调用；防御加固的本意就是即使复现也能拿到可解读结果
- 不改变任何 action 的输入 schema、action ID、错误码字符串值
- 不动 `semantic-model-foundation` 的核心实现（`SemanticModelRepository`、`SemanticModel` record、L0/L1/L2 verified query router 等）
- 不改 UI/视觉/layout/interaction——前端唯一改动是 `QueryEditorAdapter.ts` 中一个返回 string literal 的替换
- 不修复 `~/.data-talk/semantic/` 空目录这一外部状态——它是合理的"还没建模"的初始状态，handler 必须容忍

## Decisions

### D1: 空 query 走 happy path 而非快速失败

```java
String rawQuery = (String) input.get("query");
if (rawQuery == null || rawQuery.isBlank()) {
    Map<String,Object> result = new LinkedHashMap<>();
    result.put("matches", List.of());
    result.put("total", 0);
    result.put("warning", "empty_query");
    return CompletableFuture.completedFuture(result);
}
String query = rawQuery.toLowerCase();
```

**为什么不直接抛 IllegalArgumentException？** Input schema 已在 dispatcher 层把 `query` 声明为 `required`——JSON schema 验证理论上会拦掉 null。但实测 dispatcher 是否真的执行了校验未知（T29 既然能到达 handler，说明至少 `query="订单"` 这个合法值能过）。在 handler 层多一层 null guard 是廉价的纵深防御，且把空 query 当"匹配为空"语义化处理符合搜索类 action 的直觉。

**Alternatives considered:**
- 抛 `IllegalArgumentException` → dispatcher 翻译成 400：契约干净，但回路更长，与"防御性"目标不一致
- 用 `Optional` 包装：过度设计，handler 内部 use 1 次

### D2: `Map.of(...)` → `LinkedHashMap` 全量替换

handler 内 4 处构造匹配项使用 `Map.of(...)`。如果 entity 的 `description`、`type`、`physical.table`，或 measure/metric/dimension 的 `labelZh`/`labelEn`/`description` 任一为 null（YAML 字段缺失），`Map.of` 直接 `NullPointerException("value")`。

**决策：** 替换为 `LinkedHashMap` + `put`。保留键顺序，允许 null value，构造代码稍长但更鲁棒。

**Alternatives considered:**
- `HashMap.of`/工具方法：标准库无此 API
- 用 `Optional` 把 null 包成空字符串：会向上游隐瞒"该字段未定义"的事实，不符合契约真实性
- 在 record 层强制 `Objects.requireNonNull`：违反 D1 决策（防御应该在 boundary，不应该往 domain 渗透），且 `Measure`/`Dimension`/`Metric` record 已经对 labelZh/labelEn 做了 nonNull——entity 的 type/description 故意是 nullable
- 直接调 `Map.entry` + `Map.ofEntries`：仍不接受 null value，无效

### D3: handle() 包 try-catch，未捕获异常翻译为 `ActionExecutionException`

```java
public CompletionStage<Map> handle(ActionContext ctx, Map input) {
    try {
        // ... 现有逻辑
    } catch (RuntimeException ex) {
        log.error("semantic_lookup failed: connectionId={} query={}",
            ctx.connectionId(), maskedQuery, ex);
        throw new ActionExecutionException(
            "semantic_lookup_failed",
            ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(),
            ex);
    }
}
```

**为什么不返回 `Map.of("ok", false, ...)` 而要抛？** Dispatcher 的契约是 handler 返回的 Map 进 `action_result.data`，异常进 `action_result.error`。把 NPE 误塞进 data 会让 AI 误以为查询成功。抛专用异常让 dispatcher 走错误路径，且保留 cause 链供日志诊断。

**前提验证：** 确认 `ActionExecutionException` 类存在；若不存在则用现有的 `RuntimeException` 子类（如 `ActionInvocationException` 或类似），不为此一处新增异常类。

### D4: `ArchiveArtifactAction.errorResult` 改为支持 hint 的 builder

```java
private static Map<String, Object> errorResult(String error, String hint) {
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("ok", false);
    m.put("error", error);
    if (hint != null) m.put("hint", hint);
    return m;
}

// 按错误码注入对应 hint
private static String hintFor(PathSafetyError err) {
    return switch (err) {
        case PATH_OUTSIDE_SESSION_DIR -> "archive_artifact requires the file to be located under the current session's working directory (~/.data-talk/opencode/<sessionId>/...). Move the file under the session dir or skip archiving for ad-hoc artifacts.";
        case PATH_NOT_FOUND -> "The requested path does not exist on disk.";
        case PATH_IS_DIRECTORY -> "archive_artifact only accepts regular files, not directories.";
        case PATH_IS_SYSTEM -> "Cannot archive files from system directories (/proc, /sys, /etc, etc).";
        case PATH_CONTAINS_SYMLINK -> "archive_artifact rejects symlinks to prevent path traversal.";
        case PATH_TOCTOU_RACE -> "Path safety re-check failed (race detected). Retry the call.";
    };
}
```

**为什么用 switch 而非 enum 内置方法？** `PathSafetyError` 是 application 层 enum，hint 文案是 adapter 层关注（属于 action 契约展示）。在 application enum 上加 `hint()` 方法会让 application 知道 adapter 的事；在 adapter 里 switch 保持分层正确。Java 21 sealed/exhaustive switch 在 enum 上 compile-time 完备，不会漏分支。

**向后兼容：** `hint` 是新增字段。`outputSchema()` 增加可选 `hint` property。现有 IT 断言只看 `error` 字段（grep 已确认 `containsEntry("error", ...)`），不会因 `hint` 加入而失败。

### D5: T12 前端 noop reason 结构化

`QueryEditorAdapter.ts:530`：

```ts
// before
return { success: true, data: { noop: true, reason: 'source=user editor cannot follow session' } }

// after
return {
  success: true,
  data: {
    noop: true,
    reason: 'user_editor_pinned_to_origin',
    detail: 'This editor was opened by the user and is explicitly bound to its origin session. AI cannot make it follow the active session; use an AI-opened editor or have the user explicitly switch context.',
  },
}
```

**为什么 reason 改值？** 原值 `'source=user editor cannot follow session'` 是混合了机器键值对（`source=user`）和自然语言（`editor cannot follow session`）的复合串，AI 解析困难。改为单一标识符 `user_editor_pinned_to_origin` 让 reason 成为 stable enum-like 值，详细描述移到 `detail`。

**契约影响：** 现有调用方（AI agent）只是日志或忽略 reason 字段。前端单测（grep 未发现对 `'source=user...'` 字符串的断言）不受影响。如有遗漏，apply 阶段会暴露。

### D6: 测试策略

新增 `SemanticLookupActionHandlerTest`（`server/data-talk-adapter/src/test/java/.../actions/semantic/`）：

| Case | 输入 | 预期 |
|---|---|---|
| `emptyQuery_returnsEmptyMatches` | `input={}` | `{matches:[], total:0, warning:"empty_query"}` |
| `blankQuery_returnsEmptyMatches` | `input={"query":"   "}` | 同上 |
| `noModelDirectory_returnsEmptyMatches` | connection 无目录 | `{matches:[], total:0}` |
| `entityWithNullDescription_doesNotThrow` | YAML 有 entity 但 description=null | 不抛 NPE，正常返回匹配 |
| `nullConnectionId_returnsEmptyOrGracefulError` | `ctx.connectionId()==null` | 不抛 NPE |

**测试基础设施：** 使用 `@TempDir Path tempHome` 实例化 `FsSemanticModelRepository(tempHome)`，避免 mock；手写 minimal YAML 文件覆盖 null-field 场景。符合本仓库测试惯例（`FsSemanticModelRepositoryIT.java` 已采用 `@TempDir`）。

**前端测试：** `QueryEditorAdapter.ts` 改动仅返回字面量调整。仓库已有 `WorkspaceAdapter.test.ts` 类似的 adapter 测试，若 `QueryEditorAdapter` 有对应测试文件涉及 noop case，update 断言；若无，本次不补——属于纯字面量替换，回归风险极低。

### D7: SKILL.md "Known limits" 标准化

3 个 SKILL.md 增加同名段落，作为对 AI 调用方的预期对齐：

```markdown
## Known Limits

- `<具体限制 1>` — `<原因 / 触发条件>`
- `<具体限制 2>` — ...
```

放在 SKILL.md 末尾、`## Examples` 之后（如有）。仅描述真实约束，不重复 schema。

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| 没拿到 T29 的真实 stack trace，可能没修对地方 | D3 的兜底 try-catch 即使没修中实际原因，下次复现时日志会带 cause 链，可二次诊断；D1/D2 是基于代码静态分析的高置信防御，命中率高 |
| `ActionExecutionException` 类是否存在未确认 | apply 阶段第一步先 grep 仓库；若类不存在，用现有泛型 `RuntimeException` 子类（保留 cause 链即可），不增加新异常类型 |
| `outputSchema` 增加 `hint` 字段可能让某些严格 schema 验证的下游报错 | `hint` 是 optional property（未列入 `required`），JSON Schema additionalProperties 默认 true，正常不会失败；如有失败案例由 apply 阶段处理 |
| T12 前端 reason 改值可能破坏 AI 端对原字符串的依赖 | grep 整个仓库无对 `'source=user editor cannot follow session'` 字符串的断言/匹配；外部 AI 调用方按约定应当只对 `noop:true` 做幂等判断，不应解析 reason 文本 |
| Map.of → LinkedHashMap 让返回 map 从 immutable 变 mutable | 下游 dispatcher 立即序列化为 JSON 发出，不存在二次 mutation。无业务影响 |
| 新增 4 个单测会拖慢 backend `mvn verify` | handler 测试是 unit-level（无 Spring Context、无 DB），单 case <100ms，可忽略 |

## Migration Plan

无数据迁移、无 schema 迁移、无配置变更。

部署顺序：

1. 后端：`mvn install -pl data-talk-adapter -am -DskipTests` 刷新 jar（CLAUDE.md "Backend Run vs Compile" 警告）
2. 前端：`npm run build` 或 dev 自动 HMR
3. 验证：
   - 后端 `mvn -pl data-talk-adapter test -Dtest='SemanticLookupActionHandlerTest'`
   - 前端 `npx tsc --noEmit`
   - 端到端：在 opencode 里手动调一次 `mcp__datatalk__semantic_lookup({"query":"订单"})` 确认得到空匹配结果而非 500

**Rollback：** 直接 `git revert` 本次 commit。无状态变更、无 schema 变更，回滚零副作用。

## Open Questions

- **Q1：** `ActionExecutionException` 是否是仓库现有的异常类？apply 阶段需先 grep `class ActionExecutionException` 或类似命名。若不存在，决策回退到抛 `RuntimeException` + `log.error` 即可，不引入新类。
- **Q2：** T29 是否需要回到测试报告里把状态从"⚠️ 失败"改成"防御加固完成"？建议本次 apply 阶段顺手更新报告对应行，避免后续误读为未修复。
- **Q3：** 是否要把 hint 文案做 i18n？现有 action 错误码全部是英文裸字符串（无 i18n key），保持一致即可——hint 沿用英文，AI 调用方都能理解。如未来要做 i18n，单独立项。
