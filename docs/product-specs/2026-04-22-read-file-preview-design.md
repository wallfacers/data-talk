# Read File Preview In Session Stage Design

- **日期**：2026-04-22
- **状态**：proposed
- **前置**：
  - [AI Message Rendering Migration](./2026-04-19-ai-message-rendering-migration-design.md)
  - [Stage UI Object Protocol](./2026-04-20-stage-ui-object-protocol-design.md)
  - [Stage SQL Workbench Rebuild Design](./2026-04-21-stage-sql-workbench-rebuild-design.md)
  - [Stage SQL Workbench Polish Design](./2026-04-22-stage-sql-workbench-polish-design.md)

## 1. 背景与目标

当前 OpenCode `read` 工具结果在聊天区只走通用工具卡片 fallback：标题展示 `read`，副标题展示 `filePath`，正文直接把 `state.output` 原样放进 `<pre>`。这意味着：

- `<path> / <type> / <content>` 只是纯文本
- 文件正文不会按后缀做语法高亮
- 用户无法把一次 `read` 结果提升到工作台内持续查看

用户目标不是把这类结果做成全局工作台 Tab，也不是扩展后端 ontology artifact，而是把单次 `read` 文件结果同步为**当前会话内**可反复查看的预览卡片，并在工作台中以 Tab 承载。

本次目标：

1. 识别 `tool=read` 中的文件型输出
2. 在聊天工具卡片上提供“同步到工作台”入口
3. 将该结果打开为当前会话内的 `file_preview` Tab
4. 在工作台中只对文件正文做高亮渲染，文件属性保持普通文本
5. 全程只改前端，不改后端协议与 SSE 事件结构

## 2. 范围与非目标

### 2.1 范围

- `read` 工具的前端专属 renderer
- `read` 文件结果字符串解析 helper
- 一个新的 session-scoped `file_preview` Stage tab 类型
- 工作台内容区对 `file_preview` 的渲染分发
- 文件预览 Tab 的 Tag 元数据区与正文预览区
- 基于文件后缀的语言映射与只读高亮
- 对应 vitest 单测

### 2.2 非目标

- 不改后端 `read` 工具输出结构
- 不引入新的 SSE / RPC 事件
- 不把 `read` 结果写入 ontology artifact 时间线
- 不新增 `Artifact.kind = 'file'`
- 不把该预览做成 workspace-scoped 全局 Tab
- 不改聊天区普通 markdown/code block 的通用高亮链路
- 不实现“重新读取文件”“加载剩余内容”等协议扩展能力

## 3. 方案选择

### 3.1 推荐方案

采用“`read` 专属 renderer + session `file_preview` tab”的前端闭环方案：

- 聊天区新增 `read` 工具专属 renderer，而不是继续走 `GenericTool`
- renderer 将 `state.output` 解析为结构化文件预览数据
- 点击“同步到工作台”时，打开当前 session 的 Stage，并创建或聚焦 `file_preview` Tab
- 工作台内容区新增 `FilePreviewTab` 组件，负责 Tag 信息与正文预览
- 正文预览复用仓库已存在的 Monaco 编辑器栈，以只读模式提供语法高亮

该方案最贴合“当前会话预览卡片/产物”的目标，同时避免把一次性文件读取结果塞进更重的 artifact/ontology 生命周期。

### 3.2 不选方案

- 扩展 ontology artifact，新增 `kind='file'`
  - 侵入面过大，需要连带修改 reducer、canvas、dispatcher、timeline 和后续版本语义
  - `read` 输出本质是前端可消费的临时预览，不值得上升到 ontology 级别
- 只增强聊天区工具卡片
  - 改动最小，但无法满足“同步到工作台，以 Tab 承载”的需求
- 做成 workspace-scoped 全局 Tab
  - 与用户明确选择的“当前会话预览卡片/产物”不一致

## 4. 交互与行为

### 4.1 聊天区行为

仅当以下条件同时满足时，`read` 卡片显示“同步到工作台”按钮：

- `part.tool === 'read'`
- `part.state.status === 'completed'`
- `part.state.output` 是字符串
- 能从该字符串中提取出 `<type>file</type>` 与 `<content>...</content>`

