DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS products;

CREATE TABLE users (
  id INT PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(100),
  status VARCHAR(20)
);

CREATE TABLE products (
  id INT PRIMARY KEY,
  sku_code VARCHAR(64) UNIQUE,
  name VARCHAR(255)
);

CREATE TABLE orders (
  id INT PRIMARY KEY,
  user_id INT,
  user_email VARCHAR(255),
  amount DECIMAL(10,2),
  status VARCHAR(20),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE order_items (
  id INT PRIMARY KEY,
  order_id INT,
  product_id INT,
  product_sku VARCHAR(64),
  quantity INT,
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_oi_product FOREIGN KEY (product_id) REFERENCES products(id)
);

INSERT INTO users (id, email, name, status) VALUES
  (1, 'alice@example.com', 'Alice', 'active'),
  (2, 'bob@example.com', 'Bob', 'inactive'),
  (3, 'charlie@example.com', 'Charlie', 'active');

INSERT INTO products (id, sku_code, name) VALUES
  (1, 'SKU-001', 'Widget'),
  (2, 'SKU-002', 'Gadget');

INSERT INTO orders (id, user_id, user_email, amount, status) VALUES
  (1, 1, 'alice@example.com', 128.50, 'completed'),
  (2, 1, 'alice@example.com', 256.00, 'pending'),
  (3, 2, 'bob@example.com', 99.99, 'completed');

INSERT INTO order_items (id, order_id, product_id, product_sku, quantity) VALUES
  (1, 1, 1, 'SKU-001', 2),
  (2, 1, 2, 'SKU-002', 1),
  (3, 2, 1, 'SKU-001', 5);
