# sql-editor-context-menu Specification

## Purpose
TBD - created by archiving change sql-editor-context-menu. Update Purpose after archive.
## Requirements
### Requirement: Disable Monaco native context menu

The system SHALL disable the Monaco editor's built-in native context menu and replace it with a custom React-based context menu using `@base-ui/react/context-menu`.

#### Scenario: Right-click opens custom menu

- **WHEN** user right-clicks anywhere inside the SQL editor
- **THEN** a custom context menu SHALL appear at the cursor position
- **AND** Monaco's native browser context menu SHALL NOT appear

### Requirement: Execute all SQL

The context menu SHALL include a "Run All" item that executes the entire editor content. The keyboard shortcut `Ctrl+Enter` / `Cmd+Enter` SHALL be displayed.

#### Scenario: Run All executes full editor content

- **GIVEN** a SQL editor with content `SELECT 1; SELECT 2;`
- **WHEN** user clicks "Run All" in the context menu
- **THEN** both statements SHALL be submitted for execution

### Requirement: Execute current statement

The context menu SHALL include a "Run Current Statement" item that identifies the SQL statement at the cursor position (delimited by `;`) and executes it. The keyboard shortcut `Ctrl+Shift+Enter` / `Cmd+Shift+Enter` SHALL be displayed.

#### Scenario: Run current statement with cursor in first statement

- **GIVEN** a SQL editor with content `SELECT 1;\nSELECT 2;`
- **AND** the cursor is on line 1
- **WHEN** user clicks "Run Current Statement"
- **THEN** only `SELECT 1` SHALL be submitted for execution

#### Scenario: Run current statement via keyboard shortcut

- **GIVEN** a SQL editor with content `SELECT 1;\nSELECT 2;`
- **AND** the cursor is on line 2
- **WHEN** user presses `Ctrl+Shift+Enter`
- **THEN** only `SELECT 2` SHALL be submitted for execution

### Requirement: Execute selected SQL

The context menu SHALL include a "Run Selected SQL" item when text is selected. This item SHALL NOT appear when no text is selected. It SHALL execute only the selected text.

#### Scenario: Run Selected appears with selection

- **GIVEN** a SQL editor with selected text `SELECT 1`
- **WHEN** user right-clicks
- **THEN** the "Run Selected SQL" item SHALL be visible

#### Scenario: Run Selected hidden without selection

- **GIVEN** a SQL editor with no text selection
- **WHEN** user right-clicks
- **THEN** the "Run Selected SQL" item SHALL NOT appear

### Requirement: Cancel execution

The context menu SHALL include a "Cancel Execution" item that aborts the currently running query. This item SHALL only appear when a query is executing. The `Escape` keyboard shortcut SHALL be displayed.

#### Scenario: Cancel appears during execution

- **GIVEN** a SQL query is currently executing
- **WHEN** user right-clicks
- **THEN** the "Cancel Execution" item SHALL be visible

#### Scenario: Cancel hidden when not executing

- **GIVEN** no query is executing
- **WHEN** user right-clicks
- **THEN** the "Cancel Execution" item SHALL NOT appear

### Requirement: Format SQL

The context menu SHALL include a "Format SQL" item that formats the editor content. The keyboard shortcut `Ctrl+Shift+F` / `Cmd+Shift+F` SHALL be displayed.

#### Scenario: Format SQL reformats editor content

- **GIVEN** a SQL editor with unformatted content `select * from users where id=1`
- **WHEN** user clicks "Format SQL"
- **THEN** the editor content SHALL be reformatted

### Requirement: Toggle line comment

The context menu SHALL include a "Toggle Comment" item that comments or uncomments the selected line(s) using SQL line comment syntax (`--`). The keyboard shortcut `Ctrl+/` / `Cmd+/` SHALL be displayed.

#### Scenario: Toggle comment adds comments

- **GIVEN** a SQL editor with uncommented line `SELECT 1`
- **WHEN** user clicks "Toggle Comment"
- **THEN** the line SHALL become `-- SELECT 1`

#### Scenario: Toggle comment removes comments

- **GIVEN** a SQL editor with commented line `-- SELECT 1`
- **WHEN** user clicks "Toggle Comment"
- **THEN** the line SHALL become `SELECT 1`

### Requirement: Clipboard operations

The context menu SHALL include Cut (`Ctrl+X`), Copy (`Ctrl+C`), and Paste (`Ctrl+V`) items. Cut and Copy SHALL be disabled when no text is selected.

#### Scenario: Cut and Copy disabled without selection

- **GIVEN** a SQL editor with no text selection
- **WHEN** user right-clicks
- **THEN** Cut and Copy items SHALL appear disabled

#### Scenario: Cut and Copy enabled with selection

- **GIVEN** a SQL editor with selected text
- **WHEN** user right-clicks
- **THEN** Cut and Copy items SHALL be enabled

### Requirement: Undo and Redo

The context menu SHALL include Undo (`Ctrl+Z`) and Redo (`Ctrl+Shift+Z` / `Cmd+Shift+Z`) items. They SHALL be disabled when there is no undo/redo history available.

#### Scenario: Undo available after edit

- **GIVEN** a SQL editor where text was just modified
- **WHEN** user right-clicks
- **THEN** the Undo item SHALL be enabled

#### Scenario: Redo disabled when no redo history

- **GIVEN** a SQL editor where undo was not performed
- **WHEN** user right-clicks
- **THEN** the Redo item SHALL be disabled

### Requirement: Select All

The context menu SHALL include a "Select All" item that selects all text in the editor. The keyboard shortcut `Ctrl+A` / `Cmd+A` SHALL be displayed.

#### Scenario: Select All selects entire editor content

- **GIVEN** a SQL editor with content `SELECT 1`
- **WHEN** user clicks "Select All"
- **THEN** all text in the editor SHALL be selected

### Requirement: Menu visual grouping

The context menu items SHALL be organized into four groups separated by `ContextMenuSeparator`: execution (Run All, Run Current Statement, Run Selected, Cancel), formatting (Format SQL, Toggle Comment), clipboard (Cut, Copy, Paste), and history (Undo, Redo, Select All).

#### Scenario: Menu renders with separator groups

- **WHEN** user right-clicks on the SQL editor
- **THEN** menu items SHALL appear in four visually separated groups
- **AND** separators SHALL render between execution, formatting, clipboard, and history groups

### Requirement: Keyboard shortcut registration

The system SHALL register two new Monaco editor keyboard shortcuts:
- `Ctrl+Shift+Enter` / `Cmd+Shift+Enter` for "Run Current Statement"
- `Ctrl+/` / `Cmd+/` for "Toggle Comment"

#### Scenario: Run Current Statement via keyboard

- **GIVEN** a SQL editor with multiple statements separated by `;`
- **AND** the cursor is positioned within a specific statement
- **WHEN** user presses `Ctrl+Shift+Enter`
- **THEN** only the statement at cursor position SHALL be executed

#### Scenario: Toggle Comment via keyboard

- **GIVEN** a SQL editor with an uncommented line
- **WHEN** user presses `Ctrl+/`
- **THEN** the line SHALL be toggled between commented and uncommented

