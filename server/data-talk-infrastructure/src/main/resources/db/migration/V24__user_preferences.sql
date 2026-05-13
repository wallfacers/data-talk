CREATE TABLE user_preferences (
  id         TEXT PRIMARY KEY,
  timezone   TEXT NOT NULL DEFAULT 'UTC',
  date_format TEXT NOT NULL DEFAULT 'yyyy-MM-dd HH:mm:ss',
  updated_at INTEGER NOT NULL
);

INSERT OR IGNORE INTO user_preferences (id, timezone, date_format, updated_at)
VALUES ('default', 'UTC', 'yyyy-MM-dd HH:mm:ss', unixepoch());
