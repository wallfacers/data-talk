# ER Graph Browsing & Designing — `er_inspector` + `er_designer`

> Status: Draft · 2026-04-29 · wallfacers
>
> Scope: 把 DataTalk 的 ER 能力从「LayoutErdAction placeholder + ErdArtifact 字段对不上的废弃组件」彻底升级为两个一等 Stage Tab type：`er_inspector`（只读浏览真库 + 视图层标注）和 `er_designer`（独立 schema 草稿 + DDL 生成 → query_editor → guarded apply）。引入 `@xyflow/react` + `dagre`，复用 Task 6 持久化 + UI Object Protocol，DDL apply 复用 Task 5 L2/L3 confirm，零新建 mutation 出口。

---

## 1. 背景与定位

### 1.1 当前现状

- `LayoutErdAction` (`server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java`) 仅做 4 列网格 placeholder：`position={col*240, row*180}`，无任何布局算法；输出 `nodes={id, position, columns}` + `edges={from,to,fromCol,toCol}`，作为 erd artifact 写入。
- `ErdArtifact` (`client/src/features/ontology/components/erd-artifact.tsx`) 渲染 `node.name` + `node.fields`，**与后端实际给出的 `node.id` + `node.columns` 字段完全对不上**，这块从未端到端跑通过。
- `client/package.json` 没有任何图渲染或图布局库。
- `tab-type-registry.ts` 已为 `query_editor / artifact_preview / file_preview / workspace / diagnostic` 注册类型；`er_designer` 在 AGENTS.md L241 仅为 future 占位。
- Task 6 (Cross-Session Workbench Tabs) 已 ship 持久化 + FTS5 + `StagePersistenceCoordinator` + `ui_find` 三段式协议；Task 5 (Guarded DDL/DML) 已 ship L2/L3 confirm 与 `ExecuteSqlAction`。
- 路线图 `2026-04-25-next-implementation-roadmap-plan.md` Task 8.2 推荐 ER graph browsing 作为可视化扩展首切片，evolving toward editable `er_designer`。

### 1.2 产品诉求

将 ER 能力定位为两个独立的产品模式，统一在 Stage Tab 体系内：

| 模式 | Tab type | 起点 | 编辑面 | AI 价值 |
|---|---|---|---|---|
| Inspect（理解既有库）| `er_inspector` | 真 schema | 选表清单 / 节点位置 / 折叠 / 虚拟关系 / 注释 | "看一下 X 表跟谁有关系"、"标注代码里隐式关联"、"找含某字段的 ER" |
| Author（设计草稿）| `er_designer` | 空白或 fork inspector | 增删表 / 列 / 关系 / DDL 生成 | "设计电商订单 schema"、"从生产库 fork 改表后落到测试库" |

两种模式**永远不绕开 query_editor + Task 5 guarded execution**：Inspector 完全不动真库；Designer 通过 `generate_ddl` 把 DDL 灌进新建的 query_editor Tab，由用户在熟悉的 SQL 工作台 review + L2/L3 confirm 后落库。

### 1.3 与 open-db-studio 的关系

`/home/wushengzhou/workspace/github/open-db-studio` 同样使用 `@xyflow/react` + `dagre`，工程借鉴范围**借形不借色**：

**借鉴的工程做法**：固定 280px 宽节点、每列两 handle、smoothstep + borderRadius 8、line-jump 桥接、关系标签 anti-overlap、自关联 loopback path、AI 修改两阶段高亮（pulse → residual）、viewport 持久化、键盘导航、edge zIndex 提升、节点删除回写 store。

**必须本地化**：所有 CSS token（emerald accent / amber key-primary / sky edge-fk / 节点自定义颜色）→ DESIGN.md semantic（cobalt-only accent / amber 仅 warn）；font / typography → ui-xs/sm/md + Source Sans 3 + JetBrains Mono；i18n 框架 → 自家 `useI18n`；store → 挂 `useStageStore` + `StagePersistenceCoordinator`；MCP 协议 → Stage UI Object Protocol（`ui_read/patch/exec/find`）；后端 → Spring Boot REST + 现有 `ExecuteSqlAction` + Task 5 guarded；DDL 弹窗 → 灌 query_editor 不另开 Dialog。

---

## 2. 决策摘要

| 编号 | 决策 |
|---|---|
| Q1 | day-1 范围：模式 1（Inspector）+ 模式 2（Designer）**同 spec 全部规划**，但**两个 child plan 分批实施**（Plan A: Inspector 先，Plan B: Designer 后） |
| Q2 | Tab type：**两个独立 Tab type** `er_inspector` + `er_designer`，共享 ~70% canvas 层；不混合为「一个 type 两 mode」 |
| Q3 | 渲染栈：`@xyflow/react` v12 + `dagre` v0.8（前端 web worker 跑布局），与 open-db-studio 一致；后端不做布局 |
| Q4 | AI 编辑边界：Inspector 仅视图层 patch（不动真 schema）；Designer 经 `generate_ddl` → query_editor → Task 5 L2/L3 confirm 落库；**永远没有 AI 直接写库的路径** |
| Q5 | Inspector 入口：A1 Schema panel 多选表 + "View ER"；A2 单表右键 "View ER (此表 + 邻居)"；A3 AI `ui_exec(workspace, open_er_inspector)` 三入口共享同一 Adapter |
| Q6 | Designer 入口：B1 Sidebar `[+ 新建] → ER 设计稿`；B2 Inspector 工具栏 "Fork to Designer"；B3 AI `ui_exec(workspace, open_er_designer)` 三入口共享 |
| Q7 | 表选择策略：Inspector 全库 ER 硬上限 100 表，超过 truncate + warning；neighborDepth 默认 1，工具栏可调 0/1/2 |
| Q8 | 节点视觉：固定 280px 宽，header + 列行（PK KeyRound + FK Link）+ > 12 列折叠 + 底部「+ 添加列」（仅 designer）；关系基数标签默认显示 + anti-overlap |
| Q9 | 借形：line-jump 桥接、label anti-overlap、self-ref loopback、AI 两阶段高亮、viewport 持久化全部 day-1 实现 |
| Q10 | DDL apply：灌新建 query_editor Tab + 绑 targetConnection + 走 Task 5 L2/L3 confirm；不另开 DDL Dialog |
| Q11 | day-1 DDL 范围：CREATE TABLE / ALTER ADD COLUMN / ALTER ADD FK / CREATE INDEX；DROP / ALTER COLUMN type / rename **拒绝**，标 SkippedOp + aiHint 引导用户手写 |
| Q12 | dialect：Inspector mysql/postgresql/h2 完整，sqlite 跟随现状（前端连接表单未暴露），oracle 显式 unsupported；Designer mysql/postgresql/h2 完整，sqlite 仅 CREATE TABLE，oracle unsupported |
| Q13 | FK 推断：仅 JDBC `getImportedKeys`；不做命名启发（`user_id` → `users.id` 这类 day-1 不做） |
| Q14 | Tab payload schema：Inspector 薄（视图字段）；Designer 厚（完整 schema 草稿）；都 `persistent: true / scope: 'workspace'`；都进 `stage_tabs` + `stage_tab_payload` + FTS5 trigram |
| Q15 | `ui_patch`：复用既有 `client/src/services/ui-router/jsonPatch.ts` —— RFC 6902 子集（仅 `add` / `remove` / `replace`）+ `/key[matchKey=matchValue]` 寻址扩展 + `/-` 数组 tail；批量 ops 数组；`baseVersion: 'auto'` 默认 + designer 结构性修改 strict；add op 返回 `assignedIds`；**不用** `merge` / `move` / `copy` / `test` |
| Q16 | server actions：废弃 `datatalk.layout_erd`；不新增 ER 专用 server action；AI 通过 `ui_exec(workspace, open_er_*)` 创建 Tab，与 `query_editor.create` 模式一致 |
| Q17 | Tab type registry：独立 `useErTabsStore`（与 `useSqlWorkbenchStore` 同模式），不塞 `useStageStore` 避免渲染敏感性 |
| Q18 | AI ergonomics：12 条强制原则（见 §4），核心 = 零坐标计算 / 零 baseVersion 心智 / 批量 patch / 打开即可用 / aiHint 必带 / 全英文 prompt（P12） |
| Q19 | `erd` artifact kind 整个退场（LayoutErdAction、ErdArtifact、event-reducer 联合分支、artifact-created.tsx 映射、i18n key），不留兼容层 |
| Q20 | **既有代码现状对接**（review 后补）：6 处现存代码必须同步改动才能让 ER 协议落地 —— `UiExecAction.java` / `UiPatchAction.java` schema、`stage-persistence-bootstrap.ts` ER content 订阅、`ui-handlers.ts` force-flush 覆盖新建 tabId、`stage-ui-object-registry.tsx` Adapter 全局注册、Inspector payload 增 `tablesSnapshot` 字段、patch path 写法对齐 `[name=X]` 寻址扩展。详见 §17 |

---

## 3. 范围、非目标、不变量

### 3.1 范围

1. 两个 Tab type `er_inspector` + `er_designer` 注册进 `tab-type-registry.ts`，全部 `persistent: true / scope: 'workspace'`
2. 共享 canvas 层（`<ErCanvas mode='...'>`、`<ErTableNode>`、`<ErEdge>`、`useDagreLayout` worker、`useErHighlight`、`useErKeyboard`、line-jump / label-positioning / self-ref-path utils）
3. Inspector Adapter：完整 `ui_read / ui_patch / ui_exec` 实现 + `extractContent` 索引
4. Designer Adapter：完整 `ui_read / ui_patch / ui_exec` 实现 + `bind_target / diff_against_db / generate_ddl / sync_from_db`
5. WorkspaceAdapter 新增 `open_er_inspector / open_er_designer` 两个 exec 动词
6. 后端 service：`ErRelationDiscoveryService` / `ErDdlGeneratorService` / `ErSchemaDiffService` / `DialectTypeRegistry` / 4 个 dialect-specific `DdlGenerator`
7. 4 个内部 REST 端点 `/api/er/seed-inspector`、`/api/er/generate-ddl`、`/api/er/diff`、`/api/er/sync-from-db`（不对 AI 开放）
8. AGENTS.md 新增 §"ER Tabs (Inspector & Designer)" + recipe 表 + 删 `datatalk_layout_erd` 入口；`STAGE_TAB_DIGEST` ER 行新增统计
9. 新增 `docs/references/er-tab-protocol.md` 完整契约文档
10. 删除 `LayoutErdAction.java` + 旧 `ErdArtifact.tsx` + `'erd'` artifact kind 全 codebase 退场

### 3.2 非目标（day-1 不做）

