## Context

DataTalk 让 AI 通过 OpenCode 协议触发 bezel skill，把 dashboard 描述用 ```dashboard``` + ```dashboard-html``` 两个 fenced block 投递到 chat。前端 `DashboardBlock`（`client/src/features/chat/components/markdown/dashboard-block.tsx`）：

1. base64 解码 mount 上的 `data-dashboard-json-b64`
2. 调 `parseDashboard(json)` → JSON.parse + `dashboardSchema.safeParse`
3. 成功 → 显示带 "在工作台打开" 按钮的预览卡
4. 失败 → 渲染红色 "Dashboard 解析失败" 错误卡，折叠区单 `<pre>{json}` 加 Zod issue 拼接

`dashboardSchema` 与后端 `dashboard-schema.json` 共同持有 widget id 正则 `^[a-z]+_w_[a-zA-Z0-9_]{4,32}$`（BUG-0047 时已经从 `[a-zA-Z0-9]{4,16}` 放宽过一次）。但 bezel `SKILL.md:58` 一直写的是 `<lowercase>_w_<4-16 alphanumerics>`，与真实 schema 三处不一致：上限错（16 vs 32）、下限隐式（"4-16" 让 AI 误以为弹性区间而非硬下限）、漏掉下划线（实际允许）。BUG-0053 复现 AI 输出 `kpi_w_gmv`（后缀 3 字符）被拒，错误卡用英文 Zod issue 拼接，用户既不知是哪个 widget 哪条字段，也不知"`_w_` 后缀至少 4 字符"这条规则。

下游链路（promote / iframe / SQL execution）全部正常——根因严格在 chat 第一公里。

## Goals / Non-Goals

**Goals:**

- bezel `SKILL.md` 的 widget id 描述 100% 与 `dashboardSchema` / `dashboard-schema.json` 同源正则一致，并附 ✓/✗ 例子和强制 pre-emit 检查项。
- `DashboardBlock` 错误卡按 `issue.path` 定位字段，按 `issue.code` + `issue.path` 模式分类，常见 4 类正则失败给出中文/英文人话解释，未知 issue 回退原文不丢信息。
- 不打断现有依赖链路：错误卡仍是同一组件、同一 i18n 系统、同一 status.danger 配色，最小入侵。

**Non-Goals:**

- 不改 `dashboardSchema` / `dashboard-schema.json` 正则本身（S5 alternative — 见下）。
- 不在 `/api/dashboards/promote` 加服务端 widget id sanitize（S7 alternative — 见下）。
- 不引入 OpenCode 端 pre-emit 校验 hook（S2 alternative — 单独大设计）。
- 不收紧 `BezelHtmlValidator`（S3 alternative — 与本次根因无关）。
- 不修复 BUG-0053 复现样本里 SQL 方言假设、KPI label 列冗余、polling IIFE 偏离 canonical 等 P2/P3 杂项（在原诊断中已列，留给单独 backlog）。

## Decisions

### D1：把 widget id 规则的"事实来源"写回到 SKILL.md，而不是放宽 schema

**Decision:** SKILL.md `_w_` 规则与 schema.ts / dashboard-schema.json 严格一致，附正则原文与 ✓/✗ 例。Pre-emit checklist 增加显式校验项。

**Why:**

- BUG-0047 已经把正则从 `{4,16}` 放宽到 `{4,32}`，并且把字符集从 `[a-zA-Z0-9]` 加上 `_`。BUG-0047 Notes 明确反对"再放宽"路线，理由是 `_w_` 后缀的语义负载（"必须有意义"）会被进一步侵蚀。
- AI 实际更容易遵守显式规则（"≥4 字符"）+ ✗ 反例，而不是从 "4-16 alphanumerics" 这种半正式描述里推出"≥4 是硬下限"。
- 改 SKILL.md 是零代码风险（仅 markdown），但 prompt 信号强度直接抬升。

**Alternatives considered:**

