MERGE INTO users (name, email, created_at) KEY (name) VALUES ('Alice', 'alice@example.com', CURRENT_TIMESTAMP);
MERGE INTO users (name, email, created_at) KEY (name) VALUES ('Bob', 'bob@example.com', CURRENT_TIMESTAMP);
MERGE INTO users (name, email, created_at) KEY (name) VALUES ('Charlie', 'charlie@example.com', CURRENT_TIMESTAMP);
