## Context

现状(实测核对):

- **后端**:`DashboardController`(adapter)暴露 `promote`(接收 JSON + html bytes)、`GET /{id}`、`PATCH /{id}`(JSON-Patch + 乐观锁)、`GET /{id}/html`(serve-time 替换 `__BEZEL_SERVER_ORIGIN__` 与 echarts CDN→`/bezel/echarts.min.js`)、`POST /{id}/widgets/{wid}/data`。`DashboardArtifactService`(application)用 `FileArtifactService` + 文件系统(`workdirRoot/dashboards/{id}.dashboard.json` + `.html`)存储,登记于 `file_artifact` 表(kind='dashboard', scope='workspace'),含 v1→v2 迁移。已有 `BezelHtmlValidator`(校验 CSP / `__BEZEL_SERVER_ORIGIN__` / `window.__BEZEL_CONFIG__` / type-aware scheduler)。`schemaVersion` 当前为 2,`renderer` const 'bezel'。
- **AI skill**:位于 `server/data-talk-adapter/src/main/resources/skills/bezel/`(**不是** `skills-src`),含 SKILL.md(134 行)、`references/{compile-rules(606),patterns-catalog(165),design-language,data-contract}.md` + 12 industries + 6 styles、`scripts/{validate,preview}.py`、`assets/templates/*.html`(12 套)。skill 经**通用** `SkillResourceSyncer.syncSkill(name, dir)` 同步到 `.opencode/skills/<name>`(marker `.opencode/.bezel-skill-synced`,注:`.gitignore` 写的是 `.bezel-installed`,现状本身不一致)。`resources/static/bezel/echarts.min.js` 提供本地化 echarts。
- **前端**:`client/src/features/dashboard/` 现有 `iframe-shell.tsx`(`srcDoc` + `sandbox="allow-scripts"`)、`schema.ts`(v2 Zod)、`dashboard-tab.tsx`、`iframe-protocol.ts`(`params/update` / `theme/preview` / `refresh/*` / `ready` / `error` / `metric`,**无单 widget 更新消息**)、`services/dashboard-api.ts`(`promoteDashboard(payload, html?, sessionId?)`)。`dashboard-canvas.tsx` / `engines/` / `widgets/` **已不存在**。`chat/.../markdown.tsx` 解析 `dashboard` 与 `dashboard-html` 两种 fence,后者 base64 附到前者的 mount。

约束:四层依赖 domain ← application ← infrastructure ← adapter;Stage 全局态;client/DESIGN.md token 契约。

## Goals / Non-Goals

**Goals:**

- AI 只输出 v3 JSON;HTML 由服务端 `compile(json)` 纯函数确定性产出(首次 ~50ms)。
- `pattern-catalog.yaml` 单一真源,运行时驱动编译器 + 构建期生成文档 + CI 防漂移。
- 多轮迭代总耗时 <2s(Differ 增量编译 + postMessage 单 widget 热更新)。
- 6 布局 + 13 行业 CSS + 8 图表类型全量重建。
- 旧实现彻底删除(v1/v2、dashboard-html fence、AI scheduler 契约、Python 脚本、12 定制模板),**不做存量迁移**。

**Non-Goals:**

- 不改 widget SQL 执行链路(`WidgetDataService` 的 SELECT-only guard、连接/database/schema 级联解析保持不变)。
- 不引入前端 widget 渲染器(HTML 仍由后端生成,前端只做 iframe host)。
- 不引入服务端模板引擎(用 `String.replace` 占位符)。
- 不为存量 v2 dashboard 提供迁移器(经确认)。

## Decisions

### D1. 编译器归属 application 层,纯函数 + 无副作用

`compile(json) → html` 不访问 DB、不读 session。落在 `data-talk-application/.../dashboard/`:`DashboardCompiler`(入口)、`TemplateResolver`、`WidgetCompiler`、`OptionMerger`、`CspInjector`、`SchedulerBundler`、`HtmlValidator`、`DashboardDiffer`。**复用并合并现有 `BezelHtmlValidator` 为 `HtmlValidator`**(不是凭空新增)。理由:纯函数利于 golden-file 测试与 LRU 缓存(key=JSON sha256)。

### D2. ⭐ Widget → 模板槽位映射(原设计缺失,本设计补全)

