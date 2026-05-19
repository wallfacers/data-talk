## ADDED Requirements

### Requirement: File Import Button in Toolbar

The SQL editor toolbar SHALL display a file import button with a `FileUpIcon` icon, positioned next to the Explain button. The button MUST use the `ghost` variant and include a tooltip with accessible label.

#### Scenario: Button renders in toolbar
- **WHEN** the SQL editor toolbar is rendered
- **THEN** a file import button with `FileUpIcon` icon is visible next to the Explain button
- **AND** the button has `aria-label` "导入文件"

#### Scenario: Button tooltip
- **WHEN** user hovers over the file import button
- **THEN** a tooltip displays "导入文件"

### Requirement: File Selection via Hidden Input

Clicking the import button SHALL trigger a hidden `<input type="file">` element. The system MUST allow only a single file selection. The file input SHALL NOT restrict file types via `accept` attribute, so that extensionless files (common for SQL dumps and scripts) can be selected.

#### Scenario: File selector opens on click
- **WHEN** user clicks the import file button
- **THEN** the native file selection dialog opens
- **AND** all file types are selectable (no accept filter)

#### Scenario: User selects a file
- **WHEN** user selects any file
- **THEN** the file content is read via FileReader API

### Requirement: File Reading via FileReader

The system SHALL use the browser FileReader API (`readAsText`) to read the selected file's text content. No server upload SHALL occur. After reading, the system SHALL validate the content to detect likely non-text files.

#### Scenario: File content is read successfully
- **WHEN** a valid text file is selected
- **THEN** the file content is read as UTF-8 text
- **AND** no network request is made

#### Scenario: File exceeds size limit
- **WHEN** user selects a file larger than 1MB
- **THEN** a toast error message is displayed
- **AND** the file content is NOT read

#### Scenario: Non-text file detected
- **WHEN** FileReader completes reading a file
- **AND** the content contains more than 1% `\0` (null) or `�` (Unicode replacement character) characters
- **THEN** a toast warning is displayed: "文件可能不是文本文件"
- **AND** the file content is NOT imported into the editor

### Requirement: Empty Editor — Replace Content

When the SQL editor is empty, the system SHALL replace the editor content with the imported file content directly, without showing a confirmation dialog.

#### Scenario: Import into empty editor
- **WHEN** user selects a valid file
- **AND** the editor has no content (empty string or whitespace only)
- **THEN** the editor content is replaced with the file content
- **AND** no confirmation dialog is shown

### Requirement: Non-Empty Editor — Confirmation and Insert at Cursor

When the SQL editor has content, the system SHALL display an AlertDialog to confirm the import. Upon user confirmation, the file content SHALL be inserted at the current cursor position.

#### Scenario: Confirmation dialog for non-empty editor
- **WHEN** user selects a valid file
- **AND** the editor has existing content
- **THEN** an AlertDialog is displayed asking "编辑器已有内容，文件内容将插入到光标位置，是否继续？"

#### Scenario: User confirms import
- **WHEN** the confirmation dialog is displayed
- **AND** user clicks "确认" (confirm)
- **THEN** the file content is inserted at the current cursor position

#### Scenario: User cancels import
- **WHEN** the confirmation dialog is displayed
- **AND** user clicks "取消" (cancel)
- **THEN** the editor content remains unchanged
- **AND** no file content is inserted