- ❌ 节点自定义颜色 / 表分组 / 表标签 / ER 视图主题切换（DESIGN.md cobalt-only 强约束）
- ❌ 命名启发 FK 推断（`user_id` → `users.id`）
- ❌ Designer 生成 DROP / ALTER COLUMN type / RENAME DDL（用户必须在 query_editor 手写）
- ❌ Oracle / SQL Server ER 支持（与 diagnostics unsupported 一致）
- ❌ 通过 ER Tab 直接执行 DDL（必须经 query_editor + L2/L3 confirm）
- ❌ ER 模板库 / 多视图共享同一草稿 entity
- ❌ ER 拓扑 SVG / PNG 导出（后续 spec）
- ❌ ER 内 SQL 高亮 / 表内行预览（属于 query_editor 域）
- ❌ AI 协作编辑 ER（多 AI agent 同时改同一 ER tab；day-1 走既有乐观并发即可）
- ❌ 命令面板 (Cmd+P) 跨 ER Tab 跳转

### 3.3 核心不变量

| 不变量 | 说明 |
|---|---|
| **Inspector 不动真 schema** | 所有变更只写 Tab payload；`refresh` 只读不写库 |
| **Designer 落库唯一通道 = query_editor** | `generate_ddl` 输出灌 query_editor Tab；不引入第二个 DDL 执行路径 |
| **AI 不绕开 L2/L3** | 即使 AI 在 chat 主动 apply，最终仍经 query_editor + 用户确认 |
| **零坐标计算（AI）** | AI `open_er_*` / `ui_patch` 不传坐标；前端 dagre 自动算；用户拖动后回写 payload |
| **统一口子** | `useErTabsStore.applyInspectorPatch / applyDesignerPatch` 是唯一 mutation 入口；ESLint custom rule + vitest 静态扫描双门禁 |
| **`ui_find` 只读** | ER Tab 检索复用 Task 6 通用 `ui_find`；任何 mutation 必须走 `ui_patch` / `ui_exec` |
| **Force-flush 后再回应 AI** | `ui_patch` / `ui_exec` 返回前 `await coordinator.flush(targetTabId)`，AI 下一步 `ui_find` 立即看到自己的写 |
| **AI-facing strings 全英文 (P12)** | AGENTS.md / aiHint / STAGE_TAB_DIGEST / open_er_* summary / inputSchema description 全英文 |

---

## 4. AI Ergonomics Principles（横切原则）

12 条强制原则贯穿后续所有节，违反即视为设计倒退。

**P1. 零坐标计算**
AI 永远不传坐标。`open_er_inspector / open_er_designer` 接受 `{connectionId, tables[]}` 或草稿 schema；坐标全由前端 dagre worker 算。"重排"调一句 `ui_exec(auto_layout)`。AI 加表只传表名。

**P2. 零 baseVersion 心智（默认）**
ui_patch / ui_exec 默认 `baseVersion: 'auto'`，服务器自动拉最新版本写入；冲突时返回 `409 conflict_with_concurrent_edit { currentVersion, currentValue, hint }`，AI 重读再写。**Inspector 视图字段（位置/折叠/选择）默认 last-write-wins**；**Designer 表/列/关系定义改写强制 strict baseVersion**（结构变更冲突要让 AI 看到）。

**P3. 批量 patch**
单次 `ui_patch` 支持 op 数组（JSON Patch RFC 6902 风格）。AI 加 5 张表 + 8 个关系 = 一次请求。

**P4. 打开即可用**
`ui_exec(workspace, open_er_inspector, {connectionId, tables})` 单次完成：拉真 schema → 创建 Tab → dagre 布局 → 切焦点 → 返回 `{tabId, summary: "users + orders + 5 neighbors, 7 tables / 9 edges", payloadVersion}` ≤ 200 token。AI 不需要二次 `ui_read` 确认。

**P5. `ui_find` 完全复用 Task 6 三段式**
不新增 ER 专用查找接口。`extractContent` 已设计为可索引文本（§5）。

**P6. 不可感知的持久化**
ensureHydrated / debounce 1s / force-flush 全部由 `StagePersistenceCoordinator` 处理。`ui_patch` 返回前服务器 force-flush。

**P7. 错误码必带 `aiHint`（英文，P12）**
每个失败响应包含 `error.aiHint` —— 一句话告诉 AI 下一步该做什么。完整错误码表见 §6.5。

**P8. AGENTS.md 写入"User-language → action recipe"映射表**
不让 AI 现学协议；AGENTS.md ER 段落直接给 7+ recipe（用户原话 → 第一步 ui_exec / ui_patch 调用样板）。详见 §7.1。

**P9. AI 改虚拟关系 / 加表 / 加列 一步到位**
后端在 `add` op 时自动分配 id（`vr_{nanoid}`、`t_{nanoid}`、`c_{nanoid}`），返回 `assignedIds: { "/path": "newId" }`，AI 后续引用直接用返回 id，不用 round-trip。

**P10. STAGE_TAB_DIGEST 摘要里 ER Tab 多带一行（英文）**
每个 ER Tab 行追加 `(N tables · M relations · conn=xxx)` / `(N tables · M relations · target=xxx/yyy)`。

**P11. AI 触发的 mutation 都附 `highlightScopeId` = 当前调用 sessionId**
前端两阶段高亮（pulse 2.4s → residual fade）让用户看到 AI 改了哪张表 / 哪条关系。Adapter 自动从 `ActionContext` 读取并附上，AI 无感知。

**P12. AI-facing strings 强制英文**
适用范围：AGENTS.md 新增 ER 段落、`error.aiHint`、`STAGE_TAB_DIGEST` 统计、`open_er_*` summary、`@DataTalkAction.description` 默认 messages.properties 文案、`inputSchema` / `outputSchema` description 字段、任何 OpenCode prompt 注入片段。
不受影响：用户 UI 文本（i18n 双语）、`messages_zh_CN.properties`、用户写在 ER Tab 的 `notes` / `comment` 字段、面向最终用户的 `error.message`。
理由：OpenCode 模型在英文上下文里 tool-call 准确率更稳定；既有 AGENTS.md / STAGE_TAB_DIGEST 全英文，对齐现状。

---

## 5. Tab Type Registry, Payload Schema, Persistence

### 5.1 Tab Type Registry 新增条目

`client/src/features/stage/registry/tab-type-registry.ts`：

```ts
import { NetworkIcon, TableIcon } from 'lucide-react'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'

er_inspector: {
  type: 'er_inspector',
  persistent: true,
  scope: 'workspace',
  icon: NetworkIcon,
  labelKey: 'tabType.erInspector',
  extractContent: (p) => {
    const o = p as ErInspectorPayload | null
    if (!o) return ''
    const sel = (o.selection ?? []).join(' ')
    // P1-5 修订：把真 schema snapshot 也加入索引，让 ui_find FTS5
    // 能命中"含 user_email 字段的 ER"这类按列名 / 列类型查询。
    const snapshot = (o.tablesSnapshot ?? [])
      .map(t => {
        const cols = (t.columns ?? []).map(c => `${c.name} ${c.type}`).join(' ')
        return `${t.name} ${cols} ${t.comment ?? ''}`.trim()
      })
      .join('\n')
    const vrels = (o.virtualRelations ?? [])
      .map(r => `${r.from.table}.${r.from.column} ${r.to.table}.${r.to.column}`)
      .join('\n')
    const notes = Object.values(o.notes ?? {}).join('\n')
    return [sel, snapshot, vrels, notes].filter(Boolean).join('\n')
  },
  rehydrate: (tabId, p) => {
    useErTabsStore.getState().hydrateInspector(tabId, p as ErInspectorPayload)
  },
},

er_designer: {
  type: 'er_designer',
  persistent: true,
  scope: 'workspace',
  icon: TableIcon,
  labelKey: 'tabType.erDesigner',
  extractContent: (p) => {
    const o = p as ErDesignerPayload | null
    if (!o) return ''
    return (o.tables ?? [])
      .map(t => {
        const cols = (t.columns ?? []).map(c => `${c.name} ${c.type}`).join(' ')
        return `${t.name} ${cols} ${t.comment ?? ''}`.trim()
      })
      .join('\n')
  },
  rehydrate: (tabId, p) => {
    useErTabsStore.getState().hydrateDesigner(tabId, p as ErDesignerPayload)
  },
},
```

### 5.2 Inspector Payload Schema

```jsonc
{
  "kind": "er_inspector",
  "connectionId": "conn-prod-mysql",
  "database": "ecommerce",
  "schema": null,                                    // mysql 单 schema 写 null
  "selection": ["users", "orders", "products"],      // 用户/AI 选的表清单
  "neighborDepth": 1,                                // 0 / 1 / 2
  "layout": "dagre-LR",                              // day-1 仅 dagre-LR
  "tablesSnapshot": [                                // P1-5 修订：拉真库后的列定义副本，供 FTS5 索引 + AI ui_read
    {
      "name": "users",
      "comment": null,
      "columns": [
        { "name": "id",    "type": "BIGINT",       "isPK": true,  "isFK": false },
        { "name": "email", "type": "VARCHAR(255)", "isPK": false, "isFK": false }
      ],
      "fkOut": [
        { "fromColumn": "id", "toTable": "orders", "toColumn": "user_id" }
      ]
    }
  ],
  "snapshotAt": 1714123456789,                       // 拉取/refresh 时间
  "positions": {
    "users":    { "x": 0,    "y": 0   },
    "orders":   { "x": 320,  "y": 0   },
    "products": { "x": 640,  "y": 200 }
  },
  "collapsed": ["products"],                         // 折叠的节点
  "virtualRelations": [
    {
      "id": "vr_xk3p9q",
      "from": { "table": "orders", "column": "user_email" },
      "to":   { "table": "users",  "column": "email" },
      "type": "many_to_one",
      "note": "implicit link in app code"
    }
  ],
  "notes": {
    "orders": "订单主表"
  },
  "viewport": { "x": 0, "y": 0, "zoom": 1.0 }
}
```

### 5.3 Designer Payload Schema

```jsonc
{
  "kind": "er_designer",
  "targetConnectionId": "conn-test-mysql" | null,    // 可绑定目标库（用于 diff / apply）
  "targetDatabase": "test_db" | null,
  "targetSchema": null,
  "dialect": "mysql",                                // required: mysql / postgresql / h2 / sqlite
  "tables": [
    {
      "id": "t_abc123",
      "name": "users",
      "comment": "用户表",
      "columns": [
        {
          "id": "c_def456",
          "name": "id",
          "type": "BIGINT",
          "nullable": false,
          "isPrimaryKey": true,
          "isAutoIncrement": true,
          "default": null,
          "comment": "user PK"
        },
        {
          "id": "c_def789",
          "name": "email",
          "type": "VARCHAR(255)",
          "nullable": false,
          "isPrimaryKey": false,
          "isAutoIncrement": false,
          "default": null,
          "comment": null
        }
      ],
      "indexes": [],
      "uniques": [{ "columns": ["email"] }]
    }
  ],
  "relations": [
    {
      "id": "r_ghi012",
      "fromTableId": "t_abc123",
      "fromColumnId": "c_def456",
      "toTableId":   "t_jkl345",
      "toColumnId":  "c_mno678",
      "type": "one_to_many",
      "constraintMethod": "database_fk"              // 或 "comment_ref"
    }
  ],
  "positions": { "t_abc123": { "x": 0, "y": 0 } },
  "collapsed": [],
  "viewport": { "x": 0, "y": 0, "zoom": 1.0 }
}
```

