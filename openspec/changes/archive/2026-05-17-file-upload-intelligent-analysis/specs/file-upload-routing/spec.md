## ADDED Requirements

### Requirement: AI routes uploaded files based on pre-analysis summary

When a user message contains a `file_upload` part, the AI SHALL examine the `analysis` field and route to the appropriate action based on the file type and analysis results.

#### Scenario: Structured CSV/Excel data routed to import suggestion

- **WHEN** a user uploads a CSV or Excel file and the analysis detects structured tabular data with headers
- **THEN** the AI SHALL suggest importing the data into a database table
- **AND** the AI SHALL propose a table name and target schema based on the file content

#### Scenario: SQL file with DML statements routed to confirmation flow

- **WHEN** a user uploads a SQL file and the analysis detects INSERT/UPDATE/DELETE statements
- **THEN** the AI SHALL describe the statements (type, count, target tables) and ask the user to confirm execution
- **AND** the AI SHALL indicate the risk level from the analysis

#### Scenario: SQL file with DDL statements routed to high-risk confirmation

- **WHEN** a user uploads a SQL file and the analysis detects CREATE/ALTER/DROP statements
- **THEN** the AI SHALL warn the user about schema changes and require explicit confirmation before proceeding

#### Scenario: SQL file with only SELECT statements routed to query editor

- **WHEN** a user uploads a SQL file and the analysis detects only SELECT statements
- **THEN** the AI SHALL suggest opening the SQL in the query editor for execution

#### Scenario: JSON array of objects routed to import suggestion

- **WHEN** a user uploads a JSON file and the analysis detects an array of flat objects
- **THEN** the AI SHALL suggest importing as a database table, similar to CSV routing

#### Scenario: Text/Markdown/Log file routed to content analysis

- **WHEN** a user uploads a TXT/MD/LOG file
- **THEN** the AI SHALL analyze the text content and summarize key findings
- **AND** the AI SHALL NOT suggest database import

#### Scenario: Unrecognized file type routed to user intent inquiry

- **WHEN** a user uploads a file with `analysis.type = "unknown"`
- **THEN** the AI SHALL describe what it can determine (size, partial preview) and ask the user what they want to do

#### Scenario: AI needs more file content than the summary provides

- **WHEN** the AI determines it needs to see more of a large file to make a routing decision
- **THEN** the AI SHALL call `datatalk_file_read` with appropriate offset and limit to read the relevant portion
- **AND** the AI SHALL NOT attempt to read the entire file at once

### Requirement: AGENTS.md defines file upload trigger gate

The AGENTS.md SHALL include a `## File Upload & Analysis` section in the Trigger Gate that activates file routing rules when a `file_upload` part is present in the user message.

#### Scenario: Trigger gate activates on file upload

- **WHEN** a user message contains a `file_upload` part
- **THEN** the AI SHALL follow the file upload routing rules defined in AGENTS.md
- **AND** the AI SHALL NOT attempt to read the raw file from disk directly