每个布局模板定义**具名 slot**,JSON 用 `widget.slot` 显式声明落位,而非靠数组顺序:

- 模板 HTML 含 `<div data-slot="kpi-1">…</div>` 等具名槽;`pattern-catalog.yaml` 的 `templates[].slots` 声明每个 slot 的 `id`、`kind`(`kpi`/`chart`/`table`/`free`)、`capacity`。
- `widgetV3` 新增 `slot: string` 字段(必填);schema 校验 `slot` ∈ 该 template 的 slots。
- **溢出策略**:同一 slot 的 widget 数 > `capacity` → schema 校验失败(明确报错,而非静默截断);未填的 slot 由 CSS 折叠(`:empty` 隐藏)。
- 替代方案(已否决):①数组顺序自动填位 — AI 难以预测落点,改一个 widget 顺序全乱;②保留 v2 的 `GridPosition(x,y,w,h)` 自由网格 — 与"6 个定制布局"目标冲突,且 AI 难产出像素级坐标。具名 slot 兼顾确定性与 AI 友好。

### D3. ⭐ HTML 存储与 serve-time 占位符替换(解决"GET 不重编译"矛盾)

`compile(json)` 产出的 HTML **保留** `__BEZEL_SERVER_ORIGIN__` 占位符与 `/bezel/echarts.min.js` 引用形式落盘。`GET /{id}/html` **不重新编译**,但**仍做 serve-time 字符串替换**(origin 注入、dashboardId 注入),沿用现有 `DashboardController` 逻辑。理由:sandbox iframe 是 opaque origin,echarts 必须用绝对 URL;origin 随部署/端口变化,不能在 promote 时烤死。`CspInjector` 生成的 `script-src` 同样以占位符形式存储,serve 时替换。

### D4. ⭐ 多轮迭代:AI 全量 JSON + 服务端 Differ + 双通道热更新

- AI 每轮输出**完整** v3 JSON(不做 patch);`POST /{id}/update` 带 `baseVersion` 乐观锁。
- `DashboardDiffer.diff(old, new)`:`layout.template` 或 `theme` 变化、或 widget 增减 → **全量**(返回 `{version, html}`);仅 ≤3 个 widget 的属性变化(chartSemantics/title/query)→ **增量**(返回 `{version, changes:[{widgetId, baseOption, html?}]}`)。
- **iframe 热更新协议(新增 postMessage 类型)**:
  - 全量 → host 用新 `srcDoc` 替换 iframe(~200ms)。
  - 增量 → host `postMessage({type:'widget/update', widgetId, baseOption, html?})`;`scheduler.js` IIFE 监听并对单 widget `setOption`/替换 innerHTML(~50ms,无闪烁)。
  - `iframe-protocol.ts` 新增 `widget/update` 消息类型与 Zod 校验;`scheduler.js` 内置 handler。
- 删除旧 `PATCH /{id}`(JSON-Patch)与 `JsonPatchApplier`,统一走 `update`。

### D5. 单一真源 YAML + 构建期文档 + CI 防漂移

- `pattern-catalog.yaml` 运行时由 `jackson-dataformat-yaml`(Spring Boot 已含)加载为 `PatternCatalog` 不可变模型;**启动期自洽校验**:`defaultChartType ∈ supportedChartTypes ⊆ chartTypes`、`defaultColorScheme ∈ supportedColorSchemes`、每 `patternId.renderKind ∈ {chart,html}`。
- `scripts/generate-bezel-docs.sh` 由 YAML 生成 `references/patterns-catalog.md` + `layout-templates.md`,产物提交进 git。
- **CI 防漂移闸门**:CI 跑 `generate-bezel-docs.sh && git diff --exit-code`;不一致即 fail。这是单一真源能否真正成立的关键(否则 BUG-0055 类漂移会复发)。
- `mvn package` 不重跑生成(仅 dev/CI),避免 build 复杂度。

### D6. ECharts Option 三级合并

`最终 option = deepMerge(Layer1, Layer2, Layer3)`:Layer1 = `echarts-options/{chartType}.json`(内置默认);Layer2 = `chartSemantics` 字段转换;Layer3 = `widget.options.rawEchartsOption`(用户透传,最高优先级)。深合并语义:object 递归合并,array 整体替换。

### D7. 8 种图表类型,map 特殊处理

