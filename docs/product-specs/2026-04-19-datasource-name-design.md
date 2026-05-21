# 数据源名称字段与表格样式修复

**日期**: 2026-04-19

## 背景

数据源功能缺少一个用户可自定义的名称字段，用户只能通过主机/端口/数据库来区分不同数据源，不够直观。同时，设置页面的数据源表格存在样式问题：列标题间距不一致，操作列缺少标题。

## 需求

1. 数据源新增 `name` 字段，前后端完整实现
2. `name` 字段必须唯一
3. 修复表格样式：所有列标题间距统一，操作列显示"操作"标题

## 设计

### 数据库层

**Flyway 迁移**: `V7__connection_name.sql`

```sql
ALTER TABLE connections ADD COLUMN name TEXT NOT NULL DEFAULT '';
CREATE UNIQUE INDEX idx_connections_name ON connections(name);

-- 为现有记录生成默认名称
UPDATE connections SET name = '数据源-' || substr(id, 1, 8) WHERE name = '';
```

- `name` 列：`NOT NULL`，添加唯一索引确保名称不重复
- 为现有数据生成默认名称，避免迁移失败

### 后端层

**修改文件**:

| 文件 | 变更 |
|------|------|
| `ConnectionRecord.java` | 新增 `String name` 字段（第2位） |
| `ConnectionDto.java` | 新增 `String name` 字段（第2位） |
| `ConnectionCreateRequest.java` | 新增 `String name` 字段 |
| `ConnectionUpdateRequest.java` | 新增 `String name` 字段 |
| `ConnectionService.java` | `create()`/`update()` 处理 name 参数 |
| `ConnectionRepository.java` | `insert()`/`update()` SQL 添加 name 列 |
| `JdbcDbConnectionRepository.java` | 实现层 SQL 更新 |

**唯一性校验**: 依赖 SQLite 唯一索引约束。捕获 `SQLException` (SQLite error code 2067 - UNIQUE constraint failed) 返回 409 Conflict 响应。

### 前端层

**修改文件**:

| 文件 | 变更 |
|------|------|
| `api.ts` 类型 | 由后端 OpenAPI 生成，自动同步 |
| `ConnectionFormPanel.tsx` | 新增名称输入框（必填，第一项） |
| `DataSourcesPage.tsx` | 表格新增"名称"列（第一列），修复样式 |

**表格布局**:
- 列顺序：名称 | 类型 | 地址 | 数据库 | 用户 | 操作
- 所有 `<th>` 统一 `pb-2` 样式
- 操作列 `<th>` 添加"操作"标题

### 错误处理

- 名称重复：返回 HTTP 409 + 提示"名称已存在"
- 名称空：前端校验必填，后端校验 `NOT NULL`

## 任务范围

- 后端：数据库迁移 + 6个 Java 文件 + 1个 Repository 实现
- 前端：2个组件文件
- 测试：更新相关测试用例的断言

## 不包含

- 名称长度限制（暂不做）
- 名称修改历史（暂不做）