## ADDED Requirements

### Requirement: Composer supports file drag-and-drop upload

The PromptComposer SHALL display a persistent drop zone at its bottom edge when idle, styled with `border.strong` dashed border + `bg.subtle` background. When a file is dragged over, the drop zone border SHALL highlight with `accent.primary`.

The drop zone SHALL accept files of type CSV (.csv), Excel (.xlsx/.xls), JSON (.json/.jsonl), SQL (.sql), and plain text (.txt/.md/.log). Single file size SHALL NOT exceed 50MB.

#### Scenario: User drags a CSV file onto the composer

- **WHEN** user drags a `.csv` file over the composer drop zone
- **THEN** the drop zone border SHALL change to `accent.primary`
- **AND** the file SHALL begin uploading on drop

#### Scenario: User drags an unsupported file type

- **WHEN** user drags a `.exe` file over the composer drop zone
- **THEN** the drop zone SHALL show an error state
- **AND** the file SHALL NOT be uploaded

#### Scenario: User drags a file exceeding 50MB

- **WHEN** user drops a file larger than 50MB
- **THEN** the system SHALL show an error message indicating the file exceeds the size limit
- **AND** the file SHALL NOT be uploaded

### Requirement: Composer supports file paste via keyboard

The PromptComposer SHALL detect Ctrl+V (or Cmd+V on macOS) paste events containing files and initiate upload for valid file types.

#### Scenario: User pastes a file from clipboard

- **WHEN** user presses Ctrl+V with a file in the clipboard
- **THEN** the file SHALL be validated and uploaded if the type is allowed

#### Scenario: User pastes plain text

- **WHEN** user presses Ctrl+V with text (no files) in the clipboard
- **THEN** the text SHALL be inserted into the composer as normal (no upload triggered)

### Requirement: Uploaded files display as attachment chips

After upload begins, the PromptComposer SHALL display an attachment chip for each file showing: filename, file size, file type icon, and a delete button (×). Multiple files SHALL be supported.

#### Scenario: File successfully uploaded

- **WHEN** a file upload completes
- **THEN** an attachment chip SHALL display the filename, formatted size, type icon, and delete button
- **AND** the chip SHALL NOT show a progress indicator

#### Scenario: File upload in progress

- **WHEN** a file is being uploaded
- **THEN** the attachment chip SHALL display an `accent.primary` progress bar with percentage
- **AND** the delete button SHALL be available to cancel

#### Scenario: User removes an attachment

- **WHEN** user clicks the delete button on an attachment chip
- **THEN** the chip SHALL be removed
- **AND** the uploaded file SHALL be deleted from the server

### Requirement: Backend multipart upload endpoint

The backend SHALL expose `POST /api/files/upload` as a multipart endpoint. It SHALL accept a single file per request, validate MIME type and size constraints, store the file to `~/.data-talk/uploads/<fileId>/<original-name>`, run local pre-analysis, and return the result.

The response SHALL include: `fileId`, `filename`, `mimeType`, `sizeBytes`, and `analysis` (structured metadata).

#### Scenario: Valid CSV file upload

- **WHEN** a valid `.csv` file (2MB) is uploaded
- **THEN** the system SHALL return `fileId`, `mimeType: "text/csv"`, `sizeBytes: 2097152`, and `analysis` containing `headers`, `estimatedRows`, `sampleRows`, and `encoding`

#### Scenario: Rejected executable file

- **WHEN** a `.exe` file is uploaded
- **THEN** the endpoint SHALL return HTTP 422 with an error message indicating unsupported file type

#### Scenario: Zero-byte file

- **WHEN** a 0-byte file is uploaded
- **THEN** the endpoint SHALL return HTTP 422 with an error message indicating empty file

### Requirement: Backend local pre-analysis per file type

The `FileAnalysisService` SHALL analyze uploaded files locally without AI involvement. Small files (< 4KB) SHALL include full content in the analysis. Large files (≥ 4KB) SHALL include only a structured summary.

#### Scenario: Small SQL file (< 4KB) includes full content

- **WHEN** a 2KB `.sql` file is uploaded
- **THEN** the analysis SHALL include `fullContent: true` and the complete file text

#### Scenario: Large CSV file includes summary only

- **WHEN** a 10MB `.csv` file is uploaded
- **THEN** the analysis SHALL include `fullContent: false`, `headers`, `estimatedRows`, `sampleRows` (first 5 rows), and `detectedTypes` (inferred column types)
- **AND** the analysis SHALL NOT include the full file content

#### Scenario: SQL file pre-analysis extracts statement metadata

- **WHEN** a large `.sql` file is uploaded
- **THEN** the analysis SHALL include `statementCount`, `statementTypes` (e.g., INSERT, CREATE TABLE), `targetTables`, and `preview` (first 10 statements)
- **AND** the analysis SHALL include `riskLevel` based on statement types

#### Scenario: Excel file pre-analysis extracts sheet metadata

- **WHEN** an `.xlsx` file is uploaded
- **THEN** the analysis SHALL include `sheets` array, each with `name`, `columnCount`, `headers`, and `estimatedRows`

#### Scenario: Unrecognized file type falls back to basic info

- **WHEN** a file with unrecognized MIME type passes the extension check
- **THEN** the analysis SHALL include `type: "unknown"`, `sizeBytes`, and `preview` (first 20 lines)

### Requirement: File auto-cleanup with 24h TTL

Uploaded files SHALL be automatically deleted by `HousekeepingScheduler` after 24 hours. File metadata in SQLite SHALL also be cleaned up.

#### Scenario: File older than 24 hours is cleaned up

- **WHEN** the HousekeepingScheduler runs and finds an uploaded file record older than 24 hours
- **THEN** the physical file SHALL be deleted from disk
- **AND** the metadata record SHALL be deleted from SQLite

### Requirement: File read-on-demand action

The system SHALL provide a `datatalk_file_read` MCP action that reads a specified portion of an uploaded file. Single read SHALL return at most 4KB.

#### Scenario: AI reads a file segment

- **WHEN** the AI calls `datatalk_file_read` with `fileId`, `offset: 0`, `limit: 4096`
- **THEN** the system SHALL return up to 4KB of file content starting from the specified offset

#### Scenario: AI reads beyond file end

- **WHEN** the AI calls `datatalk_file_read` with `offset` greater than file size
- **THEN** the system SHALL return an empty content response

#### Scenario: Invalid file ID

- **WHEN** the AI calls `datatalk_file_read` with a non-existent `fileId`
- **THEN** the system SHALL return an error response indicating file not found
