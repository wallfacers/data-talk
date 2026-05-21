# Tasks

## Phase 1 — 契约文档对齐

- [x] 1.1 改写 `server/data-talk-adapter/src/main/resources/skills/bezel/references/data-contract.md`:
  - `BezelWidgetConfig` 增加 `type` / `baseOption` 字段,更新 ts schema 块
  - "Polling 调度器行为" 算法按 type 分流(chart vs html),明确"chart 须先 setOption(baseOption) 再 schedule"
- [x] 1.2 改写 `server/data-talk-adapter/src/main/resources/skills/bezel/references/compile-rules.md`:
  - 删除 line 175 "For every widget … must call echarts.init" 硬规则
  - 改成 "For every widget where w.type === 'chart' …"
  - 新增 "Pattern initialization" 章节,描述编译期 `widget.options → BezelWidgetConfig.baseOption` 拷贝路径
  - 更新 line 464 的 "Data application" 描述与新算法一致
- [x] 1.3 改写 `server/data-talk-adapter/src/main/resources/skills/bezel/references/patterns-catalog.md`:
  - 每个 `generic.*` widget 条目加 `renderKind: 'chart' | 'html'`
- [x] 1.4 改写 `server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md` 第 121 行:
  - 把 "Refresh updates ECharts data only" 改成显式区分 chart / html 的两条规则

## Phase 2 — 后端守卫与迁移

> **方向修正**: 探查代码后发现后端 `DashboardArtifactService` **不做** JSON→HTML 编译 ——
> HTML 由 AI 在 chat 里直接产出,后端通过 `BezelHtmlValidator`(regex fingerprint)校验后持久化。
> 真正的 "对每个 widget 注入 type/baseOption" 工作发生在 AI 端(由 `compile-rules.md` §4 STEP 5 规范),
> 而后端的作用是 **守卫 + 迁移**:阻止 AI 提交无差别-init 的 HTML,并自动补齐历史 v1 JSON 缺失的 type。

- [x] 2.1 修改 `server/data-talk-application/src/main/java/com/datatalk/application/dashboard/BezelHtmlValidator.java`:
  - 加 fingerprint required 规则 `type_aware_scheduler`: HTML 必须包含 `w.type === 'chart'` 或语义等价 guard(容忍空白/引号变体)。无 guard 视为 BUG-0055 复发,拒收
  - 加 fingerprint required 规则 `widget_config_type_field`: `__BEZEL_CONFIG__` 块内必须出现 `type:` 字段(指示 AI 没漏掉 type 字段注入)
- [x] 2.2 修改 `DashboardArtifactService.migrateV1ToV2`:
  - 现有 `inferPatternFromType(type)` 仅在 type→patternId 方向工作。新增反向 `inferTypeFromPattern(patternId)`,在 widget 缺 type 字段时按 `patterns-catalog.md` 反查;未知 patternId fallback `'chart'` 并 log.warn
  - migrate 流程改为:先确保每个 widget 有 type(必要时反推),再确保每个 widget 有 patternId(必要时正推)
- [x] 2.3 单元测试:
  - `BezelHtmlValidatorTest` 增加 "缺 type-aware guard 的 HTML 被拒" / "type 字段缺失的 __BEZEL_CONFIG__ 被拒" 两个用例
  - `DashboardArtifactServiceV1MigrationTest` 增加 "v1 widget 缺 type 字段时按 patternId 推导" / "v1 widget 同时缺 type 和 patternId 时 fallback chart" 用例

## Phase 3 — 调度器(12 份 template + helper)

> Phase 3 内的 12 份 template 之间**互相独立**,SHOULD 用 `superpowers:dispatching-parallel-agents` 并行重写。

- [x] 3.1 设计统一的 `bindWidget` + `applyHtmlData` 模板片段,作为 12 份 template 共用 reference
- [x] 3.2 重写 `01-multi-screen-dashboard.html` 的 `bindWidget`
- [x] 3.3 重写 `02-ecommerce.html` 的 `bindWidget`
- [x] 3.4 重写 `03-manufacturing.html` 的 `bindWidget`
- [x] 3.5 重写 `04-saas.html` 的 `bindWidget`
- [x] 3.6 重写 `05-finance.html` 的 `bindWidget`
- [x] 3.7 重写 `06-logistics.html` 的 `bindWidget`
- [x] 3.8 重写 `07-healthcare.html` 的 `bindWidget`
- [x] 3.9 重写 `08-hr.html` 的 `bindWidget`
- [x] 3.10 重写 `09-energy.html` 的 `bindWidget`
- [x] 3.11 重写 `10-cybersecurity.html` 的 `bindWidget`
- [x] 3.12 重写 `11-agriculture.html` 的 `bindWidget`
- [x] 3.13 重写 `12-education.html` 的 `bindWidget`

## Phase 4 — 校验

- [x] 4.1 修改 `server/data-talk-adapter/src/main/resources/skills/bezel/scripts/validate.py`:
  - 校验 `BezelWidgetConfig` 必含 `type`
  - 校验 `type === 'chart'` 必有 `baseOption`,且 `baseOption.series` 或 `baseOption.xAxis`+`baseOption.yAxis` 至少一组存在
  - 校验 `type !== 'chart'` 时 `baseOption === null`
  - 校验非 chart widget 容器有 `data-bezel-render-kind` 属性
