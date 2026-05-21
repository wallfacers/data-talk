# Stage Window Layout Refactor Design

**日期**：2026-04-21  
**状态**：approved  
**范围**：将 Stage 从“顶部标题栏 + 标签栏 + 中央内容区 + 底部 Dock”的平铺工作区，重构为“顶部浏览器式页签 + 左侧导航侧栏 + 右侧 Tabs 工作区”的结构化工作台，并统一视觉语言  
**关联现状**：当前 `StageWindow` 已接通 `StageStore` 多 tab 能力，`query_editor` 已可用，`StageDock` 仍是底部悬浮工具入口；`SessionDataContext` 已建立 `connectionId / database / schema` 上下文链路

---

## 1. 背景

当前 Stage 已具备基础多 tab 能力，但整体交互模型仍停留在“内容容器 + 浮动工具入口”的阶段：

1. **入口信息架构弱**：底部 `StageDock` 适合快速触发单个工具，不适合承载持续扩展的 Stage 能力体系。
2. **资源上下文缺位**：用户无法在 Stage 内直观看到“当前有哪些连接 / database / schema 可用，以及我正在哪个资源上下文中工作”。
3. **页签视觉不成立**：当前 `StageTabBar` 是偏 pill 风格的普通 Tabs，无法形成浏览器式工作台的层次感与连续感。
4. **布局语义过平**：Stage 更像消息区旁边塞入的一张大卡片，而不是独立的工作台。

你已经明确了这次的目标：

- 左侧不是简单工具 launcher，而是 **资源浏览器**
- 底部 `StageDock` **整体移除**
- 顶部只保留一行轻量工具入口
- 主导航结构为：**上半区工具、下半区连接资源**
- 左侧单击节点即可触发行为
- 顶部 tab 改为 **Chrome-inspired** 的浏览器式页签，而不是高拟真复刻
- 左侧边栏支持 **收起 / 展开**，但第一版 **不做拖拽调宽**

---

## 2. 目标

### 2.1 本期目标

1. 将 Stage 重构成明确的 **Workbench** 结构：
   - 顶部窗口栏
   - 左侧 `StageSidebar`
   - 右侧 `WorkspacePane`
2. 用左侧轻量工具行替代底部 `StageDock`
3. 新增连接资源浏览器，让用户能在 Stage 中浏览：
   - 连接
   - database / schema
   - 基于资源上下文打开工具页
4. 建立稳定的 tab 去重与激活规则
5. 用统一的视觉语言升级顶部 TabBar 和整体 Stage 气质

### 2.2 非目标

1. 第一版**不实现完整数据库对象浏览器**
   - 不展开到表 / 视图 / 字段层级
2. 第一版**不实现可拖拽 sidebar 宽度**
3. 第一版**不把资源树本身做成内容承载区**
   - 真正工作内容仍由右侧 tab 承载
4. 第一版**不要求 ER / Report / Dashboard 全部功能落地**
   - 可先保留为 disabled / coming soon
5. 第一版**不新建第二套 Stage 全局状态中心**
   - 继续复用现有 `StageStore` 和 `SessionDataContext`

---

## 3. 设计原则

### 3.1 左侧负责定位，右侧负责工作

左侧栏表达“我在什么资源上下文里选择做事”，右侧 tab 表达“我已经打开了哪些工作页”。  
资源树不直接替代内容区，避免导航层和工作层职责混淆。

### 3.2 资源节点与工具动作分离

连接 / database / schema 节点只负责：

- 展开 / 收起
- 选中
- 确认当前资源上下文

真正打开 tab 的，是资源节点下面的工具动作项，比如：

- 在此打开 SQL 编辑器
- 在此打开 ER 图设计器

这样不会出现“点 schema 到底是展开、切上下文，还是开 tab”的歧义。

### 3.3 复用成熟的 tab 能力，不重复造轮子

现有 `StageStore` 的 `openTab / focusTab / closeTab / listTabs` 已成立。  
本次重点是：

- 增加导航态
- 抽出稳定的 open-or-focus 规则
- 重构布局与视觉

而不是重写整个 tab store。

### 3.4 浏览器式工作台，而不是高拟真浏览器

视觉上借鉴 Chrome 的成熟结构：

- 页签连续排列
- 激活态与内容区形成连续面
- 非激活态后退但不脏

但不做拟真的完整复刻，避免与 DataTalk 现有窗口壳和控件语言冲突。

---

## 4. 总体布局

## 4.1 新布局

Stage 重构为以下结构：

```text
StageWindow
  ├── WindowTopBar
  └── WorkbenchBody
       ├── StageSidebar
       │    ├── ToolRow
       │    └── ResourceBrowser
       └── WorkspacePane
            ├── StageTabBar
            └── StageTabContent
```

