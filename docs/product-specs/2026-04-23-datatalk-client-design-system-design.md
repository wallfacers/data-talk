# DataTalk Client Design System Design

- **日期**：2026-04-23
- **状态**：draft
- **前置**：
  - [Client Rebuild (Tauri + Vite)](./2026-04-16-client-rebuild-tauri-vite-design.md)
  - [AI Message Code Window and Table Design](./2026-04-20-ai-message-code-window-and-table-design.md)
  - [Stage SQL Workbench Polish Design](./2026-04-22-stage-sql-workbench-polish-design.md)
  - [Workspace And Backend I18n Design](./2026-04-22-workspace-i18n-design.md)

## 1. 背景与目标

### 1.1 背景

当前 `client/` 已经具备可用的前端骨架：

- 左侧 `Sidebar` 承担会话与全局入口
- 中央 `SessionCanvas` 承担聊天与协作主流
- 右侧 `StageWindow` 承担 SQL / Artifact / Workbench
- 组件层以 `shadcn/ui + Tailwind v4` 为基础

但视觉体系仍停留在“可用的默认灰阶主题 + 局部打磨”的阶段，缺少一份**能同时约束 AI 与人工实现**的客户端设计契约，导致三个问题：

1. **品牌表达弱**：现有界面更像一组组件拼装，而不是有明确产品气质的工作台
2. **语义不稳**：`chat / stage / settings / data-grid` 对颜色、层级、密度和状态的理解还不完全统一
3. **落地效率低**：没有统一 `DESIGN.md` 时，AI 生成界面容易漂移，人工实现也容易继续写裸色值和局部特例

### 1.2 本轮目标

产出一份针对 `client/` 的正式设计系统 spec，作为后续 `client/DESIGN.md` 与前端实现的唯一真源，明确：

1. **产品视觉定位**：`AI-native data research workbench`
2. **主题策略**：亮色与暗色同权的 `dual-theme`
3. **核心结构**：`Chat + Workbench` 并列双核心，而非单列聊天页或单体 SQL IDE
4. **Token 体系**：primitive → semantic → interaction → component alias 四层
5. **组件语义**：按钮、输入、消息、Stage、表格、设置页、空状态的统一规则
6. **治理规则**：如何把这份契约稳定落到 `client/DESIGN.md`、`globals.css` 和现有 feature 代码

### 1.3 成功标准

完成后，DataTalk 客户端应当满足：

- 不再依赖 `shadcn/ui` 默认审美来决定最终视觉
- 任何新 UI 都能先从 `client/DESIGN.md` 找到明确 token 和页面模式
- 明暗主题切换不需要重写组件语义，只切 semantic token 映射
- 聊天与工作台两个主舞台共享一套视觉语言，但允许密度和强调重心不同

## 2. 非目标

本 spec 明确不包含以下内容：

1. **不在本轮做全量视觉重构**：本轮定义契约，不要求一次性重写所有页面
2. **不新增营销页风格体系**：DataTalk 是桌面研究工作台，不需要 landing-page 视觉语言
3. **不引入与现有技术栈冲突的新 UI 框架**：继续以 `shadcn/ui + Tailwind v4` 为基础
4. **不把 chat 或 stage 降为附属区域**：两者是并列核心
5. **不允许“赛博霓虹终端”或“大面积玻璃拟态”成为主风格**

## 3. 产品视觉定位

### 3.1 一句话定义

DataTalk 的客户端应该被设计成：

> **一台可协作、可推理、可操作的数据研究仪器**

这一定义同时约束品牌与界面：

- 它必须**专业可信**，不能像营销站点
- 它必须**AI 原生**，不能像传统 BI 面板简单加一个 chat 侧栏
- 它必须是**工作台**，不是社交聊天工具，也不是纯代码 IDE

### 3.2 目标气质

本产品的视觉气质固定为：

- `professional`
- `precise`
- `research-lab`
- `instrumental`
- `calm`
- `sharp`

不采用以下气质：

- `playful`
- `ornamental`
- `consumer-social`
- `cyberpunk`
- `retro-terminal`

### 3.3 结构认知

用户看到的不是“一个聊天框 + 一堆弹窗”，而是三个稳定层级：

1. `navigation skeleton`：Sidebar
2. `conversation lane`：Chat / Session
3. `instrument lane`：Stage / SQL / Artifact / Data Workbench

