# ER 设计器与查看器视觉重设计

- 状态：已落地（2026-04-30；PR 未创建，截图 / axe-core 手工验收 deferred）
- 范围：`client/` ER Designer Tab + ER Inspector Tab 的画布外壳、节点卡片、边、关系标签、工具栏、空态、上下文菜单
- 不在范围：后端、OpenCode 协议、JSON Patch 路径、ER inspector 侧栏（如有）、Generate DDL 的下游展示（按 [ER Graph Browsing & Designing](./2026-04-29-er-graph-browsing-design.md) Q10：DDL 灌入新建 `query_editor` Tab + 走 Task 5 L2/L3 confirm，不另开 Dialog；本 spec 只覆盖工具栏按钮自身的视觉）、Diff vs DB 的下游展示、ER 业务行为
- 关联文档：
  - [client/DESIGN.md](../../client/DESIGN.md)（设计契约）
  - [ER Graph Browsing & Designing](./2026-04-29-er-graph-browsing-design.md)（ER 能力总体）
  - [ER tab protocol](../references/er-tab-protocol.md)
  - [docs/I18N.md](../I18N.md)

## 0. Design Inputs（前端设计契约引用）

本 spec 严格依据 `client/DESIGN.md` 草案版本（contract source of truth）。直接适用的约束：

- **Principles**：Precision First；Dual-Core, One System；Neutral Backbone, Focused Signal；Calm in Light, Crisp in Dark；Dense but Breathable；Motion as Confirmation。
- **Theme Semantics**：双主题，组件不写主题分支，全部走语义 token（`bg.*` `text.*` `border.*` `accent.*` `status.*` `interaction.*`）。
- **Component Rules**：Stage 用 `bg.subtle` 作为 chrome、`bg.canvas` 作为主工作面；Tabs / 工具栏元素与 Stage 同语言。
- **Component-level（YAML）**：本次涉及的 `stage` `chart` `composer` 别名子键允许 component-level warning（schema 暂未跟上），但零 lint error 必须保留。
- **Data Visualization**：cobalt 仅用于 focus / current object / primary action；warn 用于 compare / warning；status 色仅用于 outcome / health；neutrals 承载背景与上下文。
- **Accessibility**：body ≥ 4.5:1、key icon ≥ 3:1、focus ring 双主题可见、不依赖颜色单通道、键盘可达、`prefers-reduced-motion` 必须尊重、icon-only 必须有 accessible name。
- **Don't**：禁止使用 raw primitive；禁止把 Designer / Viewer 拆成两套不相关视觉系统；禁止过度使用 accent 色；禁止 glassmorphism / cyberpunk / 装饰性动画。

## 1. 目标与非目标

### 目标

1. **贴合 DESIGN.md**：补齐 token 使用、密度档、状态对比、图标色规则等系统化合规项。
2. **轻量身份化**：Designer 与 Viewer 视觉强相关、靠"模式徽章 + 强调色 + 节点 pencil/lock"区分身份，不另立视觉语言。
3. **Studio vs Instrument 气质**：Designer 偏 Studio（点阵更密、节点 `shadow-sm`、ring 更厚），Viewer 偏 Instrument（点阵稀疏、节点扁平、ring 克制）。差异仅靠 token 与几何参数，不引入装饰元素。
4. **节点 / 边 / 工具栏 / 空态 / 上下文菜单一并系统化**：保证 ER 画布所有表面用同一组 token、同一密度档、同一交互语言。

### 非目标

- 不动 ReactFlow 几何与 ER store 协议（节点 / 边 / 跨线让位算法保持现状）。
- 不引入 crow's foot 基数符号（学习成本与发现性权衡后，保留中央关系类型 chip + 端点 chevron 箭头）。
- 不重写 ER inspector 侧栏（独立后续工作）；Generate DDL 与 Diff vs DB 的下游产物不属于本 spec —— 按 ER 总体 spec Q10 / Q11，DDL 走 query_editor Tab 灌入流程，**不存在 DDL Dialog**；Diff vs DB 下游展示亦不在本范围。
- 不变更 i18n 文案语义结构，仅按 §5 增量。

## 2. 总体决策记录

| 维度 | 决定 | 理由 |
|---|---|---|
| 核心目标 | DESIGN.md 合规 + Designer/Viewer 区分 + 画布观感升级 | 用户优先级排序 |
| 模式区分强度 | 轻量身份化 | 服从 "Dual-Core, One System" |
| 视觉范围 | 节点 + 边 + 工具栏 + 空态 + 上下文菜单 | 让画布呈现一致语言 |
| 气质差异 | 混合 (Designer = Studio, Viewer = Instrument) | 差异落在 token / 几何参数，避免装饰 |
| 列行排版 | 左侧色条（PK 实色 / FK 虚线）+ 单行 | "Dense but Breathable"；rail+icon+字重三通道传递列角色 |
| 列行 rail 颜色 | 默认 `border.strong` / `border.default` 中性，hover/selected 升级为 `accent.primary` | 严守 "cobalt 仅用于 focus / selection / primary action" |