`echarts-options/` 备齐 8 份:bar/line/area/pie/funnel/scatter/radar/map。**map** 需 geo JSON:随产物内置中国/世界 geoJSON 到 `resources/static/bezel/geo/`,`scheduler.js` 按需 `echarts.registerMap` 后再 init;CSP `connect-src` 白名单加该静态路径(同 origin,占位符替换)。若 geo 数据加载失败,降级为提示文案而非空白。

### D8. AI Skill 精简 + fence 单一化

SKILL.md ~30 行:只要求 emit 单个 `dashboard` fence(v3 JSON),禁止 `dashboard-html`。`references/` 由 YAML 生成。widget id 三处同源(SKILL.md 正则 + `schema.ts` Zod + `dashboard-schema.json` pattern,均 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`)约束**保留**(dashboard-emit-feedback 既有要求)。skill 仍走通用 `SkillResourceSyncer`,无需改 syncer。

### D9. 聊天内预览(无 HTML 后)

`dashboard-block.tsx` 只解析 `dashboard` fence(JSON)。预览卡渲染策略:JSON 解析成功后,调用 `POST /{id?}/preview`(不落库、不递增 version)拿编译 HTML 渲染缩略 iframe;streaming 中显示骨架;parse 失败显示 Zod 人话错误(沿用 `zod-issue-humanizer.ts`)。"Open to Workbench" 调 `promoteDashboard(json)`(去掉 html 参数),服务端编译落库。

## Risks / Trade-offs

- [6 布局复刻 12 定制模板视觉降级] → 全量重建路线已定;先做 2 个行业(电商 Mosaic + 财务 Monument)PoC 对照人工模板验收,再铺其余;`rawEchartsOption` 兜底。验收 owner 与阈值在 specs 中定义。
- [map geo 数据在 sandbox 内加载/CSP] → geoJSON 同 origin 静态内置 + 占位符替换 + 加载失败降级文案。
- [增量 postMessage 与全量 srcDoc 状态不一致] → 每次响应带 `version`,host 比对;version 跳跃则强制全量重载兜底。
- [YAML 单一真源仍可能被绕过] → CI `git diff --exit-code` 闸门 + 启动期自洽校验双保险。
- [删除 v1/v2 破坏存量] → 经确认无需考虑老实现,直接硬删;存量 v2 文件不再可编辑(可接受)。
- [domain record 升 v3] → 改 `Dashboard`/`Widget` sealed/record 后,MUST 全量 grep application 层 exhaustive switch(widget.type / DtEvent 等)同步,跑 `mvn install -pl data-talk-domain -am -DskipTests` 刷新 jar。

## Migration Plan

无数据迁移(硬删 v1/v2)。无 Flyway migration(沿用 `file_artifact` 表,无新表/新列)。部署即切换:

1. **Phase 1 — 编译器上线**:编译器 + YAML + 模板/CSS/JS/echarts-options;`promote` 改接收纯 JSON;新增 `update`/`preview`;前端 `promoteDashboard` 去 HTML 参数。端到端:一句话 → v3 JSON → 编译 → iframe。
2. **Phase 2 — 前端 + skill 切换**:SKILL.md 重写;`DashboardBlock` 移除 dashboard-html;`schema.ts` 升 v3;`DashboardFrame` 替换 `iframe-shell`(含 widget/update);文档生成脚本 + CI 闸门。端到端:多轮 <2s。
3. **Phase 3 — 清理**:删 `scripts/*.py`、12 定制模板、旧 `compile-rules.md`/`design-language.md`、`JsonPatchApplier`、旧 `PATCH`、v1→v2 迁移、旧 `BezelHtmlValidator`(合并入 `HtmlValidator`)、前端 dashboard-html fence 处理。全量回归 + E2E。

回滚:Phase 1/2 未合并前可直接弃用分支;一旦合并因彻底删除旧路径,回滚=revert 整个变更。

## Open Questions

- 6 布局的具体 slot 拓扑(每模板槽位数/kind/capacity)需在实现时随模板 HTML 一并定稿,初版以 `single-focus`/`two-column-*`/`three-column-kpi-center`/`top-kpi-bottom-charts`/`grid-equal` 的常识布局起步。
- A/B 视觉验收的判定人与通过阈值(建议:人工目检 + 关键截图对照,由 owner wallfacers 拍板)。
