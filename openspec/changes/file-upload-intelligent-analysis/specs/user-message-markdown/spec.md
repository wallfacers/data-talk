## MODIFIED Requirements

### Requirement: User message bubble renders markdown

The `UserBubble` component SHALL render user message text as markdown using the existing `Markdown` component, replacing the current plain-text-only `HighlightedText` component.

The markdown rendering SHALL use the same `Markdown` component and sanitization (DOMPurify) as AI message bubbles. User messages SHALL NOT include SQL execute buttons or chart rendering extensions that are specific to AI responses.

The user bubble SHALL retain its existing styling (right-aligned, `bg-primary text-primary-foreground` rounded container) and existing chrome (copy button, timestamp, retry/delete for failed messages, bang-query terminal icon + rerun button).

When the user message contains `file_upload` parts, the `UserBubble` SHALL render a `FileUploadCard` for each file upload part, displaying the filename, file size, file type icon, and analysis summary.

#### Scenario: Plain text user message renders unchanged

- **GIVEN** a user message with text "帮我查一下订单表"
- **WHEN** the `UserBubble` renders
- **THEN** the text SHALL appear identical to the previous plain-text rendering
- **AND** the bubble SHALL remain right-aligned with primary background

#### Scenario: Markdown user message renders formatted

- **GIVEN** a user message with text "**SQL 执行报错**\n\n- **连接**: MySQL-prod\n- **错误**: Table not found"
- **WHEN** the `UserBubble` renders
- **THEN** "SQL 执行报错" SHALL render as bold heading
- **AND** "MySQL-prod" and "错误" SHALL render as bold list items
- **AND** the bubble SHALL remain right-aligned with primary background

#### Scenario: Code fence in user message renders with syntax highlighting

- **GIVEN** a user message containing a markdown code fence with SQL
- **WHEN** the `UserBubble` renders
- **THEN** the code block SHALL render with monospace font and syntax highlighting
- **AND** the code block SHALL NOT have an "Execute SQL" button (AI-only feature)

#### Scenario: Bang-query user message continues to show terminal icon

- **GIVEN** a user message with `displayKind = 'bang_query_user'` and text "!select * from users"
- **WHEN** the `UserBubble` renders
- **THEN** the terminal icon and rerun button SHALL still appear
- **AND** the text SHALL be rendered as markdown (plain text is markdown subset)

#### Scenario: Failed message retains error chrome

- **GIVEN** a user message with `__failed = true` and `__failReason = 'Network error'`
- **WHEN** the `UserBubble` renders
- **THEN** the red error border and retry/delete buttons SHALL still appear
- **AND** the text SHALL be rendered as markdown

#### Scenario: Pending message renders with reduced opacity

- **GIVEN** a user message with `__pending = true`
- **WHEN** the `UserBubble` renders
- **THEN** the bubble SHALL render with `opacity-85`
- **AND** the text SHALL be rendered as markdown

#### Scenario: User message with file upload renders file upload card

- **GIVEN** a user message containing a `file_upload` part with `filename: "orders.csv"` and `analysis.type: "CSV"`
- **WHEN** the `UserBubble` renders
- **THEN** a `FileUploadCard` SHALL appear in the bubble showing the filename "orders.csv", file size, CSV type icon, and a summary of the analysis (headers, estimated rows)
- **AND** the card SHALL be clickable to expand the full analysis detail