> 早期讨论中曾考虑把 PK rail 直接用 `accent.primary` / FK rail 用 `status.info` 来突出 PK/FK。该路径会让 cobalt 出现在每张节点的每行，稀释焦点强调色。最终采纳"中性默认 + 状态点亮"，把 cobalt 留给真正的交互态。

## 3. 画布外壳与气质（chrome）

### 3.1 通用外壳

| 区域 | Token | 备注 |
|---|---|---|
| 顶部工具栏 | `bg.subtle` + 底边 `border.subtle`，高度 40px | 与 Stage chrome 同档 |
| 画布主体 | `bg.canvas` | 主工作面 |
| 节点 | 见 §6 | |
| 节点 header | `bg.subtle` + 底边 `border.default` (Designer) / `border.subtle` (Viewer) | |
| 网格点 | `border.subtle` | 颜色随主题自动 |
| 选择 / 聚焦环 | `accent.primary` + `accent.primarySurface` | |

### 3.2 Studio vs Instrument

| 参数 | Designer (Studio) | Viewer (Instrument) |
|---|---|---|
| 网格 gap | 18px | 24px |
| 网格点透明度 | 100% `border.subtle` | 60% `border.subtle` |
| 节点阴影 | `shadow-sm` 常驻 | 无 |
| 节点 hover ring | 4px `accent.primarySurface` | 2px `accent.primarySurface` |
| 节点边框 | `border.default` 1px | `border.subtle` 1px |

差异只通过 token 取值与几何参数实现，不引入额外色彩或纹理。

### 3.3 模式徽章（mode badge）

工具栏最左侧固定一枚不可点击的状态徽章：

| 模式 | 视觉 | 字体 | 图标 | aria |
|---|---|---|---|---|
| Designer | bg=`accent.primarySurface`、text=`accent.primary`、padding=4 8、radius=`md` | `ui-xs` 500w | `Pencil` `size-3.5` `accent.primary` | `role="status"` + `aria-live="off"` |
| Viewer | bg=`bg.subtle`（dark 同），text=`text.muted`、padding=4 8、radius=`md` | `ui-xs` 500w | `Lock` `size-3.5` `text.muted` | 同上 |

## 4. 图标色契约（贯穿所有 ER 表面）

DESIGN.md 没有图标色专条，本 spec 把"Neutral Backbone, Focused Signal"落实到图标层面：

| 场景 | Token |
|---|---|
| 默认 / 装饰性图标（chrome 装饰、表头 `TableIcon`、列行图标默认态） | `text.muted` |
| 强调性图标（hover 后） | `text.base` |
| 主操作 / 主动作图标 | `accent.primary` |
| 模式徽章图标 | 跟徽章字色（Designer = `accent.primary`、Viewer = `text.muted`） |
| 节点 header 模式提示 | Designer `Pencil` = `accent.primary`、Viewer `Lock` = `text.soft` |
| 列行 PK / FK 图标（默认） | `text.muted` |
| 列行 PK / FK 图标（hover/selected 行） | `accent.primary` |
| 危险操作图标（默认） | `text.soft` |
| 危险操作图标（hover） | `status.danger`（配 `status.dangerSurface` 背景） |
| 警告 / 虚拟关系图标 | `accent.warn` |
| 成功反馈图标 | `status.success` |
| 禁用图标 | `interaction.disabled`（必须配 `aria-disabled`，不可只靠灰度） |
| Connection handle 球（Designer） | 两端统一 `border.strong`，靠形状区分（target 实心圆 / source 环形圆）；hover 时 ring `accent.primary` |

**通用尺寸**：装饰性 `size-3.5`（14px）、按钮内联 `size-4`（16px）、列行内 PK/FK `size-3`（12px）。

**写法**：优先用 Tailwind 语义类（`text-text-muted`、`bg-bg-canvas`、`border-border-default` 等，由项目 `@theme inline` 映射至 `var(--dt-*)`）；SVG `stroke` / `fill` / `<marker>` 等 Tailwind 工具难以直达的位置允许用 `var(--dt-*)` CSS 自定义属性。**禁止**直接使用 primitive 值（如 `text-cobalt-700`、`#3B82F6`、Tailwind 调色板里的 `bg-blue-500`），也禁止在组件内引入 component-local 颜色值——这是 DESIGN.md "Don't use raw primitive colors directly in feature code" 的硬约束。