### 5.4 `useErTabsStore`

挂在 `useStageStore` 之外的独立 store（与 `useSqlWorkbenchStore` 同模式），避免厚 ER 状态影响其他 Tab 的渲染敏感性。

```ts
// client/src/features/stage/stores/er-tabs-store.ts
interface ErTabsState {
  inspectors: Map<string /* tabId */, ErInspectorPayload>
  designers:  Map<string /* tabId */, ErDesignerPayload>
  
  hydrateInspector(tabId, payload): void
  hydrateDesigner(tabId, payload): void
  applyInspectorPatch(tabId, ops: JsonPatchOp[]): { newVersion, assignedIds }
  applyDesignerPatch(tabId, ops: JsonPatchOp[]):  { newVersion, assignedIds }
  
  getInspectorView(tabId): { nodes: ErNodeData[], edges: ErEdgeData[] } | null
  getDesignerView(tabId):  { nodes: ErNodeData[], edges: ErEdgeData[] } | null
}
```

`applyInspectorPatch / applyDesignerPatch` 是**唯一** mutation 入口；用户拖动 / Adapter ui_patch / 工具栏点击都走这两个方法。Cross-Session Tabs spec 的 ESLint custom rule + vitest 静态扫描兜底。

### 5.5 Persistence

- 不新增 SQLite migration；Task 6 已 ship 的 `stage_tabs` + `stage_tab_payload` 是泛型表，新 type 直接复用
- `payload_json` 存完整 JSON，`extractContent` 输出由触发器同步到 `stage_tab_index` (FTS5)
- payload 大小估算（含 tablesSnapshot）：Inspector 100 表 / 平均 15 列 / 200 关系 / 30 虚拟关系 ≈ 110 KB；Designer 30 表 / 200 列 / 50 关系 ≈ 60 KB；硬上限 1 MB（与 Cross-Session Tabs spec 一致），超过返回 `er_payload_oversized`

#### 5.5.1 ER 内容订阅（P0-2 修订）

`client/src/features/stage/persistence/stage-persistence-bootstrap.ts` 当前仅订阅 `useStageStore` 元数据 + `useSqlWorkbenchStore` 文本。**ER patches 不会自动持久化也不会进 FTS5 索引**，必须新增订阅。

修改 `stage-persistence-bootstrap.ts`：

```ts
// 新增
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'

function diffErContentAndSchedule(
  next: { inspectors: Map<string, ErInspectorPayload>; designers: Map<string, ErDesignerPayload> },
  prev: typeof next,
): void {
  for (const [tabId, payload] of next.inspectors) {
    const prevPayload = prev.inspectors.get(tabId)
    if (prevPayload === payload) continue
    const tab = useStageStore.getState().findTab(tabId)
    if (!tab || !isPersistent(tab.type)) continue
    const desc = TAB_TYPE_REGISTRY[tab.type]
    coordinator.scheduleContentWrite(tabId, {
      payload,
      contentText: desc?.extractContent?.(payload) ?? '',
      expectedVersion: tab.payloadVersion,
    })
  }
  // designers 同上
}

// 在 useSqlWorkbenchStore 订阅块后追加
{
  let prevEr = {
    inspectors: useErTabsStore.getState().inspectors,
    designers:  useErTabsStore.getState().designers,
  }
  useErTabsStore.subscribe((state) => {
    const next = { inspectors: state.inspectors, designers: state.designers }
    if (next.inspectors !== prevEr.inspectors || next.designers !== prevEr.designers) {
      diffErContentAndSchedule(next, prevEr)
      prevEr = next
    }
  })
}
```

debounce 仍由 `coordinator.scheduleContentWrite` 内部处理（1s）；`ui_patch` / `ui_exec` 返回前的 force-flush 由 §5.5.2 ui-handlers 修订统一处理。

#### 5.5.2 ui-handlers force-flush 覆盖新建 tabId（P1-3 修订）

`client/src/features/actions/ui-handlers.ts:59-77` `resolveTarget` 仅识别 input target；`MUTATING_EXEC` 集合不含 ER verbs；且 `open_er_inspector / open_er_designer` 等动作返回的是**新建** tabId，flush input target 没意义。

修改：

```ts
// MUTATING_EXEC 扩充（强制 flush）
const MUTATING_EXEC = new Set([
  'open', 'focus', 'detach', 'archive', 'trash',
  'set_context', 'apply_text_edits', 'replace_content',
  // ER verbs（P1-3 修订）
  'open_er_inspector', 'open_er_designer',
  'refresh', 'auto_layout', 'fit_view', 'add_neighbors', 'fork_to_designer',
  'bind_target', 'unbind_target', 'sync_from_db', 'generate_ddl',
  // diff_against_db 是只读，不在此集合
])

registerClientHandler('datatalk.ui.exec', async (input) => {
  const i = input as ExecInput
  const target = resolveTarget(i)
  if (target) await coordinator.ensureHydrated(target)
  if (i.action === 'run_sql' && target) await coordinator.flush(target)
  const result = await forward({ ... })
  if (target && isMutatingExec(i.action)) await coordinator.flush(target)
  // 新增：mutating action 创建/产生新 tab → flush 返回的 tabId（P1-3）
  const created = isRecord(result)
    ? (result.tabId as string | undefined)
      ?? (result.newTabId as string | undefined)
      ?? (result.queryEditorTabId as string | undefined)
    : undefined
  if (created && created !== target) await coordinator.flush(created)
  return result
})
```

`datatalk.ui.patch` handler 已经无条件 flush input target，无需改动；ER patch 全走 input target 路径。

#### 5.5.3 Adapter 全局注册（P1-4 修订）

`client/src/features/stage/components/stage-ui-object-registry.tsx:32` 当前仅 filter `query_editor` 全局注册。如果 ER Adapter 只在 `<ErInspectorTab>` / `<ErDesignerTab>` 渲染时才注册，**非 active 的持久化 ER tab 在 `ui_read` / `ui_patch` / `ui_find` → `ui_read` 等场景下命中不到**（既有 query_editor 全局注册的良好行为不会自动复制到 ER tab type）。

修改 `stage-ui-object-registry.tsx`：

```tsx
function RegisteredErInspector({ tabId, sessionId }: { tabId: string; sessionId: string | null }) {
  const instance = useMemo(() => new ErInspectorAdapter(tabId, () => sessionId), [tabId, sessionId])
  useUIObjectRegistry(instance)
  return null
}
function RegisteredErDesigner({ tabId, sessionId }: { tabId: string; sessionId: string | null }) {
  const instance = useMemo(() => new ErDesignerAdapter(tabId, () => sessionId), [tabId, sessionId])
  useUIObjectRegistry(instance)
  return null
}

export function StageUIObjectRegistry({ tabs }: { tabs: StageTab[] }) {
  // ... 既有 ...
  return (
    <>
      <RegisteredInstance instance={workspace} />
      {tabs.filter(t => t.type === 'query_editor').map(t => <RegisteredQueryEditor ... />)}
      {tabs.filter(t => t.type === 'er_inspector').map(t => <RegisteredErInspector key={t.tabId} tabId={t.tabId} sessionId={t.originSessionId ?? null} />)}
      {tabs.filter(t => t.type === 'er_designer').map(t => <RegisteredErDesigner  key={t.tabId} tabId={t.tabId} sessionId={t.originSessionId ?? null} />)}
    </>
  )
}
```

`<ErInspectorTab>` / `<ErDesignerTab>` 组件本身**不**调用 `useUIObjectRegistry`；它们只负责画布 + 工具栏。`§7.9 Stage Tab 渲染入口`已同步修订。

#### 5.5.4 Java action schema 改动（P0-1 修订）

`server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java:46-57` 当前 `object` 隐式 enum 仅 `workspace / query_editor`，且 `workspace.action` enum 仅 `["open", "focus", "choose_connection", "detach", "archive", "trash"]`，`workspace open` 的 `params.type` enum 仅 `["query_editor"]` —— 现状不支持 ER。同样 `UiPatchAction.java:36` `object` enum 仅 `["query_editor"]`。

**改动清单**：

`UiExecAction.java`：

1. `inputSchema()` `oneOf` 列表追加 `erInspectorExecSchema()` / `erDesignerExecSchema()`
2. `workspaceExecSchema()` `action` enum 追加 `"open_er_inspector"` / `"open_er_designer"`
3. `workspaceExecSchema()` `params` 增字段：`tables` (array of string)、`neighborDepth` (number 0/1/2)、`dialect` (enum mysql/postgresql/h2/sqlite)、`targetConnectionId` / `targetDatabase` / `targetSchema` / `seedTables` / `seedRelations`
4. 新建 `erInspectorExecSchema()`：`object: "er_inspector"`，`action` enum `["refresh","auto_layout","fit_view","add_neighbors","fork_to_designer"]`，对应 params
5. 新建 `erDesignerExecSchema()`：`object: "er_designer"`，`action` enum `["auto_layout","fit_view","bind_target","unbind_target","sync_from_db","diff_against_db","generate_ddl"]`，对应 params

`UiPatchAction.java`：

1. `inputSchema()` `object` enum 追加 `"er_inspector"` / `"er_designer"`
2. `replaceOp` 列表为 ER 增加路径：
   - 简化方案：因为 ER paths 较多（§6.3 / §6.4 共 ~20 个），day-1 用 **宽松 path schema**（`type: string`，描述列出白名单），由 client `ErInspectorAdapter` / `ErDesignerAdapter` `patchCapabilities` 守门并返回结构化错误；不在 server schema 里枚举具体 path（避免 schema 巨大）
   - 严格方案（后续可选）：把每个 ER path 加到 oneOf 列表
3. ER ops 的 `op` 允许 `add` / `remove` / `replace`（与 query_editor `replace`-only 不同）
4. `baseVersion` 字段移到顶层 input，标 `'auto' | number`，并在 description 里说明 designer 结构性 path 强制 number

i18n keys 同步加：

```
action.ui_exec.er_inspector.refresh.description
action.ui_exec.er_inspector.fork_to_designer.description
action.ui_exec.er_designer.generate_ddl.description
... (覆盖所有 ER verbs)
```

`server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java` 同步增 ER schema 一致性断言（AGENTS.md 引用的 verb 都能在 inputSchema enum 中找到）。

`server/data-talk-application/src/main/java/com/datatalk/application/sql/JdbcResultValueNormalizer.java` 不受影响（ER 不读 row data）。

`server/data-talk-application/src/main/java/com/datatalk/application/opencode/McpNameMapper.java` 不需要改（仍是 `datatalk.ui.exec` ↔ `datatalk_ui_exec` 映射，新增 verb 不增加 tool 数量）。

### 5.6 i18n