### 4.2 布局行为

- 保留当前最上方窗口标题与最大化 / 关闭控制
- 删除底部 `StageDock`
- 中间工作区改成左右两栏
- 左侧 `StageSidebar` 为固定宽度
- 左侧支持收起 / 展开
- 右侧 `WorkspacePane` 承载顶部浏览器式页签和主内容区

### 4.3 收起态

收起后左栏缩成窄条，仅保留：

- 工具 icon
- 一个“展开资源栏”控制

不保留复杂迷你树。  
重新展开后恢复：

- 上次展开节点
- 上次选中节点

---

## 5. 左侧导航模型

## 5.1 顶部工具行

左侧顶部不是大面积工具面板，只保留一行轻量入口。

首批入口：

- `SQL 编辑器`
- `ER 图设计器`
- `报表`
- `Dashboard`

其中第一版行为定义如下：

| 工具 | 第一版状态 | 点击行为 |
|------|------------|----------|
| SQL 编辑器 | enabled | 打开或激活全局 SQL 编辑器 tab |
| ER 图设计器 | 可为 disabled | 若未实现则提示“即将支持” |
| 报表 | disabled | 轻量提示“即将支持” |
| Dashboard | disabled | 轻量提示“即将支持” |

说明：

- 即使未实现，也应保留入口，维持 Stage 工作台的结构完整性
- 不再把这些入口放到底部悬浮 Dock 中

## 5.2 资源浏览器

资源浏览器采用三级结构：

```text
连接
  └── database / schema
       ├── SQL 编辑器
       └── ER 图设计器
```

第一版展开粒度：

- 第一层：连接
- 第二层：database / schema
- 第三层：基于该资源上下文的工具动作项

第一版不展开到：

- 表
- 视图
- 字段
- 存储过程

## 5.3 资源节点行为

### 连接节点

单击连接节点时：

- 更新左侧选中态
- 展开或收起该连接的下级资源
- 将该连接视为当前 Stage 资源上下文

### database / schema 节点

单击 database / schema 节点时：

- 更新左侧选中态
- 展开或收起该节点
- 将该节点对应的 `connectionId / database / schema` 设为当前 Stage 资源上下文
- **不直接打开 tab**

### 工具动作项

单击资源下的 `SQL 编辑器` / `ER 图设计器` 时：

- 按当前资源上下文打开或激活对应 tab

---

## 6. Tab 打开与去重规则

## 6.1 两类入口

Stage 内 tab 入口分两类：

1. **顶部工具行入口**
2. **资源浏览器入口**

它们都进入右侧 tab 工作区，但去重规则不同。

## 6.2 顶部工具行的去重规则

顶部工具行打开的 tab 按“工具类型”去重。

示例：

- 再次点击 `SQL 编辑器`
  - 如果已有全局 SQL 编辑器 tab，则直接激活
  - 否则新建一个全局 SQL 编辑器 tab

即：

```text
identity = toolType
```

## 6.3 资源浏览器的去重规则

资源浏览器打开的 tab 按“工具类型 + 资源上下文”去重。

即：

```text
identity = toolType + connectionId + database + schema
```

示例：

- `SQL 编辑器 / conn-a / db1 / public`
- `SQL 编辑器 / conn-a / db1 / sales`

这两个是不同 tab。

再次点击同一资源动作项：

- 若已有相同 identity 的 tab，则直接激活
- 否则创建新 tab

## 6.4 Tab 标题策略

顶部页签要做 Chrome-inspired 视觉，标题必须克制。

建议：

- 主标题只显示工具名
  - `SQL 编辑器`
  - `ER 图设计器`
- 资源上下文不硬塞进长标题
- 详细上下文放到：
  - subtitle
  - tooltip
  - `aria-label`

示例：

```text
主标题：SQL 编辑器
辅助上下文：aaa / db1 / public
```

这样可以避免浏览器式页签被长字符串撑坏。

## 6.5 统一开 tab 接口

不再让各入口直接裸调用 `openTab({ tabId: Date.now()... })` 拼随机 ID。  
新增统一能力，例如：

- `openOrFocusStageToolTab`
- `buildStageTabIdentity`

职责：

1. 根据工具类型与资源上下文生成稳定 identity
2. 在 `StageStore` 中查找已有 tab
3. 已存在则 `focusTab`
4. 不存在才 `openTab`

这样顶部工具行和资源树入口共用一套规则。

---

## 7. 状态模型

## 7.1 保留现有 StageStore 核心能力

以下现有状态与方法继续保留：