**对比度兜底**：所有图标必须满足 DESIGN.md 的 "key icon contrast ≥ 3:1"。`text.muted` 在 light 对 `bg.canvas` 是 5.74:1、对 `bg.subtle` 是 5.5:1；dark 同理满足。

## 5. 工具栏

### 5.1 通用规格

| 参数 | 值 |
|---|---|
| 高度 | 40px（min-h-10） |
| 内边距 | `px-3 py-1` |
| 底边 | 1px `border.subtle` |
| 背景 | `bg.subtle` |
| 组间 gap | 8px (`gap-2`) |
| 组内 gap | 2px (`gap-0.5`) |
| 分隔条 | `h-4 w-px bg-border-subtle` |

### 5.2 通用按钮

| 角色 | 高度 | radius | 字号 | 状态 | token |
|---|---|---|---|---|---|
| Ghost 工具按钮 | 28px | `md` | `ui-xs` | 默认 | text=`text.muted`、icon=`text.muted`、bg=transparent |
| | | | | hover | text=`text.strong`、icon=`text.base`、bg=`interaction.hover` |
| | | | | focus-visible | ring 2px `interaction.focusRing` |
| | | | | disabled | `interaction.disabled` + cursor-not-allowed + tooltip 解释原因 |
| Primary outline 按钮 | 28px | `md` | `ui-xs` 500w | 默认 | text+icon+border=`accent.primary`，bg=transparent |
| | | | | hover | bg=`accent.primarySurface`、border=`accent.primaryHover` |
| | | | | focus-visible | ring 2px `interaction.focusRing` |
| | | | | disabled | text+icon+border=`interaction.disabled` + tooltip 解释原因 |
| 模式徽章 | 24px | `md` | `ui-xs` 500w | — | 见 §3.3 |

### 5.3 Designer 工具栏分组

```
[🖉 Designer]  +Add table | ⌷Auto layout  ⤢Fit view  │  🔗Bind target  ▼Diff vs DB  </>Generate DDL  ──ml-auto──  Dialect:[ MySQL▾]
```

| 组 | 内容 | 变体 |
|---|---|---|
| G1 模式徽章 | `🖉 Designer` | 见 §3.3 |
| G2 画布操作 | `+ Add table`、`Auto layout`、`Fit view` | Ghost |
| 分隔条 | | |
| G3 主操作 | `Bind target`、`Diff vs DB`、`Generate DDL` | Primary outline；`Diff` 与 `Generate DDL` 在 `hasTarget=false` 时 disabled，tooltip 文案接 `erCanvas.toolbar.disabledHint.bindFirst` |
| G4 设置（`ml-auto`） | `Dialect:` 标签 + Select | Select：h-7、`border.default`、`bg.canvas` |

### 5.4 Viewer 工具栏分组

```
[🔒 Viewer]  ↻Refresh  ⌷Auto layout  ⤢Fit view  │  Depth:[ 1▾]  │  +VR  ──ml-auto──  ⑂Fork to Designer
```

| 组 | 内容 | 变体 |
|---|---|---|
| G1 模式徽章 | `🔒 Viewer` | 见 §3.3 |
| G2 画布操作 | `Refresh`、`Auto layout`、`Fit view` | Ghost |
| 分隔条 | | |
| G3 邻居深度 | `Depth:` 标签 + Select | 与 Dialect 同款 |
| 分隔条 | | |
| G4 添加虚拟关系 | `+ Add virtual relation` | Ghost（不是主操作） |
| G5 出口操作（`ml-auto`） | `Fork to Designer` | Primary outline |

### 5.5 响应行为

宽度 < 720px 时 G2 ghost 按钮折叠为 icon-only（保留 `aria-label` + tooltip），主操作组与 Select 不折叠文字。

### 5.6 键盘可达性

`Tab` 顺序按视觉左→右；`useErKeyboard` 的画布快捷键继续生效，工具栏按钮通过 `aria-keyshortcuts` 暴露给 SR。

## 6. 节点卡片（Table Node）

### 6.1 卡片容器

| 参数 | Designer | Viewer |
|---|---|---|
| 宽度 | 320px | 320px |
| 边框 | `border.default` 1px | `border.subtle` 1px |
| Radius | `md`（10px） | 同 |
| 阴影 | `shadow-sm` | 无 |
| selected 边框 | `accent.primary` 1.5px | 同 |
| selected ring | 4px `accent.primarySurface` | 2px `accent.primarySurface` |
| 背景 | `bg.canvas` | `bg.canvas` |

### 6.2 Header

| 参数 | 值 |
|---|---|
| 高度 | 36px |
| 背景 | `bg.subtle` |
| 底边 | 1px `border.default` (Designer) / `border.subtle` (Viewer) |
| 内边距 | `px-3 py-2` |
| 内容 | flex `justify-between gap-2 items-center` |