- **S5 — 把正则下限从 4 放到 2**：覆盖 `kpi_w_gmv` 自然过。**否决**——BUG-0047 的 Notes 已点过这条路，再走会让 `kpi_w_a` / `chart_w_x` 这种没有信息量的 id 合规，violating spirit of "naming should encode meaning"。且会让现有人工编写的合规 dashboard 命名习惯失锚。
- **S7 — 服务端 promote 时 sanitize widget id**（如 `kpi_w_gmv` → `kpi_w_gmv001`）。**否决**——bezel HTML 在 `<div id>` / `__BEZEL_CONFIG__.widgets[].id` / endpoint URL（"/api/dashboards/{id}/widgets/{wid}/data"）三处复刻同一 widget id，服务端单点改了 JSON 但不改 HTML 等价于把 widget 渲染挂掉。强行同步要解析 HTML、改 base64 mount、潜在影响 `JSON_HASH` 完整性，复杂度爆炸。

### D2：humanizer 走规则匹配而非 generic LLM-style 改写

**Decision:** 新建 `client/src/features/dashboard/zod-issue-humanizer.ts`，对每个 Zod issue 以 `(path, code, validation?)` 为 key 做规则匹配，命中已知 4 类（widget id / dashboard id / patternId / theme）则返回结构化 friendly；不命中回退 `issue.message` 原文。

**Why:**

- Zod issues 在我们的 schema 内可枚举：4 个常见 regex 失败 + 偶发的"missing field" / "wrong type" 等。规则匹配既快又稳定（O(1) 查表，没有运行时网络/LLM 依赖）。
- 失败兜底用原文而不是吞掉 → 不会因 humanizer 缺映射而把信息从用户屏幕上抹掉（这是 BUG-0028/BUG-0031 风格的反例提醒：fallback 永远要透明）。

**Alternatives considered:**

- **每个错误调一次 LLM 翻译**：成本高、慢、错误链路复杂、可能在 chat 流式中卡住。**否决**。
- **直接在 Zod schema 上加 `.refine(...)` + 自定义 message**：能让 message 就地变中文，但 i18n 切换会失效（schema 模块没引 useI18n），且 message 仍丢 path。**否决**。

### D3：错误卡 UI 形态——结构化条目列表 + 折叠 raw JSON

**Decision:** 错误卡折叠区 layout：

```
┌─ Dashboard 解析失败 ───────────────────────────┐
│ ▾ <fragment summary, 不变>                     │
├─────────────────────────────────────────────────┤
│ ▸ 错误明细（点击展开/折叠）                     │
│   widgets[0].id      kpi_w_gmv                  │
│      → widget id「kpi_w_gmv」不符合命名规则：…  │
│   theme              modern                     │
│      → theme「modern」必须以 industry- 开头… │
│ ▸ 原始 JSON（点击展开复制）                     │
│   <pre>{...}</pre>                              │
└─────────────────────────────────────────────────┘
```

**Why:**

- 路径标签 `widgets[0].id` 用 `typography.mono-sm`（数据/技术内容用 mono treatment，per client/DESIGN.md "Tables ... mono treatment for numeric or technical content"）。
- friendly 文本 `typography.ui-sm` + `text.base` 颜色，普通可读流。
- 原始 JSON 移到二级折叠：保留 copy/debug 路径，不挡视野。

**Alternatives considered:**

- **直接把所有 issues 平摊到一行 join('; ')（现状）**：信息密度高但全英文 + 无 path，对人无效。**否决**（即根因）。
- **把 friendly 直接展示在卡片头部，不需要折叠**：但 dashboard 大屏可能有 10+ widget，每个错都展开会塞满 chat。保留折叠。

### D4：i18n 走 placeholder 模板 + 数字插值

**Decision:** zh-CN.json / en-US.json 新增：

