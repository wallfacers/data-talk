## Why

当前 bezel 大屏由 AI 全量生成 HTML/CSS/JS,首次 ~407s、每轮迭代 ~410s,且受模型随机性影响持续产出缺陷(BUG-0048/0051/0055)。根因是把"HTML 编译"这件确定性工作交给了非确定性的 AI,并依赖 7+ 份互相矛盾的契约文档(BUG-0055 即 `compile-rules.md` 与 `patterns-catalog.md` 对 KPI 是否 `echarts.init` 的规定打架)。本变更把 HTML 编译从 AI 职责中彻底移除,交给服务端确定性编译器:AI 只输出纯 JSON(schemaVersion 3),Java 编译器毫秒级编译 JSON → HTML。

## What Changes

- **新增服务端编译器**:`compile(json) → html` 纯函数,六阶段管线(Schema Validate → Template Resolve → Widget Compile → Option Merge → Assemble → HTML Validate),首次全量 ~50ms。
- **AI 只产出 JSON**:删除 `dashboard-html` fenced block;AI 只输出 `dashboard` fence(v3 JSON)。SKILL.md 从 134 行精简到 ~30 行,reference 文档由 YAML 自动生成。
- **单一真源 `pattern-catalog.yaml`**:同时驱动 Java 编译器(运行时)和 AI 文档(构建期生成),并加 CI 防漂移闸门,从机制上消除 doc-divergence 类缺陷。
- **多轮迭代 ~410s → <2s**:AI 每轮输出完整 v3 JSON;服务端 `DashboardDiffer` 判定增量/全量编译;iframe 走 `srcDoc` 全量替换或 `postMessage` 单 widget 增量热更新。
- **JSON Schema v3**:新增 `chartSemantics`(8 种图表类型 + 配色/堆叠/图例等语义字段 + `rawEchartsOption` escape hatch)、`layout.template`(6 个布局模板)、widget→槽位映射。
- **6 个通用布局 + 13 套行业 CSS 变量**:全量重建,替代现有 12 套整页定制模板(CSS 变量解耦 layout 与 theme)。
- **BREAKING — 彻底删除旧实现,不做迁移**:删除 v1/v2 schema 支持、v1→v2 迁移逻辑、`dashboard-html` fence 管线、AI 端 scheduler/HTML 生成契约、`scripts/validate.py`/`preview.py`、12 套定制模板。存量 v2 dashboard 不迁移(经确认无需考虑老实现)。

## Capabilities

### New Capabilities

- `dashboard-server-compiler`: 服务端确定性 JSON→HTML 编译器 — 六阶段管线、`OptionMerger` 三级合并、模板槽位填充、CSP/echarts 注入、`HtmlValidator` 安全校验,以及编译器输入契约 schemaVersion 3(`chartSemantics` + `layout.template` + widget 槽位)。
- `dashboard-pattern-catalog`: `pattern-catalog.yaml` 单一真源 — 运行时驱动编译器、构建期生成 AI 文档、启动期自洽校验、CI 防漂移校验(`generate && git diff --exit-code`)。
- `dashboard-incremental-update`: 服务端 `DashboardDiffer` + 增量/全量编译策略 + iframe 热更新协议(`srcDoc` 全量 / `postMessage` 单 widget)+ `update`/`preview` API + 乐观锁。

### Modified Capabilities

- `bezel-scheduler-contract`: 轮询调度器从"AI emit 时遵守 + `validate.py` 校验"改为编译器内置 `scheduler.js` IIFE 注入;`baseOption` 由 `OptionMerger` 产出而非 AI 手写;type-aware(chart vs HTML-only)契约语义保留,但强制点从 AI 移到编译器。
- `dashboard-emit-feedback`: SKILL.md 精简为 ~30 行并引用 YAML 自动生成的 reference;widget id 规则三处同源约束保留;`DashboardBlock` 只读 `dashboard` fence(移除 `dashboard-html` 读取),聊天内预览改为按需调用 `preview` API 编译。

## Impact