这三个层级共享一套 token，但视觉存在感依次增强：`skeleton < conversation < instrument`

## 4. 设计原则

### 4.1 Precision First

界面应像仪器，不像海报。层次依赖结构、边界、密度、对比，而不是依赖大面积装饰。

### 4.2 Dual-Core, One System

`Chat` 与 `Workbench` 是并列双核心，共用一套语义 token、字体和状态规则；差异来自信息密度和控件编排，而不是来自两套完全不同的设计语言。

### 4.3 Neutral Backbone, Focused Signal

中性灰阶承担 70% 以上的界面面积；品牌蓝只用于焦点、选中、主动作和当前对象；琥珀只用于提醒或高价值辅助动作；红绿只用于状态，不参与品牌表达。

### 4.4 Calm in Light, Crisp in Dark

亮色主题不是纯白办公软件，暗色主题也不是黑客终端。两套主题都应该支持长时间使用，并在明暗切换后保持语义稳定。

### 4.5 Dense but Breathable

信息密度应明显高于普通 SaaS，但不允许拥挤。默认采用紧凑工作台节奏，通过栅格、间距和容器层级维持呼吸感。

### 4.6 Motion as Confirmation

动效只负责确认状态变化、焦点切换、加载与流式输出，不承担装饰职责。

## 5. 主题策略与 Token 架构

### 5.1 主题策略

DataTalk 采用 `dual-theme`：

- `light`：明亮、纸面感、结构清晰
- `dark`：克制、锐利、仪器面板感

两个主题在语义上完全对等：

- 不存在“亮色是主稿、暗色是反相稿”
- 所有组件必须同时定义 light/dark 语义映射
- 新组件验收必须同时通过两套主题

### 5.2 Token 分层

采用四层 token：

1. `primitive`
   颜色、尺寸、半径、时长等原始值
2. `semantic`
   直接对应界面含义，如 `bg.panel`、`text.muted`
3. `interaction`
   对应 `hover / focus / active / selected / disabled / pending`
4. `component alias`
   给高频组件固定落点，如 `sidebar.bg`、`stage.chrome`

禁止在 feature 代码中跳过 semantic 层直接消费 primitive 色值。

### 5.3 Primitive Color Palette

`client/DESIGN.md` 中的颜色原语采用以下基线：

```yaml
primitives:
  neutral:
    0: "#FFFFFF"
    25: "#FCFDFE"
    50: "#F8FAFC"
    100: "#F1F5F9"
    200: "#E2E8F0"
    300: "#CBD5E1"
    400: "#94A3B8"
    500: "#64748B"
    600: "#475569"
    700: "#334155"
    800: "#1E293B"
    900: "#0F172A"
    950: "#020617"
  cobalt:
    50: "#EFF6FF"
    100: "#DBEAFE"
    200: "#BFDBFE"
    300: "#93C5FD"
    400: "#60A5FA"
    500: "#3B82F6"
    600: "#2563EB"
    700: "#1D4ED8"
    800: "#1E40AF"
    900: "#1E3A8A"
  amber:
    50: "#FFFBEB"
    100: "#FEF3C7"
    200: "#FDE68A"
    300: "#FCD34D"
    400: "#FBBF24"
    500: "#F59E0B"
    600: "#D97706"
    700: "#B45309"
    800: "#92400E"
  green:
    100: "#DCFCE7"
    500: "#22C55E"
    700: "#15803D"
  red:
    100: "#FEE2E2"
    500: "#EF4444"
    700: "#B91C1C"
  sky:
    100: "#E0F2FE"
    500: "#0EA5E9"
    700: "#0369A1"
```

颜色角色：

- `neutral`：骨架、文本、分层、表面
- `cobalt`：品牌焦点、选中、主行动
- `amber`：提醒、辅助高价值动作、异常趋势
- `green/red/sky`：状态与图表语义

### 5.4 Semantic Mapping

`client/DESIGN.md` 的语义层至少覆盖以下键：

