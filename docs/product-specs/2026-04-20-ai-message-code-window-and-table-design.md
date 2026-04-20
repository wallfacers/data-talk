# AI Message Code Window and Table Design

## Background

当前 AI 消息渲染已经具备 Markdown、流式文本、SQL 动作按钮和基础代码复制能力，但“需要展示结构化内容的区域”仍然是分散实现：

- 普通 fenced code 只是 `pre` 外包一个简单容器，视觉上离用户给出的“桌面窗体感”差距很大
- SQL 代码块有独立 header，但与普通代码块不是同一套 surface 语言
- reasoning、tool、error 等 AI 输出区域与代码块之间缺少统一的视觉边界
- Markdown 表格仍然依赖默认 HTML 输出，对 AI 常见的 pipe table 轻微格式噪声不够稳健，最终视觉也不够统一

这次设计的目标不是重写整套聊天渲染器，而是在现有 `markdown.tsx -> DOMPurify -> morphdom` 链路上，把代码块和 Markdown 表格都收口成可复用的增强 surface。

## Goal

在所有 AI 输出里建立统一的“结构化内容显示”体验：

- 所有代码块统一为带顶部 chrome 的浅色窗体式 surface
- 深色主题下代码窗仍保持亮面窗体，不退回常见黑底代码框
- SQL 代码块保留语义动作，但并入同一套 header 体系
- Markdown 表格增加定向规范化、统一容器装饰与 token 驱动的视觉皮肤
- 保持现有 Markdown 流式渲染链路，不引入第二套消息渲染系统

## Non-Goals

- 不整体重做 assistant 普通文本气泡或 turn 布局
- 不把所有 tool / reasoning / error 容器都强行做成与代码块完全相同的大卡片
- 不做通用 Markdown 重写器；只做代码块和表格的定向增强
- 不引入新的主题系统；所有新增视觉变量都基于现有 design token

## Design

### 1. Code Window Surface

所有 AI 输出中的代码展示统一采用“桌面窗体感”设计：

- 外层为浅色浮层窗体，拥有更柔和的大圆角、细描边和轻阴影
- 顶部固定 chrome bar，左侧显示语言或场景标签，右侧显示复制与动作按钮
- 正文区使用高可读的 `font-mono`，背景保持纸面感，不使用纯黑或纯白极端底色
- 长代码块仅正文区横向滚动，header 保持稳定，不跟随滚动
- 按钮默认低干扰，hover 整个代码窗时再增强对比

### 2. Theme Strategy

代码块视觉不按页面主题简单切换浅深两套，而是保持统一的亮面窗体策略：

- 浅色主题：偏暖白底、深石墨文字、轻灰蓝边线
- 深色主题：页面继续走暗色，但代码窗仍是浅色亮面 surface，只提升边线、标签和按钮的对比度
- inline code 只保留同色系药丸样式，不升级成完整窗体

这样能在不同主题下都保留“窗体感”，同时避免深色模式出现发灰、脏、阅读疲劳的问题。

### 3. Rendering Structure

代码块增强继续收口在现有 Markdown 链路中，而不是分散到消息组件里：

- `client/src/features/chat/components/markdown/markdown.tsx`
  继续负责把 `pre` 包装成统一代码窗体，并补齐 header 结构
- `client/src/features/chat/components/markdown/markdown.css`
  承接代码窗体的圆角、描边、阴影、顶部 bar、正文区、inline code、hover/focus 状态
- `client/src/features/chat/components/markdown/sql-code-block.ts`
  只保留 SQL 类型、风险和动作语义，不再自带独立皮肤
- `client/src/features/chat/components/turn/reasoning-part.tsx`
  调整 reasoning 内容区的边距与层级，让内部 Markdown 代码块融入统一 code window 语言
- `client/src/features/chat/components/generic-tool-card.tsx` 及相关 tool renderer
  非代码内容只做轻量协调；一旦出现代码展示，则复用统一 code window surface

### 4. Markdown Table Rendering Structure

Markdown 表格的统一渲染同样收口在 `client/src/features/chat/components/markdown/markdown.tsx` 链路里，让所有走 `Markdown` / `PacedMarkdown` 的位置自动获得一致体验。

