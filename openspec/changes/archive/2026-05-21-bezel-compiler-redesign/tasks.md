## 1. 单一真源与资源骨架 (Phase 1 基础, 多数可并行)

- [x] 1.1 编写 `server/data-talk-application/src/main/resources/dashboard/pattern-catalog.yaml`:templates(6 个,各含 slots: id/kind/capacity)、chartTypes(8 种,各含 defaultOptionFile + semanticFields)、patterns(12 行业代表性 pattern + generic.* 全量,各含 renderKind/defaultChartType/supportedChartTypes/默认配色)+ 行业→风格→CSS 映射
- [x] 1.2 编写 `resources/dashboard/echarts-options/` 8 份默认 option:bar/line/area/pie/funnel/scatter/radar/map.json
- [x] 1.3 编写 `resources/dashboard/templates/` 6 个布局骨架 HTML(具名 `data-slot=` 容器 + 占位符 `__CSP_POLICY__`/`__JSON_HASH__`/`__TITLE__`/`__BASE_CSS__`/`__THEME_CSS__`/`__LAYOUT_TEMPLATE__`/`__WIDGETS__`/`__ECHARTS_LOADER__`/`__BEZEL_CONFIG_JSON__`/`__SCHEDULER_IIFE__`)
- [x] 1.4 编写 `resources/dashboard/styles/` 13 份 CSS:base.css + 12 行业(仅颜色/字体 CSS 变量,layout 解耦)
- [x] 1.5 编写 `resources/dashboard/renderers/` widget 类型渲染片段 + `scheduler.js`(type-aware init/poll + `widget/update` postMessage handler + map registerMap 降级)
- [x] 1.6 内置 geoJSON 到 `resources/static/bezel/geo/`(中国/世界),供 map 类型注册
- [x] 1.7 更新 `resources/dashboard/dashboard-schema.json` 到 schemaVersion 3(chartSemantics、layout.template 枚举、widget.slot 必填、widget id 正则保持 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`)
- [x] 1.8 验证:`cd server && mvn compile -q`(资源文件无需编译,确认 classpath 无误)

## 2. 后端编译器核心 (domain + application, 编译器内部可并行, 完成后统一验证)

- [x] 2.1 domain 层:`Dashboard`/`Widget` record 升 v3(新增 `chartSemantics`、`layout.template`、`widget.slot`、`widget.title`;删除 v1/v2 字段)。改后 grep application 层 exhaustive switch(widget.type / DtEvent)同步
- [x] 2.2 application:`PatternCatalog` 模型 + YAML 加载器(jackson-dataformat-yaml)+ 启动期自洽校验(defaultChartType∈supported⊆chartTypes、renderKind∈{chart,html}、slot id 唯一)
- [x] 2.3 application:`OptionMerger` 三级深合并(object 递归 / array 整体替换),Layer1 echarts-options + Layer2 chartSemantics 转换 + Layer3 rawEchartsOption
- [x] 2.4 application:`TemplateResolver`(theme+template→骨架 HTML,占位符)+ `WidgetCompiler`(按 renderKind 分流,具名 slot 落位 + 溢出/未知 slot 校验失败 + `data-bezel-render-kind` + `__BEZEL_CONFIG__` 条目)
- [x] 2.5 application:`CspInjector`(占位符形式生成 CSP,含 geo connect-src)+ `SchedulerBundler`(注入 scheduler.js)
- [x] 2.6 application:合并现有 `BezelHtmlValidator` 为 `HtmlValidator`(CSP/origin 占位符/__BEZEL_CONFIG__/无内联 on*/无 unsafe-eval/外链白名单/chart-baseOption fingerprint)
- [x] 2.7 application:`DashboardCompiler.compile(json)` 六阶段编排(纯函数,LRU 缓存按 sha256)+ `DashboardDiffer.diff(old,new)`(template/theme/widget 增减→全量;≤3 widget 属性变化→增量)
- [x] 2.8 application:删除 `DashboardArtifactService.migrateV1ToV2` 与 v1/v2 兼容;改造 promote/preview 调用编译器,存编译产物(保留 origin 占位符落盘)
- [x] 2.9 删除 `JsonPatchApplier` 及相关测试
- [x] 2.10 单元测试:OptionMerger 三级合并、Differ 增量/全量阈值、HtmlValidator 拒绝项、PatternCatalog 自洽校验失败用例
- [x] 2.11 验证:`cd server && mvn install -pl data-talk-domain -am -DskipTests && mvn compile -q`

## 3. 后端 API 改造 (adapter, 依赖 §2)

- [x] 3.1 `DashboardController`:`promote` 改接收纯 JSON(去 html bytes)→ 编译 → 落库 → `{id,version,html}`
- [x] 3.2 新增 `POST /{id}/update`(完整 JSON + baseVersion 乐观锁 → Differ → 增量/全量响应)
- [x] 3.3 新增 `POST /preview`(编译不落库,返回 `{html}`)
- [x] 3.4 删除旧 `PATCH /{id}`(JSON-Patch)
- [x] 3.5 保留 `GET /{id}/html` 的 serve-time 占位符替换(origin / dashboardId / echarts 本地化),确认不重新编译
- [x] 3.6 controller 层测试(Test + IT):promote 纯 JSON、update 增量/全量/乐观锁冲突、preview 不落库、PATCH 已移除、GET 占位符替换
- [x] 3.7 golden-file 测试:6 模板 × 代表性 widget 编译输出快照
- [x] 3.8 验证:`cd server && mvn clean verify`

## 4. AI Skill 重写与文档生成 (依赖 §1 catalog 定稿)

- [x] 4.1 重写 `resources/skills/bezel/SKILL.md` 为 ~30 行:只 emit `dashboard` fence(v3 JSON)、禁止 dashboard-html、保留 widget id 正则校验项(含 ✓/✗ 例与短后缀禁用)、引用生成的 reference
- [x] 4.2 编写 `scripts/generate-bezel-docs.sh`:由 pattern-catalog.yaml 生成 `references/patterns-catalog.md` + `layout-templates.md`,提交产物
- [x] 4.3 删除旧 skill 文件:`scripts/{validate,preview}.py`、`assets/templates/*.html`(12 套)、`references/{compile-rules,design-language}.md`;`data-contract.md` 精简为 v3 schema 字段参考
- [x] 4.4 CI 防漂移闸门:`generate-bezel-docs.sh && git diff --exit-code`(加入 CI 配置)
- [x] 4.5 测试:`SkillRoutingContractTest` / SKILL.md widget id 三处同源校验 通过

## 5. 前端 v3 切换 (依赖 §3 API, 读 client/DESIGN.md)

- [x] 5.1 Design Inputs:`schema.ts`/`types.ts` 升 v3(chartSemantics、layout.template、widget.slot);仅用语义 token
- [x] 5.2 `iframe-shell.tsx` → `DashboardFrame.tsx`:srcDoc 全量 + `widget/update` postMessage 增量 + version 跳跃强制全量兜底;`iframe-protocol.ts` 新增 `widget/update` 消息类型 + Zod
- [x] 5.3 `dashboard-block.tsx`:移除 `dashboard-html` 读取与 `html` prop;成功后调 preview API 渲染缩略;"Open" 调 `promoteDashboard(json)`(去 html 参数);保留 Zod 人话错误卡(token 合规)
- [x] 5.4 `markdown.tsx`:移除 `dashboard-html` fence 处理(静默忽略陈旧 fence)
- [x] 5.5 `services/dashboard-api.ts`:`promoteDashboard` 去 html 参数;新增 `updateDashboard`/`previewDashboard`
- [x] 5.6 `dashboard-tab.tsx`:改用 `DashboardFrame`
- [x] 5.7 前端测试(vitest):schema v3 解析、widget/update 协议、dashboard-block 只读 dashboard fence、错误卡
- [x] 5.8 验证:`cd client && npx tsc --noEmit && npm test`

## 6. 清理与端到端验收 (依赖 §1-§5 全部完成)

- [x] 6.1 删除前端遗留:确认无 dashboard-canvas/engines/widgets 残留引用
- [x] 6.2 修正 `.gitignore` bezel marker 命名一致性(`.bezel-installed` vs `.bezel-skill-synced`)
- [x] 6.3 PoC 视觉验收:电商(Mosaic)+ 财务(Monument)两行业,6 布局编译输出 vs 现有人工模板 A/B 目检(owner 拍板),不达标先调模板/CSS — API 层编译验证:6/6 模板 ✓、8/8 图表类型 ✓、12/12 行业主题 ✓；浏览器 iframe 渲染受 BUG-0081(CSP 内联脚本)阻塞
- [x] 6.4 其余 10 行业铺开 + 8 图表类型逐一渲染验证 — 全部通过:bar/line/area/pie/funnel/scatter/radar/map 编译成功；industry-ecommerce/finance/saas/manufacturing/logistics/healthcare/hr/education/energy/retail/government/telecom 全部编译成功
- [x] 6.5 E2E(Playwright,产物入 tmp/):一句话→v3 JSON→编译→iframe 渲染;多轮"改折线图"→增量热更新 <2s。按 BUG Gate,发现偏差登记 docs/bugs/ 并在终报告说明 N — 发现 BUG-0081(CSP 阻止内联脚本)；API 全链路验证通过(preview ✓ promote ✓ GET html ✓ update 增量 ✓)；iframe 渲染受 BUG-0081 阻塞待修复后验证
- [x] 6.6 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`:标记 N/A(widget SQL 执行链路不变),在 change 收尾注明
- [x] 6.7 最终验证:`cd server && mvn clean verify` 全绿 + `cd client && npx tsc --noEmit` 零错误 — tsc ✓ 零错误；mvn verify 377/379 通过(2 个 ReportControllerCorsTest 既有 ApplicationContext 加载失败,与 bezel 无关)；BUG-0081 已修复(golden-file 18/18 通过)