按钮点击行为：

1. 打开当前 session 的 Stage
2. 创建或聚焦一个 session-scoped `file_preview` Tab
3. 若同一条 `read` 结果已经同步过，则不重复开 Tab，只聚焦已有 Tab

### 4.2 工作台行为

`file_preview` Tab 的标题使用文件名，如 `AGENTS.md`。若无法从路径解析文件名，则回退为 `read output`。

Tab 内容由两部分组成：

- 顶部 Tag 区：语言、文件类型、截断状态
- 正文区：仅展示 `<content>` 中的文件正文，并按后缀高亮

以下信息保持普通文本，不做“绚烂”：

- 完整路径
- 原始 `<type>` 值
- 其他文件属性/辅助说明

### 4.3 重复打开规则

同一 session 下，`read` 结果通过稳定 `sourceKey` 去重：

- 优先使用 `callID`
- 若无 `callID`，回退为 `${messageID}:${part.id}`

同一 `sourceKey` 对应的 `file_preview` Tab 在一个 session 中至多存在一个。

## 5. 解析与数据模型

### 5.1 输出解析策略

不将 `state.output` 当作 HTML 或 Markdown 渲染，而是当作普通字符串解析。解析 helper 仅识别这三个包裹标签：

- `<path>...</path>`
- `<type>...</type>`
- `<content>...</content>`

实现策略采用轻量位置提取，而不是引入通用 XML parser：

- `path`、`type` 取首个成对标签内容
- `content` 取首个 `<content>` 与最后一个 `</content>` 之间的原始子串，保留换行与缩进

这样可以尽量容忍正文中出现 `<`、`>` 或 markdown 标记，不把文件内容本身误当成结构标签。

若解析失败，则 `read` renderer 自动回退为当前普通工具卡片展示，不影响其他工具或其他 `read` 输出形态。

### 5.2 `file_preview` tab payload

`StageTab.type` 新增 `file_preview`，其 `payload` 采用稳定结构：

```ts
type FilePreviewPayload = {
  sourceKey: string
  filePath: string | null
  filename: string
  fileType: string
  content: string
  truncated: boolean
  language: string
}
```

字段约束：

- `filePath`：来自 `<path>`，允许为空
- `filename`：由 `filePath` 推导，失败时回退为 `read output`
- `fileType`：来自 `<type>`，当前预期固定为 `file`
- `content`：原始正文
- `truncated`：来自 `part.state.metadata?.truncated === true`
- `language`：由文件名后缀映射得到，未知时回退 `plaintext`

## 6. 实现设计

### 6.1 聊天区边界

新增 `read` 专属 renderer，替代当前 `GenericTool` fallback。职责：

- 解析 `state.output`
- 在可解析时展示结构化摘要
- 提供“同步到工作台”按钮
- 无法解析时回退普通文本输出

推荐新增文件：

- `client/src/features/chat/components/tools/renderers/read-file.tsx`
- `client/src/features/chat/components/tools/renderers/read-file-output.ts`

`ToolRegistry` 中为 `read` 显式注册该 renderer。

### 6.2 打开/聚焦逻辑

新增一个 session tab helper，职责类似现有 `openOrFocusStageToolTab`，但服务于文件预览：

- 输入：`sessionId`、结构化文件预览数据、`sourceKey`
- 逻辑：在当前 session 的 `tabsBySession` 中查找 `type='file_preview' && payload.sourceKey===sourceKey`
- 找到则 `focusTab`
- 找不到则 `openTab`

这样可避免把“打开工作台文件预览”的逻辑散落在聊天 renderer 内。

### 6.3 Stage 内容分发

当前 [StageTabContent](/home/wushengzhou/workspace/github/data-talk/client/src/features/stage/components/stage-tab-content.tsx:10) 只渲染 `query_editor`。本次应扩展为按 `tab.type` 分发：

- `query_editor -> SqlWorkbenchTab`
- `file_preview -> FilePreviewTab`
- 其他类型继续返回 `null`

### 6.4 `FilePreviewTab` 结构

