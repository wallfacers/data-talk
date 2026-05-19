## Why

用户需要在 SQL 编辑器中手动导入执行 SQL 文件（如 `.sql` 或 `.txt`），当前只能通过复制粘贴将文本填入编辑器，操作繁琐。提供一个工具栏"导入文件"按钮，让用户一键将本地 SQL 文件内容加载到编辑器中。

## What Changes

- SQL 编辑器工具栏新增"导入文件"按钮（`FileUpIcon`，ghost variant），位于"执行计划"按钮旁边
- 点击按钮触发文件选择对话框（`accept=".sql,.txt"`）
- 通过浏览器 FileReader API 直接读取文件文本内容，无需服务端上传
- 编辑器为空时直接替换全部内容；编辑器已有内容时弹出 AlertDialog 确认后插入到光标位置
- 文件大小上限 1MB（文本编辑器场景）

## Capabilities

### New Capabilities

- `sql-editor-file-import`: SQL 编辑器工具栏支持通过文件选择器导入本地文本文件内容到编辑器

### Modified Capabilities

<!-- None -->

## Impact

- `client/src/features/stage/components/sql-editor-toolbar.tsx` — 新增 `onImportFile` prop 和对应按钮
- `client/src/features/stage/components/sql-workbench-tab.tsx` — 实现文件选择和 FileReader 读取逻辑
- `client/src/features/stage/components/sql-editor-toolbar.test.tsx` — 更新测试
- `client/src/i18n/messages.ts` — 新增按钮标签和确认弹窗的 i18n key