分三层处理：

1. 输入规范化
   在 `marked.parse()` 之前增加一个窄范围的表格预处理，只修正明确长得像 Markdown pipe table 的连续块，例如表头分隔行前后多余空行、对齐分隔行附近的轻微格式噪声。该逻辑不碰普通段落、代码块、引用块。
2. 渲染后装饰
   保留 `marked + DOMPurify + morphdom` 现有链路，在 HTML 落地后增加 `decorateTables()`，给 `<table>` 外包一层滚动容器并打上统一的 `data-component` / `data-slot` 标记，让样式层稳定命中。
3. 统一视觉皮肤
   在 `client/src/features/chat/components/markdown/markdown.css` 增加一套遵守现有 token 的“聊天增强版表格”样式：
   - 外层有卡片感，但仍使用 `--border`、`--background`、`--muted`、`--foreground`
   - 表头背景更明显
   - 单元格边框、hover、间距统一
   - 横向溢出可滚动
   - 深色主题自动跟随 token，不写死浅色值

### 5. Compatibility Boundaries

为了保证稳定性，这次不会做通用 Markdown 重写器，只做代码块和表格的定向增强。

表格增强的边界如下：

- 不处理代码块里的 `|` 文本
  代码围栏和行内代码保持原样，避免 SQL、日志或 shell 输出被误判为表格
- 不强行把普通文本改成表格
  只有检测到“表头行 + 分隔行 + 至少一行数据”的连续块，才进入规范化
- 流式输出期间允许渐进降级
  `PacedMarkdown` 在文本未完整到达前，表格块可以暂时按普通文本渲染；结构完整后再切换成正式表格，优先稳定而不是半成品 `<table>` 抖动
- 解析不明确时直接回退现状
  如果规范化逻辑判断不明确，直接交回现有 `marked` 路径，不因为增强逻辑导致整段消息损坏
- 深色主题不维护第二套 CSS
  表格样式完全跟随现有主题 token

### 6. Interaction Details

- 复制按钮常驻在代码窗 header，但默认低对比；hover 整个代码窗时才显著
- 语言标签优先展示 fenced code language；识别不到时显示弱化的 `Text` 或直接隐藏
- SQL 块继续保留“执行 / 解释”能力，但动作按钮进入统一 header
- 流式更新保持当前 `morphdom` 策略，避免代码窗 header 在 streaming 过程中频繁重建

## Files

- Modify: `client/src/features/chat/components/markdown/markdown.tsx`
- Modify: `client/src/features/chat/components/markdown/markdown.css`
- Modify: `client/src/features/chat/components/markdown/sql-code-block.ts`
- Modify: `client/src/features/chat/components/turn/reasoning-part.tsx`
- Modify: `client/src/features/chat/components/generic-tool-card.tsx`
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`
- Add or Modify: `client/src/features/chat/components/markdown/__tests__/markdown-stream.test.ts`
- Add or Modify: 表格规范化相关单测文件（与 `markdown.tsx` 同目录）

## Testing

至少补齐以下验证：

- 普通 fenced code 被包装成统一 code window
- SQL 代码块仍保留动作按钮，并与统一 header 共存
- copy 按钮交互不回归
- reasoning 中的 Markdown 代码块继续正常渲染
- 标准 pipe table 能渲染成 `<table>`，并被包进统一滚动容器
- 常见 AI 输出的轻微表格格式噪声可被规范化
- 代码块、普通段落和非表格文本不会被表格规范化误伤

执行验证：

- `cd client && npx vitest run client/src/features/chat/components/markdown`
- `cd client && npx tsc --noEmit`

## Acceptance Criteria

1. assistant 普通 Markdown、reasoning 中的代码块和 SQL 代码块都呈现统一的浅色窗体式 surface
2. 深色主题下代码块仍保持亮面窗体视觉，而不是退回黑底代码框
3. SQL 动作按钮保留且融入统一 code window header
4. Markdown 表格拥有统一容器、表头层级、边框与滚动行为
5. AI 常见的轻微 pipe table 格式噪声可以被修正为有效表格
6. 流式输出、代码复制和现有 Markdown 渲染稳定性不回归