- `workspaceTabs`
- `tabsBySession`
- `activeWorkspaceTabId`
- `activeTabIdBySession`
- `openTab`
- `focusTab`
- `closeTab`
- `listTabs`
- `updateTabPayload`

原因：

- 右侧仍然是 tab workspace
- 当前 store 能满足 tab CRUD，不需要重写

## 7.2 新增导航态

本次只新增左侧栏需要的导航态。

建议新增：

```ts
sidebarCollapsedBySession: Map<string, boolean>
sidebarSelectionBySession: Map<string, SidebarSelection | null>
resourceTreeExpandedBySession: Map<string, string[]>
```

其中：

```ts
type SidebarSelection =
  | { kind: 'tool'; tool: 'sql' | 'er' | 'report' | 'dashboard' }
  | { kind: 'connection'; connectionId: string }
  | { kind: 'database'; connectionId: string; database: string }
  | { kind: 'schema'; connectionId: string; database?: string | null; schema: string }
  | {
      kind: 'resource_tool'
      tool: 'sql' | 'er'
      connectionId: string
      database?: string | null
      schema?: string | null
    }
```

说明：

- 这是**导航态**
- 不是执行上下文的唯一真相源

## 7.3 与 SessionDataContext 的关系

明确区分两套概念：

### SessionDataContext

真正的执行上下文：

- `connectionId`
- `database`
- `schema`

它决定查询、schema 读取、AI 工具最终在哪个上下文下执行。

### Sidebar Selection

只是当前左侧浏览位置与选中状态。

### 第一版联动规则

第一版采用最简单、最稳的用户心智：

- 当用户单击连接 / database / schema 资源节点时
  - 更新 sidebar selection
  - 同时同步当前 session 的 data context

也就是：

```text
左侧资源选择 = 当前 session 工作上下文
```

但要注意：

- 这不意味着已打开 tab 跟随后续选择漂移
- 它只影响之后新开的 tab 与当前全局执行上下文

## 7.4 Tab 自身上下文快照

从资源树打开的 tab，应在创建时写入：

- `connectionId`
- `database`
- `schema`

这样 tab 是一个独立工作页快照。

第一版规则：

- 新开 tab 取创建当下的资源上下文
- 已打开 tab 保持自己的上下文快照
- 后续 sidebar 切到别的资源时，已打开 tab 不自动漂移

这与现有“tab override 不反向污染 session”的方向一致。

---

## 8. 组件拆分建议

## 8.1 StageWindow

职责：

- 保留窗口顶栏
- 组织左右工作台结构
- 不再直接承载底部 Dock

应从“卡片内再套卡片”的结构，重构为统一 workbench shell。

## 8.2 StageSidebar

新增组件，职责：

- 管理收起 / 展开
- 渲染顶部工具行
- 渲染资源浏览器
- 转发点击事件为：
  - 选中资源
  - 更新 session context
  - 打开或激活工具 tab

## 8.3 StageToolRow

职责：

- 渲染顶部一行工具入口
- 支持 enabled / disabled / coming soon 状态
- 调用统一 `openOrFocusStageToolTab`

## 8.4 StageResourceBrowser

职责：

- 展示连接树
- 展示 database / schema 节点
- 展示资源下的工具动作项
- 管理展开态与选中态

## 8.5 WorkspacePane

职责：

- 包裹 `StageTabBar`
- 包裹 `StageTabContent`
- 形成右侧统一工作面

## 8.6 StageTabBar

保留组件名，但重写视觉语言与结构。  
功能上继续支持：

- 激活 tab
- 关闭 tab
- 关闭其他
- 关闭全部
- 关闭左侧 / 右侧

---

## 9. 视觉设计

## 9.1 总体气质

视觉目标：

**像浏览器式工作台，但比浏览器更克制、更干净。**

从当前“白卡片 + pill tabs + 底部悬浮工具球”的感觉，切换到：

- 更连续的工作台壳体
- 更稳定的页签层次
- 更安静的侧栏结构

## 9.2 顶部 TabBar：Chrome-inspired

顶部 tab 改成浏览器式而不是 pill 风格。

视觉要求：

1. 横向连续排列，不再像独立按钮
2. 激活 tab 与下方内容区视觉连成一个面
3. 非激活 tab 轻微后退，边界更浅
4. 轮廓用柔和弧线，不做高拟真复刻
5. close 按钮只在 hover / active 更明显
6. icon 缩小，文本优先

避免的问题：

- 不再让 tab 看起来像 shadcn TabsTrigger
- 不再使用过强的 pill 背景块
- 不再让 icon 抢占标题注意力

## 9.3 StageWindow 壳体

外层保留窗口感，内层改成统一 pane 工作台：

