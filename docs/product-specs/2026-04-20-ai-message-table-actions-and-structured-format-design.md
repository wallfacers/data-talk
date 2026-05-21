# AI Message Table Actions and Structured Format Design

## Background

当前 AI 消息渲染已经具备两项关键基础能力：

- Markdown pipe table 能被稳定规范化并渲染成统一表格容器
- 代码块已经具备 header + action button 的直接操作体验

但表格仍停留在“只可看”的阶段，缺少与 DataTalk 数据协作定位匹配的直接操作能力。对于数据库助手场景，AI 输出表格往往不是最终消费形态，而是会被继续复制到聊天、文档、Excel、Sheets、脚本或下游系统中。仅支持视觉展示不够，必须补齐消息内结构化数据的复制与导出能力。

这次设计聚焦 Markdown 表格，不重写整套消息渲染系统，而是在现有 `marked -> DOMPurify -> morphdom -> decorateTables()` 链路上增加表格级动作层，并顺手整理后续结构化格式支持路线。

## Goal

- 为所有走统一 Markdown 渲染链路的表格增加直接可见、可操作的复制/导出能力
- 首期支持 `复制表格`、`复制 CSV`、`复制 TSV`、`复制 Markdown`、`复制 JSON`、`下载 CSV`
- 保持 assistant 普通消息、reasoning、tool 卡片中的 Markdown 表格交互一致
- 延续当前代码块的“结构化内容可直接操作”视觉语言
- 在不重构渲染器的前提下，为后续结构化格式扩展预留最小可复用模型

## Non-Goals

- 不把消息内 Markdown 表格升级为完整 data-grid
- 不做筛选、排序、冻结列、单元格选区复制等交互式表格能力
- 不在首期支持 `XLSX`、`PDF`、图片导出
- 不把这次工作扩张为通用“结构化块动作框架”重构
- 不尝试从原始 Markdown 恢复富语义导出；首期统一以渲染后的表格文本为准

## Options Considered

### 1. DOM-first 表格增强

在现有 `decorateTables()` 阶段为渲染后的 `<table>` 添加表格卡片、工具栏与动作事件；导出时从最终 DOM 提取表头、行和单元格值。

- 优点：改动最小，天然覆盖所有走统一 Markdown 渲染链路的场景
- 缺点：数据语义来自渲染后 DOM，不是 Markdown AST

### 2. Parse-first 结构化渲染

在 Markdown 解析前后提取表格结构，建立内部 `TableModel`，再改由专门渲染器落地。

- 优点：长期结构更干净
- 缺点：明显侵入现有流式渲染链路，首期成本与风险过高

### 3. 通用结构化块动作框架

先抽象“块级 header + actions”平台，表格只是第一类接入者。

- 优点：长期最统一
- 缺点：超出当前问题范围，容易把一次表格增强做成平台化重构

## Decision

首期采用 `DOM-first`，但补一个内部可复用的轻量 `TableModel` / serializer 层。

对外保持现有渲染链路不变：

- `marked.parse()`
- `DOMPurify`
- `morphdom`
- `decorateTables()`

对内避免把导出逻辑写成零散按钮脚本：

- 从 `<table>` DOM 提取统一 `TableModel`
- 所有复制/下载格式都从同一个 serializer 集生成
- 为后续键值表、JSON/YAML 块扩展保留小而清晰的接口

## Design

### 1. Scope Boundary

这次增强覆盖所有走 `client/src/features/chat/components/markdown/markdown.tsx` 的 Markdown 表格，包括：

- assistant 普通消息
- reasoning 内部 Markdown
- tool 卡片里复用 Markdown 的内容

不单独为某一类消息做分叉逻辑。只要最终产出 `<table>`，就获得同一套动作能力。

### 2. Interaction Model

每个表格升级为“表格卡片 + 轻量动作栏”：

- 表格右上角常驻轻量工具栏
- 默认直接展示两个高频动作：`复制表格`、`CSV`
- 提供一个 `更多` 菜单，承接 `TSV`、`Markdown`、`JSON`、`下载 CSV`
- 工具栏默认可见，但保持低对比；hover 整个表格卡片时增强视觉反馈
- 小屏场景可压缩为 `复制` + `更多`，避免工具栏挤压表头与可视区域

推荐动作排序：

1. `复制表格`
2. `CSV`
3. `更多`

`更多` 菜单内部顺序：

1. `TSV`
2. `Markdown`
3. `JSON`
4. 分隔线
5. `下载 CSV`

### 3. DOM Structure

保留当前 `decorateTables()` 增强方式，不把表格改造成 React 子树重渲染。

建议 DOM 结构：

- 外层：`data-component="markdown-table"`
- 顶部栏：`data-slot="markdown-table-bar"`
- 弱标签：`data-slot="markdown-table-label"`
- 动作区：`data-slot="markdown-table-actions"`
- 更多菜单触发器：`data-slot="markdown-table-more"`
- 滚动区：`data-slot="markdown-table-scroll"`

这样能与代码块现有的 `markdown-code-bar` / `markdown-code-actions` 保持一致的结构语言，同时保证 `decorateTables()` 继续幂等。

### 4. TableModel

所有导出动作都先从最终表格 DOM 提取一个统一的轻量数据模型：

- `headers: string[]`
- `rows: string[][]`
- `sourceHtml: string`
- `columnCount: number`

提取规则：

- 优先读取 `thead > th` 作为表头
- `tbody > tr` 作为数据行
- 单元格取用户可见文本，不取原始 Markdown
- 行内加粗、链接、inline code 等都降级为纯文本值
- 去掉首尾空白，但不激进压缩内部空白
- 列数不齐时自动补空字符串，确保导出矩阵稳定

