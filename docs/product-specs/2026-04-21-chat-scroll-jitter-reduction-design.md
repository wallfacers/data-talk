# Chat Scroll Jitter Reduction Design

**日期**：2026-04-21  
**状态**：draft  
**范围**：降低 AI 流式输出在接近底部 composer 时的窗体抖动，重点只改滚动结构与 auto-follow 策略，不改变消息视觉与对齐  
**关联现状**：当前 `SplitView` 在消息态下把消息列表与 composer 拆成两个纵向滚动区域；`useAutoScroll` 通过 `MutationObserver` 监听整个消息树；assistant text 在流式阶段使用 `PacedMarkdown` + `Markdown/morphdom` 持续重排

---

## 1. 目标

### 1.1 本期目标

1. 明显降低 AI 流式输出在靠近底部输入框时的抖动感。
2. 保持现有消息视觉、左右对齐、宽度、排版节奏不变。
3. 保持现有 composer 视觉不变，只调整其所在滚动结构与跟随策略。
4. 将修改范围限制在滚动容器与 auto-follow 层，避免无关渲染链路回归。

### 1.2 非目标

1. 本期**不**重做 assistant Markdown 渲染体系。
2. 本期**不**改变 `SessionTurn` / `UserBubble` / `TextPart` 的视觉语言。
3. 本期**不**引入新的消息动画、过渡效果或主题样式。
4. 本期**不**重构 Stage、Split ratio、ChatHeader 或 PromptComposer 内部控件外观。

---

## 2. 现象与根因判断

当前“AI 消息已经贴近底部 composer 时，窗体抖动明显加剧”的体感，与以下结构叠加一致：

1. `SplitView` 在消息态下存在两个纵向区域：
   - 消息列表滚动区
   - composer 自身所在的底部区域
2. `useAutoScroll` 通过 `MutationObserver` 监听整个消息树的 DOM 变化，并在用户仍处于底部附近时，每次变化都立即 `scrollToBottom('auto')`。
3. assistant text 在流式阶段每 `24ms` 级别推进一小段文本，随后重新走 Markdown 生成与 `morphdom`，造成频繁内容高度变化。
4. 当内容底部接近 composer 上沿时，滚动锚点刚好卡在“消息区高度变化”和“composer 边界”之间，因此抖动最明显。

这说明问题主因更偏向：

- 双滚动容器边界
- 过于激进的 auto-follow
- 流式内容持续重排

而不是消息气泡样式本身。

---

## 3. 约束与视觉不变量

这是本次设计的硬约束，不允许在实现时偏离：

1. **消息左右对齐不变**
   - user 继续右对齐
   - assistant 继续左对齐
2. **消息宽度与间距不变**
   - 气泡 `max-width`
   - turn 间距
   - 文本区上下留白
3. **消息视觉不变**
   - 字体、字号、颜色、圆角、边框、阴影不调整
   - `thinking`、reasoning、tool、error 等现有视觉保留
4. **composer 外观不变**
   - 输入框高度、圆角、按钮位置、模型/数据源选择器排列保持一致
5. **不通过“减弱渲染效果”换平稳**
   - 本期不牺牲现有消息内容表现力来换抖动下降

换句话说：这次是“滚动工程修复”，不是“消息 UI 改版”。

---

## 4. 方案对比

### 方案 A：保留现结构，只微调 auto-scroll

做法：

- 保留双滚动容器
- 仅对 `useAutoScroll` 做节流、降低触发频率、减小强制滚底次数

优点：

- 改动最小
- 风险最低

缺点：

- 根因没有处理
- 消息贴近 composer 时仍处在双滚动边界附近
- 只能缓解，不太可能彻底解决

### 方案 B：单滚动容器 + sticky composer + 节流 auto-follow（推荐）

做法：

- 将消息态改成单一纵向滚动根
- composer 固定在同一滚动根内的底部 sticky 区域
- auto-follow 改成基于 `requestAnimationFrame` / 尺寸变化的节流跟随

优点：

- 直接处理当前最可疑的结构性根因
- 改动仍可限制在 `SplitView` 与 `useAutoScroll`
- 不需要碰消息视觉组件

缺点：

- 需要谨慎处理 composer 与消息底部 padding 的关系
- 需要补若干布局回归测试

### 方案 C：保留布局，重做流式渲染链

做法：

- streaming 阶段不再频繁做完整 Markdown + morphdom
- 先渲染轻量文本，完成后再升级到完整 Markdown

优点：

- 理论上能大幅减少底部重排频率

缺点：

- 风险最高
- 直接影响消息“绚烂感”和内容表现节奏
- 改动范围大，不适合作为本轮第一刀

### 推荐

采用 **方案 B**。

原因：