```yaml
semantic:
  light:
    bg:
      app: "neutral.25"
      canvas: "neutral.0"
      panel: "neutral.0"
      subtle: "neutral.50"
      elevated: "neutral.0"
      overlay: "rgba(15, 23, 42, 0.40)"
    text:
      strong: "neutral.900"
      base: "neutral.800"
      muted: "neutral.600"
      soft: "neutral.500"
      inverse: "neutral.0"
    border:
      subtle: "neutral.200"
      default: "neutral.300"
      strong: "neutral.400"
    accent:
      primary: "cobalt.700"
      primaryHover: "cobalt.800"
      primarySurface: "cobalt.50"
      primaryBorder: "cobalt.200"
      warn: "amber.500"
      warnSurface: "amber.50"
    status:
      success: "green.500"
      successSurface: "green.100"
      warning: "amber.500"
      warningSurface: "amber.50"
      danger: "red.500"
      dangerSurface: "red.100"
      info: "sky.500"
      infoSurface: "sky.100"
  dark:
    bg:
      app: "neutral.950"
      canvas: "neutral.900"
      panel: "neutral.900"
      subtle: "neutral.800"
      elevated: "neutral.800"
      overlay: "rgba(2, 6, 23, 0.72)"
    text:
      strong: "neutral.25"
      base: "neutral.100"
      muted: "neutral.400"
      soft: "neutral.500"
      inverse: "neutral.950"
    border:
      subtle: "rgba(255, 255, 255, 0.08)"
      default: "rgba(255, 255, 255, 0.12)"
      strong: "rgba(255, 255, 255, 0.18)"
    accent:
      primary: "cobalt.400"
      primaryHover: "cobalt.300"
      primarySurface: "rgba(37, 99, 235, 0.18)"
      primaryBorder: "rgba(96, 165, 250, 0.32)"
      warn: "amber.400"
      warnSurface: "rgba(245, 158, 11, 0.14)"
    status:
      success: "green.500"
      successSurface: "rgba(34, 197, 94, 0.14)"
      warning: "amber.400"
      warningSurface: "rgba(245, 158, 11, 0.14)"
      danger: "red.500"
      dangerSurface: "rgba(239, 68, 68, 0.16)"
      info: "sky.500"
      infoSurface: "rgba(14, 165, 233, 0.16)"
```

### 5.5 Interaction Tokens

交互层必须稳定，不允许组件自行发明 hover/selected 色值：

```yaml
interaction:
  focusRing:
    light: "rgba(37, 99, 235, 0.35)"
    dark: "rgba(96, 165, 250, 0.38)"
  hover:
    light: "rgba(15, 23, 42, 0.04)"
    dark: "rgba(255, 255, 255, 0.06)"
  active:
    light: "rgba(15, 23, 42, 0.08)"
    dark: "rgba(255, 255, 255, 0.10)"
  selected:
    light: "cobalt.50"
    dark: "rgba(37, 99, 235, 0.18)"
  disabled:
    light: "rgba(15, 23, 42, 0.38)"
    dark: "rgba(255, 255, 255, 0.34)"
```

### 5.6 Component Alias Tokens

以下 alias 必须在 `client/DESIGN.md` 中出现，作为当前前端主路径的稳定接口：

```yaml
components:
  sidebar:
    bg: "bg.subtle"
    border: "border.subtle"
    activeBg: "interaction.selected"
    activeText: "text.strong"
  composer:
    bg: "bg.panel"
    border: "border.default"
    focus: "interaction.focusRing"
  message:
    userSurface: "bg.subtle"
    assistantSurface: "bg.canvas"
    toolSurface: "bg.panel"
    errorSurface: "status.dangerSurface"
    errorBorder: "status.danger"
  stage:
    chrome: "bg.subtle"
    surface: "bg.canvas"
    tabIdle: "text.muted"
    tabActive: "accent.primary"
    railBg: "bg.panel"
  table:
    headerBg: "bg.subtle"
    rowHover: "interaction.hover"
    rowSelected: "interaction.selected"
  chart:
    focus: "accent.primary"
    compare: "accent.warn"
    grid: "border.subtle"
```

### 5.7 Shape, Border, Elevation

DataTalk 的形体语言固定为“中等圆角 + 明确边界 + 克制阴影”：

- `radius.sm = 8px`
- `radius.md = 10px`
- `radius.lg = 14px`
- `radius.xl = 20px`

规则：

- 容器分层优先使用 `border + bg`，而不是大阴影
- 阴影只用于 `dialog / popover / sheet / 特殊高层级 stage 容器`
- 不允许将圆角推到“卡通圆润”或完全锐角

