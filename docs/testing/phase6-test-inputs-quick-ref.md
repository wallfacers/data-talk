# Phase 6 测试输入速查表

## 前置条件

**数据库表要求**：确保连接的数据库有以下表（否则 AI 会报"表不存在"）

```sql
-- 最小测试表结构
CREATE TABLE users (
  id INT PRIMARY KEY,
  name VARCHAR(100),
  email VARCHAR(100)
);

CREATE TABLE orders (
  id INT PRIMARY KEY,
  user_id INT,
  amount DECIMAL(10,2),
  created_at DATETIME
);

-- 插入测试数据
INSERT INTO users VALUES (1, 'Alice', 'alice@test.com');
INSERT INTO users VALUES (2, 'Bob', 'bob@test.com');
INSERT INTO users VALUES (101, 'Test1', 'test1@test.com');
INSERT INTO users VALUES (102, 'Test2', 'test2@test.com');

INSERT INTO orders VALUES (1, 1, 100.00, '2024-01-01');
INSERT INTO orders VALUES (2, 2, 200.00, '2024-01-02');
```

---

## 16 个场景输入速查

### 场景 1: 切走再切回不丢 AI 消息

**输入**：
```
查询 users 表的所有数据
```

**操作**：发送 → 等响应完成 → 点击侧边栏「+」创建新会话 → 点击侧边栏第一个会话切回

**看什么**：对话区消息完整重现，无空白

---

### 场景 2: 刷新页面不丢

**输入**：
```
查询 users 表的所有数据
```

**操作**：发送 → 等响应 → 按 F5 刷新 → 点击侧边栏该会话

**看什么**：消息完整显示，无丢失

---

### 场景 3: 500 字回复流式实时感

**输入**：
```
请详细解释 MySQL 的 InnoDB 存储引擎架构，包括缓冲池、日志系统、锁机制、事务模型，不少于 500 字
```

**操作**：发送 → 观察文字逐字出现过程

**看什么**：平滑流式输出，无跳跃、无卡顿

---

### 场景 4: 乐观 UI 发送 → ID 无缝切换

**输入**：
```
查询 users 表的结构
```

**操作**：点击发送按钮后立即观察消息区（看瞬间）

**看什么**：消息立即出现 → 后端响应后无缝切换真实 ID，无闪烁

---

### 场景 5: 空会话 [] 不报错

**输入**：无

**操作**：点击侧边栏「+ 创建会话」

**看什么**：空状态 UI 正常，无报错、无白屏

---

### 场景 6: OpenCode 离线 → "AI 服务不可用"

**输入**：
```
测试消息
```

**操作**：停止后端/OpenCode → F5 刷新 → 发送消息

**看什么**：黄色离线提示条 + 发送失败提示

---

### 场景 7: Markdown 代码块复制按钮

**输入**：
```
请给我一个 Python HTTP GET 请求的示例代码
```

**操作**：等 AI 返回代码块 → 鼠标悬浮代码块右上角 → 点击 Copy

**看什么**：Copy 按钮 → 点击后变 ✓ → 内容已复制

---

### 场景 8: SQL L1 代码块「执行」

**输入**：
```
查询 users 表的所有数据
```

**操作**：等 AI 返回 SQL 代码块 → 点击右上角「执行」按钮

**看什么**：
- composer 为空时：自动填充 + 自动发送
- composer 有内容时：追加 + toast 提示

---

### 场景 9: SQL L3 预览 + 二次确认

**输入**：
```
删除 users 表中 id 大于 100 的所有记录
```

**操作**：等 preview_sql 卡片 → 点击底部「执行」按钮

**看什么**：卡片显示 "将影响 N 行" + 执行/取消按钮 → 确认后显示结果

---

### 场景 10: 工具 pending shimmer

**输入**：
```
列出当前数据库的所有表
```

**操作**：发送 → 观察 AI 调用工具过程

**看什么**：工具卡片标题 shimmer 扫光动画 → 完成后停止

---

### 场景 11: AI 思考阶段提示

**输入**：
```
分析 users 和 orders 两表的关系，设计一个多表关联查询方案
```

**操作**：发送 → 观察响应开始前的瞬间

**看什么**：显示"思考中…"（TextShimmer）→ reasoning heading 逐词浮现

---

### 场景 12: 连续元数据工具合并

**输入**：
```
列出所有表，然后逐一描述 users 表和 orders 表的结构
```

**操作**：发送 → 等多个工具调用完成

**看什么**：工具卡片折叠为单个 ContextToolGroup → 标题显示 "已收集上下文 · N 项"

---

### 场景 13: 风险等级边框色

**输入 1（绿色 L1）**：
```
查询 users 表的所有数据
```

**输入 2（黄色 L2）**：
```
更新 users 表，将 name 字段改为 'updated'
```

**输入 3（红色 L3）**：
```
删除 users 表中 id 等于 1 的记录
```

**操作**：依次发送三条 → 观察 SQL 卡片左上角风险指示点

**看什么**：SELECT 绿、UPDATE 黄、DELETE 红

---

### 场景 14: Artifact 卡片跳 Stage

**输入**：
```
查询 users 表的数据，将结果保存为 artifact
```

**操作**：等 artifact_created 卡片 → 点击底部「打开 Stage」

**看什么**：右侧 Stage 面板展开 → 显示 Artifact 内容

---

### 场景 15: Pending user 发送失败 → 重试

**输入**：
```
测试消息
```

**操作**：
1. 打开 DevTools → Network → 设为 Offline
2. 发送消息
3. 等待失败

**看什么**：消息变红 → 出现「重试」「删除」按钮

---

### 场景 16: 未知 part type 占位

**输入**：需后端配合

**操作**：让后端返回未知 part type（如 `unknown_type`）

**看什么**：不白屏 → 显示 UnknownPart 黄色占位卡

---

## 一键复制：全部测试输入

按顺序执行，每个场景一条输入：

```
查询 users 表的所有数据
查询 users 表的所有数据
请详细解释 MySQL 的 InnoDB 存储引擎架构，包括缓冲池、日志系统、锁机制、事务模型，不少于 500 字
查询 users 表的结构
（无输入，直接点创建会话）
测试消息
请给我一个 Python HTTP GET 请求的示例代码
查询 users 表的所有数据
删除 users 表中 id 大于 100 的所有记录
列出当前数据库的所有表
分析 users 和 orders 两表的关系，设计一个多表关联查询方案
列出所有表，然后逐一描述 users 表和 orders 表的结构
查询 users 表的所有数据
更新 users 表，将 name 字段改为 'updated'
删除 users 表中 id 等于 1 的记录
查询 users 表的数据，将结果保存为 artifact
测试消息
（需后端配合）
```

---

## 注意事项

1. **场景 6、15 需要断网测试**：用 DevTools Offline 模式
2. **场景 16 需后端配合**：需临时修改后端返回未知 part type
3. **场景 9、13 的 DELETE 操作**：测试前确保表有足够数据，测试后可恢复