左侧：`TableIcon`（`size-3.5` `text.muted`）+ `gap-2` + 表名（`ui-md` 500w `text.strong`、truncate）。
右侧模式提示：Designer = `Pencil size-3.5 accent.primary`、Viewer = `Lock size-3.5 text.soft`。

### 6.3 列行（Column Row）

通用骨架（自左 → 右）：

```
▍▍ 🔑 column_name           ╶╴TYPE╶╴ NN  ⊗
│   │  │                       │       │  │
│   │  │                       │       │  └─ delete (designer hover)
│   │  │                       │       └─ NN pill (only when notNull)
│   │  │                       └─ type chip (mono)
│   │  └─ name (input/span)
│   └─ role icon (size-3, text.muted)
└─ left rail (3px wide, mt/mb 0)
```

| 参数 | Designer | Viewer |
|---|---|---|
| 行高 | 32px (min-h-8) | 28px (min-h-7) |
| 内边距 | `px-3 py-1.5` | `px-3 py-1` |
| 底边（除最后行） | 1px `border.subtle` | 同 |
| hover 背景 | `interaction.hover` | 同 |

### 6.4 左侧色条规则

色条紧贴行左缘，宽 3px，行高 100%：

| 列角色 | 默认态 rail | hover/selected-row rail | 配套图标 |
|---|---|---|---|
| PK | 实色 `border.strong` | 实色 `accent.primary` | `KeyRound size-3 text.muted → accent.primary` |
| FK | 1.5px 虚线 `border.default` | 实色 `accent.primary` | `Link size-3 text.muted → accent.primary` |
| PK + FK | 实色 `border.strong`，rail 内右侧贴 1px 虚线（在 3px 实色上叠加 dashed inset，直观上"既实又虚"） | 实色 `accent.primary` | 两图标并排（PK 在前） |
| 普通列 | 无 rail | 无 rail | 无图标，占位 12px |

不依赖颜色：rail 形态（实/虚/无）+ 图标 + 字重三通道传递列角色。

### 6.5 列名

| 模式 | 实现 |
|---|---|
| Viewer | `<span>` truncate；PK 行 `font-medium text.strong`；FK / 普通 `text.base` |
| Designer | `<input>` 默认 `border-transparent bg-transparent`；hover 出微弱 `border.subtle`；focus 出 `border.default bg.panel ring focus`；字号同 viewer |

字体：`ui-md` (14/20)。

### 6.6 类型芯片（Type chip）

| 参数 | 值 |
|---|---|
| 高度 | 24px |
| Radius | `sm`（8px） |
| 字体 | `mono-sm` `text.muted` |
| 背景 | `bg.subtle` |
| 边框 | `border.subtle` |
| 内边距 | `px-1.5` |
| 最大宽度 | 112px (`max-w-28`) |

Designer 模式下整个芯片即 `<SelectTrigger>`，下拉项按当前 `dialect` 取自 `DIALECT_COLUMN_TYPE_OPTIONS`，保留现有 dialect 联动。

### 6.7 NOT NULL 标记（新增）

| 状态 | 视觉 |
|---|---|
| `nullable === false` | 类型芯片右侧 4px 间距 + `NN` 极小胶囊：`h-4 px-1 radius-sm text-[10px] text.soft border border-border-subtle` |
| `nullable === true` | 不渲染 |

排版承重而非颜色，符合 a11y。

### 6.8 行内 Delete 按钮（仅 Designer）

| 状态 | 样式 |
|---|---|
| 默认 | `opacity-0` |
| 行 hover | `opacity-100` 渐显（180ms） |
| 默认色 | icon `text.soft` |
| hover | bg `status.dangerSurface` + icon `status.danger` |
| focus-visible | ring `interaction.focusRing` |

由"常驻显示"改为"hover 才出"。

### 6.9 Designer "+ Add column" 行

位于列表底部。

| 参数 | 值 |
|---|---|
| 行高 | 32px |
| 顶边 | 1px `border.subtle` |
| 文字 | `ui-xs` 500w `accent.primary` 居中 |
| 默认背景 | transparent |
| hover 背景 | `accent.primarySurface` |
| focus-visible | ring `interaction.focusRing` inset |

### 6.10 折叠 / 溢出

保留 `COLUMN_PREVIEW_LIMIT = 12` + "X more" 模式。

- "X more" 行：`ui-xs` `text.muted` 居中，hover → `text.strong` + bg `interaction.hover`
- 折叠态（`data.collapsed=true`）：列列表整体不渲染，header 底边降为 `border.subtle`，header 右侧追加 `ChevronDownIcon`（`size-3 text.muted`）作为可点开提示

### 6.11 Connection Handles