## 6. Typography、Spacing 与 Density

### 6.1 字体系统

本产品采用两套字体角色：

- `font.ui = Source Sans 3`
- `font.mono = JetBrains Mono`

落地规则：

- `font.heading` 不单独换家族，等同 `font.ui`
- 正文、导航、表单、按钮统一使用 `font.ui`
- SQL、代码、表格数字、技术元信息使用 `font.mono`
- 如果首轮实现尚未打包 Web Font，允许用系统 fallback stack 承接，但 `client/DESIGN.md` 中仍以这两套字体为正式目标

建议 fallback：

```yaml
typography:
  font:
    ui: "'Source Sans 3', 'Noto Sans SC', 'PingFang SC', sans-serif"
    mono: "'JetBrains Mono', 'SFMono-Regular', 'Cascadia Mono', monospace"
```

### 6.2 字号与行高

字体档位固定如下：

| Token | 尺寸 | 行高 | 用途 |
|------|------|------|------|
| `text.xs` | 12px | 16px | tag、辅助元信息、caption |
| `text.sm` | 13px | 18px | 次级正文、控件标签、table meta |
| `text.md` | 14px | 20px | 默认正文 |
| `text.lg` | 16px | 24px | 强调正文、小标题 |
| `text.xl` | 20px | 28px | 页面级标题 |
| `text.2xl` | 24px | 32px | 空状态或少数场景标题 |

字重只开放：

- `400 regular`
- `500 medium`
- `600 semibold`

规则：

- 默认正文使用 `text.md`
- 工作台默认控件优先使用 `text.sm` 或 `text.md`
- 不允许为了“更醒目”把 table、toolbar、tabs 普遍抬到 `16px`

### 6.3 Spacing Scale

使用 `4px` 基准栅格，只开放少数离散档位：

| Token | 值 |
|------|----|
| `space.1` | 4 |
| `space.2` | 8 |
| `space.3` | 12 |
| `space.4` | 16 |
| `space.5` | 20 |
| `space.6` | 24 |
| `space.8` | 32 |
| `space.10` | 40 |
| `space.12` | 48 |

规则：

- 组件内部以 `8 / 12 / 16` 为主
- 区块间距以 `16 / 24 / 32` 为主
- 不鼓励新代码出现 `14 / 18 / 22 / 26` 这类零散 spacing 值

### 6.4 Density Modes

设计系统定义三档密度：

- `compact`
- `comfortable`
- `focused`

落地用途：

- `compact`：sidebar、table、toolbar、tabs、metadata、设置导航
- `comfortable`：chat message、composer、dialog 主体、empty state
- `focused`：hero、关键确认、首次进入引导

默认控件尺寸：

- 按钮高度：`32 / 36`
- 输入高度：`36`
- tab 高度：`32`
- 列表行高：`32-36`

## 7. 布局与页面模式

### 7.1 App Shell

整体 Shell 固定为桌面工作台布局，不采用单列内容页模式：

- `sidebar`：稳定导航骨架，推荐宽度 `264px`
- `conversation lane`：中等阅读宽度，优先保证可读性，不追求铺满
- `instrument lane`：Stage / Artifact 工作表面，边界比 chat 更强

### 7.2 主页面模式

产品内页面模式固定为五类：

1. `Conversation Workspace`
   - 对应：首页主视图
   - 由聊天主列 + Workbench 并列组成
2. `Instrument Panel`
   - 对应：Stage / SQL / Artifact
   - 更强边界、更高密度、更稳定 chrome
3. `Resource Management`
   - 对应：数据源、模型、provider、会话列表
4. `Structured Form`
   - 对应：连接配置、确认流程、编辑表单
5. `Focused Modal Surface`
   - 对应：Dialog、Picker、Sheet、Alert

除这五类外，不再额外发明页面模板。

### 7.3 Empty State

空状态只承担三件事：

1. 解释当前为什么空
2. 指出下一步主动作
3. 不抢主工作流

规则：

- 不写大段宣传文案
- 不用大面积插画
- 每个空状态最多 1 个主动作、2 个次动作

### 7.4 Responsive Strategy

虽然产品是 Tauri 桌面应用，仍需显式定义窄宽度退化：

