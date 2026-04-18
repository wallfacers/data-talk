ALTER TABLE connections ADD COLUMN name TEXT NOT NULL DEFAULT '';

-- 为现有记录生成默认名称（使用 id 前8位）
UPDATE connections SET name = '数据源-' || substr(id, 1, 8) WHERE name = '';

-- 创建唯一索引确保名称不重复
CREATE UNIQUE INDEX idx_connections_name ON connections(name);