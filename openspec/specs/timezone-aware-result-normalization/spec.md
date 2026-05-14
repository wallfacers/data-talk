## ADDED Requirements

### Requirement: Column type metadata in QueryResult
The system SHALL preserve JDBC column type information alongside query results so consumers can identify temporal columns.

#### Scenario: QueryResult carries column types
- **WHEN** a SQL query is executed and returns a result set
- **THEN** the `QueryResult` value object contains `columnTypes: List<Integer>` where each element is a `java.sql.Types` constant for the corresponding column

#### Scenario: Backward compatibility for column types
- **WHEN** a consumer reads `QueryResult` that was created without column type metadata
- **THEN** `columnTypes` is an empty list or null, and consumers handle this gracefully

### Requirement: Temporal value normalization with timezone
The system SHALL convert temporal JDBC result values to formatted strings according to the user's configured timezone and date format when a timezone preference is provided to the normalizer.

#### Scenario: java.sql.Timestamp converted to user timezone
- **WHEN** a result set contains a `java.sql.Timestamp` column with value `2025-06-15T08:00:00.000Z` (UTC epoch)
- **AND** the user's timezone is set to `Asia/Shanghai`
- **AND** the user's date format is `yyyy-MM-dd HH:mm:ss`
- **THEN** the normalized value is the string `"2025-06-15 16:00:00"`

#### Scenario: java.sql.Date left as-is
- **WHEN** a result set contains a `java.sql.Date` column with value `2025-06-15`
- **THEN** the normalized value is the string `"2025-06-15"` (no timezone conversion for date-only types)

#### Scenario: java.sql.Time left as-is
- **WHEN** a result set contains a `java.sql.Time` column with value `14:30:00`
- **THEN** the normalized value is the string `"14:30:00"` (no timezone conversion for time-only types)

#### Scenario: java.time.OffsetDateTime converted
- **WHEN** a result set contains a `java.time.OffsetDateTime` column
- **AND** the user's timezone is set to `Asia/Shanghai`
- **THEN** the normalized value is the OffsetDateTime converted to the user's timezone and formatted

#### Scenario: java.time.LocalDateTime passed through as-is
- **WHEN** a result set contains a `java.time.LocalDateTime` column
- **THEN** the normalized value is formatted as-is without timezone conversion

#### Scenario: Non-temporal values unaffected
- **WHEN** a result set contains non-temporal values (strings, numbers, booleans)
- **THEN** those values are normalized as before with no timezone processing

#### Scenario: No timezone preference configured
- **WHEN** no timezone preference is provided (timezone is null or blank)
- **THEN** temporal values pass through without timezone conversion

#### Scenario: Driver-formatted string temporal values
- **WHEN** a JDBC driver returns a temporal value already formatted as a String (e.g., ClickHouse `DateTime`)
- **THEN** the value passes through unchanged (the normalizer does not attempt to parse and re-format)
