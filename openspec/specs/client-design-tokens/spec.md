# client-design-tokens Specification

## Purpose

定义 DataTalk 前端设计 token 体系（primitives → semantic → component aliases）的语义契约：light / dark 两个主题各自有完整、自洽、可访问性达标的颜色、文字、边框、交互、状态 token 集合；业务组件代码不允许直接使用原色 hex / oklch 字面量。

本 spec 在归档时作为 capability `client-design-tokens` 的基线落入 `openspec/specs/client-design-tokens/`。当前 `openspec/specs/` 下无 design-token 相关 capability，本变更新增之。

## Added Requirements

### Requirement: Light 主题 SHALL 使用暖色中性骨架（warm-neutral spine）

`client/DESIGN.md` 的 `primitives.neutral.*` 与 `semantic.light.*` token 集合 SHALL 使用以 manus.im `#34322D` / `#F8F8F7` 体系为参考反推的暖灰族（hue ≈ 80° in OKLCH），且 `client/src/styles/globals.css :root` 内的 `--dt-bg-*` / `--dt-text-*` / `--dt-border-*` / `--dt-interaction-*` SHALL 与该 token 集合一致。

#### Scenario: body 背景与文字在 light 主题下呈现暖色基色

- **GIVEN** 前端构建产物已在 chromium 中加载，`<html>` 元素 `classList` 不含 `dark`
- **WHEN** `getComputedStyle(document.body)` 读取背景与文字色
- **THEN** `backgroundColor` SHALL 等于 `rgb(248, 248, 247)`（即 `#F8F8F7`，对应 OKLCH `oklch(0.97 0.004 80)`）
- **AND** `color` SHALL 等于 `rgb(52, 50, 45)`（即 `#34322D`，对应 OKLCH `oklch(0.29 0.008 80)`）

#### Scenario: bg.canvas 与 bg.app 之间存在视觉亮度梯度

- **GIVEN** light 主题生效
- **WHEN** 检查任意业务卡片容器（消息卡片、Stage tab 内容、composer 输入框等使用 `bg-bg-canvas` / `bg-bg-panel` 类名的元素）的背景色
- **THEN** 其 `backgroundColor` SHALL 等于 `rgb(255, 255, 255)`（真白）
- **AND** 与 `document.body` 背景 `rgb(248, 248, 247)` 形成 ΔL ≈ 2–3%（可被肉眼分辨的"卡片漂浮"层级）

#### Scenario: bg.subtle 比 bg.app 更深一档，用于 sidebar 与分组背景

- **GIVEN** light 主题生效
- **WHEN** 检查 sidebar 容器（`bg-bg-subtle` 类名）的背景色
- **THEN** 其 `backgroundColor` SHALL 等于 `rgb(241, 241, 239)`（即 `#F1F1EF`，对应 OKLCH `oklch(0.945 0.005 80)`）
- **AND** 与 `bg.app` 之间形成 ΔL ≈ 3% 的可辨梯度

### Requirement: Light 主题边框与交互覆层 SHALL 使用 α-warm-dark 透明色

`semantic.light.border.*` 与 `interaction.*.light`（hover / active / disabled）SHALL 使用 `rgba(55, 53, 47, α)` 形式的透明覆层，**不允许**使用固定中性灰 hex 作为边框。

#### Scenario: border.subtle 在白底卡片上呈现极细描边

- **GIVEN** light 主题生效
- **WHEN** 检查任意 `border-border-subtle` 类名元素的 `borderColor`
- **THEN** 计算后的 `borderColor` SHALL 满足 `rgba(55, 53, 47, 0.06)` 的语义（实际呈现近似 `rgb(240, 240, 239)` over `#FFFFFF`，`rgb(234, 234, 232)` over `#F8F8F7`）
- **AND** 边框在 `bg.canvas`（`#FFFFFF`）与 `bg.app`（`#F8F8F7`）两种底色上 SHALL 自然过渡，无"贴一条冷灰线"的撕裂感

#### Scenario: hover 覆层与文字同 hue

- **GIVEN** light 主题，鼠标悬停在可点击元素上
- **WHEN** 元素应用 `interaction.hover.light` 覆层
- **THEN** 覆层色 SHALL 满足 `rgba(55, 53, 47, 0.04)`（即 `oklch(0.29 0.008 80 / 0.04)`）的语义
- **AND** 覆层 hue（80°）SHALL 与 `text.strong` hue 一致，整套 light 主题视觉同源

### Requirement: 文字三档（strong / base / muted / soft）SHALL 通过 4.5:1 body / 3:1 large 文本对比度

`semantic.light.text.strong` / `text.base` / `text.muted` 在 `bg.app` 与 `bg.canvas` 任一底色上 SHALL 满足 WCAG AA body 文本对比度 ≥ 4.5:1；`text.soft` 仅用于 large text、icon、metadata 等非 body 内容，SHALL 满足 ≥ 3:1。

#### Scenario: text.strong 在 bg.app 上对比度

