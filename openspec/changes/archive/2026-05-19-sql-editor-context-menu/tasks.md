## 1. SqlMonacoEditor — New Props & Shortcuts

- [x] 1.1 Extend `SqlMonacoEditorProps` with new props: `onRunCurrentStatement: () => void`, `onCancel: () => void`, `isRunning: boolean`
- [x] 1.2 Register `Ctrl+Shift+Enter` keyboard shortcut that calls `onRunCurrentStatement`
- [x] 1.3 Register `Ctrl+/` keyboard shortcut that triggers Monaco `editor.action.commentLine`
- [x] 1.4 Set `contextmenu: false` in `monacoOptions` to disable Monaco native context menu

## 2. SqlMonacoEditor — Context Menu Component

- [x] 2.1 Import `ContextMenu`, `ContextMenuTrigger`, `ContextMenuContent`, `ContextMenuItem`, `ContextMenuSeparator`, `ContextMenuShortcut` from `@/components/ui/context-menu`
- [x] 2.2 Wrap editor container `<div>` with `<ContextMenu>` + `<ContextMenuTrigger render={<div>}` (or use `render` prop to avoid extra DOM wrapper)
- [x] 2.3 Render `<ContextMenuContent>` with 12 menu items in 4 groups:
  - Execution: Run All (`Ctrl+Enter`), Run Current Statement (`Ctrl+Shift+Enter`), Run Selected (conditional), Cancel (conditional)
  - Formatting: Format SQL (`Ctrl+Shift+F`), Toggle Comment (`Ctrl+/`)
  - Clipboard: Cut (`Ctrl+X`), Copy (`Ctrl+C`), Paste (`Ctrl+V`)
  - History: Undo (`Ctrl+Z`), Redo (`Ctrl+Shift+Z`), Select All (`Ctrl+A`)
- [x] 2.4 Wire Monaco built-in actions (Cut/Copy/Paste/Undo/Redo/SelectAll/ToggleComment) via `editorRef.current.trigger('keyboard', '<actionId>')`
- [x] 2.5 Implement context-aware visibility:
  - `showRunSelected`: check `!editor.getSelection()?.isEmpty()` at menu-open time
  - `showCancel`: controlled by `isRunning` prop
  - Clipboard items disabled when no selection
- [x] 2.6 Ensure menu items have accessible labels (icon + text, not icon-only)

## 3. SqlWorkbenchTab — Run Current Statement Handler

- [x] 3.1 Add `handleRunCurrentStatement` callback: get `currentStatementRange` from `parseSqlOutline` + `resolveCurrentSqlOutlineStatement`, extract statement text, call `runQueryEditorSql({ sqlOverride })`
- [x] 3.2 Pass `onRunCurrentStatement={handleRunCurrentStatement}`, `onCancel={handleCancel}`, `isRunning={tabState.executeStatus === 'running'}` to `SqlMonacoEditor`
- [x] 3.3 Extract current cursor line from `useState` / `onCursorChange` for statement resolution

## 4. Verification

- [x] 4.1 Run `cd client && npx tsc --noEmit` — confirm zero type errors
- [ ] 4.2 Manual smoke: right-click on SQL editor, verify all menu items appear, separators render, conditional items show/hide correctly
- [ ] 4.3 Manual smoke: test each menu item triggers correct action (run/format/cut/copy/paste/undo/redo/selectAll/toggleComment)
- [ ] 4.4 Manual smoke: test `Ctrl+Shift+Enter` runs current statement, `Ctrl+/` toggles comment