- 以 `1024 / 1280 / 1440+` 为主要断点
- 小于 `1024px` 时优先折叠 rail、sidebar、次级信息区
- 不允许把 `chat` 与 `stage` 同时压缩到不可读
- `dialog / settings / picker` 在窄宽度下必须可完整使用

## 8. 组件语义

### 8.1 Buttons

按钮固定为五类语义：

- `primary`
- `secondary`
- `ghost`
- `danger`
- `tonal`

规则：

- 一个局部容器中最多出现一个 `primary`
- `ghost` 不承担危险操作
- `danger` 不允许只靠图标表达，必须有明确文案或明确上下文
- Stage toolbar 以 `ghost / tonal` 为主，不应出现一排高饱和主按钮

### 8.2 Inputs 与 Composer

输入组件统一使用：

- 实体底色
- 明确边框
- 清晰 focus ring

`composer` 是特种输入，不等同普通 `textarea`：

- 它必须拥有更高层级和组合能力
- 但仍属于当前页面，不应被设计成漂浮插件
- `model / datasource / mode switch / send / stop` 共同组成一个工作环

### 8.3 Sidebar

Sidebar 是导航骨架，不是主舞台。

规则：

- 整体存在感低于 chat 和 stage
- 通过 active row、hover、icon rhythm 建立秩序
- “新建会话”允许成为侧栏内唯一明确强调项
- 收起状态必须保留可用操作，而不是单纯隐藏文字

### 8.4 Chat Messages

聊天区是协作记录，不是 IM 产品。

规则：

- `user` 与 `assistant` 需要可区分，但不使用两套强彩色大气泡
- assistant 输出更接近“结果流 / 文档流”
- `reasoning / tool / error / sql / markdown table` 必须有自己的语义样式
- reasoning 默认不抢正文优先级

### 8.5 Stage / Workbench

Stage 是完整工作表面，而不是聊天附属面板。

其结构固定为：

- `chrome`
- `tooling`
- `work surface`
- `support rail`

规则：

- active tab、running query、error result、selected row 必须一眼可分
- 优先使用边界、背景层与局部 accent 建立秩序
- 不使用夸张阴影或多色卡片堆叠

### 8.6 Tables 与 Data Grid

表格是核心能力，不允许只套默认样式。

规则：

- header 与 body 必须层次稳定
- hover 轻，selected 明确
- 数字列与技术元信息使用 `font.mono`
- 默认优先扫描效率，而不是装饰性

### 8.7 Settings 与表单

设置页仍属于同一工作台系统，不是孤立后台页。

规则：

- 设置导航与内容区分层明确
- 线性清晰优先于大卡片堆叠
- 推荐项、危险项、系统状态项必须有稳定语义
- 不允许把占位文案当作字段 label 使用

## 9. 图表与数据可视化

图表遵循“中性上下文，颜色表达含义”：

- `brand blue`：当前关注对象
- `amber`：比较对象、异常提醒、次级强调
- `green/red`：增长下降、成功失败、健康风险
- `neutral`：背景序列、历史序列、网格与坐标轴

必须存在以下图表 token：

```yaml
chart:
  focus: "cobalt.600"
  neutral1: "neutral.500"
  neutral2: "neutral.400"
  compare: "amber.500"
  success: "green.500"
  danger: "red.500"
  grid:
    light: "neutral.200"
    dark: "rgba(255, 255, 255, 0.08)"
  axis:
    light: "neutral.500"
    dark: "neutral.400"
```

规则：

- 一张图只允许一个主强调对象
- tooltip 像仪器读数，不像营销浮卡
- chart 与 table 在同一页时必须共享同一语义体系

## 10. Motion 与 Accessibility

### 10.1 Motion

动效 token 固定为：

```yaml
motion:
  fast: "120ms"
  normal: "180ms"
  slow: "240ms"
  easing:
    standard: "cubic-bezier(0.2, 0, 0, 1)"
    enter: "cubic-bezier(0.16, 1, 0.3, 1)"
    exit: "cubic-bezier(0.3, 0, 0.8, 0.15)"
```

允许使用动画的场景：

- sidebar 收放
- dialog / sheet / popover 进出场
- tab 高亮切换
- composer / stage 面板切换
- skeleton、streaming、text reveal、loading

禁止：