| 模式 | 形态 |
|---|---|
| Designer target（左） | 8px **实心圆**，填充 `border.strong`，2px `bg.canvas` 外描边；常驻；hover 缩放 1.25 + 2px ring `accent.primary`，去除 status-色 glow |
| Designer source（右） | 8px **环形圆**（透明填充 + 2px `border.strong` 描边 + 1px `bg.canvas` 内描边形成"环"）；常驻；hover 缩放 1.25 + 2px ring `accent.primary` |
| Viewer source/target | 默认隐藏（opacity 0）；行 hover 时显示为 6px 圆（target 实心 / source 环形，沿用 Designer 的形状语义），`border.strong` + `bg.canvas` 描边；不可拖拽 |

Designer 永远暴露 connection affordance（active workshop）；Viewer 只在 hover 给暗示（passive instrument）。

**形状区分而非颜色**：DESIGN.md 把 `status.*` 严格保留给 health / warning / danger / info 语义，连接锚点的"目标 / 源"不属其中。本 spec 因此放弃早期的"绿目标 / 红源"双态色，改用形状（实心 / 环形）区分两端，符合 a11y "状态不靠颜色"原则；hover 才用 `accent.primary` 点亮——这本就是 cobalt 在 DESIGN.md 中的合法用途（focus / 当前对象）。

### 6.12 空表（columns 数为 0）

- Header 正常显示
- 列区显示一行 `ui-xs` `text.soft` 居中文案：`"No columns yet"`（i18n key `erCanvas.node.empty`）
- Designer 仍渲染 "+ Add column" 行，作为唯一 CTA
- Viewer 不渲染 CTA

### 6.13 状态总览（速查）

| 状态 | 卡片边框 | ring | 列行 bg | rail | 图标 |
|---|---|---|---|---|---|
| 默认 (Designer) | `border.default` | — | transparent | strong / dashed default | `text.muted` |
| 默认 (Viewer) | `border.subtle` | — | transparent | strong / dashed default | `text.muted` |
| 节点 selected | `accent.primary` 1.5px | 4px / 2px `accent.primarySurface` | — | — | — |
| 行 hover | — | — | `interaction.hover` | `accent.primary` | `accent.primary` |
| 卡片 hover (Designer) | — | 4px `accent.primarySurface`（subtle） | — | — | — |
| disabled 列（未来扩展） | — | — | — | `interaction.disabled` 实/虚 | `interaction.disabled` |

## 7. 边 / 关系标签

### 7.1 几何

保留现有 `getSmoothStepPath` + 自连环 (`buildSelfRefPath`) + 跨线让位 (`computeCrossings`) 三套逻辑，只调样式。

| 参数 | 值 |
|---|---|
| BORDER_RADIUS | 8（保持） |
| JUMP_RADIUS | 6（保持） |

### 7.2 描边样式

| 状态 | stroke | 宽度 | dasharray |
|---|---|---|---|
| 默认（实 FK） | `border.strong` | 1.5px | — |
| 虚拟（comment_ref） | `accent.warn` | 1.5px | `4 3` |
| selected | `accent.primary` | 2px | 保持原 dasharray |
| node-dragging | 无变化 | 同上 | 同上 |

### 7.3 端点方向标记（新增）

target 端添加 6px 等腰三角箭头，用 SVG `<marker>` 实现，命名 `er-edge-arrow-default` / `er-edge-arrow-virtual` / `er-edge-arrow-selected`，由 `<path markerEnd="url(#er-edge-arrow-...)">` 引用。Source 端无 marker。

**实现位置**：marker `<defs>` 必须在 `ErCanvas.tsx` 内、ReactFlow 容器作为 children 注入一次（新增子组件 `ErEdgeMarkers`，作为 ReactFlow 内挂载点）。**不要**写在 `ErEdge.tsx` 里——那会让每条边都重复声明同一组 marker。

不引入 crow's foot，弥补当前完全无方向信号的缺陷。

### 7.4 跨线让位（jump-over）

保留现有圆弧 + `bg.canvas` mask circle 实现，描边一致跟随 §7.2。

### 7.5 关系标签 chip

| 模式 | 视觉 |
|---|---|
| Viewer / 不可编辑 | `pointer-events-none`，`radius.sm`，`border.subtle`，`bg.canvas`，`px-1.5 py-0.5`，`mono-sm` `text.muted` |
| Designer / 可编辑 | `pointer-events-auto`，`radius.sm`，`border.default`，`bg.canvas`，`shadow-xs`，`px-1 py-0.5`，含 `Select`（关系类型） + `Trash2` 删除按钮 |

虚拟标记：`data.kind === 'virtual'` 时标签后缀小胶囊 `virtual`：`h-4 px-1 radius-sm text-[10px] accent.warn` + `accent.warnSurface` 底，与 dashed 边形成两通道呼应。