- **GIVEN** light 主题，`text.strong` = `#34322D`、`bg.app` = `#F8F8F7`
- **WHEN** 按 WCAG 公式计算对比度
- **THEN** 对比度 SHALL ≥ 13:1（远超 4.5:1 body 阈值）

#### Scenario: text.muted 在 bg.app 上对比度

- **GIVEN** `text.muted` = `#5E5E5B`、`bg.app` = `#F8F8F7`
- **WHEN** 计算对比度
- **THEN** SHALL ≥ 6:1（通过 AA body 4.5:1 阈值）

#### Scenario: text.soft 不可用于 body 文本

- **GIVEN** `text.soft` = `#858481`、`bg.app` = `#F8F8F7`
- **WHEN** 计算对比度
- **THEN** SHALL 约为 3.4:1（仅满足 large text / icon 3:1 阈值）
- **AND** 业务组件 SHALL NOT 把 `text-text-soft` 用于 body 文本类内容

### Requirement: 强调色 SHALL 保留 cobalt 系，不切到 manus.im 的 azure

`semantic.light.accent.primary` / `semantic.dark.accent.primary` SHALL 继续使用 cobalt 蓝（`#1D4ED8` 系 / `#60A5FA` 系），**不切**到 manus.im 的 azure `#0081F2`。cobalt 是 DataTalk 的品牌信号色，本变更对它不动。

#### Scenario: light 主题主按钮使用 cobalt accent

- **GIVEN** light 主题
- **WHEN** 检查任意 primary action 按钮（如 "Send"、"Execute SQL"、Stage focus 选中态）的背景色或文字色
- **THEN** 其颜色 SHALL 派生自 `var(--dt-accent-primary)`
- **AND** 该变量的 OKLCH SHALL 约为 `oklch(0.53 0.2 262)`（cobalt 700 / `#1D4ED8` 派生）
- **AND** SHALL NOT 等于 azure `oklch(0.65 0.18 240)` 范围（即 `#0081F2` 派生）

### Requirement: Dark 主题 token SHALL 不受本变更影响

`semantic.dark.*` token 集合 SHALL 与本变更前完全一致；`client/src/styles/globals.css` 的 `.dark { ... }` 块 SHALL 不被修改。

#### Scenario: 切换到 dark 主题后视觉与变更前一致

- **GIVEN** 变更前已记录 dark 主题下 `body` 背景为 `rgb(...)`（具体值见 `globals.css .dark { --dt-bg-app: oklch(0.16 0.008 255); }` 派生）
- **WHEN** 应用本变更并切换 `<html>` 至 `.dark`
- **THEN** `getComputedStyle(document.body).backgroundColor` SHALL 与变更前的 dark 值一致（diff = 0）
- **AND** dark 主题所有 `--dt-*` 变量 SHALL 与变更前 byte-identical

### Requirement: 业务组件代码 SHALL NOT 直接使用原色 hex 字面量

`client/src/features/` 与 `client/src/components/ui/` 下的代码 SHALL 通过语义类名（`bg-bg-*` / `text-text-*` / `border-border-*` / `bg-accent-primary*` / `bg-status-*` 等）使用颜色，**不允许** 在 inline style、`className` 字符串、`tailwind.config` 派生工具类外，直接写 `#RRGGBB` 或 `oklch(...)` / `rgba(...)` 字面量作为最终颜色值。

> 例外：测试文件（`*.test.tsx` / `*.spec.ts`）可以包含 hex 字面量用于断言；`DESIGN.md` / `globals.css` 是 token 的"源头"文件，自然包含 hex。

#### Scenario: grep 不到原色 hex 直引

- **GIVEN** 项目已应用本变更
- **WHEN** 执行 `grep -rE "#[0-9A-Fa-f]{6}" client/src/features client/src/components/ui --exclude="*.test.*" --exclude="*.spec.*"`
- **THEN** 输出 SHALL 不含将 hex 作为 `style={{ color: '#XXXXXX' }}` 或 `style="background: #XXXXXX"` 用法的命中
- **AND** 仅允许的命中是 SVG `fill` / `stroke` 属性、Recharts 配置内部需要 hex 的 prop —— 这类需在 grep 后人工分类，不作为 spec 拒绝条件

### Requirement: 主题切换 SHALL 是纯 CSS cascade，无运行时重渲

切换 light ↔ dark 主题（通过 `<html>` 的 `.dark` 类增删）SHALL 立即生效，**不依赖** React 组件 re-render、JavaScript token 重新解析或任何异步流程。

#### Scenario: 主题切换无 flicker、无 JS 调用

- **GIVEN** 应用已加载，light 主题生效
- **WHEN** `document.documentElement.classList.add('dark')` 在主线程同一 tick 内执行
- **THEN** 所有 `--dt-*` 变量 SHALL 立即切换到 `.dark { ... }` 块定义的值
- **AND** 所有依赖 `var(--dt-*)` 的元素背景、文字、边框 SHALL 立刻更新
- **AND** SHALL NOT 触发任何 React state 更新或组件重渲
- **AND** SHALL NOT 出现可被肉眼察觉的 flicker / 残色