```json
"dashboard": {
  "errorDetail": {
    "widgetIdRegex": "widget id「{value}」不符合命名规则：必须形如 `<lowercase>_w_<≥4 字符后缀>`，当前后缀长度 {suffixLength}",
    "dashboardIdRegex": "dashboard.id「{value}」格式不对：必须形如 `dash_xxxx`（≥4 字符）",
    "patternIdRegex": "widget patternId「{value}」必须形如 `<前缀>.<后缀>`（kebab-case，例：ecommerce.gmv-kpi）",
    "themeRegex": "theme「{value}」必须以 industry- 开头（例：industry-ecommerce）"
  }
}
```

英文同结构。`useI18n().t(key, { value, suffixLength })` 走现有 i18n provider，无新依赖。

**Why:** 与现有 i18n 体系（`client/src/i18n/`）一致，零新基础设施。

## Risks / Trade-offs

- **[Risk] humanizer 漏分类导致部分错误仍以英文显示** → Mitigation: 显式 fallback 到 `issue.message` 原文 + 新增 vitest case 覆盖未知 issue 路径，确保 `issues.length === friendly.length`。
- **[Risk] SKILL.md 改后 AI 仍偶犯**（prompt 不是硬约束） → Mitigation: humanizer 是终极兜底，即便 AI 犯了用户也能看明白怎么修；后续观察一周如再次复现 widget id 短后缀，可考虑升级到 S7（服务端三处同步 sanitize）作为独立 change。
- **[Risk] 现有 `dashboard-block.test.tsx` 用 `getByText('Invalid string')` 之类硬断言被新结构破坏** → Mitigation: task 4 显式列出"先跑一次 npm test 找到所有 dashboard-block 相关失败 case，与新结构同步修"。
- **[Trade-off] humanizer 增加约 80 行 client 代码 + i18n 字符串** → 收益是用户能自助修，避免反复 round-trip 让 AI 重生成。

## Migration Plan

无 schema / 数据迁移。修改完直接 vitest + tsc 验证 → merge → 用户下次刷新即生效。

回滚策略：本变更全部是新增 + 局部重构，git revert 单个 commit 即可，无 DB / Flyway 风险。

## Open Questions

- 是否需要把 humanizer 中文文案与"如何修"指引拆开两段？（如 friendly + suggestion）。本设计里 friendly 已包含"必须形如 X"已隐含修法，先不拆，等用户反馈再迭代。
- patternId / theme 字段如果 AI 完全省略（不是正则失败而是 undefined），目前会被 Zod 报 `Required` issue，humanizer 是否也要覆盖此 code？建议覆盖（task 2 顺手实现 `code: 'invalid_type'` + path 中含 `id` / `theme` / `patternId` 的 fallback friendly）。

## Design Inputs（client/DESIGN.md，Frontend Design Contract Gate）

本变更只触前端 UI 层（`dashboard-block.tsx` 错误卡），严格映射如下 token：

| 用途 | client/DESIGN.md token |
|---|---|
| 错误卡边框/背景 | `status.danger` / `status.dangerSurface` |
| chevron / 折叠图标颜色 | `text.muted` |
| chevron 折叠展开动画 | `motion.fast` (120ms) + `motion.easing.standard` (`cubic-bezier(0.2, 0, 0, 1)`) |
| 结构化条目正文文字 | `text.base` |
| path 标签字体 | `typography.mono-sm`（13px monospace） |
| friendly 文字 | `typography.ui-sm`（13px sans） |
| 二级折叠 raw JSON 字体 | `typography.mono-sm`（与现状一致） |
| 错误标题颜色 | `status.danger`（与现状一致） |
| 错误标题字体 | `typography.ui-sm`（与现状一致） |
| copy 按钮 hover | `interaction.hover` |

A11y 约束：

- 错误卡 `role="alert"` 与 `aria-live="polite"` 保留（如已有）。
- 每个结构化条目可独立选中、复制（不依赖颜色单独传达"哪条错了"——条目文字本身已说明）。
- 折叠按钮可键盘聚焦（与现状 button 一致），`aria-expanded` 同步状态。

不引入：

- 无新原始色（只复用 token）
- 无 glassmorphism / 无装饰动画（仅 chevron 翻转）
- 不破坏 Chat 与 Workbench 视觉一致性