### 5. Export Rules

#### 5.1 复制表格

这是“拿去直接粘贴”的主动作。

- 同时写入 `text/html` 和 `text/plain`
- `text/html` 使用当前安全表格片段
- `text/plain` 使用 TSV 作为兜底，提升粘贴到 Excel / Sheets / 文本输入框的兼容性

#### 5.2 复制 CSV

- 含表头
- 使用逗号分隔
- 行分隔统一使用 `\r\n`
- 遵循 RFC 4180 风格转义
- 包含逗号、双引号、换行的单元格统一包裹双引号
- 内部双引号转义为 `""`

#### 5.3 复制 TSV

- 含表头
- 使用 `\t` 分隔
- 行分隔统一使用 `\n`
- 单元格中的真实换行替换为空格，优先保证粘贴到表格软件时不炸列

#### 5.4 复制 Markdown

- 输出规范化 pipe table
- 含表头与分隔行
- 单元格中的 `|` 做转义
- 单元格换行转为 `<br />`
- 不恢复加粗、链接等富文本语义，只输出稳定文本

#### 5.5 复制 JSON

- 默认输出数组对象：`[{ "星期": "周一" }]`
- key 优先使用表头
- 表头为空、重复或不合法时，自动规范为 `column_1`, `column_2`
- 重复列名依次补 `_2`, `_3`

#### 5.6 下载 CSV

- 内容与 `复制 CSV` 一致
- 下载文件增加 UTF-8 BOM，提升中文在 Excel 中的打开稳定性
- 文件名使用 `table-YYYYMMDD-HHmmss.csv`
- 整个流程纯前端完成，不依赖后端

### 6. Feedback and Accessibility

交互反馈沿用代码块复制能力的现有语言：

- 成功后按钮短暂切换为 success / check 状态
- 菜单项触发后提供就地成功反馈，不仅依赖 toast
- 失败时必须显式反馈，不能静默吞掉

可访问性要求：

- 工具栏按钮可通过 Tab 聚焦
- 菜单支持 Enter 触发、Escape 关闭
- `aria-label` 必须包含动作与格式，例如 `Copy table as CSV`

### 7. Streaming and Edge Cases

以下边界作为首期行为约束：

- 流式输出期间未形成正式 `<table>` 前，不展示表格动作栏
- 同一条消息里的多张表格分别独立导出，不合并
- 空单元格保留为空字符串
- 不支持 `rowspan/colspan` 语义展开，遇到复杂表格按线性文本结果降级导出
- 导出内容统一以当前渲染后的表格内容为准，不从原始 Markdown 回推

### 8. Future Structured Format Roadmap

这次只落地 Markdown 表格动作，但应同步形成格式扩展优先级。

高优先级：

- `JSON` code block：复制 JSON、格式化/压缩切换
- `YAML` code block：复制 YAML
- `Mermaid`：图表渲染与复制源码
- `Task list`：更清晰的 checklist 视觉
- `Alert / callout block`：风险、提示、结论等块级强调

中优先级：

- 键值表 / 定义列表
- 数学公式
- 方案对比型 tabbed block
- 引用块增强

低优先级：

- `XLSX`
- `PDF`
- 图片化表格
- 复杂 merged-cell 编辑与消息内交互式 grid

长期可以把消息格式支持归成三条线：

- 文本增强：标题、列表、引用、callout、task list
- 代码增强：SQL、JSON、YAML、Shell、Mermaid
- 结构化数据增强：Markdown 表格、键值表、结果摘要卡片

本次工作是“结构化数据增强”的第一步。

## Files

- Modify: `client/src/features/chat/components/markdown/markdown.tsx`
- Modify: `client/src/features/chat/components/markdown/markdown-table.ts`
- Modify: `client/src/features/chat/components/markdown/markdown.css`
- Add: `client/src/features/chat/components/markdown/table-model.ts`
- Add: `client/src/features/chat/components/markdown/table-serializers.ts`
- Add or Modify: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`
- Add: `client/src/features/chat/components/markdown/__tests__/table-model.test.ts`
- Add: `client/src/features/chat/components/markdown/__tests__/table-serializers.test.ts`

## Testing

至少覆盖以下验证：

- 标准 Markdown 表格渲染后出现统一表格动作栏
- assistant / reasoning / tool 中复用 Markdown 的表格都具备相同动作能力
- `复制表格` 同时写入 `text/html` 与 `text/plain`
- `CSV / TSV / Markdown / JSON` 序列化输出符合约定
- 中文、逗号、双引号、换行、空单元格、重复表头都能稳定处理
- 多张表格时动作作用域正确，不串表
- streaming 未闭合时不展示错误动作栏
- 小屏下工具栏不会破坏表格可读性

执行验证：

- `cd client && npx vitest run client/src/features/chat/components/markdown`
- `cd client && npx tsc --noEmit`

## Acceptance Criteria

1. 所有通过统一 Markdown 渲染链路产出的表格，都显示一致的表格级动作栏
2. 用户可直接执行 `复制表格`、`复制 CSV`、`复制 TSV`、`复制 Markdown`、`复制 JSON`、`下载 CSV`
3. `复制表格` 在富文本与纯文本粘贴目标中都具备稳定表现
4. 表格导出不依赖后端，全部在前端本地完成
5. 多表格、重复表头、空单元格和常见转义场景下导出结果稳定
6. 现有 Markdown 表格渲染与 streaming 行为不回归