```ts
// client/src/i18n/messages.ts
'tabType.erInspector':  'ER Inspector' | 'ER 浏览',
'tabType.erDesigner':   'ER Designer'  | 'ER 设计',
'erCanvas.toolbar.refresh': 'Refresh schema' | '刷新',
'erCanvas.toolbar.autoLayout': 'Auto layout' | '自动布局',
'erCanvas.toolbar.fitView': 'Fit view' | '适配视图',
'erCanvas.toolbar.neighborDepth': 'Neighbor depth' | '邻居深度',
'erCanvas.toolbar.addVirtualRelation': 'Add virtual relation' | '添加虚拟关系',
'erCanvas.toolbar.forkToDesigner': 'Fork to Designer' | '分叉到设计稿',
'erCanvas.toolbar.addTable': 'Add table' | '添加表',
'erCanvas.toolbar.bindTarget': 'Bind target' | '绑定目标',
'erCanvas.toolbar.diffVsDb': 'Diff vs DB' | '对比目标库',
'erCanvas.toolbar.generateDdl': 'Generate DDL' | '生成 DDL',
'erCanvas.toolbar.dialect': 'Dialect' | '方言',
'erCanvas.empty.oracle': 'ER is not supported for Oracle. Use query_editor + read_schema for inspection.' | 'ER 不支持 Oracle。请使用 query_editor + read_schema 查看。',
'erCanvas.empty.sqlite': 'ER for SQLite requires backend connection setup. Use query_editor for now.' | 'SQLite ER 需要后端连接配置。当前请使用 query_editor。',
// ...
```

---

## 6. UI Object Protocol Contract

### 6.1 WorkspaceAdapter 新增 2 个 exec 动词

```jsonc
// ui_exec(workspace, open_er_inspector)
input: {
  "connectionId":  "string",                         // required
  "tables":        ["users", "orders"],              // required, 1..100
  "neighborDepth": 0 | 1 | 2,                        // default 1
  "title":         "string"                          // optional Tab title
}
output: {
  "tabId":          "er_inspector_a1b2",
  "payloadVersion": 1,
  "summary":        "users + orders + 5 neighbors, 7 tables / 9 edges",
  "tables":         ["users","orders","products","order_items","..."],
  "edges":          9,
  "warnings":       []
}

// ui_exec(workspace, open_er_designer)
input: {
  "dialect":              "mysql"|"postgresql"|"h2"|"sqlite",   // required
  "title":                "string",
  "targetConnectionId":   "string"|null,
  "targetDatabase":       "string"|null,
  "targetSchema":         "string"|null,
  "seedTables":           [{ "name":"...", "columns":[...] }],   // optional 初始表
  "seedRelations":        [...]
}
output: {
  "tabId":          "er_designer_c3d4",
  "payloadVersion": 1,
  "summary":        "blank designer (mysql, no target)" | "3 tables, 2 relations (mysql → conn=prod)"
}
```

### 6.2 Patch grammar（P2-6 修订）

复用既有 `client/src/services/ui-router/jsonPatch.ts`，**RFC 6902 子集 + `[name=X]` 寻址扩展**：

- **支持的 op**：`add`、`remove`、`replace`（**不支持** `merge` / `move` / `copy` / `test`）
- **path 写法**：
  - 普通 key：`/selection`、`/dialect`、`/notes/orders`（`orders` 是 map key）
  - 数组按索引：`/collapsed/0`（不稳定，仅用于 remove）
  - **数组按 `[matchKey=matchValue]` 寻址**（推荐用于稳定 id 寻址）：`/tables[id=t_abc123]`、`/tables[id=t_abc123]/columns[id=c_def456]`、`/relations[id=r_ghi012]`、`/virtualRelations[id=vr_xk3p9q]`
  - **数组 tail（用于 add）**：`/-`，例如 `/virtualRelations/-`、`/tables/-`、`/tables[id=t_abc123]/columns/-`
- **批量**：单次 `ops` 数组多个独立 op，**按顺序**应用；任一失败整体回滚（jsonPatch.ts 已是 immutable apply，回滚天然）
- 这些规则与 `ui-router/jsonPatch.ts` `walk()` 函数实现一致；新 ER paths 直接复用现有路径解析器，无需扩展

### 6.2.1 `ui_patch` 通用契约

```jsonc
input: {
  "tabId":       "er_inspector_a1b2",
  "baseVersion": 17 | "auto",                        // default "auto"
  "ops": [
    { "op": "add",     "path": "/virtualRelations/-",     "value": {...} },
    { "op": "replace", "path": "/notes/orders",            "value": "订单主表" },
    { "op": "remove",  "path": "/virtualRelations[id=vr_xk3p9q]" }
  ]
}
output: {
  "tabId":          "er_inspector_a1b2",
  "payloadVersion": 18,
  "assignedIds": {
    "/virtualRelations/0": "vr_xk3p9q"               // add op 的索引位 + 后端分配 id
  }
}
```

### 6.3 Inspector `ui_patch` 路径白名单

| path | op | 默认 baseVersion |
|---|---|---|
| `/selection` | replace | auto |
| `/neighborDepth` | replace | auto |
| `/positions` | replace（整对象） | auto (last-write-wins) |
| `/positions/{tableName}` | replace, remove | auto |
| `/collapsed` | replace | auto |
| `/virtualRelations` | replace | auto |
| `/virtualRelations/-` | add | auto |
| `/virtualRelations[id=<vrId>]` | replace, remove | auto |
| `/notes` | replace | auto |
| `/notes/{tableName}` | replace, remove | auto |
| `/viewport` | replace | auto (last-write-wins) |

> `/positions` 整对象 replace 取代了原 `merge` op；批量改坐标用一次 `replace /positions value={...}`。

### 6.4 Designer `ui_patch` 路径白名单

| path | op | 默认 baseVersion |
|---|---|---|
| `/tables/-` | add | **strict** |
| `/tables[id=<tid>]` | replace, remove | strict |
| `/tables[id=<tid>]/name` | replace | strict |
| `/tables[id=<tid>]/comment` | replace | auto |
| `/tables[id=<tid>]/columns/-` | add | strict |
| `/tables[id=<tid>]/columns[id=<cid>]` | replace, remove | strict |
| `/relations/-` | add | strict |
| `/relations[id=<rid>]` | replace, remove | strict |
| `/positions` | replace（整对象） | auto |
| `/positions/{tableId}` | replace, remove | auto |
| `/collapsed` | replace | auto |
| `/viewport` | replace | auto |
| `/dialect` | replace | strict |
| `/targetConnectionId`, `/targetDatabase`, `/targetSchema` | replace | strict |

### 6.5 Inspector `ui_exec` 动词集

| action | params | output | 用途 |
|---|---|---|---|
| `refresh` | — | `{updatedTables, removedTables, payloadVersion}` | 重新拉真 schema 更新列定义 |
| `auto_layout` | — | `{positions, payloadVersion}` | 触发 dagre worker 写回 positions |
| `fit_view` | — | `{viewport, payloadVersion}` | 重置 viewport |
| `add_neighbors` | `{table}` | `{addedTables, payloadVersion}` | 把指定表的一阶邻居加进 selection |
| `fork_to_designer` | `{title?}` | `{newTabId, payloadVersion}` | 创建 designer Tab 复制全部表 schema |

### 6.6 Designer `ui_exec` 动词集

| action | params | output | 用途 |
|---|---|---|---|
| `auto_layout` | — | `{positions, payloadVersion}` | 同 |
| `fit_view` | — | `{viewport, payloadVersion}` | 同 |
| `bind_target` | `{connectionId, database?, schema?}` | `{payloadVersion}` | 绑定目标库 |
| `unbind_target` | — | `{payloadVersion}` | 解绑 |
| `sync_from_db` | `{tables?}` | `{updatedTables, addedTables, removedTables, payloadVersion}` | 从绑定库拉 schema 覆盖 designer |
| `diff_against_db` | — | `{diff: [...]}` | 不写库，仅返回 diff |
| `generate_ddl` | `{includeDrops?: false}` | `{queryEditorTabId, ddl, skippedOps, payloadVersion}` | 生成 DDL → 灌 query_editor Tab |

### 6.7 错误码全表（带英文 aiHint）

| code | HTTP | 触发 | aiHint (EN, P12) |
|---|---|---|---|
| `dialect_unsupported` | 400 | dialect=oracle/sqlserver/未列举 | `"ER does not support {dialect}. Use query_editor with read_schema for inspection, or pick mysql/postgresql/h2 for design drafts."` |
| `connection_unavailable` | 404 | connectionId 不存在 | `"The target connection no longer exists. Call datatalk_list_connections and ask the user to pick a valid one."` |
| `tables_not_found` | 404 | 任一 table 不存在 | `"Tables {missing} were not found in the connection. Use datatalk_read_schema with pattern to confirm exact names; common typos: {suggestions}."` |
| `er_payload_oversized` | 413 | > 100 表 / payload > 1MB | `"Payload exceeds limit. Narrow down using read_schema with pattern/limit, or split into multiple ER tabs by domain."` |
| `invalid_path` | 400 | path 不在白名单 | `"Path {path} is not patchable on this tab type. Allowed paths: {allowed}."` |
| `invalid_op` | 400 | op + path 组合不合法 | `"Op {op} is not allowed on path {path}. See ER protocol reference."` |
| `conflict_with_concurrent_edit` | 409 | strict baseVersion 冲突 | `"Tab was modified concurrently (server v{currentVersion} vs your v{baseVersion}). Re-read the tab and re-apply your patch on top of the latest state."` |
| `schema_validation_failed` | 422 | value 不符 schema | `"Validation failed: {fieldErrors}. Check the er-tab-protocol reference for field types and constraints."` |
| `immutable_path_in_inspector` | 400 | Inspector 改 /tables 等 | `"Inspector tabs are read-only views of real schema. To edit tables, fork this tab to a designer first via ui_exec(fork_to_designer)."` |
| `ddl_generation_partial_skipped` | 200 (warn) | 含 DROP/ALTER COLUMN 跳过 | `"Generated DDL written to query_editor {queryEditorTabId}. Skipped operations require manual SQL: {skippedOps}. Have the user write them in query_editor and run with L2/L3 confirmation."` |
| `target_required_for_apply` | 400 | generate_ddl 但未 bind_target | `"generate_ddl requires bind_target first. Call ui_exec(designer, bind_target, {connectionId, database, schema}) and retry."` |

---

## 7. Frontend Architecture

### 7.1 目录树

