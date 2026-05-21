## Context

SQL 编辑器工具栏 (`sql-editor-toolbar.tsx`) 当前提供运行、格式化、执行计划三个操作按钮。用户导入本地 SQL 文件需要手动打开文件、全选、复制、切回编辑器、粘贴，操作繁琐。

此设计在工具栏新增"导入文件"按钮，通过浏览器 FileReader API 直接读取本地文本文件内容并填充到编辑器中，无需服务端参与。

**Design Inputs** (来自 `client/DESIGN.md`):
- 工具栏使用 `bg.soft`，density 为 compact
- 新按钮使用 `ghost` variant，与已有 Format/Explain 按钮一致
- 图标使用 `FileUpIcon` (lucide-react)，`aria-label` 确保无障碍访问
- 状态不可仅依赖颜色传达（按钮必须带 tooltip + aria-label）
- 使用语义 token（`bg.soft`, `border.subtle`），不直接使用原始色值

## Goals / Non-Goals

**Goals:**
- 在 SQL 编辑器工具栏新增导入文件按钮，支持选择任意文件（无 accept 后缀限制）
- 通过 FileReader API 读取文件内容，无需服务端上传
- 编辑器为空时直接替换内容；有内容时弹窗确认后插入光标位置
- 对非文本文件或超大文件给出明确错误提示

**Non-Goals:**
- 不支持批量多文件导入（单文件即可满足场景）
- 不上传到服务端（非聊天附件场景，无需持久化）
- 不修改后端 API
- 不支持拖拽导入（仅文件选择器方式）

## Decisions

### 决策 1: 使用 FileReader API 而非服务端上传

**选择**: 浏览器端 FileReader.readAsText()

**理由**:
- SQL 编辑器工具栏是 workspace 级别组件，不强制绑定 session，`useFileUpload` 需要 sessionId
- 即时读取，无网络延迟
- 场景是"把文本填入编辑器"，无需将文件持久化到服务端
- 隐私考虑：文件内容不离开用户机器

**替代方案**: 复用 `useFileUpload` + `/api/files/upload` → 需 sessionId，多一次网络往返，且功能过剩（上传、附件管理、并发控制等都用不上）

### 决策 2: 空编辑器替换 vs 有内容时插入光标

**选择**: 空编辑器 → `setSqlText()` 替换；有内容 → AlertDialog 确认 → `insertAtCursor()` 插入

**理由**:
- 空编辑器场景：用户就是要把文件内容作为 SQL 来编辑/执行，直接替换最符合直觉
- 有内容场景：保留用户已有 SQL，将文件内容追加到光标位置，更灵活
- 确认弹窗防止误操作覆盖已有内容

### 决策 3: 文件类型和大小限制

**选择**: 不设置 `accept` 属性（文件选择器不过滤任何文件类型），上限 1MB，配合非文本文件检测

**理由**:
- 现实中 SQL 文件经常没有 `.sql` 后缀（导出脚本、临时文件、无后缀命名），去掉 accept 限制让用户总能选到文件
- FileReader.readAsText() 读二进制文件不会崩溃，只是内容无意义——配合 `\0` 和 `�` 密度检测，读到非文本文件时 toast 提示
- 1MB 大小限制已经兜底安全性和性能
- 超出限制时 toast 提示用户

### 决策 4: 按钮位置和样式

**选择**: 放在"执行计划"按钮旁边，`ghost` variant + `FileUpIcon`

**理由**:
- 与 Format / Explain 按钮的 `ghost` variant 一致，视觉统一
- `FileUpIcon` 语义明确（文件 + 向上箭头 = 导入文件）
- 放在左侧操作按钮组，与 Run / Format / Explain 作为同级编辑器操作

## Risks / Trade-offs

- **文件编码**: FileReader 默认 UTF-8，非 UTF-8 文件（如 GBK）可能乱码 → 当前用户群以 UTF-8 为主，后续可按需添加编码检测
- **非文本文件**: 去掉 accept 后用户可能误选二进制文件 → FileReader 读完后检测内容中 `\0` 或 `�`（Unicode replacement character）的密度，超过阈值（如 1%）则 toast 提示"文件可能不是文本文件"，不导入
- **大文件性能**: 1MB 上限确保浏览器不会卡死 → 上限可调，1MB 已覆盖绝大多数 SQL 文件
- **与 AI 生成内容冲突**: 如果编辑器内容由 AI 生成，用户手动导入文件后内容混合 → 用户主动操作，确认弹窗已降低误操作风险

## Migration Plan

无需迁移。纯前端新增功能，无后端变更，无数据库 schema 变更。

## Open Questions

<!-- None -->