`FilePreviewTab` 作为独立组件，建议文件位置：

- `client/src/features/stage/components/file-preview-tab.tsx`

UI 结构：

1. 顶部信息条
   - 文件名标题
   - 普通文本路径
2. Tag 行
   - 语言 Tag
   - 类型 Tag
   - `truncated` 时显示告警 Tag
3. 正文区
   - 只读代码预览

Tab 页签图标建议新增 `file_preview -> FileTextIcon`，避免继续落到默认 `SparklesIcon`。

## 7. 高亮与语言映射

### 7.1 高亮方案

正文预览复用现有 Monaco 依赖与主题体系，不新增第二套高亮引擎。实现上采用只读 Monaco：

- `readOnly: true`
- 关闭运行/格式化等交互
- 保留语法高亮、滚动、选中复制、行号

这样可以避免在聊天 markdown 链路之外再引入 `shiki` / `highlight.js` 一类新依赖，并与 Stage 现有编辑器视觉保持一致。

### 7.2 Monaco 主题复用

现有 `SqlMonacoEditor` 内部定义了 `datatalk-sql-light/dark` 主题。为避免 `FilePreviewTab` 单独挂载时主题未注册，本次应将 Monaco 主题注册逻辑抽到共享 helper，由 SQL 编辑器和文件预览共同调用。

推荐新增：

- `client/src/features/stage/components/monaco-theme.ts`

该 helper 只负责：

- 注册 DataTalk light/dark Monaco theme
- 返回当前主题名

### 7.3 文件后缀映射

初版仅支持常见后缀，未知统一回退 `plaintext`：

- `.md` -> `markdown`
- `.json` -> `json`
- `.yml` / `.yaml` -> `yaml`
- `.js` -> `javascript`
- `.jsx` -> `javascript`
- `.ts` -> `typescript`
- `.tsx` -> `typescript`
- `.java` -> `java`
- `.sql` -> `sql`
- `.sh` -> `shell`
- `.xml` -> `xml`
- `.properties` -> `ini`
- `.toml` -> `toml`
- 无法识别 -> `plaintext`

即使 Monaco 对个别语言回退到弱高亮，本次也接受；目标是覆盖常见文件后缀，不追求一次性做全量语言生态。

## 8. 测试策略

需要至少覆盖以下三层：

1. `read` renderer 单测
   - 文件型输出时显示“同步到工作台”按钮
   - 非文件输出时不显示按钮
   - 解析失败时回退普通工具输出
2. Stage/Tab 单测
   - 点击按钮后创建 `file_preview` session tab
   - 同一 `sourceKey` 重复点击只聚焦，不重复开 tab
   - `StageTabContent` 能渲染 `file_preview`
3. `FilePreviewTab` 单测
   - Tag 正确显示语言、类型、截断状态
   - 正文只展示 `<content>` 内容，不重复显示结构标签
   - 未知后缀回退 `plaintext`

完成实现后需要保持：

- `cd client && npx tsc --noEmit` 通过
- 相关 vitest 通过

## 9. 风险与取舍

- 只解析固定三段标签，意味着若未来后端输出格式变化，需要同步更新 helper；这是有意的，因为当前需求就是围绕既定 `read` 结果形态做增强
- 复用 Monaco 比纯 `<pre>` 更重，但仓库已经在 Stage 主路径依赖 Monaco，新增一处只读预览的边际成本可接受
- 不接 ontology artifact 意味着该预览不会进入 artifact 时间线；这是本次范围内的刻意收敛
- `truncated=true` 时只能提示“内容已截断”，无法在当前协议下主动补拉剩余内容；本次不试图扩展协议

## 10. 验收标准

- `read` 文件结果在聊天区可显示“同步到工作台”入口
- 点击后会在当前 session 的 Stage 中打开或聚焦 `file_preview` Tab
- Tab 以普通文本展示路径/文件属性，以高亮代码区展示正文
- 常见文件后缀可得到合理语言映射，未知后缀安全回退
- 不影响其他工具卡片与现有 `query_editor` Tab 行为
- 前端类型检查与相关测试通过