- hover 大幅缩放
- 卡片悬浮上跳
- 彩色脉冲光晕
- 持续性背景动画

### 10.2 Accessibility

可访问性要求是硬约束：

- 正文对比度 `>= 4.5:1`
- 大文本与关键 icon 对比度 `>= 3:1`
- light/dark 下 focus ring 都必须清晰
- 状态不能只靠颜色表达
- keyboard 必须覆盖 `sidebar / tabs / dialogs / menus / composer`
- `prefers-reduced-motion` 必须生效
- icon-only 按钮必须提供可访问名称
- SQL、代码、数字默认使用等宽数字或 `tabular` 风格

## 11. `client/DESIGN.md` 契约形态

### 11.1 文件位置

正式设计契约文件固定放在：

`client/DESIGN.md`

### 11.2 顶层结构

该文件应遵循 `design.md` 风格，包含：

1. **YAML front matter**
   - `name`
   - `product`
   - `themes`
   - `primitives`
   - `semantic`
   - `interaction`
   - `typography`
   - `spacing`
   - `radius`
   - `motion`
   - `components`
2. **Markdown sections**
   - `Overview`
   - `Principles`
   - `Theme Semantics`
   - `Layout Modes`
   - `Component Rules`
   - `Data Visualization`
   - `Accessibility`
   - `Do / Don't`

### 11.3 使用规则

后续所有前端设计和实现都遵循：

1. 写 UI 前先读取 `client/DESIGN.md`
2. 新组件优先映射已有 semantic token
3. 映射不了时，先补 `client/DESIGN.md`，再补实现
4. feature 文件禁止直接写裸色值，除图表 palette 注册等极少数例外

## 12. 实施触点与分阶段落地

### 12.1 首轮必改文件

第一轮实现应至少覆盖：

- `client/DESIGN.md`：新增设计契约文件
- `client/src/styles/globals.css`：改造成 semantic token 映射承载层
- `client/src/components/ui/button.tsx`
- `client/src/components/ui/input-group.tsx`
- `client/src/components/ui/table.tsx`
- `client/src/components/ui/sidebar.tsx`
- `client/src/features/session/prompt-composer.tsx`
- `client/src/features/workspace/components/app-sidebar.tsx`
- `client/src/features/stage/components/stage-window.tsx`
- `client/src/features/stage/components/stage-workbench-empty-state.tsx`

### 12.2 分阶段落地顺序

建议实施顺序：

1. `Contract Layer`
   - 写 `client/DESIGN.md`
   - 整理 `globals.css` semantic token
2. `Primitive Alignment`
   - 对齐 `button / input / table / sidebar` 等基础组件
3. `Dual-Core Surfaces`
   - 对齐 `chat / composer / stage`
4. `Secondary Surfaces`
   - 对齐 `settings / dialogs / charts / empty states`

## 13. 验收标准

实现本 spec 时，PR 级完成标准至少包括：

- `client/DESIGN.md` 已存在且被索引为唯一前端设计契约
- 亮色和暗色主题都可用
- `chat / stage / settings / sidebar` 共享统一 token 语义
- 无新增裸色污染
- `hover / focus / active / disabled / pending / error` 状态完整
- keyboard/focus 行为完整
- `prefers-reduced-motion` 已覆盖主要动效

## 14. 风险与约束

### 14.1 字体引入风险

`Source Sans 3` 与 `JetBrains Mono` 的引入会改变当前文本宽度与换行。实施时应先建立 fallback，再逐步替换，不允许一次性全量替换导致布局突变。

### 14.2 现有 shadcn token 兼容性

当前 `globals.css` 以 `--background / --foreground / --primary` 为主。实施时必须先建立“旧 token → 新 semantic token”的映射层，避免一次性打碎现有组件。

### 14.3 双主题偏移风险

如果只在亮色主题做视觉校准，暗色会重新滑回默认 shadcn 风格。实施时必须以语义 token 为中心，同时验证 light/dark。

## 15. 结论

DataTalk 客户端的正确方向不是通用 SaaS，也不是单体 IDE，而是一套以 `Chat + Workbench` 为双核心的 `AI-native data research workbench`。本 spec 将其收敛为可执行设计契约，后续以 `client/DESIGN.md` 为正式落点，并据此逐步校准基础组件和关键工作台表面。
