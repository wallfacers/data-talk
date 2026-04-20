# Bang Query Badge Minimization Design

## Goal

弱化聊天区 `!select` / `!with` 直查消息的视觉存在感，保留“这是一条 SQL 直查消息”的识别能力，但不再占用正文上方一整行 badge。

## Current Problem

当前 `bang_query_user` 需要被识别为直查消息，但显式 badge 会让短 SQL 显得过于突出。它会：

- 抬高消息视觉层级，抢占正文注意力
- 对短 SQL（如 `! select 1`）尤其显眼
- 让直查消息看起来像“特殊卡片”，而不是普通用户输入

## Chosen Approach

采用最小可见标记：

- 移除正文上方的文字 badge
- 对 `displayKind === bang_query_user` 的消息，仅在气泡右上角显示一个极小、低对比度的单色图标
- 图标不带背景、不带边框、不额外占行
- 正文继续按普通用户气泡渲染，保留原始 SQL 文本

## Visual Rules

- 图标尺寸：`10-12px`
- 位置：气泡右上角内侧
- 颜色：沿用气泡前景色，但降低透明度
- 交互：纯提示，不可点击
- 无可见文字

## Accessibility

- 图标保留 `aria-label`，让测试与辅助技术仍能识别其语义
- 可见文案 `SQL 直查` 不再出现在气泡正文中

## Testing

- 更新渲染测试：不再断言可见 `SQL 直查` 文案
- 改为断言存在 bang-query 标记图标，以及正文仍显示原始 SQL 文本