关系类型映射：维持 `1:1 / 1:N / N:1 / N:N`，文案不变。

### 7.6 边 hover 提示（新增）

边自身 hover（鼠标在 path 8px 容差范围内）：

- 描边宽度 +0.5（默认 → 2px、selected → 2.5px）
- `prefers-reduced-motion` 下不变化

## 8. 空态（ErEmptyState）

四种 reason 对应 4 个图标 + 双行文案：

| reason | icon | title (`ui-md` 500w `text.strong`) | body (`ui-sm` `text.muted`) | CTA |
|---|---|---|---|---|
| `dialect_unsupported` | `BanIcon` | "Dialect not supported" | i18n 现有 `erCanvas.empty.unsupported` | 无 |
| `empty_selection` | `SquareMousePointerIcon` | "Nothing selected" | i18n 现有 `erCanvas.empty.selection` | 无 |
| `empty_designer` | `Database` | "Empty designer" | i18n 现有 `erCanvas.empty.designer` | + Add table（primary outline） |
| `oversized` | `ZoomOutIcon` | "Diagram too large" | i18n 现有 `erCanvas.empty.oversized` | 无 |

**布局**：

| 参数 | 值 |
|---|---|
| 容器 | flex h-full w-full center on `bg.canvas` |
| 内容容器 | max-w-md（448px） |
| 图标尺寸 | `size-8` `text.soft`，与文字之间 16px |
| title → body 间距 | 4px |
| body → CTA 间距 | 16px |

i18n：现有 4 个 `erCanvas.empty.<reason>` 保留作 body；新增 4 个 `erCanvas.empty.<reason>.title`。

## 9. 上下文菜单（ErTableContextMenu）

| 参数 | 值 |
|---|---|
| 容器 | min-w-44（176px）、`radius.md`（10px）、`border.subtle`、`bg.panel`、`shadow-md`、`p-1.5` |
| 项 | h-7、`radius.sm`、`px-2`、`gap-2`、`ui-xs` |
| 默认项 | text+icon `text.base`，hover `interaction.hover`，focus-visible inset ring `interaction.focusRing` |
| 危险项 | text+icon `status.danger`，hover `status.dangerSurface`，focus-visible inset ring |
| 项内图标 | `size-3.5`，跟字色 |
| 分隔条 | 危险项前 `h-px bg-border-subtle my-1` |

**条目**：

1. `PencilIcon` Rename
2. `PlusIcon` Add column
3. ── 分隔条 ──
4. `Trash2Icon` Delete table（danger）

**键盘**（新增）：

- `ArrowDown` / `ArrowUp`：在条目间循环聚焦
- `Enter`：触发当前项
- `Escape`：关闭菜单
- 第一项默认获得焦点；外点击仍关闭

## 10. 动效

| 元素 | 时长 | easing |
|---|---|---|
| 行 hover bg / rail / icon 升级 | `motion.fast`（120ms） | `easing.standard` |
| 卡片 selected ring 渐入 | `motion.normal`（180ms） | `easing.enter` |
| 边 selected 描边色变化 | `motion.normal` | `easing.standard` |
| 边 hover 宽度变化 | `motion.fast` | `easing.standard` |
| Connection handle hover scale + glow | 150ms（保留） | `easing.standard` |
| Designer 行 hover delete 渐显 | `motion.fast` | `easing.standard` |
| 上下文菜单出现 | `motion.fast` opacity-only | `easing.enter` |
| 空态切换 | 即时 | — |

`prefers-reduced-motion` 下：所有 transition 时长 → 0ms，保留最终态切换；handle hover scale 改为 box-shadow only。

## 11. 可达性

- **对比度**：所有文本 ≥ 4.5:1、关键图标 ≥ 3:1。当前 token 已达标，本次实施跑回归。
- **聚焦环**：每个交互元素有 focus-visible 显式 ring（按钮、Select、列名 input、删除按钮、菜单项、空态 CTA）。
- **aria-label**（保留 / 强化）：
  - 节点 `<section>`：`aria-label="Table ${name}"`
  - 模式提示图标：Designer `editable designer table` / Viewer `read-only inspector view`
  - 列 input：`aria-label="Column name ${name}"`
  - 类型 SelectTrigger：`aria-label="Type for ${columnName}"`
  - 删除列按钮：`aria-label="Delete column ${name}"`
  - 工具栏 ghost / primary 按钮：`aria-label` 同可见文字；icon-only 折叠态保留
  - 模式徽章：`role="status"` + `aria-live="off"`
  - 上下文菜单：`role="menu"` + `aria-orientation="vertical"`，项 `role="menuitem"`
