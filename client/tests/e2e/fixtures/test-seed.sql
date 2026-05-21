CREATE TABLE IF NOT EXISTS users (
  id INT PRIMARY KEY,
  name VARCHAR(100),
  email VARCHAR(100),
  status VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS orders (
  id INT PRIMARY KEY,
  user_id INT,
  amount DECIMAL(10,2),
  status VARCHAR(20)
);

MERGE INTO users (id, name, email, status) VALUES
  (1, 'Alice', 'alice@example.com', 'active'),
  (2, 'Bob', 'bob@example.com', 'inactive'),
  (3, 'Charlie', 'charlie@example.com', 'active');

MERGE INTO orders (id, user_id, amount, status) VALUES
  (1, 1, 128.50, 'completed'),
  (2, 1, 256.00, 'pending'),
  (3, 2, 99.99, 'completed');
