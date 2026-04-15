MERGE INTO users (id, name, email, created_at) KEY (name) VALUES (DEFAULT, 'Alice', 'alice@example.com', CURRENT_TIMESTAMP);
MERGE INTO users (id, name, email, created_at) KEY (name) VALUES (DEFAULT, 'Bob', 'bob@example.com', CURRENT_TIMESTAMP);
MERGE INTO users (id, name, email, created_at) KEY (name) VALUES (DEFAULT, 'Charlie', 'charlie@example.com', CURRENT_TIMESTAMP);
