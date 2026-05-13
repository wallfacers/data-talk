## ADDED Requirements

### Requirement: User preferences table
The system SHALL persist user preferences in a `user_preferences` table with a single-row design (keyed by a fixed ID), storing at minimum `timezone` (IANA timezone ID string) and `date_format` (date format pattern string).

#### Scenario: Table schema
- **WHEN** the Flyway migration is applied
- **THEN** a `user_preferences` table exists with columns: `id TEXT PRIMARY KEY`, `timezone TEXT NOT NULL DEFAULT 'UTC'`, `date_format TEXT NOT NULL DEFAULT 'yyyy-MM-dd HH:mm:ss'`, `updated_at INTEGER NOT NULL`

#### Scenario: Default values on first access
- **WHEN** no row exists for the fixed ID
- **THEN** the repository returns a default with `timezone = 'UTC'` and `date_format = 'yyyy-MM-dd HH:mm:ss'`

### Requirement: Read user preferences API
The system SHALL expose a REST endpoint `GET /api/preferences` that returns the current user's timezone and date format preferences.

#### Scenario: Successful read
- **WHEN** a GET request is made to `/api/preferences`
- **THEN** the response contains `{ "timezone": "Asia/Shanghai", "dateFormat": "yyyy-MM-dd HH:mm:ss" }` with HTTP 200

#### Scenario: No preferences stored yet
- **WHEN** a GET request is made and no preferences row exists
- **THEN** the response contains the system default values with HTTP 200

### Requirement: Update user preferences API
The system SHALL expose a REST endpoint `PUT /api/preferences` that accepts `timezone` and `dateFormat` fields and persists them.

#### Scenario: Successful update
- **WHEN** a PUT request is made with `{ "timezone": "America/New_York", "dateFormat": "MM/dd/yyyy HH:mm:ss" }`
- **THEN** the values are persisted and HTTP 200 is returned with the updated values

#### Scenario: Invalid timezone rejected
- **WHEN** a PUT request is made with `{ "timezone": "Not/A_Real_Zone" }`
- **THEN** HTTP 400 is returned with an error message indicating the timezone is not valid

#### Scenario: Partial update
- **WHEN** a PUT request is made with only `{ "timezone": "Asia/Tokyo" }`
- **THEN** the timezone is updated and dateFormat remains unchanged
