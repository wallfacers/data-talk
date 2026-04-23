# Reasoning Auto-Expand Setting Design

**日期**：2026-04-24  
**状态**：已实现（2026-04-24）  
**范围**：在 `设置 > 通用` 新增“思考中自动展开”开关，控制 reasoning 面板在流式思考开始时是否自动展开，并保持思考完成后统一收起  
**关联现状**：`client/src/features/chat/components/turn/reasoning-part.tsx` 当前把“流式思考中”硬编码为默认展开；`client/src/stores/ui-settings-store.ts` 目前只持久化语言和分栏拖拽设置

---

## 1. 目标

### 1.1 本期目标

1. 在 `设置 > 通用` 中为 reasoning 面板新增独立开关，位置放在“分栏拖拽调整”上方。
2. 该开关默认关闭。
3. 开关关闭时，思考开始后不自动展开 reasoning 面板。
4. 开关打开时，思考开始后自动展开 reasoning 面板。
5. 无论开关开关状态如何，思考完成后 reasoning 面板都自动收起。
6. 保留用户手动点击标题展开或收起 reasoning 面板的能力。

### 1.2 非目标

1. 本期不改右侧 Stage 分栏行为。
2. 本期不改变 reasoning 面板的视觉样式、动画、文案结构。
3. 本期不做按会话维度的展开策略记忆。

---

## 2. 方案对比

### 方案 A：把自动展开策略做成全局设置（推荐）

做法：

- 在 `ui-settings-store` 增加持久化布尔值 `autoExpandReasoning`
- `reasoning-part.tsx` 根据该值决定“流式开始时是否自动展开”
- 流式结束时统一自动收起

优点：

- 与“配置里默认关闭/打开”的需求直接对应
- 改动面小，影响范围清晰
- 易于补测试

缺点：

- 只支持全局偏好，不区分不同会话

### 方案 B：把展开策略记到每个会话状态里

优点：

- 可为不同会话保留不同偏好

缺点：

- 超出当前需求
- 状态面扩大到 session/store，复杂度明显更高

### 方案 C：不加设置，仅改默认行为

优点：

- 代码最少

缺点：

- 无法满足用户显式提出的可配置需求

### 推荐

采用 **方案 A**。该需求是纯前端偏好设置，最合适的承载点就是现有 `ui-settings-store`。

---

## 3. 设计输入

- Source: [`client/DESIGN.md`](../../client/DESIGN.md)
- Applied constraints:
  - 设置页属于 `compact` 密度，新增项应复用现有信息架构和开关样式，不能引入新布局范式。
  - 文案、边框、背景、hover/focus 必须继续走现有语义 token 与 shadcn 组件，不写临时视觉规则。
  - 保持现有 `General` 页的节奏和层级，新增项只做最小插入，不重排主题/语言/危险操作区。

---

## 4. 选定设计

### 4.1 设置模型

在 `client/src/stores/ui-settings-store.ts` 中新增：

- `autoExpandReasoning: boolean`
- `setAutoExpandReasoning(v: boolean): void`

持久化规则：

- key 继续复用 `ui-settings`
- 默认值为 `false`
- 与已有 `splitResizable`、`language` 一样存入 `localStorage`

### 4.2 设置页交互

在 `client/src/features/settings/general/general-panel.tsx` 中新增一项：

- 标题：`思考中自动展开`
- 描述：说明它只控制“思考过程中是否自动展开内容面板”，并明确“思考完成后会自动收起”

布局要求：

- 放在“分栏拖拽调整”上方
- 继续使用现有左右分布的 `Switch` 行布局

### 4.3 Reasoning 面板状态机

`reasoning-part.tsx` 的展开规则调整为：

1. 初始渲染或流式开始时：
   - 若 `autoExpandReasoning = true`，自动展开
   - 若 `autoExpandReasoning = false`，保持收起
2. 流式进行中：
   - 用户仍可手动展开/收起
3. 流式结束时：
   - 无条件自动收起

这样可满足：

- 关闭配置时，系统不帮用户展开
- 打开配置时，系统在思考开始时自动展开
- 结束后统一收口，避免历史消息长期占用高度

### 4.4 测试要求

前端测试至少覆盖：

1. `ui-settings-store` 默认值为 `false`
2. `GeneralSettingsPanel` 能显示并切换新开关
3. `ReasoningPart` 在设置关闭时，流式思考开始默认收起
4. `ReasoningPart` 在设置打开时，流式思考开始默认展开
5. `ReasoningPart` 在两种设置下，思考完成后都自动收起

---

## 5. 实现边界

允许修改：

- `client/src/stores/ui-settings-store.ts`
- `client/src/features/settings/general/general-panel.tsx`
- `client/src/i18n/messages.ts`
- `client/src/features/chat/components/turn/reasoning-part.tsx`
- 相关前端测试文件

默认不修改：

- 任何后端代码
- Stage / SplitView 分栏逻辑
- reasoning 面板视觉外观与 Markdown 渲染实现

---

## 6. 风险与防护

### 风险 1：历史 reasoning 消息在回放时错误展开

防护：

- 非流式状态继续以收起为默认
- 测试显式覆盖 completed/history 场景

### 风险 2：自动行为覆盖用户正在进行的手动操作

防护：

- 自动行为仅发生在“流式开始”和“流式结束”两个边界
- 中间阶段保留用户手动控制权

### 风险 3：设置项默认值与持久化不一致

防护：

- 为 store 默认值与切换行为补测试
- 继续复用当前统一的 `ui-settings` 持久化入口