- 顶部壳体和 tab bar 有连续关系
- 左侧栏与右侧 workspace 属于同一平面
- 内容区不再“卡片里套卡片”

结果应更像真正的 Stage，而不是被嵌进去的一块局部面板。

## 9.4 Sidebar

左侧栏气质要求：

- 固定宽度，偏窄但不拥挤
- 与右侧 workspace 有轻微明度区分
- 树节点缩进清晰，但线条弱化
- 当前选中节点用柔和背景高亮
- 图标与展开箭头弱化，文字层次主导

顶部工具行建议：

- 简洁横排
- icon + label 小型化
- 已实现工具和未实现工具风格统一

## 9.5 动效原则

只保留少量有意义的过渡：

- sidebar 收起 / 展开
- 树节点展开 / 收起
- tab 激活切换
- hover 状态变化

避免无意义炫技动效，保持“工作台”的稳定感。

---

## 10. 首版细节决策

## 10.1 顶部工具行是否与资源树共用去重规则

不共用。

- 顶部工具行：按工具类型去重
- 资源树工具项：按“工具类型 + 资源上下文”去重

原因：

- 顶部入口表达“打开一个全局工具面板”
- 资源树入口表达“在某个具体资源上下文中打开工具”

## 10.2 database / schema 节点是否直接打开 tab

不直接打开。

原因：

- 节点本身的职责是导航与上下文确认
- 直接打开 tab 会混淆资源层与工具层

## 10.3 工具未实现时是否隐藏

不隐藏。

统一展示，但 disabled / coming soon。

原因：

- 保持 Stage 信息架构稳定
- 避免功能入口随着实现进度忽隐忽现

## 10.4 Sidebar 是否支持拖拽宽度

第一版不支持。

只支持：

- 固定宽度
- 一键收起 / 展开

---

## 11. 迁移策略

## 11.1 现有 Dock

`StageDock` 删除，不再渲染、不再保留入口职责。

## 11.2 现有 StageWindow

保留以下已有能力：

- 窗口顶栏
- 最大化 / 关闭
- tab 关闭菜单
- `StageTabContent` 内容分发

重构以下区域：

- 中央内容布局
- tab bar 视觉
- 工具入口位置

## 11.3 现有 WorkspaceAdapter

需要适配统一开 tab 能力，使：

- 顶部工具行
- 资源树动作项
- AI / UI Object 的 `workspace.open`

最终都能共享同一套 tab identity 规则。

---

## 12. 测试要求

## 12.1 组件测试

至少覆盖：

1. 左侧栏收起 / 展开
2. 单击连接节点只更新选中 / 展开，不直接开 tab
3. 单击 database / schema 节点同步 session data context
4. 单击资源下 `SQL 编辑器` 时：
   - 首次创建 tab
   - 再次点击同一节点时激活已有 tab，不重复创建
5. 顶部工具行 `SQL 编辑器`：
   - 全局只保留一个工具 tab
6. disabled 工具点击时不创建 tab，只给轻量提示

## 12.2 状态测试

覆盖：

1. `sidebarCollapsedBySession`
2. `sidebarSelectionBySession`
3. `resourceTreeExpandedBySession`
4. `openOrFocusStageToolTab` 的 identity 与去重逻辑

## 12.3 视觉回归关注点

重点人工检查：

1. Chrome-inspired 页签激活态是否与内容区连成一体
2. 非激活 tab 是否足够退后但仍清晰可点
3. Sidebar 收起态是否干净、不拥挤
4. 左右分栏在常见宽度下是否稳定
5. 长标题 / 长上下文是否被优雅截断

---

## 13. 成功标准

满足以下条件才算本设计落地成功：

1. Stage 不再依赖底部 Dock
2. 用户能从左侧资源浏览器单击定位到 connection / database / schema
3. 用户能从资源树直接打开资源上下文绑定的 SQL / ER tab
4. 顶部工具行和资源树都具备稳定的“打开或激活”去重行为
5. 顶部 TabBar 的视觉已摆脱当前 pill 风格，形成清晰的浏览器式工作台气质
6. 左侧 Sidebar 可收起 / 展开，且收起态仍好用

---

## 14. 实施建议

为了降低风险，建议按以下顺序实现：

1. 先落结构：
   - `StageWindow` 左右分栏
   - 删除 `StageDock`
2. 再落状态：
   - sidebar 导航态
   - tab identity 能力
3. 再落交互：
   - 资源树
   - 工具行
4. 最后做视觉收口：
   - Chrome-inspired TabBar
   - Sidebar 样式
   - 收起态 polish

这样可以避免视觉改造和结构 / 行为变更同时纠缠。
