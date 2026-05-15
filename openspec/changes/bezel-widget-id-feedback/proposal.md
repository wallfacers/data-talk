## Why

BUG-0053 复现了一条 BUG-0047 Notes 早就预警的遗留风险：AI 通过 bezel skill 生成 dashboard JSON 时偶尔写出 `kpi_w_gmv` 这样后缀仅 3 字符的 widget id，违反 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$` 正则；前端 `DashboardBlock` 直接渲染成红色"Dashboard 解析失败"卡片，错误文本是 Zod issue.message 的英文拼接，**既不告诉用户是哪个 widget 哪个字段，也不告诉用户违反了什么人话规则**。结果是用户既不能上下游修，又不能再手动救场——大屏交付链路第一公里就断在 chat 里。

根因落在两处：(1) bezel `SKILL.md` 第 58 行写的 widget id 规则 `<lowercase>_w_<4-16 alphanumerics>` 与真实 schema `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$` 三处不一致（上限错、下限隐式、漏掉下划线），AI 受误导；(2) `DashboardBlock.parseDashboard` 把 Zod issues 直接拼接成英文，丢失 `issue.path` 信息，对中文用户和不熟正则语义的人零指导价值。本次只做这两层最小修。

## What Changes

- **修 `server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md`** widget id 描述与 schema 一致，附正则原文、✓/✗ 例子，pre-emit checklist 新增"逐 widget id 跑一遍正则"项。
- **新增 `client/src/features/dashboard/zod-issue-humanizer.ts`**：把 Zod issue 转成结构化 `{ path, code, friendly }` 条目，覆盖 widget id / dashboard id / patternId / theme 四类常见正则失败，未知 issue 回退原文。
- **重构 `client/src/features/chat/components/markdown/dashboard-block.tsx`** 错误状态渲染：折叠区从单行 `<pre>{json}` 改为"结构化条目列表 + 可展开 raw JSON"。错误条目逐行显示 `path` + `friendly` 文本，全部可选中可复制。
- **新增 i18n 字符串** 到 `client/src/i18n/locales/zh-CN.json` 与 `en-US.json`：`dashboard.errorDetail.widgetIdRegex` / `dashboardIdRegex` / `patternIdRegex` / `themeRegex` 四条带 placeholder 模板。
- **新增 vitest case** 覆盖三种已知错误形态（widget id 太短、dashboard id 错、多 issue 同时存在），并保持原有 schema.test.ts 不动。

不修改 widget id 正则本身、不动 promote / iframe / SQL execution 链路、不改 BezelHtmlValidator。

## Capabilities

### New Capabilities
- `dashboard-emit-feedback`: AI 通过 bezel skill 输出 dashboard 与前端校验失败反馈的契约——SKILL.md 必须与 schema 正则严格一致，DashboardBlock 必须把 Zod 失败翻译成定位明确的人话条目。

### Modified Capabilities
（无——本变更不修改 `agent-skill-routing` 的 AGENTS.md 骨架或 skill 注册一致性约束，也不修改 `user-message-markdown` 的用户气泡 markdown 渲染契约）

## Impact

**代码：**
- `server/data-talk-adapter/src/main/resources/skills/bezel/SKILL.md`（纯文档，不影响编译/打包）
- `client/src/features/dashboard/zod-issue-humanizer.ts`（新文件）
- `client/src/features/chat/components/markdown/dashboard-block.tsx`（错误路径重构）
- `client/src/features/chat/components/markdown/__tests__/dashboard-block.test.tsx`（新增 case）
- `client/src/i18n/locales/zh-CN.json` 与 `en-US.json`（新增 4 条 errorDetail keys）

**测试：** vitest 单测；不需要新增 backend 测试。

**API / 数据库：** 不变。

**关联 BUG：** [BUG-0053](../../../docs/bugs/BUG-0053-bezel-ai-widget-id-too-short-and-zod-error-unhelpful.md) 状态从 `investigating` → `fixed`，`fixCommit` 在合并后回填。

**风险：**
- BUG-0050 / BUG-0049 处于 `fixed` 等待 verify 状态，但二者改动面（widget skeleton 取数 / HTML 中文乱码）与本次（SKILL.md prompt + 错误 UX）正交，无冲突。
- 历史 `dashboard-block.test.tsx` 假定错误 UX 单 `<pre>{json}` 形态，本次改后若有 case 用 `getByText('Invalid string')` 之类硬断言会被破坏——需要在 task 内同步修。

**Design Inputs（client/DESIGN.md，Frontend Design Contract Gate 强制）：** 详见 `design.md` 的 `Design Inputs` 节。本次 UI 改动严格映射：
- 错误卡边框 / 背景：`status.danger` / `status.dangerSurface`（已有，保留）
- 结构化条目正文：`text.base`，path 标签：`typography.mono-sm`，friendly 文字：`typography.ui-sm`
- 折叠 chevron 动画：`motion.fast` (120ms) + `motion.easing.standard`
- A11y：错误卡 `role="alert"`（保留），新增条目逐项可选中可复制；不依赖颜色单独传达状态。