- 它处理的是最直接的结构性根因
- 收益高于方案 A
- 风险远低于方案 C
- 最符合“先解决抖动，但不碰消息视觉”的要求

---

## 5. 选定设计

## 5.1 单滚动根

消息态下，聊天列改为单一 `overflow-y-auto` 滚动根。

新结构目标：

```text
ChatColumn
  ├── ChatHeader
  └── ScrollRoot (唯一纵向滚动容器)
       ├── TurnList
       └── StickyComposer
```

关键点：

1. 不再让 composer 外层单独成为第二个纵向滚动区。
2. `TurnList` 与 composer 属于同一 scroll context。
3. composer 通过 sticky 固定在底部，而不是依赖独立底栏滚动区。

这样做的目的不是改视觉，而是移除“消息内容增长时刚好撞到另一个滚动边界”的结构问题。

## 5.2 Composer 占位策略

因为 composer 改成 sticky，消息列表底部需要保留与 composer 等高的视觉安全区。

约束：

1. 该安全区只用于避免最后一条消息被 composer 压住。
2. 安全区高度应来自稳定的布局常量或实际测量值。
3. 该调整不能改变消息本身宽度、左右对齐和内容排版。

## 5.3 Auto-follow 策略

`useAutoScroll` 不再对整个子树的每次 DOM 变化立即强制 `scrollToBottom()`。

改为：

1. 使用节流后的跟随调度
   - 同一帧内只执行一次滚底
2. 触发信号以“内容高度变化”为主，而不是每次字符级 DOM 改动
3. 只有用户仍处于底部阈值内时才自动跟随
4. 用户一旦主动上滚，停止强制跟随

这会减少：

- 流式字符更新导致的高频 `scrollTop` 写入
- `morphdom` 与滚底竞争造成的视觉抖动

## 5.4 Scroll Anchoring 策略

在 auto-follow 打开时，可为主滚动根显式设置：

- `overflow-anchor: none`

目的：

- 避免浏览器自身滚动锚点机制与我们手写的 follow-to-bottom 打架

该设置仅属于滚动工程层，不影响消息视觉。

---

## 6. 实现边界

本次实现优先只允许修改以下区域：

- `client/src/features/session/split-view.tsx`
- `client/src/hooks/use-auto-scroll.ts`
- 与其直接相关的测试文件

默认**不修改**：

- `client/src/features/chat/components/turn/*`
- `client/src/features/chat/components/effects/paced-markdown.tsx`
- `client/src/features/chat/components/markdown/markdown.tsx`
- `client/src/features/session/prompt-composer.tsx` 的视觉样式

只有当第一轮结构修复后仍无法接受，才允许单独立新 spec 讨论流式渲染链优化。

---

## 7. 风险与防护

### 风险 1：composer 位置变化导致视觉错位

防护：

- 保持同样的 `max-w-3xl`、横向 padding、底部间距
- 只变滚动层级，不变横向布局约束

### 风险 2：sticky composer 造成消息底部被遮挡

防护：

- 为消息内容区增加稳定底部安全区
- 手动验收最后一条长消息与短消息两种情况

### 风险 3：auto-follow 变弱后，流式期间不再可靠贴底

防护：

- 保持“用户在底部阈值内就自动跟随”的产品语义
- 只是从“每次 DOM 变动立刻滚”改成“节流且更稳定地滚”

### 风险 4：为了稳而误伤消息视觉

防护：

- 把视觉不变量写入验收清单
- 任何涉及气泡宽度、间距、排版、颜色的变化都视为回归

---

## 8. 测试与验收

### 8.1 自动化测试

至少补这几类：

1. `SplitView` 结构测试
   - 消息态只有一个主滚动根
   - composer 仍在消息态底部可见
2. `useAutoScroll` 行为测试
   - 底部阈值内：内容增长时自动跟随
   - 用户上滚后：停止强制跟随
   - 多次变化同帧内不重复触发滚底
3. 类型检查
   - `cd client && npx tsc --noEmit`

### 8.2 手动验收

必须覆盖：

1. AI 短回答贴近底部 composer 时，无明显抖动
2. AI 长回答连续流式输出贴近 composer 时，抖动显著低于现状
3. user / assistant 气泡左右对齐与当前一致
4. 气泡宽度、行宽、间距、字体观感与当前一致
5. composer 外观、宽度、按钮位置与当前一致
6. 用户主动上滚查看历史时，auto-follow 不会强拉到底

---

## 9. 结论

本轮不应该先碰 assistant 渲染链，而应先做更低风险、收益更高的滚动层修复：

1. 收敛为单滚动根
2. composer 改为 sticky 底部
3. auto-follow 改为节流、按尺寸变化跟随

这样既能优先解决“靠近底部输入框时抖动明显”的主问题，也能最大程度满足你的核心要求：

**改动不能影响当前消息绚烂感和对齐。**