```
client/src/features/stage/components/er-canvas/
├─ ErCanvas.tsx                  # 主画布（ReactFlow 容器，mode-aware）
├─ ErTableNode.tsx               # 节点（mode-aware：可编辑性由 props 控制）
├─ ErEdge.tsx                    # 边（含 line-jump + label anti-overlap + self-ref）
├─ ErToolbar.tsx                 # 工具栏（mode-specific 按钮集）
├─ ErMinimap.tsx                 # 右下小地图（自动出现于 > 10 节点）
├─ ErEmptyState.tsx              # 空态（含 Oracle / SQLite 兜底文案）
├─ hooks/
│  ├─ useDagreLayout.ts          # web worker 入口
│  ├─ useErKeyboard.ts           # Tab/方向键/Backspace/+- 等
│  └─ useErHighlight.ts          # AI pulse → residual 两阶段高亮
├─ utils/
│  ├─ crossings.ts               # line-jump 桥接算法（借自 open-db-studio）
│  ├─ label-positioning.ts       # 关系标签防重叠（借自 open-db-studio）
│  ├─ self-ref-path.ts           # 自关联表 loopback path（借自 open-db-studio）
│  └─ payload-to-graph.ts        # ErInspectorPayload | ErDesignerPayload → {nodes, edges}
└─ workers/
   └─ dagre-layout.worker.ts     # dagre LR 布局，避免主线程 jank

client/src/features/stage/components/er-inspector-tab.tsx
client/src/features/stage/components/er-designer-tab.tsx
client/src/features/stage/adapters/ErInspectorAdapter.ts
client/src/features/stage/adapters/ErDesignerAdapter.ts
client/src/features/stage/stores/er-tabs-store.ts
client/src/features/stage/registry/tab-type-registry.ts        # 修改
```

### 7.2 `<ErCanvas>` 单一 mode-aware 入口

```tsx
<ErCanvas
  tabId={tabId}
  mode='inspector' | 'designer'
  graph={derivedFromUseErTabsStore}
  onNodeDragStop={(positions) => storeApi.applyPatch([{op:'replace', path:'/positions', value: positions}])}
  onConnect={mode==='designer' ? handleConnect : undefined}    // Inspector 禁用拖拽建关系
  onNodesDelete={mode==='designer' ? handleDelete : undefined}
  toolbar={<ErToolbar tabId={tabId} mode={mode} />}
  highlightScopeId={tabId}
/>
```

### 7.3 `<ErTableNode>` mode 切换

- `mode='inspector'`：列行只读；header 右上角 lock 图标；列名 / 类型不可双击编辑；无 `+` 加列按钮
- `mode='designer'`：列行可编辑（双击列名进入 input；类型 dropdown 选择）；header 显示 pencil 图标；底部 `+ Add column`；右键菜单（增列 / 删表）

### 7.4 `<ErEdge>` 完全共享

- 借鉴 open-db-studio 的 `computeCrossings` + `resolveLabelPos` + `buildSelfRefPath` 三个算法
- 边样式：`accent.primary` (selected/hover) / `border.strong` (idle, fk) / `accent.warn` (idle, virtual ref, dashed 4-2)
- handle 颜色：`border.strong` (idle) → `accent.primary` (hover/connected)，**不用** open-db-studio 的红/绿双色
- 关系基数 label 默认显示（`1:N` / `N:N` 等），用 anti-overlap 算法防糊
- 节点 header 右上加约束方式小圆点：实 FK = `accent.primary`，虚拟引用 = `accent.warn`

### 7.5 `<ErToolbar>` 按钮集

Inspector：
```
[Refresh schema] [Auto layout] [Fit view] [Neighbor depth: 0|1|2] | [Add virtual relation] | [Fork to Designer ↗]
```

Designer：
```
[Add table] [Auto layout] [Fit view] | [Bind target: conn▾] [Diff vs DB] [Generate DDL ↗] | [Dialect: mysql▾]
```

样式契约：
- `bg.subtle` 容器 + `border.subtle`
- 按钮 idle = `text.muted`，hover = `interaction.hover`，pressed = `interaction.active`
- 主要 CTA（"Generate DDL"、"Fork to Designer"）= `accent.primary` 描边 + 文字
- ARIA name 必填，键盘 Tab 序列正确

### 7.6 `useDagreLayout` worker 协议

```ts
type LayoutMessage = {
  nodes: { id: string; width: number; height: number }[]
  edges: { source: string; target: string }[]
  config: { rankdir: 'LR'; nodesep: 80; ranksep: 200 }
}
type LayoutResponse = {
  positions: Record<string, { x: number; y: number }>
  durationMs: number
}
```

性能预算：100 表 < 50ms；1000 表 < 300ms。worker 不阻塞主线程，超过 100 表的 ER 也能流畅交互。

### 7.7 `useErHighlight` 两阶段高亮

```
pulse phase:    0 ~ 2.4s, 强 accent.primary 描边 + drop-shadow
residual phase: 2.4 ~ 8s, 弱 accent.primary 描边淡出
触发: Adapter 接收到 ui_patch 时，从 ActionContext.sessionId 设置 highlightScopeId
      useErHighlight 通过 store subscribe 获知 scope changes，对 path 涉及的 node/edge id 进入 pulse 状态
      prefers-reduced-motion: 跳过 pulse，直接 residual
```

实现复用 open-db-studio 的 `useFieldHighlight` hook 思路，但颜色全换 `accent.primary` + `accent.primarySurface`（不用 indigo）。

### 7.8 键盘契约（`useErKeyboard`）

| 键 | Inspector | Designer |
|---|---|---|
| `Tab` | 在节点间循环 focus | 同 |
| `Enter` / `Space` | toggle 折叠当前节点 | 同 |
| `Backspace` / `Delete` | 移除当前节点出 selection | 删表 |
| `←→↑↓` | 平移画布 | 同 |
| `+` / `-` | 缩放 | 同 |
| `Esc` | 取消选择 | 同 |
| `Cmd/Ctrl + L` | trigger auto_layout | 同 |
| `Cmd/Ctrl + 0` | fit_view | 同 |

`prefers-reduced-motion`：禁掉 selection / hover / pulse 渐变。

### 7.9 Stage Tab 渲染入口

`stage-tab-content.tsx` 已有按 `tab.type` 分发的 switch；新增两个 case：

```tsx
case 'er_inspector': return <ErInspectorTab tabId={tab.tabId} />
case 'er_designer':  return <ErDesignerTab  tabId={tab.tabId} />
```

`<ErInspectorTab>` / `<ErDesignerTab>` 各自:
1. `useEffect` ensureHydrated（lazy load payload）
2. 从 `useErTabsStore` 读 payload + 派生 graph
3. 渲染 `<ErCanvas mode='...' graph={...} />` + `<ErToolbar mode='...' />`

**Adapter 注册不在 Tab 组件内**（P1-4 修订）：`ErInspectorAdapter` / `ErDesignerAdapter` 由 `stage-ui-object-registry.tsx` 全局注册（与 `QueryEditorAdapter` 一致），覆盖所有持久化 ER tab —— 即使 tab 不是 active，AI 通过 `ui_read` / `ui_patch` / `ui_find` → `ui_read` 也能命中。详见 §5.5.3。

### 7.10 ErdArtifact 弃用清理

删除：
- `client/src/features/ontology/components/erd-artifact.tsx`
- `client/src/features/ontology/components/artifact-dispatcher.tsx` 的 `case 'erd'` 分支
- `client/src/services/channel/event-reducer.ts` 的 `kind: 'table' | 'chart' | 'erd'` 联合中 `'erd'`
- `client/src/features/chat/components/tools/renderers/artifact-created.tsx` 的 `'erd'` 映射
- `client/src/i18n/messages.ts` 的 `'artifact.erdEmpty'` 两条
- `client/src/features/session/hooks/use-session-history.ts` 的 `'erd'` 联合分支

`erd` 这个 artifact kind 整个从 codebase 退场。

### 7.11 包依赖

`client/package.json` 新增：

```json
{
  "@xyflow/react": "^12.10.1",
  "dagre": "^0.8.5",
  "@types/dagre": "^0.7.54"
}
```

---

## 8. Backend Architecture

### 8.1 模块布局

```
server/data-talk-domain/src/main/java/com/datatalk/domain/er/
├─ ErRelation.java              # record (sourceTable, sourceColumn, targetTable, targetColumn, type, source)
├─ ErTableMeta.java             # record (name, comment?, columns: List<ErColumnMeta>)
├─ ErColumnMeta.java            # record (name, type, nullable, isPK, isFK, isAutoIncrement, default?, comment?)
├─ ErGraph.java                 # record (nodes: List<ErTableMeta>, edges: List<ErRelation>, summary: String)
├─ ErDdlPlan.java               # record (statements: List<ErDdlStatement>, skipped: List<SkippedOp>)
├─ ErDdlStatement.java          # record (sql, kind: CREATE_TABLE | ADD_COLUMN | ADD_FK | CREATE_INDEX, table)
├─ ErSchemaDiff.java            # sealed interface: TableAdded | TableDropped | ColumnAdded | ColumnTypeChanged | ColumnDropped | ConstraintAdded | ConstraintDropped
└─ Dialect.java                 # enum { MYSQL, POSTGRESQL, H2, SQLITE }   (Oracle / SQLServer 暂不入枚)

server/data-talk-application/src/main/java/com/datatalk/application/er/
├─ ErRelationDiscoveryService.java    # JDBC getImportedKeys → ErGraph
├─ ErDdlGeneratorService.java         # ErDesignerPayload + targetSchema → ErDdlPlan
├─ ErSchemaDiffService.java           # 草稿 vs 真库 schema 计算 diff
└─ DialectTypeRegistry.java           # 抽象类型 → 各 dialect 字符串

server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/
├─ JdbcErRelationReader.java          # getImportedKeys + getColumns 实际 JDBC 调用
└─ DdlGenerators/
   ├─ MySqlDdlGenerator.java
   ├─ PostgresDdlGenerator.java
   ├─ H2DdlGenerator.java
   └─ SqliteDdlGenerator.java         # 仅 CREATE TABLE，其它操作返回 SkippedOp

server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/
└─ ErTabController.java               # POST /api/er/seed-inspector | generate-ddl | diff | sync-from-db

server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/
└─ (无新增；workspace.open_er_inspector / open_er_designer 通过 WorkspaceAdapter 注册)
└─ LayoutErdAction.java               # 删除
```

### 8.2 `ErRelationDiscoveryService` 关键签名

```java
public interface ErRelationDiscoveryService {
    /**
     * Read tables + columns + FK relations for a connection.
     * Expands selection to include direct neighbors based on neighborDepth.
     *
     * @param connectionId  target connection
     * @param tables        seed tables (caller must provide non-empty list ≤ 100)
     * @param neighborDepth 0 = strict; 1 = include direct FK neighbors; 2 = two hops
     * @return graph with nodes (full column metadata) + edges (FK relations only)
     * @throws DialectUnsupportedException if connection kind is oracle/sqlserver
     */
    ErGraph discover(String connectionId, List<String> tables, int neighborDepth);
    
    /** Refresh tables in an existing inspector — same as discover but reuses connection cache. */
    ErGraph refresh(String connectionId, List<String> tables, int neighborDepth);
}
```

实现关键点：
- 用既有 `ConnectionService.openConnection(connectionId)`（Hikari pool 已有）拿 `Connection`
- 表元数据：`meta.getColumns(catalog, schema, table, "%")`
- FK：`meta.getImportedKeys(catalog, schema, table)` 收集到 `Set<ErRelation>`
- 邻居扩展：BFS，第 N 层加入 FK 指向 / 被指向的表，去重
- 截断：表数 > 100 抛 `ErPayloadOversizedException`，含已加载表清单
- 默认 schema：`mysql` 用 `getCatalog()`；`postgresql` 用 `getSchema()` / `current_schema()`；`h2` 用 `PUBLIC` 兜底；`sqlite` 单 schema
- 大库性能：用虚拟线程 fan-out 并发拉每张表的列 + FK，10 张表并发 < 200ms