- **键盘**：保留 `useErKeyboard`（auto layout / fit view / delete）；新增菜单方向键导航。
- **不依赖颜色**：rail（实/虚/无）+ 图标 + 字重三通道传 PK/FK；状态由 ring + bg + 字色多通道；NN 用排版；virtual 用 dashed + warn + 文字三通道。

## 12. 主题处理（双第一公民）

不写任何 light/dark 条件分支：

- 优先 Tailwind 语义类（`text-text-muted`、`bg-bg-canvas` 等，由项目 `@theme inline` 映射到 `var(--dt-*)`）；SVG `stroke` / `fill` / `<marker>` 等 Tailwind 难直达处可用 `var(--dt-*)` CSS 自定义属性
- `bg.canvas`、`accent.primary`、status 色等自动按主题切换
- Connection handle 的 `bg.canvas` 描边随主题
- 网格点 `border.subtle` 在 light / dark 下都柔和
- 节点层级在两主题下都靠 header (`bg.subtle`) 与 body (`bg.canvas`) 自然差异承载：light 下 subtle=`neutral.50` / canvas=`neutral.0`，dark 下 subtle=`neutral.800` / canvas=`neutral.900`，不依赖 `bg.panel`
- 画布 (`bg.canvas`) 与节点 body 在 dark 同为 `neutral.900`，因此节点边缘必须靠 `border.default` / `border.subtle` 把节点从画布"勾"出来——本 spec §6.1 已强制 1px 边框

实施期切换 light↔dark 各跑一遍工具栏 + 节点 + 边 + 空态 + 菜单的目视回归。

## 13. 文件级改动清单

仅范围说明，具体实施留给 plan。

| 文件 | 改动性质 | 要点 |
|---|---|---|
| `client/src/features/stage/components/er-canvas/ErToolbar.tsx` | 重写样式与结构 | 新增 ModeBadge 子组件；按 §5 重新分组；按钮统一走 §5.2 规格；Disabled tooltip 接 i18n |
| `client/src/features/stage/components/er-canvas/ErTableNode.tsx` | 重写视觉、新增结构 | 添加 left rail（3px）；NN 胶囊；hover-only delete；模式提示图标颜色契约修正；列前置图标按 §4；handle 尺寸 / 可见性按 §6.11；空表与 "+ Add column" 按 §6.9 / §6.12 |
| `client/src/features/stage/components/er-canvas/ErEdge.tsx` | 调整描边 + `markerEnd` 引用 | stroke 宽度按 §7.2；`markerEnd="url(#er-edge-arrow-...)"`（不在此文件声明 marker）；hover 宽度变化；virtual 后缀胶囊 |
| `client/src/features/stage/components/er-canvas/ErEdgeMarkers.tsx` | **新建** | 单文件导出一个 React 组件，输出 `<svg><defs><marker id="er-edge-arrow-default/virtual/selected">...</marker></defs></svg>`（或直接以 ReactFlow 提供的 `<svg>` 容器注入 `<defs>`）；颜色用 `var(--dt-border-strong)` / `var(--dt-accent-warn)` / `var(--dt-accent-primary)` |
| `client/src/features/stage/components/er-canvas/ErEmptyState.tsx` | 重写布局 | reason→icon 映射；标题 / 正文拆分；CTA 走 primary outline |
| `client/src/features/stage/components/er-canvas/ErTableContextMenu.tsx` | 加键盘导航 + 视觉 | 项内图标；分隔条；Up/Down/Enter/Esc；ref 焦点管理 |
| `client/src/features/stage/components/er-canvas/ErCanvas.tsx` | 网格 / hover ring 参数化 + 挂 marker | `mode === 'designer' ? 18 : 24` 网格 gap；ring 厚度按 mode；ReactFlow children 中挂 `<ErEdgeMarkers />` 一次；其他逻辑 0 改动 |
| `client/src/features/stage/components/er-designer-tab.tsx` | 0 改动 | 仅承载容器 |
| `client/src/features/stage/components/er-inspector-tab.tsx` | 0 改动 | 同上 |
| `client/src/i18n/<locale>.json`（en / zh-CN） | 新增键 | `erCanvas.toolbar.modeBadge.designer/viewer`、`erCanvas.empty.<reason>.title`、`erCanvas.node.empty`、`erCanvas.toolbar.disabledHint.bindFirst` |
| `client/src/features/stage/components/er-canvas/__tests__/*` | 扩充测试 | 见 §14 |

不在本次范围（明确 0 改动）：

- 后端 / OpenCode 协议、JSON Patch 路径、payload 类型
- 路由 / 注册器 / Tab type registry
- ER inspector 侧栏（如有） / Diff vs DB 下游展示 / Generate DDL 下游产物（按 ER 总体 spec Q10 走 query_editor Tab 灌入，不另开 Dialog）
- 持久化 / 同步

## 14. 测试策略