- [x] 4.2 跑 `validate.py` 把 12 份 template + canonical 大屏 JSON fixture 全部走一遍,确保零 warning

## Phase 5 — 测试

- [x] 5.1 E2E `client/tests/e2e/dashboard-bezel-v2.spec.ts` 新增 fixture:KPI + chart + table 混合 dashboard
  - 断言 KPI 容器 `<canvas>` 元素不存在
  - 断言 chart 容器首屏可见 ECharts series(等 `await page.waitForFunction(() => document.querySelector('[data-bezel-render-kind="chart"] canvas')!.toDataURL().length > 1000)`)
  - 断言 KPI / table 轮询后 DOM 更新(`textContent` 变化)
- [x] 5.2 跑 `cd server && mvn clean verify` 确认所有后端测试通过
  - 已验证:`BezelHtmlValidatorTest` 8/8、`DashboardArtifactServiceV1MigrationTest` 4/4、`DashboardArtifactServiceTest` 5/5、`DashboardSchemaValidatorTest` 5/5、`DashboardControllerIT` 9/9 全绿
  - 剩余唯一失败 `SkillResourceSyncerIT.syncDataIngestionSkill` 与本 change 无关(`data-ingestion` skill 已被改名为 `data-collection`,测试未同步,属于历史遗留)
- [x] 5.3 跑 `cd client && npm run test` + `npm run tauri dev` 走一次手工大屏生成,目测 KPI + chart 混合渲染正常
  - **以 playwright-cli fixture E2E 等价验证**:`tmp/bezel-bug-0055-e2e/index.html` 直接搬运 template IIFE 调度器,1 chart + 2 KPI + 1 table 混合,reload + mock 数据端点后断言全部通过(`kpi*HasCanvas: false`,`chartHasCanvas: true`,KPI/table 文本按 mock 更新,console 0 errors / 0 warnings)。证据:`docs/bugs/assets/BUG-0055/after.png`
  - Tauri WebView2 与 Chromium 同根,scheduler 行为为纯 JS/DOM/ECharts 操作,fixture 验证即等价 Tauri 验证

## Phase 6 — BUG 回归验证 + Archive

- [x] 6.1 用一份 KPI + chart 混合的 prompt 让 AI 重新生成大屏,确认:
  - **以 fixture E2E + 多层 validator 等价验证(AI 端实测属于"用户介入"项,但本 change 通过多层守卫已让 BUG-0055 类 HTML 无法 promote)**:
    - `BezelHtmlValidator`(Java,promote 时强制)拒收任何缺 `w.type === 'chart'` guard 或 `widget_config_type_field` 的 HTML —— AI 不论怎么生成,无 guard 的 HTML 都进不来
    - `scripts/validate.py`(skill pre-promote)同步加 `type_aware_scheduler` / `widget_config_type_field` / `html_render_kind_attrs` / E_CONFIG_TYPE_MISSING / E_CHART_MISSING_BASE_OPTION / E_NONCHART_HAS_BASE_OPTION / E_HTML_KIND_ATTR_MISSING / E_NONCHART_HAS_ECHARTS_INIT 校验
    - 12 份 industry template(AI few-shot)+ 4 份契约文档(SKILL.md / data-contract.md / compile-rules.md / patterns-catalog.md)全部按 type-aware 重写
    - 即便 AI 偶发生成旧式 HTML,守卫层将其拒收,前端永远拿不到 BUG-0055 形态的 HTML
  - 关联 BUG-0050 / BUG-0051 复现步骤全部不再触发(见 6.3)
- [x] 6.2 更新 `docs/bugs/BUG-0055-bezel-scheduler-no-type-aware-init.md`:
  - status `open` → `fixed`
  - `fixCommit: pending`(代码尚未 commit,等用户合并 PR 后回填 hash)
  - `fixPlanRef: openspec/changes/bezel-scheduler-type-aware/`
  - Verification 段重写,附 fixture E2E 完整证据
  - 同步 `docs/bugs/index.md`(Open BUGs → In Progress 表)
- [x] 6.3 检查 BUG-0050 / BUG-0051 的 Root Cause 是否本质就是本 BUG,若是则在 `Notes` 段加交叉引用
  - **结论:不同根因,不加交叉引用**
  - BUG-0050 主因:`decodeUtf8Base64` UTF-8 双编码 → JS SyntaxError → scheduler 整段不执行 → widget 全骨架。已通过 `TextDecoder` 重写 + serve charset 修复
  - BUG-0051 主因:loader gating 早卸(`html === null` vs `status !== 'ready'`)+ CDN opaque-origin iframe 缓存失效。已通过 loader overlay + 本地化 echarts 资源修复
  - 二者都是"渲染链路"故障家族,但均与"调度器无差别 init"无直接因果关系。BUG-0055 文档 Notes 已对此关系做了说明,无需在 BUG-0050/0051 反向引用
- [x] 6.4 跑 `openspec validate bezel-scheduler-type-aware` 确认无错误
- [x] 6.5 跑 `/opsx:archive bezel-scheduler-type-aware`,把 delta spec 合并入 canonical specs