### 8.3 `ErDdlGeneratorService` 关键签名

```java
public record GenerateDdlRequest(
    ErDesignerPayload payload,
    String connectionId,        // 必须已 bind_target
    boolean includeDrops        // day-1 始终 false；保留以便后续启用
) {}

public record GenerateDdlResult(
    String ddl,                 // 拼好的多语句 SQL（按 dialect 分隔符 ;）
    List<ErDdlStatement> statements,
    List<SkippedOp> skipped     // 被跳过的 DROP / ALTER COLUMN 等
) {}

public interface ErDdlGeneratorService {
    GenerateDdlResult generate(GenerateDdlRequest req);
    List<ErSchemaDiff> diff(ErDesignerPayload payload, String connectionId);
}
```

调用流：
1. 从 `payload.targetConnectionId` + database + schema 拉真库 `ErGraph`（复用 `ErRelationDiscoveryService`）
2. 与 `payload.tables / relations` 计算 `List<ErSchemaDiff>`
3. 每个 diff 用对应 dialect 的 `DdlGenerator` 转成 `ErDdlStatement`
4. DROP / ALTER COLUMN 不生成 SQL，转 `SkippedOp { op, reason: "day1_unsupported", hint }`
5. 拼最终 `ddl` 字符串（按 dialect 用 `;` + 换行）
6. 写入新建 query_editor Tab 由前端 `ErDesignerAdapter.exec(generate_ddl)` 处理

### 8.4 `DialectTypeRegistry` 单一事实源

```java
public final class DialectTypeRegistry {
    public enum AbstractType {
        BIGINT, INT, SMALLINT, DECIMAL, VARCHAR, TEXT, BOOLEAN,
        DATE, TIMESTAMP, JSON, BLOB
    }
    
    public static String render(Dialect d, AbstractType t, Map<String,Object> params);
    public static String quote(Dialect d, String ident);
    public static String autoIncrementPk(Dialect d, String colName);
}
```

类型映射矩阵（day-1）：

| 抽象 | MySQL | PostgreSQL | H2 | SQLite |
|---|---|---|---|---|
| BIGINT | `BIGINT` | `BIGINT` | `BIGINT` | `INTEGER` |
| INT | `INT` | `INTEGER` | `INT` | `INTEGER` |
| SMALLINT | `SMALLINT` | `SMALLINT` | `SMALLINT` | `INTEGER` |
| DECIMAL(p,s) | `DECIMAL(p,s)` | `NUMERIC(p,s)` | `DECIMAL(p,s)` | `NUMERIC(p,s)` |
| VARCHAR(n) | `VARCHAR(n)` | `VARCHAR(n)` | `VARCHAR(n)` | `TEXT` |
| TEXT | `TEXT` | `TEXT` | `CLOB` | `TEXT` |
| BOOLEAN | `TINYINT(1)` | `BOOLEAN` | `BOOLEAN` | `INTEGER` |
| DATE | `DATE` | `DATE` | `DATE` | `TEXT` |
| TIMESTAMP | `TIMESTAMP` | `TIMESTAMP` | `TIMESTAMP` | `TEXT` |
| JSON | `JSON` | `JSONB` | `JSON` | `TEXT` |
| BLOB | `BLOB` | `BYTEA` | `BLOB` | `BLOB` |

自增列：

| Dialect | 形式 |
|---|---|
| MySQL | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| PostgreSQL | `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`（PG 10+ IDENTITY，不用 SERIAL）|
| H2 | `BIGINT AUTO_INCREMENT PRIMARY KEY` |
| SQLite | `INTEGER PRIMARY KEY AUTOINCREMENT` |

标识符引号：

| Dialect | 引号 |
|---|---|
| MySQL | backtick `` ` `` |
| PostgreSQL / H2 / SQLite | 双引号 `"` |

### 8.5 内部 REST 端点

| 端点 | 用途 |
|---|---|
| `POST /api/er/seed-inspector` | Inspector 初次打开 + refresh |
| `POST /api/er/generate-ddl` | Designer generate_ddl |
| `POST /api/er/diff` | Designer diff_against_db |
| `POST /api/er/sync-from-db` | Designer sync_from_db |

所有端点共享 `ApiAuth` filter（与 `/api/sql/execute` 一致），不对 OpenCode 暴露；AGENTS.md 中不出现这些 URL。AI 仅通过 `ui_exec` 触发，前端 Adapter 转发。

### 8.6 dialect 兼容矩阵

**Inspector**

| Dialect | 现状 | ER Inspector | 备注 |
|---|---|---|---|
| MySQL | first-class | ✅ 完整 | `getImportedKeys` 直读 InnoDB FK；MyISAM 表 0 边但能渲染节点 |
| PostgreSQL | first-class | ✅ 完整 | `getImportedKeys` + 跨 schema |
| H2 | dev/demo | ✅ 完整 | 同上 |
| SQLite | 后端 partial / 前端连接表单未暴露 | ⚠️ 跟随现状 | 不单独打通 SQLite ER 路径 |
| Oracle | stub only | ❌ unsupported | 与 diagnostics 一致；空态显式渲染 "ER unsupported for Oracle" |

**Designer**

| Dialect | CREATE TABLE | ALTER ADD COLUMN | ALTER ADD FK | CREATE INDEX |
|---|---|---|---|---|
| MySQL | ✅ | ✅ | ✅ | ✅ |
| PostgreSQL | ✅ | ✅ | ✅ | ✅ |
| H2 | ✅ | ✅ | ✅ | ✅ |
| SQLite | ✅ CREATE only | ❌（SQLite ALTER 限制重）| ❌（不支持 ADD CONSTRAINT）| ✅ |
| Oracle | ❌ unsupported | ❌ | ❌ | ❌ |

day-1 Designer 创建强制选 dialect（默认跟 targetConnection 推断；不绑库时手选 mysql/postgresql/h2 之一；oracle/sqlserver 不入枚，列出即拒）。

---

## 9. AI Integration

### 9.1 AGENTS.md 改动

`server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

**删除**

- L94 `- datatalk_layout_erd` 整段（5 行）

**修改**

- L241 段落：`Workbench tabs (query_editor, artifact_preview, future er_designer / report_designer)` → `Workbench tabs (query_editor, artifact_preview, er_inspector, er_designer, future report_designer)`

**新增段落（全英文 / P12）**

放在 §"Concurrency Contract" 之前，新加 §"ER Tabs (Inspector & Designer)"：

```md
## ER Tabs (Inspector & Designer)

DataTalk has two ER tab types — **er_inspector** (read-only view of a real
schema with annotation overlay) and **er_designer** (independent schema draft
that can generate DDL for a target connection).

### When to open which

| User says | Open | Notes |
|---|---|---|
| "show how X relates to other tables" | er_inspector | tables=[X], neighborDepth=1 |
| "show me the ER for db Y" | er_inspector | tables = read_schema(db=Y, limit=100) |
| "annotate an implicit link between A and B" | (existing er_inspector) | ui_patch /virtualRelations |
| "design a schema for ..." | er_designer | dialect required (mysql/postgresql/h2) |
| "fork prod into a draft to edit" | er_inspector → fork_to_designer | preserves table & column shapes |
| "apply this draft to the test DB" | er_designer + bind_target + generate_ddl | DDL lands in a new query_editor tab; user must confirm via L2 |
| "find the ER tab containing X" | datatalk_ui_find | filter.type=er_inspector or er_designer + query.mode=fts pattern=X |

### Hard rules

- Do not patch an inspector to "change a real column type". Inspectors are
  views; structural changes belong in a designer or query_editor.
- Designer never executes DDL on its own. generate_ddl produces a query_editor
  tab; the user runs it under the existing L2/L3 confirmation flow.
- Oracle and SQL Server are not supported by ER. Use query_editor + read_schema
  instead.
- Do not pass coordinates. Layout is computed client-side; auto_layout is one
  ui_exec call away if a relayout is wanted.

### Recipe shortcuts

#### Open an inspector for a table and its neighbors
ui_exec(workspace, open_er_inspector, { connectionId, tables: ["orders"], neighborDepth: 1 })

#### Add a virtual (non-FK) relation
ui_patch(inspector_tab, [{
  op: "add", path: "/virtualRelations/-",
  value: { from: {table:"orders",column:"user_email"},
           to:   {table:"users", column:"email"},
           type: "many_to_one", note: "implicit link in app code" }
}])

#### Create a new designer with a seed table
ui_exec(workspace, open_er_designer, {
  dialect: "postgresql", title: "Order System Draft",
  seedTables: [{ name:"users", columns:[{name:"id",type:"BIGINT",isPrimaryKey:true,isAutoIncrement:true}] }]
})

#### Apply a designer to a target DB
ui_exec(designer_tab, bind_target,    { connectionId, database, schema })
ui_exec(designer_tab, diff_against_db)
ui_exec(designer_tab, generate_ddl)
// → returns { queryEditorTabId, ddl, skippedOps }
// Hand the queryEditorTabId to the user; they review + Run + confirm.

#### Search for an ER tab by content
ui_find({
  filter: { type: "er_inspector" },
  query:  { mode: "fts", pattern: "user_email" },
  output: { mode: "metadata", headLimit: 10 }
})
```

### 9.2 `STAGE_TAB_DIGEST` ER 行（英文）

`AgentPromptBuilder` 渲染 `er_inspector` / `er_designer` Tab 行多带统计：

```
2. er_inspector_a1b2  Order System ER             (orders + 6 neighbors · 7 tables · 9 relations · conn=prod-mysql)
3. er_designer_c3d4   Order System Draft (mysql)   (3 tables · 2 relations · target=test-mysql/test_db)
```

`AgentPromptContractTest` 增 ER 行渲染断言 + 中文标题 escape 测试（防 prompt injection）。

### 9.3 新增 `docs/references/er-tab-protocol.md`

完整 schema 文档（payload schema / patch 路径白名单 / exec 动词集 / 错误码全表 / examples），与 `docs/references/ui-objects-reference.md` 风格一致。AGENTS.md 在 ER 段落引用此文档作为 deep-link。

---

## 10. Apply 端到端流程

### 10.1 用户视角

```
1. Designer 加表 / 改列 / 加 FK
       ↓
2. 工具栏 [Bind target] → 选 connection + database + schema
       ↓
3. 工具栏 [Diff vs DB] → 弹小 popover：左侧"草稿独有"、右侧"DB 独有"，标 day-1 不支持的 DROP/ALTER COLUMN
       ↓
4. 工具栏 [Generate DDL] → 自动跳到新建 query_editor Tab，DDL 已预填，connection 已绑
       ↓
5. query_editor 里 review SQL（可改、可加 DROP 自己手写）
       ↓
