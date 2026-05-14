## ADDED Requirements

### Requirement: Timezone selector in Settings > General
The system SHALL display a timezone selector in the Settings > General page that allows users to choose their preferred timezone from the full IANA timezone list.

#### Scenario: Timezone selector renders
- **WHEN** the user opens Settings and navigates to the General section
- **THEN** a timezone selector is visible with a searchable dropdown listing all IANA timezone IDs

#### Scenario: Default timezone
- **WHEN** no timezone preference has been set
- **THEN** the selector displays the system timezone as the default selection

#### Scenario: Timezone search
- **WHEN** the user types "Shanghai" in the timezone selector
- **THEN** the dropdown filters to show `Asia/Shanghai` and other matching entries

#### Scenario: Persist timezone selection
- **WHEN** the user selects a new timezone from the dropdown
- **THEN** the preference is saved to the backend via `PUT /api/preferences`

### Requirement: Date format selector in Settings > General
The system SHALL display a date format selector that allows users to customize how temporal values are displayed.

#### Scenario: Date format selector renders
- **WHEN** the user views the Settings > General page
- **THEN** a date format selector is visible with common format presets and a custom format option

#### Scenario: Regional format presets
- **WHEN** the user selects a date format from the presets
- **THEN** presets include at minimum: `yyyy-MM-dd HH:mm:ss` (China default), `MM/dd/yyyy hh:mm:ss a` (US), `dd/MM/yyyy HH:mm:ss` (EU), `yyyy年MM月dd日 HH:mm:ss` (China long)

#### Scenario: Custom format input
- **WHEN** the user selects the custom format option
- **THEN** a text input appears allowing the user to type a custom date format pattern

#### Scenario: Persist date format selection
- **WHEN** the user changes the date format
- **THEN** the preference is saved to the backend via `PUT /api/preferences`

### Requirement: SQL result table renders temporal columns with user timezone
The system SHALL display temporal result columns using the user's configured timezone format when the server has returned formatted temporal strings.

#### Scenario: Temporal column formatted per user preference
- **WHEN** SQL results are displayed in the data grid
- **AND** the server returns temporal values formatted per the user's timezone and date format
- **THEN** the data grid renders those values as plain strings without additional client-side conversion
