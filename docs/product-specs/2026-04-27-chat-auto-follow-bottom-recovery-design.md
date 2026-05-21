# Chat Auto-Follow Bottom Recovery Design

**日期**：2026-04-27  
**状态**：Shipped (2026-04-27)  
**范围**：修复聊天区在 AI 流式输出期间的 auto-follow 恢复条件，只改滚动状态机与测试，不改消息视觉、布局和按钮样式  
**设计输入**：

- [client/DESIGN.md](../../client/DESIGN.md)
  - 保持现有聊天阅读面与 composer 关系不变
  - 不新增视觉状态，不改现有按钮样式、层级和动效
  - 只允许在现有交互语义内修正行为
- [2026-04-23-chat-auto-scroll-reentry-design.md](./2026-04-23-chat-auto-scroll-reentry-design.md)
  - 该文档已定义“用户回到底部后恢复自动跟随”的产品意图
  - 当前代码与测试实现漂移为“手动回到底部也不恢复”，本设计用于纠偏实现

---

## 1. 目标

### 1.1 本期目标

1. 用户主动向上滚动后，聊天区立即停止自动跟随。
2. 用户随后只要真正回到底部，自动跟随立即恢复。
3. “回到底部”按钮与手动拖动/滚轮/触摸滚到底部，行为必须一致。
4. 保持现有按钮显隐、消息布局、滚动容器结构不变。

### 1.2 非目标

1. 本期不重做 `SplitView` 布局，不改 chat/stage 分栏结构。
2. 本期不调整消息动画、Markdown 渲染或流式文本节奏。
3. 本期不新增任何提示文案、toast 或悬浮控件。

---

## 2. 问题定义

当前 `useAutoScroll` 已把“用户主动向上滚动”与“自动跟随许可”拆开，但恢复条件被实现成了“只有显式调用 `scrollToBottom()` 才恢复”，导致两条回到底部路径行为不一致：

1. 用户向上滚动，auto-follow 被关闭。
2. 用户如果点击“回到底部”按钮，会调用 `scrollToBottom()`，于是 follow 恢复。
3. 用户如果手动把滚动条拖到底部，`followEnabled` 仍保持关闭。
4. 下一次流式内容增长时，界面仍停在原位置，不会继续跟随。

这与当前产品预期不一致。用户的心智模型很简单：**只要我已经回到底部，后续新内容就应该继续往下走。**

---

## 3. 方案对比

### 方案 A：沿用 near-bottom 阈值，进入 150px 区间就恢复

优点：

- 改动最小

缺点：

- 用户只是接近底部、仍在阅读历史内容时，也可能被系统重新接管
- “暂停阅读”和“准备恢复跟随”之间仍没有清晰边界

### 方案 B：只有严格触底才恢复（推荐）

做法：

- 保留现有 `FOLLOW_THRESHOLD_PX = 150`，继续只用于 `isAtBottom` 和按钮显隐
- 新增单独的严格触底判定，允许 1 至 2px 误差
- 用户主动上滚后关闭 follow
- 任意路径只要严格触底，就恢复 follow

优点：

- 符合用户直觉
- 不会把“接近底部”误判成“愿意恢复自动跟随”
- 与现有 UI 完全兼容

缺点：

- hook 内需要同时维护展示阈值和恢复阈值两个概念

### 推荐

采用 **方案 B**。恢复 follow 应该由“真正回到底部”触发，而不是由“差不多接近底部”触发。

---

## 4. 选定设计

### 4.1 双阈值、双语义模型

`useAutoScroll` 内维持两个概念：

1. **展示态 `isAtBottom`**：是否在底部附近，继续使用 `FOLLOW_THRESHOLD_PX = 150`
2. **恢复态 `isStrictlyAtBottom`**：是否真正触底，只允许 1 至 2px 几何误差

职责分离：

- `isAtBottom` 只服务 UI，例如“回到底部”按钮显隐
- `isStrictlyAtBottom` 只服务状态机，例如“是否恢复 auto-follow”

---

### 4.2 状态转换规则

滚动事件中的规则如下：

1. 用户向上滚动且尚未严格触底时，`followEnabled = false`
2. 只要当前已严格触底，`followEnabled = true`
3. `scrollToBottom()` 仍然保持恢复 follow 的能力
4. observer / resize / layout-driven scroll 只有在 `followEnabled = true` 时才允许继续滚底

这意味着：

- 手动拖动滚动条到底部，会恢复 follow
- 滚轮或触摸滚到底部，也会恢复 follow
- 点击“回到底部”按钮，仍会恢复 follow
- 只是进入 150px near-bottom 区域，不会恢复 follow

---

### 4.3 会话切换与用户发送

会话切换和用户主动发送消息，仍然属于显式上下文动作，继续保持现有重置语义：

- session 切换时，自动滚到底部并恢复 follow
- `userSendVersion` 变化时，自动滚到底部并恢复 follow

本设计不改变这两条已有规则，只补齐“用户手动回到底部”的恢复路径。

---

### 4.4 测试要求

前端测试至少覆盖：

1. 用户停留在底部时，内容追加会自动滚到底部
2. 用户主动上滚后，内容追加不会自动滚底
3. 用户手动滚动回严格底部后，后续内容追加会恢复自动滚底
4. “回到底部”按钮点击后，后续内容追加同样恢复自动滚底
5. 仅进入 near-bottom 区域但未严格触底时，不应恢复 auto-follow

测试重点仍放在 `useAutoScroll`，避免依赖真实流式链路。

---

## 5. 实现边界

允许修改：

- `client/src/hooks/use-auto-scroll.ts`
- `client/src/hooks/use-auto-scroll.test.tsx`
- 如有必要，少量调整 `client/src/features/session/split-view.tsx` 注释或命名
- 文档与计划索引

默认不修改：

- `client/src/features/chat/components/**`
- `client/src/features/session/prompt-composer.tsx`
- 任何后端代码

---

## 6. 风险与防护

### 风险 1：严格触底容差过小，导致恢复不稳定

防护：

- 用 1 至 2px 容差，而不是要求绝对等于 0
- 在测试里覆盖 `scrollHeight/clientHeight` 取整场景

### 风险 2：near-bottom UI 状态与 follow 恢复状态混淆

防护：

- 明确把“按钮显隐”和“恢复自动跟随”分成两套判定
- 保留现有 `isAtBottom` 对 UI 的职责，不复用作恢复条件

### 风险 3：程序触发的滚动事件误关或误开 follow

防护：

- 保持 `scrollToBottom()` 统一负责程序性恢复
- 用 hook 级测试覆盖“手动上滚 -> 严格触底 -> observer 续跟随”的完整链路
