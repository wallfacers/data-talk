## 1. Toolbar Component

- [x] 1.1 Add `onImportFile` optional prop to `SqlEditorToolbarProps`
- [x] 1.2 Import `FileUpIcon` from lucide-react
- [x] 1.3 Add import file button (ghost variant, `FileUpIcon`) next to Explain button in toolbar left group
- [x] 1.4 Add tooltip with aria-label "导入文件" (i18n key: `stage.toolbar.importFile`)

## 2. Workbench Tab — File Import Logic

- [x] 2.1 Add hidden `<input type="file">` ref in `SqlWorkbenchTab` (no accept restriction)
- [x] 2.2 Implement `handleImportFile` callback: trigger hidden input click
- [x] 2.3 Implement `handleFileChange`: validate file size (max 1MB), read via FileReader
- [x] 2.4 Implement non-text file detection: check `\0` and `�` density (>1%) after FileReader completes, toast and abort if likely binary
- [x] 2.5 Implement empty editor logic: `setSqlText(tabId, fileContent)` when editor is empty/whitespace
- [x] 2.6 Implement non-empty editor logic: show AlertDialog for confirmation, then `insertAtCursor` on confirm
- [x] 2.7 Add toast error for file size exceeded

## 3. I18n

- [x] 3.1 Add i18n keys: `stage.toolbar.importFile` (按钮标签), `stage.toolbar.importFile.confirmTitle`, `stage.toolbar.importFile.confirmMessage`, `stage.toolbar.importFile.confirm`, `stage.toolbar.importFile.cancel`, `stage.toolbar.importFile.fileTooLarge`, `stage.toolbar.importFile.notTextFile`
- [x] 3.2 Add Chinese (zh) messages
- [x] 3.3 Add English (en) messages

## 4. Tests

- [x] 4.1 Update `sql-editor-toolbar.test.tsx` to cover new button rendering and click behavior
- [x] 4.2 Verify typecheck passes (`npx tsc --noEmit`)
- [x] 4.3 Verify tests pass (`npx vitest run`)
