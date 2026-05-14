## Why

用户在 SQL 编辑器或其他功能模块遇到报错时，需要手动复制错误信息、切换到 chat、粘贴、再补充上下文才能向 AI 求助。这个流程割裂且低效。需要提供一键"问 AI"通道，自动将完整的错误上下文（连接、数据库、schema、执行的 SQL、错误消息）以结构化 markdown 格式填充到 AI 输入框，让用户审核后发送。同时，用户消息气泡当前仅支持纯文本渲染，发送 markdown 格式的咨询消息后无法正确展示，需要升级为 markdown 渲染。

## What Changes

- **SQL 错误面板新增"问 AI"按钮**：在 `SqlErrorResultPanel` 中添加操作按钮，点击后将完整的报错上下文以 markdown 格式填充到 `PromptComposer`
- **通用"错误咨询 AI"机制**：创建一个可复用的 hook/工具函数，任何模块的错误面板都可以调用它来生成 markdown 格式的上下文并填充到输入框
- **UserBubble 支持 markdown 渲染**：将用户消息气泡从纯文本渲染升级为 markdown 渲染，复用已有的 `Markdown` 组件
- **错误上下文 markdown 模板**：定义标准化的错误报告 markdown 格式，包含连接信息、数据库/schema、执行的语句、错误详情等

## Capabilities

### New Capabilities

- `error-to-ai-consulting`: 提供通用的"将错误上下文发送到 AI 输入框"能力，任何模块可调用
- `user-message-markdown`: 用户消息气泡支持 markdown 渲染，与 AI 消息保持一致的外观

### Modified Capabilities

- `query-editor-context-binding`: SQL 错误面板新增交互操作（"问 AI"按钮），需读取当前 tab 的上下文信息组装咨询内容

## Impact

- **前端**：`SqlErrorResultPanel`、`UserBubble`、`PromptComposer`、新增 `useAskAIAboutError` hook
- **无需后端改动**：错误信息已包含足够上下文，前端仅需从 store 补充连接/schema 信息
- **用户消息模型**：`TextPart` 的渲染逻辑需调整，但数据结构无需变化
- **设计系统**：按钮样式需遵循 `client/DESIGN.md` 语义 token 规范（accent.primary / status.dangerSurface）