6. 点 Run → Task 5 已 ship 的 L2/L3 confirm AlertDialog 拦截 → 用户确认 → 执行
       ↓
7. 执行成功后 toast；可选回 Designer 点 [Sync from DB] 重新对齐
```

### 10.2 AI 视角

```
User: "把这个 schema 草稿落到测试库 test-mysql"
AI:
  1. ui_exec(designer_tab, bind_target, {connectionId: "conn-test-mysql"})
       → {payloadVersion: 18}
  2. ui_exec(designer_tab, diff_against_db)
       → {diff: [...]}   // 3 个 CREATE TABLE + 2 个 ADD COLUMN + 1 个 DROP COLUMN(skipped)
  3. ui_exec(designer_tab, generate_ddl)
       → {queryEditorTabId: "query_editor_xy", ddl: "CREATE TABLE...",
          skippedOps: [{op:'drop_column', reason:'day1_unsupported'}], ...}
  4. AI 回复 user:
     "DDL has been written to a query editor. CREATE TABLE × 3 and ALTER ADD COLUMN × 2
      are ready to apply. The DROP COLUMN on `orders.deprecated` was skipped — please
      add it manually if needed. Click Run when you're ready; the L2 confirmation will
      ask before touching the database."
```

### 10.3 关键不变量

DDL 一律落到 query_editor，**不另开 Apply Dialog**。L2/L3 confirm 是唯一的 mutation 出口。即便 AI 在 chat 主动 apply，也必须最终经 query_editor + 用户确认 —— 没有 AI 直接写库的路径。

---

## 11. Testing Strategy

### 11.1 后端（JUnit 5 + AssertJ + WireMock + MockMvc）

| 测试类 | 覆盖 |
|---|---|
| `ErRelationDiscoveryServiceTest` | mysql/postgresql/h2 三种 in-memory 实例的 getImportedKeys；neighborDepth 0/1/2；> 100 表抛 oversized；oracle 抛 dialect_unsupported |
| `JdbcErRelationReaderIT` | 真 H2 实例端到端（含跨 schema FK） |
| `DialectTypeRegistryTest` | 4 dialect × 11 abstract types = 44 个 render assertion + 4 个 quote + 4 个 autoIncrementPk |
| `MySqlDdlGeneratorTest` / `PostgresDdlGeneratorTest` / `H2DdlGeneratorTest` / `SqliteDdlGeneratorTest` | 每生成器 ≥ 6 个 case：CREATE TABLE w/ PK、CREATE TABLE w/ FK、ADD COLUMN、ADD FK、CREATE INDEX、DROP / ALTER COLUMN → SkippedOp |
| `ErDdlGeneratorServiceTest` | diff 计算正确性、DROP/ALTER 跳过 + skipped reason 正确、SQL 拼装 + dialect 分隔符、targetConnectionId 缺失抛 target_required_for_apply |
| `ErSchemaDiffServiceTest` | TableAdded / ColumnAdded / ColumnTypeChanged / ColumnDropped / ConstraintAdded / ConstraintDropped 各 1 个 |
| `ErTabControllerIT` | 4 个 REST 端点正常 / 404 / 413 / dialect_unsupported |
| `WorkspaceAdapterErExecTest` | open_er_inspector / open_er_designer schema + summary 文本（英文 P12）+ 错误码携带 aiHint |
| `AgentPromptContractTest` | AGENTS.md 含新 §ER Tabs；不含 datatalk_layout_erd；STAGE_TAB_DIGEST ER 行渲染 + escaping |

### 11.2 前端（vitest + Testing Library）

| 测试 | 覆盖 |
|---|---|
| `er-tabs-store.test.ts` | hydrateInspector / hydrateDesigner / applyInspectorPatch / applyDesignerPatch 全套；JSON Patch ops 数组；assignedIds 返回；strict baseVersion 冲突；invalid_path |
| `tab-type-registry.test.ts` | er_inspector / er_designer 注册；extractContent 输出可索引文本（含中文 notes） |
| `ErCanvas.test.tsx` | 渲染节点 + 边；mode='inspector' 禁用拖拽建关系；mode='designer' onConnect 触发；prefers-reduced-motion 跳过 pulse |
| `ErTableNode.test.tsx` | inspector 显 lock 图标 + 列只读；designer 显 pencil + 双击编辑列；折叠 / 展开；> 12 列时显「⋯ N more」 |
| `ErEdge.test.tsx` | smoothstep 路径；line-jump 桥接（手工构造交叉场景）；label anti-overlap；self-ref loopback；virtual 关系 dashed |
| `dagre-layout.worker.test.ts` | 100 表 < 50ms；1000 表 < 300ms；空图返回空 |
| `ErInspectorAdapter.test.ts` | ui_read 返回 payload；ui_patch 全路径；ui_exec refresh / auto_layout / fit_view / add_neighbors / fork_to_designer |
| `ErDesignerAdapter.test.ts` | 同上 + bind_target / diff / generate_ddl 调内部 REST + 写 query_editor Tab |
| `WorkspaceAdapter.test.ts` | open_er_inspector / open_er_designer 端到端（mock fetch + spy stage store）；errors with aiHint |
| `er-empty-state.test.tsx` | Oracle 显 unsupported 兜底文案 |
| `forbidden-direct-mutation.test.ts` | 既有 ts-morph 静态扫描，确保 useErTabsStore.setState 仅在 store 内部 |
| `ai-highlight.test.ts` | useErHighlight 收到 highlightScopeId 切换 → 节点 / 边进入 pulse → residual → idle |

### 11.3 AI 行为回归（FakeOpenCodeServer + WireMock）

| 测试 | 覆盖 |
|---|---|
| `ErInspectorOpenScenarioIT` | "show ER of orders" → 单一 ui_exec 完成 + summary 正确 |
| `ErDesignerApplyScenarioIT` | "apply this draft to test DB" → bind_target + generate_ddl + 创建 query_editor Tab + DDL 内容正确 |
| `ErForkScenarioIT` | inspector → fork_to_designer → 全表 schema 携带 |
| `ErFindScenarioIT` | "找含 user_email 的 ER" → AI 调 ui_find 命中 |

---

## 12. Risks & Mitigations

| 风险 | 影响 | 对策 |
|---|---|---|
| `@xyflow/react` v12 与 React 19 兼容性问题 | Plan A 阻塞 | Plan A 第一周 spike 验证（5 节点 + 5 边 + dagre + drag）；不通则评估 v11 fallback 或自实现轻量 SVG 渲染 |
| dagre worker 在极大库（1000+ 表）jank | 用户体验差 | 性能预算硬指标 100/300ms；超出抛 `er_payload_oversized`；用户引导分多个 ER Tab |
| line-jump 桥接算法在数百边场景 O(n²) | 大图卡顿 | 边数 > 200 时禁用 line-jump（fallback 直接画）；toolbar 显式 toggle |
| AI 滥用 ui_patch 频繁触发 high-light pulse | 用户视觉疲劳 | pulse 时长 2.4s；同 highlightScopeId 1s 内复合 patch 合并为一次 pulse；prefers-reduced-motion 绕过 |
| Designer DDL 生成在边缘 dialect 语法错误 | 用户落库失败 | 每 DdlGenerator 至少 6 个单测；CI 必须通过；DDL 灌 query_editor 后由用户 review + L2 confirm 兜底 |
| AI 通过 ui_patch 删表后无法撤销 | 用户失误 | Designer 设 strict baseVersion；undo 通过 `ui_read` 历史 + 重新 patch 实现（不在 day-1 做 undo stack） |
| Inspector "fork_to_designer" 复制大库 (100 表) → Designer payload 接近 1MB 上限 | 创建失败 | fork 前 dry-run 估算 size；超阈值返回 `er_payload_oversized` + aiHint 引导分批 fork |
| sqlite ER inspector 用户期望 vs 现状落差 | 用户困惑 | 空态显式提示 "SQLite ER 需要后端连接配置"，引导改用 query_editor |
| 节点拖动频繁触发 ui_patch /positions 写盘 | 性能 / 噪声 | debounce 300ms 后批量 replace；force-flush 仅在 Tab close / app quit |
| AGENTS.md 新增段落让 prompt 长度逼近 token 上限 | 影响其他 capability | recipe 段紧凑（< 400 tokens）；deep-link er-tab-protocol.md，不展开完整 schema |
| ESLint custom rule + vitest 静态扫描遗漏新 store mutation 路径 | 数据一致性破坏 | rule 配置覆盖 `useErTabsStore.setState`；新 store 加进 forbidden-direct-mutation 白名单（仅 store 内部） |
| Oracle / SQLServer 用户尝试 Inspector 抛错而非空态 | 不友好 | 错误必经 dialect_unsupported + 前端 ErEmptyState 优先级渲染；不抛 stack trace |

---

## 13. Phasing

### Plan A — `er_inspector` 先

文件：`docs/exec-plans/2026-04-29-er-inspector-plan.md`

**估算**：2-3 周

**范围**：
- §1, §2, §3, §4 全部
- §5 (er_inspector 部分): tab-type-registry 注册 er_inspector + extractContent + rehydrate + Inspector payload schema + useErTabsStore
- §6 (Inspector ui_patch / ui_exec 全部 + WorkspaceAdapter.open_er_inspector + 错误码相关)
- §7 (shared canvas + ErTableNode mode='inspector' + ErEdge + ErToolbar Inspector 按钮 + dagre worker + AI 高亮 + 键盘 + ErdArtifact 退场)
- §8 (ErRelationDiscoveryService + DialectTypeRegistry 占位 + ErTabController 仅 seed-inspector 端点)
- §9 (AGENTS.md inspector 段 + STAGE_TAB_DIGEST inspector 行 + er-tab-protocol.md inspector 部分)
- 装包：`@xyflow/react` + `dagre`
- 删 `LayoutErdAction` + 旧 `ErdArtifact` + `erd` artifact kind 全 codebase 退场

**验收**：
- AI 能 `open_er_inspector` 看到真库 ER
- 用户能拖动节点、保存布局、跨重启恢复
- AI 能 `ui_patch /virtualRelations` 加虚拟关系
- AI 高亮可见
- mysql/postgresql/h2 三 dialect 通过 IT；oracle 显式 unsupported
- `cd server && mvn clean verify` + `cd client && npx tsc --noEmit && npm test` 通过

### Plan B — `er_designer` 接着

文件：`docs/exec-plans/2026-04-29-er-designer-plan.md`

**估算**：3-4 周

**依赖**：Plan A 完成（共享 canvas / store / Tab type 注册）

**范围**：
- §5 (er_designer 部分): tab-type-registry 注册 er_designer + Designer payload schema
- §6 (Designer ui_patch / ui_exec 全部 + WorkspaceAdapter.open_er_designer + bind/diff/generate_ddl 错误码)
- §7 (ErTableNode mode='designer' + ErToolbar designer 按钮 + 右键菜单 + 拖拽建关系)
- §8 (ErDdlGeneratorService + 4 个 DdlGenerator + ErSchemaDiffService + 3 个 REST 端点)
- §9 (AGENTS.md designer 段 + recipe 表 designer 部分 + apply flow + er-tab-protocol.md designer 部分)
- §10 (Apply 端到端流程联调)
- DialectTypeRegistry 完整实现（Plan A 占位填实）
- query_editor Tab 集成（generate_ddl 灌入流程）

**验收**：
- AI 能 `open_er_designer` 创建空 / 含 seed 草稿
- AI 能 `ui_patch` 加表 / 列 / 关系，含 strict baseVersion 冲突测试
- AI 能 `bind_target` + `diff_against_db` + `generate_ddl`，DDL 进 query_editor
- 经 Task 5 L2/L3 confirm 落库（手工验证）
- DROP / ALTER COLUMN 显式 skipped 并附 aiHint
- `cd server && mvn clean verify` + `cd client && npx tsc --noEmit && npm test` 通过

---

## 14. Definition of Done

- 两份 child plan 在 `docs/exec-plans/index.md` 登记
- 本 spec 在 `docs/product-specs/index.md` §8 登记
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 「Adapter Actions」段更新（删 LayoutErdAction，加 ER 矩阵）
- `docs/references/er-tab-protocol.md` 写完（schema + 错误码全表）
- `docs/exec-plans/tech-debt-tracker.md` 把 ER placeholder 从 backlog 移除
- 路线图 `2026-04-25-next-implementation-roadmap-plan.md` Task 8.2 / 8.1 勾选完成（Plan A 和 Plan B 都 ship 后）
- `cd server && mvn clean verify` 通过
- `cd client && npx tsc --noEmit` + `npm test` 通过

---

## 15. 引用

- 路线图 Task 8.2：[docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md](../exec-plans/2026-04-25-next-implementation-roadmap-plan.md)
- Cross-Session Workbench Tabs spec（持久化基础）：[2026-04-27-cross-session-workbench-tabs-design.md](./2026-04-27-cross-session-workbench-tabs-design.md)
- Stage UI Object Protocol：[2026-04-20-stage-ui-object-protocol-design.md](./2026-04-20-stage-ui-object-protocol-design.md)
- Guarded DDL/DML Execution（Apply 流程依赖 Task 5）：[2026-04-25-guarded-ddl-dml-execution-design.md](./2026-04-25-guarded-ddl-dml-execution-design.md)
- DataTalk Client Design System：[2026-04-23-datatalk-client-design-system-design.md](./2026-04-23-datatalk-client-design-system-design.md)
- Client Design Contract：[client/DESIGN.md](../../client/DESIGN.md)
- Data Source Type Compatibility Gate：[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
- 当前 LayoutErdAction：`server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java`
- 当前 ErdArtifact placeholder：`client/src/features/ontology/components/erd-artifact.tsx`
- 工程参考（借形不借色）：`/home/wushengzhou/workspace/github/open-db-studio/src/components/ERDesigner/`

---

## 16. 实施入口

本 spec 批准后产出两份执行计划：

1. `docs/exec-plans/2026-04-29-er-inspector-plan.md`（Plan A，先）
2. `docs/exec-plans/2026-04-29-er-designer-plan.md`（Plan B，后）

由 `superpowers:writing-plans` skill 生成。计划结构按 §13 Phasing 分批，含每文件 / 每测试粒度。

---

## 17. Existing Code Surfaces That Must Change

Review 后补章。这一节是给执行 Agent 的"现状对接清单"——每条都已在前面相关章节展开，但在这里集中列出，便于 plan 拆 task。

| # | 文件 | 现状 | 必须改动 | 章节 |
|---|---|---|---|---|
| 1 | `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java` | `inputSchema().oneOf` 仅 `workspaceExecSchema()` + `queryEditorExecSchema()`；`object` 隐式 enum 仅 `workspace / query_editor`；`workspace.action` enum 缺 ER；`workspace.params.type` enum 仅 `["query_editor"]` | 追加 `erInspectorExecSchema()` + `erDesignerExecSchema()`；`workspace.action` enum 加 `open_er_inspector` / `open_er_designer`；`workspace.params` 增 `tables / neighborDepth / dialect / targetConnectionId / targetDatabase / targetSchema / seedTables / seedRelations` | §5.5.4 |
| 2 | `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java` | `object` enum `["query_editor"]`；`replaceOp` 列表硬编码 query_editor 4 个 path | `object` enum 加 `er_inspector` / `er_designer`；ER ops 用宽松 path schema（client adapter 守门）；ER 允许 `add` / `remove` / `replace`（query_editor 仅 replace） | §5.5.4 |
| 3 | `server/data-talk-adapter/src/main/resources/messages.properties` + `messages_zh_CN.properties` | 仅有 query_editor / workspace 的 i18n keys | 新增 ER verb 对应的 description i18n keys（默认英文，P12） | §5.5.4 |
| 4 | `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java` | 不覆盖 ER | 增 ER schema 一致性断言：AGENTS.md 引用 verb 必须能在 inputSchema enum 中找到；不再引用 `datatalk_layout_erd` | §5.5.4, §11.1 |
| 5 | `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java` | placeholder 4 列网格 + erd artifact | **删除** | §1.1, §7.10 |
| 6 | `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/LayoutErdActionIT.java` | LayoutErdAction 的 IT | **删除** | §7.10 |
| 7 | `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` | L94 列出 `datatalk_layout_erd`；L241 把 `er_designer` 标 future | 删 datatalk_layout_erd 段；L241 改为 `er_inspector / er_designer` 已上；新增 §"ER Tabs (Inspector & Designer)" + recipe 表（全英文 P12） | §9.1 |
| 8 | `client/src/features/stage/persistence/stage-persistence-bootstrap.ts` | 仅订阅 `useStageStore` 元数据 + `useSqlWorkbenchStore` 文本；`diffContentAndSchedule` 硬编码读 `nextTab.sqlText` | 增 `useErTabsStore` 订阅块 + `diffErContentAndSchedule`；写入 `payload + contentText`（contentText 走 `extractContent`）+ `expectedVersion` | §5.5.1 |
| 9 | `client/src/features/actions/ui-handlers.ts` | `MUTATING_EXEC` 不含 ER verbs；force-flush 仅 flush input target | `MUTATING_EXEC` 增 11 个 ER verbs；ui_exec handler 拿到 result 后额外 flush `result.tabId / newTabId / queryEditorTabId` | §5.5.2 |
| 10 | `client/src/features/stage/components/stage-ui-object-registry.tsx` | filter 仅 `query_editor` 全局注册 | 增 `RegisteredErInspector` / `RegisteredErDesigner` 全局注册（按 tab.type filter） | §5.5.3 |
| 11 | `client/src/features/stage/registry/tab-type-registry.ts` | 已注册 query_editor / artifact_preview / file_preview / workspace / diagnostic | 增 `er_inspector` / `er_designer` 两条（含 `extractContent` + `rehydrate`） | §5.1 |
| 12 | `client/src/services/ui-router/types.ts` + `jsonPatch.ts` + `pathResolver.ts` | 已支持 add/remove/replace + `[name=X]` 寻址 + `/-` tail | **不需要改**；ER 直接复用既有 grammar | §6.2 |
| 13 | `client/src/features/stage/adapters/WorkspaceAdapter.ts` | `exec(open / focus / detach / archive / trash / choose_connection)` | 增 `case 'open_er_inspector'` / `'open_er_designer'`：fetch `/api/er/seed-inspector`（仅 inspector）→ 算 dagre → 创建 stage tab → focus → 返回 summary（英文 P12） | §6.1, §8.5 |
| 14 | `client/src/features/ontology/components/erd-artifact.tsx` | placeholder，字段对不上 | **删除** | §7.10 |
| 15 | `client/src/features/ontology/components/artifact-dispatcher.tsx` | `case 'erd'` 分支 | **删除分支** | §7.10 |
| 16 | `client/src/services/channel/event-reducer.ts` | `kind: 'table' \| 'chart' \| 'erd'` | 联合中删 `'erd'` | §7.10 |
| 17 | `client/src/features/chat/components/tools/renderers/artifact-created.tsx` | `if (part.tool === 'datatalk_layout_erd') return 'erd'` | **删除** | §7.10 |
| 18 | `client/src/features/session/hooks/use-session-history.ts` | `kind: 'table' \| 'chart' \| 'erd'` | 联合中删 `'erd'` | §7.10 |
| 19 | `client/src/i18n/messages.ts` | `'artifact.erdEmpty'` 两条 | **删除**；新增 §5.6 列出的 12 条 ER 相关 i18n keys（双语） | §5.6, §7.10 |
| 20 | `client/package.json` | 无 ER 渲染栈 | 增 `@xyflow/react@^12.10.1` / `dagre@^0.8.5` / `@types/dagre@^0.7.54` | §7.11 |
| 21 | `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 「Adapter Actions And Ontology」 | 当前列出 `LayoutErdAction.java` | 删 LayoutErdAction 引用；新增 ER Inspector / Designer 的 dialect 矩阵（mysql/postgresql/h2 完整、sqlite 仅 Designer CREATE、oracle unsupported） | §14 |
| 22 | `docs/exec-plans/tech-debt-tracker.md` | 隐含 ER placeholder backlog | 移除（路线图 8.1 已说"placeholder 是产品 backlog 不是 tech debt"，此次正式退场） | §14 |
| 23 | `client/src/features/chat/components/tools/__tests__/register-built-in-renderers.test.ts` | 含 erd / layout_erd 断言 | 删除相关断言 | §11.2 |

