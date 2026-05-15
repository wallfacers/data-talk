## 1. Domain & Persistence Foundation

- [ ] 1.1 Create `FileUploadPart` record in domain layer (`server/data-talk-domain/.../part/FileUploadPart.java`) — fields: `fileId`, `filename`, `mimeType`, `sizeBytes`, `analysis` (Map). Register in `Part` sealed interface permits list.
- [ ] 1.2 Update all exhaustive switches on `Part` sealed interface across application/infrastructure/adapter layers (ChannelService partForWire, DtEvent serialization, any pattern matches). Run `mvn compile` to catch missing branches.
- [ ] 1.3 Create Flyway migration `V{n}__uploaded_file.sql` — table `uploaded_file` with columns: id, session_id, filename, mime_type, size_bytes, physical_path, analysis_json (CLOB), created_at. Index on created_at for TTL cleanup.
- [ ] 1.4 Create `UploadedFile` record in domain and `UploadedFileRepository` in infrastructure (JDBC insert/findById/deleteById/findOlderThan).
- [ ] 1.5 **mvn install -pl domain -am -DskipTests** + **mvn install -pl infrastructure -am -DskipTests** (domain sealed interface change requires downstream modules to refresh).

## 2. Backend File Analysis Service

- [ ] 2.1 Create `FileAnalysisService` in application layer with method `analyze(Path file, String mimeType, String filename) → FileAnalysisResult`.
- [ ] 2.2 Implement MIME detection: extension-based + magic-number fallback. Reject disallowed types (not CSV/Excel/JSON/SQL/TXT/MD/LOG). Reject 0-byte files.
- [ ] 2.3 Implement SQL analyzer: regex-based statement splitting, type classification (SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/DROP), target table extraction, risk level assignment, preview (first 10 statements). Handle < 4KB full-content vs ≥ 4KB summary threshold.
- [ ] 2.4 Implement CSV analyzer: header row parsing, first 5 row sampling, encoding detection (BOM), estimated row count, column type inference.
- [ ] 2.5 Implement Excel analyzer using Apache POI: sheet list, per-sheet headers + estimated rows. Only metadata read, no full data load.
- [ ] 2.6 Implement JSON analyzer: parse top-level structure, detect array-of-objects vs nested, extract key set / array length / nesting depth.
- [ ] 2.7 Implement TXT/MD/LOG analyzer: line count, first 20 lines preview, file size.
- [ ] 2.8 Unit tests for each analyzer: `FileAnalysisServiceTest` with sample files in `src/test/resources/`.
- [ ] 2.9 **mvn install -pl application -am -DskipTests** (new service class for adapter/controller dependency).

## 3. Backend Upload Endpoint & Action

- [ ] 3.1 Create `FileUploadController` in adapter layer: `POST /api/files/upload` multipart endpoint. Validate MIME/size, delegate to `FileAnalysisService`, store via `UploadedFileRepository`, return JSON response (fileId, filename, mimeType, sizeBytes, analysis).
- [ ] 3.2 Create `FileReadActionHandler` in adapter layer: `@DataTalkAction(id="datatalk_file_read")`, input schema {fileId, offset, limit}, risk level L1, reads from disk and returns content ≤ 4KB.
- [ ] 3.3 Add `HousekeepingScheduler` integration: clean uploaded files older than 24h — delete physical files and SQLite records.
- [ ] 3.4 Update `ChannelService.partForWire()` to serialize `FileUploadPart` to OpenCode wire format (`type: "file"` + metadata fields).
- [ ] 3.5 Integration test: `FileUploadControllerIT` — upload CSV/SQL files, verify response shape, verify file stored on disk.
- [ ] 3.6 **mvn compile -q** + **mvn install -pl adapter -am -DskipTests** full verification.

## 4. AGENTS.md File Upload Rules

- [ ] 4.1 Add `## File Upload & Analysis` section to `AGENTS.md` with decision tree rules for each file type (SQL → DML/DDL routing, CSV/Excel → import suggestion, JSON → structure-based routing, TXT/MD/LOG → text analysis, unknown → ask user).
- [ ] 4.2 Add Trigger Gate row: "user message contains a `file_upload` part" → load `skill:file-upload-routing`.
- [ ] 4.3 Create `skill:file-upload-routing` SKILL.md in `server/data-talk-adapter/src/main/resources/skills/file-upload-routing/SKILL.md` — defines the AI routing rules in detail, references `datatalk_file_read` for on-demand content access, references TODO(Task 13) for `datatalk_import_data`.

## 5. Frontend: Upload UI & State

- [ ] 5.1 Create `useFileUpload` hook: manages upload state (files[], progress, errors), calls `POST /api/files/upload`, returns attachment objects with fileId + analysis.
- [ ] 5.2 Create `FileAttachmentChip` component: displays filename + size + type icon + delete button. States: uploading (progress bar), uploaded (static), error (red). Design tokens: `bg.soft` chip background, `text.base` filename, `text.muted` size, `accent.primary` progress.
- [ ] 5.3 Create `FileDropZone` component: overlay on PromptComposer with `border.strong` dashed border + `bg.subtle`. Hover state: `accent.primary` border. Accept drag events, filter by allowed types/size.
- [ ] 5.4 Integrate into PromptComposer: add `FileDropZone` overlay, render `FileAttachmentChip` list above textarea, handle Ctrl+V paste for files, include `file_upload` parts in message payload on send.
- [ ] 5.5 Create file upload API service: `client/src/services/api/file-upload.ts` — upload function, delete function, TypeScript types for upload response.

## 6. Frontend: Chat Rendering

- [ ] 6.1 Create `FileUploadCard` component: renders file upload summary in UserBubble. Shows filename, size, type icon, analysis summary (type, row count / statement count, preview). Expandable for full detail.
- [ ] 6.2 Update `UserBubble` / message part rendering to detect and render `file_upload` parts using `FileUploadCard`.
- [ ] 6.3 **npx tsc --noEmit** verification.

## 7. Verification & E2E

- [ ] 7.1 Backend full test suite: `cd server && mvn verify` — compile + unit + integration tests pass.
- [ ] 7.2 Frontend full test suite: `cd client && npx tsc --noEmit && npx vitest run` — typecheck + unit tests pass.
- [ ] 7.3 E2E smoke test (manual or Playwright): drag CSV file → verify upload chip appears → send message → verify AI response suggests import. Drag SQL file → verify AI describes statements and asks confirmation.
