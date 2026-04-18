-- Messages are now authoritative in OpenCode; DataTalk only stores events.
-- See docs/exec-plans/2026-04-19-ai-message-history-backend-plan.md.
DROP TABLE IF EXISTS messages;