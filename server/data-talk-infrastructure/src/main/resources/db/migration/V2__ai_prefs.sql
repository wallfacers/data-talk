CREATE TABLE ai_user_prefs (
  id            TEXT PRIMARY KEY,
  current_model TEXT,
  updated_at    INTEGER NOT NULL
);

INSERT INTO ai_user_prefs(id, current_model, updated_at)
VALUES ('default', NULL, strftime('%s','now')*1000);

CREATE TABLE ai_model_prefs (
  provider_id TEXT    NOT NULL,
  model_id    TEXT    NOT NULL,
  enabled     INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (provider_id, model_id)
);