**全局影响评估**

- 无 schema migration 新增（Task 6 已 ship 的 `stage_tabs` 表泛型可复用）
- 无 OpenCode tool 数量变化（仅 ui_exec / ui_patch verbs 扩充，不改 MCP 工具数）
- AGENTS.md prompt 长度增 ~400 tokens（在预算内，详见 §9.1）
- `LayoutErdAction` + `ErdArtifact` 退场 = 已存在但未跑通的代码 -150 LoC

**Plan A / Plan B 任务对应**

| 行 # | Plan A（Inspector）| Plan B（Designer）|
|---|---|---|
| 1, 2 | 仅加 ER inspector 部分 | 加 ER designer 部分 |
| 3 | 加 inspector i18n | 加 designer i18n |
| 4 | 加 inspector schema 断言 | 加 designer 断言 |
| 5, 6, 14-19, 23 | 全部 | — |
| 7 | 删 layout_erd + AGENTS.md 加 inspector recipe | AGENTS.md 加 designer recipe |
| 8 | 增 inspector 订阅 | 增 designer 订阅 |
| 9 | 增 inspector verbs flush | 增 designer verbs flush |
| 10 | 加 RegisteredErInspector | 加 RegisteredErDesigner |
| 11 | 加 er_inspector | 加 er_designer |
| 12 | — | — |
| 13 | open_er_inspector | open_er_designer |
| 20 | 全部装包 | — |
| 21 | DATA_SOURCE_TYPE_COMPATIBILITY 加 inspector 矩阵 | 加 designer 矩阵 |
| 22 | 移除 backlog | — |