- **后端(application 层)**:新增 `dashboard/` 编译器包(`DashboardCompiler`/`TemplateResolver`/`WidgetCompiler`/`OptionMerger`/`CspInjector`/`SchedulerBundler`/`HtmlValidator`/`DashboardDiffer`);改造 `DashboardArtifactService`(不再存 AI 的 HTML,改存编译产物);删除 v1→v2 迁移、`JsonPatchApplier`、旧 `BezelHtmlValidator` 合并入新 `HtmlValidator`。
- **后端(adapter 层)**:`DashboardController` 接口改造 — `promote` 接收纯 JSON、新增 `POST /{id}/update`、新增 `POST /{id}/preview`、删除旧 `PATCH /{id}`(JSON-Patch);`GET /{id}/html` 保留 serve-time 占位符替换(`__BEZEL_SERVER_ORIGIN__` / `/bezel/echarts.min.js`)。
- **后端资源**:新增 `resources/dashboard/`(`pattern-catalog.yaml` + 6 模板 + 13 CSS + renderers + `scheduler.js` + 8 份 echarts-options);新增 `scripts/generate-bezel-docs.sh`;依赖 `jackson-dataformat-yaml`(Spring Boot 已含)。
- **domain 层**:`Dashboard`/`Widget` record 升 v3 字段(`chartSemantics`、`layout.template`、`title`);删除 v1/v2 兼容。注意检查 application 层 exhaustive switch 同步。
- **AI skill**:`resources/skills/bezel/` 重写 SKILL.md;reference 改为生成产物;删除 `scripts/*.py`、`assets/templates/*.html`(12 套)、旧 `references/{compile-rules,design-language}.md`。skill 仍走通用 `SkillResourceSyncer`(无 bezel 专属解压逻辑,无需删除 syncer)。
- **前端**:`schema.ts`/`types.ts` 升 v3;`iframe-shell.tsx` → `DashboardFrame.tsx`(支持 postMessage 增量热更新);`dashboard-block.tsx` 只读 `dashboard` fence;`dashboard-api.ts` `promoteDashboard` 去掉 HTML 参数;markdown 渲染管线移除 `dashboard-html` fence 处理。
- **存储**:沿用 `FileArtifactService` + 文件系统(`workdirRoot/dashboards/{id}.dashboard.json` + `.html`,`file_artifact` 表 kind='dashboard');无新增 DB 表。
- **数据源类型兼容**:widget SQL 执行链路(SELECT-only guard、连接/database/schema 级联解析)**不变**,本变更对 `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 标记 **N/A**。

## Design Inputs (client/DESIGN.md)

本变更触及 client/(`DashboardFrame`、`dashboard-tab`、`dashboard-block`),适用约束:

- **token-only 宿主 chrome**:DashboardFrame 容器、stage tab、聊天内预览卡 MUST 仅用语义 token(`bg.canvas`/`bg.soft`/`border.subtle`/`accent.primary` 等),禁止裸用 primitive 颜色。
- **Stage 全局态**:dashboard tab 遵守 `useStageStore` 全局态约定,`StageTab` 实例无 `scope` 字段;切换 session 不改变 stage。
- **加载/错误五态**:预览卡的 streaming/parse-error/success/empty/missing 状态须显式映射 token,disabled/错误不得仅靠颜色区分(参见无障碍约束 `4.5:1`、`prefers-reduced-motion`)。
- **明确边界(刻意例外)**:iframe **内部**的大屏 HTML 是 bezel premium 设计语言(深色全幅、行业化视觉),属于独立的全屏呈现面,**不受** "不要拆分 Chat/Workbench 视觉系统" 约束;DESIGN.md 仅约束 iframe 外的宿主 chrome。此边界在本变更中刻意保留。

## Risks / Known Issues

- `docs/bugs/index.md` 当前**无 open BUG**;BUG-0048/0051/0055 均已 fixed。BUG-0055 的 doc-divergence 根因已修,本变更目标是从机制上**防止其复发**(单一真源 + CI 闸门),而非修复 live bug。
- **最大风险**:6 个通用布局 + CSS 变量能否复刻 12 套定制模板的视觉品质。经确认采用全量重建路线;`rawEchartsOption` escape hatch 兜底,以现有人工模板逐张 A/B 验收。
- **map 图表**:8 种图表类型含 `map`,需在 sandbox iframe 内处理 geo JSON 注册,工作量与安全(CSP)需在 design 中专门处理。