| 测试对象 | 类型 | 关键断言 |
|---|---|---|
| `ErTableNode` | vitest + RTL | PK rail 实色 / FK rail dashed / 普通行无 rail；行 hover → rail 与图标升级到 cobalt；NN 胶囊在 `nullable=false` 出现；Delete 按钮默认 opacity-0、`group-hover` 后 opacity-100；Designer "+ Add column" 行存在、Viewer 不存在；空表渲染 "No columns yet"；模式提示图标按 mode 切换 |
| `ErEdge` | vitest | 默认 stroke = `border.strong` 1.5px；virtual = `accent.warn` 1.5 dashed `4 3`；selected = `accent.primary` 2px；hover 时 +0.5；virtual 标签后缀 `virtual` 胶囊；marker `er-edge-arrow-*` 由 `markerEnd` 引用 |
| `ErToolbar` | vitest + RTL | Designer 模式：徽章 "Designer" + Pencil；G3 主操作 primary outline；Diff/DDL 在 `hasTarget=false` disabled；Dialect Select 联动；Viewer 模式：徽章 "Viewer" + Lock；Fork to Designer primary outline；Depth Select 联动 |
| `ErEmptyState` | vitest | 4 reason 各渲染对应 icon + title + body；`empty_designer` 显示 "+ Add table" CTA |
| `ErTableContextMenu` | vitest + RTL | 菜单项含图标；ArrowDown / ArrowUp 在 3 项间循环聚焦；Enter 触发 + 关闭；Escape 关闭；分隔条；danger token |
| `ErCanvas` | 现有维护 | 网格 gap 18 / 24 按 mode 切换；hover ring 厚度按 mode；模式互切不串色 |

**对比度回归**：实施 PR 阶段对所有 `text.*` 与 icon 颜色 + 背景组合跑 axe-core 或手动 `@adobe/leonardo-contrast-colors`，留 1 张对比表附 PR。

**视觉手测清单**（PR 描述）：

- light 主题 Designer / Viewer 各 1 张
- dark 主题 Designer / Viewer 各 1 张
- 表节点：默认 / hover / selected / 折叠 / 空表 各 1 张
- 边：默认 / virtual / selected / 自连环 / 跨线让位 各 1 张
- 空态：4 reason 各 1 张
- 上下文菜单：默认聚焦第一项、聚焦危险项 各 1 张

## 15. 验证门

实施 PR 必须通过：

1. `cd client && npx tsc --noEmit` —— 0 类型错误
2. `cd client && npm test -- --run er-canvas` —— 全绿
3. `cd client && npm test -- --run` —— 整体未回归
4. light + dark 主题手测截图（≥ 10 张）附 PR
5. axe-core 对比度报告附 PR（无 critical / serious 项）
6. 数据源类型兼容门：本次纯前端视觉重设，未触及 dialect / JDBC / SQL 路径，标 `N/A`

## 16. 与 client/DESIGN.md 的潜在分歧

- **PK rail 默认色**：早期提案是"PK = cobalt 实色 / FK = sky 实色"，与 DESIGN.md "cobalt 仅用于 focus / current object / primary action" 直接冲突。本 spec 改为"中性默认 (`border.strong` 实色 / `border.default` 虚线) + hover/selected 才升级为 `accent.primary`"，cobalt 严格保留给焦点态。
- **Connection handle 两态色**：早期提案是"target = `status.success` / source = `status.danger`"。与 DESIGN.md "status.* 仅用于 health / warning / danger / info" 冲突。本 spec 改为统一 `border.strong` 中性，靠**形状**（target 实心 / source 环形）区分两端，hover 才用 `accent.primary` ring 点亮——这是 cobalt 的合法 focus 用途。形状差异同时满足 a11y "状态不靠颜色"原则。
- **dark 主题层级**：早期提案是"Viewer 节点 dark 升至 `bg.panel`"，但 DESIGN.md 中 dark 的 `bg.canvas` 与 `bg.panel` 都是 `neutral.900`，没有差异。本 spec 改为"节点层级在两主题下都靠 header (`bg.subtle`) 与 body (`bg.canvas`) 自然差异承载，不依赖 `bg.panel`，并强制 1px 边框把节点从画布勾出"。

## 17. 后续工作（不在本 spec）

- ER inspector 侧栏视觉（如继续保留侧栏，需独立 spec）
- Generate DDL 落入 query_editor Tab 后该 Tab 内 SQL 视觉的细化（属 SQL Workbench spec 范畴，非 ER 范畴）
- Diff vs DB 下游展示形式（待与 SQL Workbench / Stage 协议对齐后单独决策）
- ER 节点上下文菜单的扩展项（如 "Duplicate table" / "Export DDL for this table"）
- 多选编辑（在 selected 多个节点 / 列时的批量操作 affordance）
